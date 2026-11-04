package dev.exynoslm.runtime.gpu

import android.content.Context
import dev.exynoslm.model.Backend
import dev.exynoslm.runtime.InferenceEngine
import dev.exynoslm.runtime.cpu.MediaPipeLlmEngine

object GpuEngines {
    /** LLM on GPU via MediaPipe's GPU backend. Vision/embedding .tflite on GPU goes through LiteRT 2.x Accelerator.GPU. */
    fun llm(context: Context): InferenceEngine = MediaPipeLlmEngine(context, Backend.GPU)
}
