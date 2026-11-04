package dev.exynoslm.runtime.enn

import android.os.Build
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * NPU engine. Loading a compiled graph works through [EnnBridge]; autoregressive LLM decoding on the NPU is NOT
 * implemented — it requires a Samsung-converted LLM (static shapes, KV-cache I/O, tokenizer) whose format we cannot
 * verify publicly. Until then, [generate] reports an error and BackendSelector falls back to GPU/CPU for chat,
 * while NPU remains usable for fixed-shape graphs (classification, embedding, ASR encoders) via [runGraph].
 */
class EnnEngine(private val bridge: EnnBridge, private val featureEnabled: Boolean = BuildConfig.ENN_ENABLED) : InferenceEngine {
    override val backend = Backend.NPU_ENN
    private var handle: EnnBridge.ModelHandle? = null

    val socInfo: String get() = "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"

    override suspend fun load(model: LoadedModel, config: InferenceConfig): LoadResult {
        if (!featureEnabled || !bridge.isAvailable()) return LoadResult.Failure(bridge.describe())
        val t0 = System.currentTimeMillis()
        return runCatching { handle = bridge.openModel(model.localPath); LoadResult.Success(System.currentTimeMillis() - t0, backend) }
            .getOrElse { LoadResult.Failure("ENN open failed: ${it.message}") }
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> =
        flowOf(GenerationEvent.Error("LLM decoding on Exynos NPU is not implemented yet; use GPU/CPU (see docs/ENN_INTEGRATION.md)"))

    fun runGraph(inputs: List<java.nio.ByteBuffer>, outputs: List<java.nio.ByteBuffer>) {
        bridge.execute(handle ?: error("no model loaded"), inputs, outputs)
    }

    override suspend fun unload() { handle?.let(bridge::close); handle = null }

    override fun capabilities(): BackendCapabilities {
        val ok = featureEnabled && bridge.isAvailable()
        return BackendCapabilities(
            backend = backend,
            available = ok,
            // Text generation is excluded on purpose: only compiled/quantized fixed-shape graphs.
            supportedFormats = setOf(ModelFormat.ENN_COMPILED), // AOT-compiled for E9965/E9955 only
            supportedQuantizations = emptySet(), // validated: full-INT8; checked by the AOT compiler, not by file name
            supportsStreaming = false,
            unavailableReason = if (ok) null else bridge.describe(),
        )
    }
}
