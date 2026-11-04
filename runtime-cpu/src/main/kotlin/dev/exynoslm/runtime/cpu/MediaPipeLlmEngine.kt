package dev.exynoslm.runtime.cpu

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dev.exynoslm.model.Backend
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.model.Quantization
import dev.exynoslm.runtime.BackendCapabilities
import dev.exynoslm.runtime.GenerationEvent
import dev.exynoslm.runtime.GenerationRequest
import dev.exynoslm.runtime.InferenceConfig
import dev.exynoslm.runtime.InferenceEngine
import dev.exynoslm.runtime.LoadResult
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

/**
 * Baseline on-device LLM engine backed by MediaPipe LLM Inference (.task bundles, e.g. litert-community/Gemma3-1B-IT).
 * The same class serves CPU and GPU — MediaPipe picks the XNNPACK (CPU) or OpenCL/ML-Drift (GPU) path.
 * On Exynos 2600 the Xclipse (RDNA-based) GPU path is NOT validated by Google; treat GPU as best-effort and
 * rely on BackendSelector fallback to CPU if load fails.
 */
class MediaPipeLlmEngine(
    private val context: Context,
    override val backend: Backend,
) : InferenceEngine {
    /** Single dedicated thread: MediaPipe sessions are not thread-safe. */
    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "llm-$backend") }.asCoroutineDispatcher()
    private val lock = Mutex()
    private var llm: LlmInference? = null
    private var model: LoadedModel? = null
    private var maxTokens = 4096

    override suspend fun load(model: LoadedModel, config: InferenceConfig): LoadResult = withContext(dispatcher) {
        lock.withLock {
            unloadLocked()
            val t0 = System.currentTimeMillis()
            try {
                val opts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(model.localPath)
                    .setMaxTokens(config.maxContextTokens)
                    .setPreferredBackend(if (backend == Backend.GPU) LlmInference.Backend.GPU else LlmInference.Backend.CPU)
                    .build()
                llm = LlmInference.createFromOptions(context, opts)
                this@MediaPipeLlmEngine.model = model
                maxTokens = config.maxContextTokens
                LoadResult.Success(System.currentTimeMillis() - t0, backend)
            } catch (t: Throwable) {
                LoadResult.Failure("${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = callbackFlow {
        val engine = llm
        val m = model
        if (engine == null || m == null) {
            trySend(GenerationEvent.Error("model not loaded")); close(); awaitClose { }; return@callbackFlow
        }
        val s = request.sampling
        val sessionOpts = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(s.topK).setTopP(s.topP).setTemperature(s.temperature)
            .apply { s.seed?.let { setRandomSeed(it) } }
            .build()
        val session = LlmInferenceSession.createFromOptions(engine, sessionOpts)
        val prompt = PromptFormatter.format(m.chatTemplate, request)
        val promptTokens = runCatching { session.sizeInTokens(prompt) }.getOrDefault(prompt.length / 4)
        if (promptTokens > maxTokens) trySend(GenerationEvent.Warning("prompt ($promptTokens tokens) exceeds context $maxTokens; older turns should be trimmed"))
        session.addQueryChunk(prompt)

        val t0 = System.currentTimeMillis(); var ttft = -1L
        val sb = StringBuilder(); var count = 0
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        if (s.repetitionPenalty != 1.0f) trySend(GenerationEvent.Warning("repetition penalty not supported by this backend; ignored"))
        session.generateResponseAsync { partial, done ->
            if (finished.get()) return@generateResponseAsync
            if (partial.isNotEmpty()) {
                if (ttft < 0) ttft = System.currentTimeMillis() - t0
                sb.append(partial); count++
                trySend(GenerationEvent.TokenGenerated(partial))
                trySend(GenerationEvent.PartialText(sb.toString()))
            }
            val stop = s.stopSequences.firstOrNull { sb.contains(it) }
            if (done || stop != null || count >= s.maxNewTokens) {
                finished.set(true)
                val text = stop?.let { sb.substring(0, sb.indexOf(it)) } ?: sb.toString()
                if (!done) runCatching { session.cancelGenerateResponseAsync() }
                trySend(GenerationEvent.Usage(Usage(promptTokens, count, ttft.coerceAtLeast(0), System.currentTimeMillis() - t0)))
                trySend(GenerationEvent.Completed(text))
                close()
            }
        }
        // Cancellation from the collector (Stop button) aborts native generation immediately.
        awaitClose {
            if (!finished.get()) runCatching { session.cancelGenerateResponseAsync() }
            runCatching { session.close() }
        }
    }.flowOn(dispatcher)

    override suspend fun unload() = withContext(dispatcher) { lock.withLock { unloadLocked() } }

    private fun unloadLocked() { runCatching { llm?.close() }; llm = null; model = null }

    override fun capabilities() = BackendCapabilities(
        backend = backend,
        available = true,
        supportedFormats = setOf(ModelFormat.MEDIAPIPE_TASK),
        supportedQuantizations = setOf(Quantization.INT4, Quantization.INT8, Quantization.FP16, Quantization.FP32, Quantization.UNKNOWN),
        supportsSeed = true,
    )
}
