package dev.exynoslm.runtime.gpu

import android.content.Context
import dev.exynoslm.model.Backend
import dev.exynoslm.runtime.InferenceEngine
import dev.exynoslm.runtime.cpu.MediaPipeLlmEngine
import org.tensorflow.lite.gpu.CompatibilityList

object GpuEngines {
    /** LLM on GPU via MediaPipe's GPU backend. */
    fun llm(context: Context): InferenceEngine = MediaPipeLlmEngine(context, Backend.GPU)

    /**
     * Whether the LiteRT GPU delegate reports this GPU as supported (used for vision/embedding .tflite graphs).
     * On Xclipse/RDNA the OpenCL path may be unavailable; the delegate then needs OpenGL ES or falls back.
     */
    fun liteRtDelegateSupported(): Boolean = runCatching { CompatibilityList().use { it.isDelegateSupportedOnThisDevice } }.getOrDefault(false)
}
