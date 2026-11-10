package dev.exynoslm.runtime.llamacpp

import android.content.Context
import dev.exynoslm.model.Backend
import dev.exynoslm.model.ChatTemplate
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.runtime.BackendCapabilities
import dev.exynoslm.runtime.GenerationEvent
import dev.exynoslm.runtime.GenerationRequest
import dev.exynoslm.runtime.InferenceConfig
import dev.exynoslm.runtime.InferenceEngine
import dev.exynoslm.runtime.LoadResult
import dev.exynoslm.runtime.Role
import dev.exynoslm.runtime.Usage
import dev.exynoslm.runtime.cpu.CpuTuning
import dev.exynoslm.runtime.cpu.PromptFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * GGUF inference on CPU through llama.cpp (tag b12257, see runtime-llamacpp/src/main/cpp). Handles any GGUF quantization
 * llama.cpp supports (e.g. gemma-4-E2B-it-Q6_K.gguf), so [BackendCapabilities.supportedQuantizations] is empty = "any".
 *
 * Prompting: by default the GGUF's built-in chat template is rendered by llama.cpp. If the model's template family is not one
 * llama.cpp's non-jinja renderer knows, native falls back to ChatML and a Warning is emitted; set
 * [LoadedModel.chatTemplate] to something other than ENGINE_DEFAULT to render the prompt on the Kotlin side instead.
 */
class LlamaCppEngine(
    @Suppress("unused") private val context: Context,
) : InferenceEngine {
    override val backend: Backend = Backend.CPU

    /** One dedicated thread: a llama.cpp context is not thread-safe, and native calls block it for the whole generation. */
    private val dispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "llm-llamacpp") }.asCoroutineDispatcher()
    private val lock = Mutex()

    /** Written only on [dispatcher]; read from other threads to cancel, hence volatile. */
    @Volatile private var handle: Long = 0L
    @Volatile private var model: LoadedModel? = null

    override suspend fun load(model: LoadedModel, config: InferenceConfig): LoadResult {
        if (!LlamaNative.isAvailable) return LoadResult.Failure("libllama_jni.so is not available for this device ABI (arm64-v8a only)", recoverable = false)
        if (model.format != ModelFormat.GGUF) return LoadResult.Failure("llama.cpp engine only loads GGUF models, got ${model.format}", recoverable = false)
        return withContext(dispatcher) {
            lock.withLock {
                unloadLocked()
                val t0 = System.currentTimeMillis()
                try {
                    handle = LlamaNative.load(model.localPath, config.maxContextTokens, CpuTuning.threadsFor(false), config.useMmap)
                    this@LlamaCppEngine.model = model
                    LoadResult.Success(System.currentTimeMillis() - t0, backend)
                } catch (t: Throwable) {
                    LoadResult.Failure("${t.javaClass.simpleName}: ${t.message}")
                }
            }
        }
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = callbackFlow {
        val h = handle
        val m = model
        if (h == 0L || m == null) {
            trySend(GenerationEvent.Error("model not loaded")); close(); awaitClose { }; return@callbackFlow
        }
        val s = request.sampling
        val stops = s.stopSequences.filter { it.isNotEmpty() }
        val maxStop = stops.maxOfOrNull { it.length } ?: 0

        // System prompt + history -> roles. TOOL has no portable chat role, so it is sent as a prefixed user turn.
        val roles = ArrayList<String>()
        val contents = ArrayList<ByteArray>()
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { roles += "system"; contents += it.toByteArray(Charsets.UTF_8) }
        for (msg in request.messages) {
            when (msg.role) {
                Role.SYSTEM -> { roles += "system"; contents += msg.content.toByteArray(Charsets.UTF_8) }
                Role.USER -> { roles += "user"; contents += msg.content.toByteArray(Charsets.UTF_8) }
                Role.ASSISTANT -> { roles += "assistant"; contents += msg.content.toByteArray(Charsets.UTF_8) }
                Role.TOOL -> {
                    val prefix = "[Tool result${msg.toolName?.let { " from $it" }.orEmpty()}]\n"
                    roles += "user"; contents += (prefix + msg.content).toByteArray(Charsets.UTF_8)
                }
            }
        }
        val rawPrompt = if (m.chatTemplate != ChatTemplate.ENGINE_DEFAULT) PromptFormatter.format(m.chatTemplate, request).toByteArray(Charsets.UTF_8) else null

        val worker = launch(dispatcher) {
            // unload() runs on this same thread; if it ran between flow start and now, the handle is gone.
            if (handle != h) { trySend(GenerationEvent.Error("model was unloaded")); close(); return@launch }
            val t0 = System.currentTimeMillis()
            var ttft = -1L
            val sb = StringBuilder()
            var emitted = 0       // chars of sb already sent downstream
            var stopHit = false

            fun emitUpTo(end: Int) {
                if (end <= emitted) return
                trySend(GenerationEvent.TokenGenerated(sb.substring(emitted, end)))
                emitted = end
                trySend(GenerationEvent.PartialText(sb.substring(0, emitted)))
            }

            val callback = object : LlamaTokenCallback {
                override fun onToken(piece: String): Boolean {
                    if (ttft < 0) ttft = System.currentTimeMillis() - t0
                    val scanFrom = (sb.length - maxStop + 1).coerceAtLeast(0)
                    sb.append(piece)
                    var stopAt = -1
                    for (stop in stops) {
                        val i = sb.indexOf(stop, scanFrom)
                        if (i >= 0 && (stopAt < 0 || i < stopAt)) stopAt = i
                    }
                    if (stopAt >= 0) {
                        stopHit = true
                        sb.setLength(stopAt)
                        emitUpTo(stopAt.coerceAtLeast(emitted))
                        return false
                    }
                    // Hold back a possible partial stop sequence at the tail (and never split a surrogate pair).
                    var safe = sb.length - (maxStop - 1).coerceAtLeast(0)
                    if (safe > 0 && safe < sb.length && sb[safe - 1].isHighSurrogate()) safe--
                    emitUpTo(safe)
                    return true   // collector cancellation is delivered through awaitClose -> LlamaNative.cancel()
                }
            }

            try {
                val r = LlamaNative.generate(
                    h, roles.toTypedArray(), contents.toTypedArray(), rawPrompt,
                    s.temperature, s.topK, s.topP, s.repetitionPenalty, s.seed?.toLong() ?: -1L, s.maxNewTokens, callback,
                )
                val status = r[0]; val promptTokens = r[1]; val completionTokens = r[2]; val templateFallback = r[3] != 0
                if (!stopHit && status != LlamaNative.STATUS_CANCELLED) emitUpTo(sb.length)
                if (templateFallback) trySend(GenerationEvent.Warning("model chat template not supported by llama.cpp's renderer; used ChatML instead (set an explicit chat template for this model)"))
                if (status == LlamaNative.STATUS_CONTEXT_FULL) trySend(GenerationEvent.Warning("context window full; output was cut short"))
                if (status != LlamaNative.STATUS_CANCELLED || stopHit) {
                    trySend(GenerationEvent.Usage(Usage(promptTokens, completionTokens, ttft.coerceAtLeast(0), System.currentTimeMillis() - t0)))
                    trySend(GenerationEvent.Completed(sb.toString()))
                }
            } catch (t: Throwable) {
                trySend(GenerationEvent.Error(t.message ?: t.javaClass.simpleName, t))
            }
            close()
        }

        // Collector cancellation (Stop button) aborts native generation, including a long prompt prefill.
        awaitClose { if (worker.isActive) LlamaNative.cancel(h) }
    }.buffer(Channel.UNLIMITED)   // pieces must never be dropped when the UI collects slower than the native loop produces

    /** Token count under the loaded model's tokenizer, or null if no model is loaded. */
    suspend fun countTokens(text: String): Int? = withContext(dispatcher) {
        val h = handle
        if (h == 0L) null else LlamaNative.tokenCount(h, text.toByteArray(Charsets.UTF_8)).takeIf { it >= 0 }
    }

    override suspend fun unload() {
        val h = handle
        if (h != 0L) LlamaNative.cancel(h)   // the dispatcher may be busy inside a native generate(); make it return first
        withContext(dispatcher) { lock.withLock { unloadLocked() } }
    }

    private fun unloadLocked() {
        val h = handle
        handle = 0L
        model = null
        if (h != 0L) runCatching { LlamaNative.free(h) }
    }

    override fun capabilities() = BackendCapabilities(
        backend = backend,
        available = LlamaNative.isAvailable,
        supportedFormats = setOf(ModelFormat.GGUF),
        supportedQuantizations = emptySet(),   // empty = any quantization llama.cpp can read
        supportsSeed = true,
        unavailableReason = if (LlamaNative.isAvailable) null else "libllama_jni.so missing (arm64-v8a only)",
    )
}
