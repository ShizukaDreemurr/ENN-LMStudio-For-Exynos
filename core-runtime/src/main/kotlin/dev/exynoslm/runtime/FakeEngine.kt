package dev.exynoslm.runtime

import dev.exynoslm.model.Backend
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.model.Quantization
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted engine for tests, UI previews and the agent demo when no model is downloaded.
 * [responder] maps the conversation to a reply.
 */
class FakeEngine(
    override val backend: Backend = Backend.CPU,
    private val available: Boolean = true,
    private val failLoad: Boolean = false,
    private val tokenDelayMs: Long = 0,
    private val responder: (List<ChatMessage>) -> String = { "Echo: " + (it.lastOrNull()?.content ?: "") },
) : InferenceEngine {
    var loaded: LoadedModel? = null; private set
    var unloadCount = 0; private set

    override suspend fun load(model: LoadedModel, config: InferenceConfig): LoadResult {
        if (failLoad) return LoadResult.Failure("simulated load failure")
        loaded = model
        return LoadResult.Success(0, backend)
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        val start = System.currentTimeMillis()
        val reply = responder(request.messages)
        val sb = StringBuilder()
        var ttft = -1L
        reply.split(Regex("(?<= )")).forEach { tok ->
            if (tokenDelayMs > 0) delay(tokenDelayMs)
            if (ttft < 0) ttft = System.currentTimeMillis() - start
            sb.append(tok)
            emit(GenerationEvent.TokenGenerated(tok))
            emit(GenerationEvent.PartialText(sb.toString()))
        }
        emit(GenerationEvent.Usage(Usage(request.messages.sumOf { it.content.length / 4 }, reply.length / 4, ttft.coerceAtLeast(0), System.currentTimeMillis() - start)))
        emit(GenerationEvent.Completed(sb.toString()))
    }

    override suspend fun unload() { loaded = null; unloadCount++ }

    override fun capabilities() = BackendCapabilities(
        backend = backend,
        available = available,
        supportedFormats = ModelFormat.entries.toSet(),
        supportedQuantizations = Quantization.entries.toSet(),
        unavailableReason = if (available) null else "fake: unavailable",
    )
}
