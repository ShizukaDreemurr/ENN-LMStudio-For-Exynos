package dev.exynoslm.runtime

import dev.exynoslm.model.Backend
import dev.exynoslm.model.LoadedModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One [InferenceEngine] facade over several engines that serve the same [backend] for different
 * file formats (e.g. MediaPipe for `.task`, LiteRT-LM for `.litertlm`, llama.cpp for `.gguf`).
 *
 * Only one delegate is resident at a time: loading a model in a different delegate unloads the
 * previously active one first, so memory is released before the new engine allocates.
 */
class FormatRouterEngine(
    override val backend: Backend,
    private val engines: List<InferenceEngine>,
) : InferenceEngine {

    private val lock = Mutex()
    @Volatile private var active: InferenceEngine? = null

    override suspend fun load(model: LoadedModel, config: InferenceConfig): LoadResult = lock.withLock {
        val target = engines.firstOrNull { e ->
            val caps = e.capabilities()
            caps.available && model.format in caps.supportedFormats
        } ?: return@withLock LoadResult.Failure("no engine for ${model.format} on $backend")

        val previous = active
        if (previous != null && previous !== target) previous.unload()
        active = null

        when (val result = target.load(model, config)) {
            is LoadResult.Success -> { active = target; result }
            is LoadResult.Failure -> result
        }
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> {
        val target = active ?: return flowOf(GenerationEvent.Error("model not loaded"))
        return target.generate(request)
    }

    override suspend fun unload() = lock.withLock {
        active?.unload()
        active = null
    }

    override fun capabilities(): BackendCapabilities {
        val all = engines.map { it.capabilities() }
        val available = all.filter { it.available }
        val isAvailable = available.isNotEmpty()
        // An empty quantization set means "any quantization". If any live delegate accepts any, so does the router.
        val quantizations = if (available.any { it.supportedQuantizations.isEmpty() }) emptySet()
        else available.flatMapTo(mutableSetOf()) { it.supportedQuantizations }
        return BackendCapabilities(
            backend = backend,
            available = isAvailable,
            supportedFormats = available.flatMapTo(mutableSetOf()) { it.supportedFormats },
            supportedQuantizations = quantizations,
            supportsStreaming = available.any { it.supportsStreaming },
            supportsSeed = available.any { it.supportsSeed },
            maxModelBytes = all.maxOfOrNull { it.maxModelBytes } ?: 0L,
            unavailableReason = if (isAvailable) null else {
                all.mapNotNull { it.unavailableReason }.distinct()
                    .ifEmpty { listOf("no engines registered for $backend") }
                    .joinToString("; ")
            },
        )
    }
}
