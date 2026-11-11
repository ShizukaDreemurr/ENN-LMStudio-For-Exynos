package dev.exynoslm.runtime.litertlm

import android.content.Context
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.RepetitionPenaltyConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dev.exynoslm.model.Backend
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.model.Quantization
import dev.exynoslm.runtime.BackendCapabilities
import dev.exynoslm.runtime.ChatMessage
import dev.exynoslm.runtime.GenerationEvent
import dev.exynoslm.runtime.GenerationRequest
import dev.exynoslm.runtime.InferenceConfig
import dev.exynoslm.runtime.InferenceEngine
import dev.exynoslm.runtime.LoadResult
import dev.exynoslm.runtime.Role
import dev.exynoslm.runtime.Usage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import com.google.ai.edge.litertlm.Backend as LmBackend

/**
 * On-device LLM engine backed by LiteRT-LM (`.litertlm` bundles, e.g. litert-community/Qwen3-0.6B).
 * Serves CPU and GPU. LiteRT-LM also exposes `Backend.NPU` / `Backend.GOOGLE_TENSOR`; those are deliberately NOT used here.
 *
 * API facts relied on (google-ai-edge/LiteRT-LM, tag v0.18.1, `kotlin/java/com/google/ai/edge/litertlm/`; every call used here is also
 * present unchanged in v0.19.0 / main):
 *  - `Config.kt`: `Backend.CPU(threadCount)` / `Backend.GPU()` (sealed class, ~L137-160), `EngineConfig(modelPath, backend, ..., maxNumTokens,
 *    ..., cacheDir)` (~L202), `ConversationConfig(systemInstruction, initialMessages, samplerConfig, maxOutputToken, ...)` (~L262),
 *    `SamplerConfig(topK, topP, temperature, seed)` with Double topP/temperature (~L295), `RepetitionPenaltyConfig(repetitionPenalty)` (~L62).
 *  - `Engine.kt`: `Engine(config)`, `initialize()` (blocking, may take ~10s), `createConversation(config)`, `close()`.
 *  - `Conversation.kt`: `sendMessageAsync(message: Message, callback: MessageCallback, extraContext, repetitionPenaltyConfig, ...)` (~L282),
 *    `cancelProcess()` (~L669), `getTokenCount()` (~L694, KV tokens = prefill + decode), `close()` (~L743), `MessageCallback{onMessage,onDone,onError}` (~L807).
 *    Cancellation surfaces as `onError(java.util.concurrent.CancellationException)` (JniMessageCallbackImpl.onError, statusCode 1).
 *  - `Message.kt`: `Message.user/model/system/tool(...)`, `Contents.of(text)`, `Message.toString()` = concatenated text of the chunk (a delta).
 *  - docs/api/kotlin/getting_started.md: cacheDir may be `context.cacheDir.path`; GPU on Android needs libOpenCL.so / libvndksupport.so (see manifest).
 *
 * LiteRT-LM has no stop-sequence option, so stop sequences and maxNewTokens (besides ConversationConfig.maxOutputToken) are also enforced here.
 * A fresh [Conversation] is created per request from the full chat history (the app owns history; the engine is stateless between calls).
 */
class LiteRtLmEngine(
    private val context: Context,
    override val backend: Backend,
) : InferenceEngine {
    /** Single dedicated thread: engine init and conversation setup/teardown are serialized here. */
    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "litertlm-$backend") }.asCoroutineDispatcher()
    private val lock = Mutex()
    private var engine: Engine? = null
    private var model: LoadedModel? = null
    private var maxTokens = 4096

    override suspend fun load(model: LoadedModel, config: InferenceConfig): LoadResult = withContext(dispatcher) {
        lock.withLock {
            unloadLocked()
            val t0 = System.currentTimeMillis()
            try {
                val lmBackend: LmBackend = when (backend) {
                    Backend.GPU -> LmBackend.GPU()
                    Backend.CPU -> LmBackend.CPU(threadCount = config.cpuThreads.takeIf { it > 0 })
                    // NPU needs a .litertlm compiled for this SoC's NPU (and the vendor dispatch libs in nativeLibraryDir).
                    // Only try it for files whose name says so, so plain CPU/GPU models fall through quickly.
                    Backend.NPU_ENN -> if (NPU_NAME.containsMatchIn(model.localPath.substringAfterLast('/')))
                        LmBackend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
                    else return@withContext LoadResult.Failure("not an NPU-compiled .litertlm (file name needs npu/e9965/exynos)")
                }
                val e = Engine(
                    EngineConfig(
                        modelPath = model.localPath,
                        backend = lmBackend,
                        maxNumTokens = config.maxContextTokens.takeIf { it > 0 },
                        cacheDir = context.cacheDir.path,
                    ),
                )
                e.initialize()
                engine = e
                this@LiteRtLmEngine.model = model
                maxTokens = config.maxContextTokens
                LoadResult.Success(System.currentTimeMillis() - t0, backend)
            } catch (t: Throwable) {
                LoadResult.Failure("${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = callbackFlow {
        val eng = engine
        if (eng == null || model == null) {
            trySend(GenerationEvent.Error("model not loaded")); close(); awaitClose { }; return@callbackFlow
        }
        val s = request.sampling
        val sysText = buildList {
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { add(it) }
            request.messages.filter { it.role == Role.SYSTEM }.forEach { add(it.content) }
        }.joinToString("\n\n")
        val turns = request.messages.filter { it.role != Role.SYSTEM }
        val last = turns.lastOrNull()
        if (last == null || last.role == Role.ASSISTANT) {
            trySend(GenerationEvent.Error("last message must be a user or tool message")); close(); awaitClose { }; return@callbackFlow
        }
        val history = turns.dropLast(1).map { it.toLm() }

        if (s.stopSequences.isNotEmpty()) trySend(GenerationEvent.Warning("stop sequences enforced client-side after generation of the matching token"))

        val conversation: Conversation = try {
            eng.createConversation(
                ConversationConfig(
                    systemInstruction = sysText.takeIf { it.isNotEmpty() }?.let { Contents.of(it) },
                    initialMessages = history,
                    samplerConfig = SamplerConfig(
                        topK = s.topK.coerceAtLeast(1),
                        topP = s.topP.toDouble().coerceIn(0.0, 1.0),
                        temperature = s.temperature.toDouble().coerceAtLeast(0.0),
                        seed = s.seed ?: 0,
                    ),
                    maxOutputToken = s.maxNewTokens.takeIf { it > 0 },
                ),
            )
        } catch (t: Throwable) {
            trySend(GenerationEvent.Error("${t.javaClass.simpleName}: ${t.message}", t)); close(); awaitClose { }; return@callbackFlow
        }

        val t0 = System.currentTimeMillis(); var ttft = -1L
        val sb = StringBuilder(); var count = 0
        val finished = AtomicBoolean(false)
        val penalty = s.repetitionPenalty.takeIf { it > 1.0f }?.let { RepetitionPenaltyConfig(repetitionPenalty = it) }

        fun finish(done: Boolean, stop: String?) {
            if (!finished.compareAndSet(false, true)) return
            if (!done) runCatching { conversation.cancelProcess() }
            val text = stop?.let { sb.substring(0, sb.indexOf(it)) } ?: sb.toString()
            // KV-cache size = prompt + completion tokens (count of streamed chunks approximates completion tokens).
            val promptTokens = runCatching { (conversation.getTokenCount() - count).coerceAtLeast(0) }
                .getOrDefault((last.content.length + sysText.length) / 4)
            if (promptTokens > maxTokens) trySend(GenerationEvent.Warning("prompt ($promptTokens tokens) exceeds context $maxTokens"))
            trySend(GenerationEvent.Usage(Usage(promptTokens, count, ttft.coerceAtLeast(0), System.currentTimeMillis() - t0)))
            trySend(GenerationEvent.Completed(text))
            close()
        }

        try {
            conversation.sendMessageAsync(
                last.toLm(),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        if (finished.get()) return
                        val partial = message.toString()
                        if (partial.isEmpty()) return
                        if (ttft < 0) ttft = System.currentTimeMillis() - t0
                        sb.append(partial); count++
                        trySend(GenerationEvent.TokenGenerated(partial))
                        trySend(GenerationEvent.PartialText(sb.toString()))
                        val stop = s.stopSequences.firstOrNull { it.isNotEmpty() && sb.contains(it) }
                        if (stop != null || (s.maxNewTokens > 0 && count >= s.maxNewTokens)) finish(done = false, stop = stop)
                    }

                    override fun onDone() = finish(done = true, stop = null)

                    override fun onError(throwable: Throwable) {
                        // Our own cancelProcess() after a stop sequence / token limit reports a CancellationException; ignore it.
                        if (finished.compareAndSet(false, true)) {
                            trySend(GenerationEvent.Error("${throwable.javaClass.simpleName}: ${throwable.message}", throwable))
                            close()
                        }
                    }
                },
                emptyMap(),
                penalty,
            )
        } catch (t: Throwable) {
            if (finished.compareAndSet(false, true)) {
                trySend(GenerationEvent.Error("${t.javaClass.simpleName}: ${t.message}", t)); close()
            }
        }
        // Cancellation from the collector (Stop button) aborts native generation immediately.
        awaitClose {
            if (finished.compareAndSet(false, true)) runCatching { conversation.cancelProcess() }
            runCatching { conversation.close() }
        }
    }.flowOn(dispatcher)

    override suspend fun unload() = withContext(dispatcher) { lock.withLock { unloadLocked() } }

    private fun unloadLocked() { runCatching { engine?.close() }; engine = null; model = null }

    private companion object { val NPU_NAME = Regex("npu|e99\\d\\d|exynos|samsung", RegexOption.IGNORE_CASE) }

    override fun capabilities() = BackendCapabilities(
        backend = backend,
        available = true,
        supportedFormats = setOf(ModelFormat.LITERT_LM),
        supportedQuantizations = setOf(Quantization.INT4, Quantization.INT8, Quantization.FP16, Quantization.FP32, Quantization.UNKNOWN),
        supportsSeed = true,
    )

    private fun ChatMessage.toLm(): Message = when (role) {
        Role.USER -> Message.user(content)
        Role.ASSISTANT -> Message.model(content)
        Role.TOOL -> Message.tool(Contents.of(content))
        Role.SYSTEM -> Message.system(content)
    }
}
