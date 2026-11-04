package dev.exynoslm.model

import kotlinx.serialization.Serializable

/** File formats the app knows how to reason about. Not every format is runnable on every backend. */
enum class ModelFormat(val extensions: List<String>) {
    LITERT_LM(listOf(".litertlm")),
    MEDIAPIPE_TASK(listOf(".task")),
    TFLITE(listOf(".tflite")),
    ONNX(listOf(".onnx")),
    GGUF(listOf(".gguf")),
    SAFETENSORS(listOf(".safetensors")),
    /** Samsung ENN compiled graph. Exact extension depends on the vendor toolchain (unverified; see docs/ENN_INTEGRATION.md). */
    ENN_COMPILED(listOf(".nnc")),
    UNKNOWN(emptyList());

    companion object {
        fun fromFileName(name: String): ModelFormat {
            val lower = name.lowercase()
            return entries.firstOrNull { f -> f.extensions.any { lower.endsWith(it) } } ?: UNKNOWN
        }
    }
}

enum class Quantization { FP32, FP16, BF16, INT8, INT4, Q4_K_M, Q5_K_M, Q8_0, MIXED, UNKNOWN;
    companion object {
        /** Best-effort guess from file name, e.g. "gemma-3-1b-it-int4.task" or "qwen2.5-0.5b-q4_k_m.gguf". */
        fun guess(name: String): Quantization {
            val n = name.lowercase()
            return when {
                "q4_k_m" in n -> Q4_K_M
                "q5_k_m" in n -> Q5_K_M
                "q8_0" in n -> Q8_0
                "int4" in n || "q4" in n || "4bit" in n -> INT4
                "int8" in n || "q8" in n || "8bit" in n -> INT8
                "bf16" in n -> BF16
                "fp16" in n || "f16" in n -> FP16
                "fp32" in n || "f32" in n -> FP32
                else -> UNKNOWN
            }
        }
    }
}

enum class ModelTask { TEXT_GENERATION, EMBEDDING, SPEECH_RECOGNITION, IMAGE_CLASSIFICATION, OBJECT_DETECTION, OCR, IMAGE_GENERATION, OTHER;
    companion object {
        fun fromPipelineTag(tag: String?): ModelTask = when (tag) {
            "text-generation", "text2text-generation", "conversational" -> TEXT_GENERATION
            "feature-extraction", "sentence-similarity" -> EMBEDDING
            "automatic-speech-recognition" -> SPEECH_RECOGNITION
            "image-classification" -> IMAGE_CLASSIFICATION
            "object-detection" -> OBJECT_DETECTION
            "image-to-text" -> OCR
            "text-to-image" -> IMAGE_GENERATION
            else -> OTHER
        }
    }
}

enum class Backend { NPU_ENN, GPU, CPU }
enum class BackendPreference { AUTO, NPU, GPU, CPU }

/** UI badges for Model Hub. */
enum class CompatBadge { NPU_READY, GPU, CPU, NEEDS_CONVERSION, TOO_LARGE, HEAVY_FOR_MOBILE }

@Serializable
data class ModelFile(
    val path: String,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
) {
    val format: ModelFormat get() = ModelFormat.fromFileName(path)
    val quantization: Quantization get() = Quantization.guess(path)
}

@Serializable
data class ModelCard(
    val repoId: String,
    val author: String? = null,
    val pipelineTag: String? = null,
    val libraryName: String? = null,
    val tags: List<String> = emptyList(),
    val license: String? = null,
    val downloads: Long = 0,
    val likes: Long = 0,
    val lastModified: String? = null,
    val gated: Boolean = false,
    val files: List<ModelFile> = emptyList(),
) {
    val task: ModelTask get() = ModelTask.fromPipelineTag(pipelineTag)
}

/** A model file that exists on local storage and can be handed to an engine. */
@Serializable
data class LoadedModel(
    val id: String,
    val displayName: String,
    val localPath: String,
    val format: ModelFormat,
    val quantization: Quantization,
    val sizeBytes: Long,
    val task: ModelTask = ModelTask.TEXT_GENERATION,
    val contextLength: Int = 4096,
    val sourceRepo: String? = null,
    val chatTemplate: ChatTemplate = ChatTemplate.ENGINE_DEFAULT,
)

enum class ChatTemplate { ENGINE_DEFAULT, GEMMA, CHATML, LLAMA3, PHI3 }

enum class LicenseRisk { PERMISSIVE, ATTRIBUTION, RESTRICTED, UNKNOWN }

object Licenses {
    private val permissive = setOf("apache-2.0", "mit", "bsd-3-clause", "bsd-2-clause", "openrail")
    private val attribution = setOf("cc-by-4.0", "cc-by-sa-4.0", "openrail++", "creativeml-openrail-m")
    private val restricted = setOf("gemma", "llama2", "llama3", "llama3.1", "llama3.2", "llama3.3", "cc-by-nc-4.0", "cc-by-nc-sa-4.0", "other", "qwen")
    fun classify(license: String?): LicenseRisk = when (license?.lowercase()) {
        null -> LicenseRisk.UNKNOWN
        in permissive -> LicenseRisk.PERMISSIVE
        in attribution -> LicenseRisk.ATTRIBUTION
        in restricted -> LicenseRisk.RESTRICTED
        else -> LicenseRisk.UNKNOWN
    }
}
