package dev.exynoslm.runtime

import dev.exynoslm.model.Backend
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.model.Quantization
import kotlinx.coroutines.flow.Flow

/** Unified engine contract. UI and agent code only ever talk to this interface. */
interface InferenceEngine {
    val backend: Backend
    suspend fun load(model: LoadedModel, config: InferenceConfig): LoadResult
    fun generate(request: GenerationRequest): Flow<GenerationEvent>
    suspend fun unload()
    fun capabilities(): BackendCapabilities
}

data class InferenceConfig(
    val maxContextTokens: Int = 4096,
    val cpuThreads: Int = 4,
    val useMmap: Boolean = true,
)

sealed interface LoadResult {
    data class Success(val loadTimeMs: Long, val backend: Backend) : LoadResult
    data class Failure(val reason: String, val recoverable: Boolean = true) : LoadResult
}

enum class Role { SYSTEM, USER, ASSISTANT, TOOL }
data class ChatMessage(val role: Role, val content: String, val toolName: String? = null)

data class SamplingParams(
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxNewTokens: Int = 512,
    val repetitionPenalty: Float = 1.1f,
    val stopSequences: List<String> = emptyList(),
    val seed: Int? = null,
)

data class GenerationRequest(
    val messages: List<ChatMessage>,
    val sampling: SamplingParams = SamplingParams(),
    val systemPrompt: String? = null,
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val timeToFirstTokenMs: Long,
    val totalTimeMs: Long,
) {
    val tokensPerSecond: Double
        get() = if (totalTimeMs - timeToFirstTokenMs <= 0) 0.0
        else completionTokens * 1000.0 / (totalTimeMs - timeToFirstTokenMs)
}

sealed interface GenerationEvent {
    data class TokenGenerated(val token: String) : GenerationEvent
    data class PartialText(val textSoFar: String) : GenerationEvent
    data class ToolCallRequested(val toolName: String, val argumentsJson: String) : GenerationEvent
    data class ToolResult(val toolName: String, val resultJson: String) : GenerationEvent
    data class Progress(val message: String, val fraction: Float? = null) : GenerationEvent
    data class Warning(val message: String) : GenerationEvent
    data class Error(val message: String, val cause: Throwable? = null) : GenerationEvent
    data class Completed(val fullText: String) : GenerationEvent
    data class Usage(val usage: dev.exynoslm.runtime.Usage) : GenerationEvent
}

data class BackendCapabilities(
    val backend: Backend,
    val available: Boolean,
    val supportedFormats: Set<ModelFormat>,
    val supportedQuantizations: Set<Quantization>,
    val supportsStreaming: Boolean = true,
    val supportsSeed: Boolean = false,
    val maxModelBytes: Long = Long.MAX_VALUE,
    val unavailableReason: String? = null,
)
