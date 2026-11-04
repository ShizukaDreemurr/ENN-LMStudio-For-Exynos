package dev.exynoslm.runtime

import dev.exynoslm.model.Backend
import dev.exynoslm.model.BackendPreference
import dev.exynoslm.model.CompatBadge
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.model.ModelFile
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.model.ModelTask

/** Snapshot of device resources used for compatibility decisions. */
data class DeviceState(
    val availableRamBytes: Long,
    val totalRamBytes: Long,
    val thermalSevere: Boolean = false,
    val batterySaver: Boolean = false,
)

data class CompatibilityReport(val backend: Backend, val ok: Boolean, val reasons: List<String>)

data class Selection(
    val engine: InferenceEngine?,
    val chosen: Backend?,
    val attempts: List<CompatibilityReport>,
) {
    /** Human-readable fallback explanation to surface in the UI. */
    val fallbackNotes: List<String> get() = attempts.filter { !it.ok }.map { "${it.backend}: ${it.reasons.joinToString("; ")}" }
}

object Compatibility {
    /** Rough RAM estimate: weights + ~25% for KV cache/activations at moderate context. */
    fun estimatedRamBytes(sizeBytes: Long, contextLength: Int): Long =
        (sizeBytes * 1.25).toLong() + contextLength.toLong() * 64 * 1024

    fun check(model: LoadedModel, caps: BackendCapabilities, device: DeviceState): CompatibilityReport {
        val reasons = mutableListOf<String>()
        if (!caps.available) reasons += caps.unavailableReason ?: "backend unavailable"
        if (model.format !in caps.supportedFormats) reasons += "format ${model.format} not supported"
        if (caps.supportedQuantizations.isNotEmpty() && model.quantization !in caps.supportedQuantizations)
            reasons += "quantization ${model.quantization} not supported"
        if (model.sizeBytes > caps.maxModelBytes) reasons += "model exceeds backend limit"
        val need = estimatedRamBytes(model.sizeBytes, model.contextLength)
        if (need > device.availableRamBytes) reasons += "needs ~${need shr 20} MB RAM, ${device.availableRamBytes shr 20} MB available"
        return CompatibilityReport(caps.backend, reasons.isEmpty(), reasons)
    }

    /** Static badge computation for Model Hub listings (before download). */
    fun badges(file: ModelFile, task: ModelTask, totalRamBytes: Long): Set<CompatBadge> {
        val out = mutableSetOf<CompatBadge>()
        when (file.format) {
            ModelFormat.MEDIAPIPE_TASK, ModelFormat.LITERT_LM -> { out += CompatBadge.GPU; out += CompatBadge.CPU }
            ModelFormat.TFLITE -> { out += CompatBadge.GPU; out += CompatBadge.CPU; out += CompatBadge.NPU_READY }
            ModelFormat.ENN_COMPILED -> out += CompatBadge.NPU_READY
            ModelFormat.GGUF, ModelFormat.ONNX, ModelFormat.SAFETENSORS, ModelFormat.UNKNOWN -> out += CompatBadge.NEEDS_CONVERSION
        }
        // TFLITE is only "NPU-ready" if quantized; float graphs usually fall back off the NPU.
        if (file.format == ModelFormat.TFLITE && file.quantization.name.startsWith("FP")) out -= CompatBadge.NPU_READY
        val size = file.sizeBytes
        if (size != null && estimatedRamBytes(size, 2048) > totalRamBytes / 2) out += CompatBadge.TOO_LARGE
        if (task == ModelTask.IMAGE_GENERATION) out += CompatBadge.HEAVY_FOR_MOBILE
        return out
    }
}

/**
 * Picks an engine. AUTO tries NPU -> GPU -> CPU; an explicit preference is tried first and
 * then falls back along the same chain, recording why each candidate was rejected.
 */
class BackendSelector(private val engines: Map<Backend, InferenceEngine>) {

    fun order(pref: BackendPreference, device: DeviceState): List<Backend> {
        val auto = if (device.thermalSevere) listOf(Backend.NPU_ENN, Backend.CPU, Backend.GPU)
        else listOf(Backend.NPU_ENN, Backend.GPU, Backend.CPU)
        val first = when (pref) {
            BackendPreference.AUTO -> null
            BackendPreference.NPU -> Backend.NPU_ENN
            BackendPreference.GPU -> Backend.GPU
            BackendPreference.CPU -> Backend.CPU
        }
        return if (first == null) auto else listOf(first) + (auto - first)
    }

    fun select(model: LoadedModel, pref: BackendPreference, device: DeviceState): Selection {
        val attempts = mutableListOf<CompatibilityReport>()
        for (b in order(pref, device)) {
            val engine = engines[b]
            if (engine == null) { attempts += CompatibilityReport(b, false, listOf("no engine registered")); continue }
            val report = Compatibility.check(model, engine.capabilities(), device)
            attempts += report
            if (report.ok) return Selection(engine, b, attempts)
        }
        return Selection(null, null, attempts)
    }

    /** Select + load, falling back to the next backend if load() itself fails at runtime. */
    suspend fun selectAndLoad(model: LoadedModel, pref: BackendPreference, device: DeviceState, config: InferenceConfig): Pair<Selection, LoadResult> {
        val attempts = mutableListOf<CompatibilityReport>()
        for (b in order(pref, device)) {
            val engine = engines[b] ?: run { attempts += CompatibilityReport(b, false, listOf("no engine registered")); null } ?: continue
            val report = Compatibility.check(model, engine.capabilities(), device)
            if (!report.ok) { attempts += report; continue }
            when (val r = engine.load(model, config)) {
                is LoadResult.Success -> { attempts += report; return Selection(engine, b, attempts) to r }
                is LoadResult.Failure -> attempts += CompatibilityReport(b, false, listOf("load failed: ${r.reason}"))
            }
        }
        return Selection(null, null, attempts) to LoadResult.Failure("no compatible backend", recoverable = false)
    }
}
