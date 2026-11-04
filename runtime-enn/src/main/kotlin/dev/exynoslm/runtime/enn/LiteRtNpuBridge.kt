package dev.exynoslm.runtime.enn

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Exynos NPU via LiteRT 2.3.2 + Samsung dispatch. Call shapes mirror the reference app that runs on SM-S947B
 * (repo exynos-xclipse-afmf-enn: NpuEnv.kt:41-80, SrBackend.kt:39-89), which builds in CI.
 *
 *   CompiledModel(Accelerator.NPU) ─► libLiteRtDispatch_Samsung.so (APK, self-built v2.3.2)
 *                                  ─► libenn_public_api_cpp.so      (system, dlopen'ed)
 *
 * Only writeInt8/readInt8 stay reflective: the reference app probes them at runtime ("API not stably public").
 */
class LiteRtNpuBridge(
    private val context: Context,
    /** Options(NPU, GPU): let LiteRT place unsupported ops on GPU instead of failing. */
    private val withGpuFallback: Boolean = false,
) : EnnBridge {

    /** Written by the AOT step (`io_sidecar.py`) next to `<name>.e9965.tflite` as `<name>.e9965.io.json`. */
    @Serializable
    data class IoSidecar(val inputs: List<Spec>, val outputs: List<Spec>) {
        @Serializable data class Spec(val name: String, val shape: List<Int>, val dtype: String, val scale: Float? = null, val zero_point: Int? = null)
    }

    private class Loaded(val model: CompiledModel, val inputs: List<TensorBuffer>, val outputs: List<TensorBuffer>, val io: IoSidecar)

    private val json = Json { ignoreUnknownKeys = true }
    private val models = ConcurrentHashMap<Long, Loaded>()
    private val ids = AtomicLong(1)
    @Volatile private var lastError: String? = null

    /** Same fallback chain as NpuEnv strategies A–E: dispatch dir, dispatch+compiler-plugin dir, plain context. */
    private val env: Environment? by lazy {
        val dir = context.applicationInfo.nativeLibraryDir
        val attempts: List<() -> Environment> = listOf(
            { Environment.create(context, mapOf(Environment.Option.DispatchLibraryDir to dir)) },
            { Environment.create(context, mapOf(Environment.Option.DispatchLibraryDir to dir, Environment.Option.CompilerPluginLibraryDir to dir)) },
            { Environment.create(context) },
        )
        for (a in attempts) runCatching(a).onSuccess { return@lazy it }.onFailure { lastError = "Environment.create: ${it.message}" }
        null
    }

    override fun isAvailable(): Boolean = runCatching {
        val e = env ?: return false
        val accs = e.getAvailableAccelerators().joinToString { it.toString() }
        ("NPU" in accs).also { if (!it) lastError = "no NPU accelerator (available: $accs)" }
    }.getOrElse { lastError = it.message; false }

    override fun describe() = "LiteRT 2.3.2 + Samsung dispatch (AOT E9965)" + (lastError?.let { " — $it" } ?: "")

    override fun openModel(path: String): EnnBridge.ModelHandle {
        val e = env ?: error(describe())
        val io = sidecarFor(path)
        val opts = if (withGpuFallback) CompiledModel.Options(Accelerator.NPU, Accelerator.GPU) else CompiledModel.Options(Accelerator.NPU)
        val model = CompiledModel.create(path, opts, e)
        val loaded = Loaded(model, model.createInputBuffers(), model.createOutputBuffers(), io)
        require(loaded.inputs.size == io.inputs.size && loaded.outputs.size == io.outputs.size) { "sidecar I/O count does not match model" }
        if (io.inputs.any { it.dtype == "int8" }) requireNotNull(writeI8(loaded.inputs[0])) { "TensorBuffer.writeInt8 not found in this LiteRT build" }
        val id = ids.getAndIncrement(); models[id] = loaded
        return EnnBridge.ModelHandle(id)
    }

    override fun inputSpecs(h: EnnBridge.ModelHandle) = models.getValue(h.id).io.inputs.map(::toSpec)
    override fun outputSpecs(h: EnnBridge.ModelHandle) = models.getValue(h.id).io.outputs.map(::toSpec)

    /** Buffers carry raw tensor bytes in the model's layout (sr_x2: NCHW int8). Quantize with dev.exynoslm.runtime.Quant first. */
    override fun execute(h: EnnBridge.ModelHandle, inputs: List<ByteBuffer>, outputs: List<ByteBuffer>) {
        val m = models.getValue(h.id)
        require(inputs.size == m.inputs.size && outputs.size == m.outputs.size) { "I/O count mismatch" }
        synchronized(NPU_LOCK) { // reference app serialises every run; multi-thread rules are unverified
            val t0 = System.nanoTime()
            inputs.forEachIndexed { i, b ->
                if (m.io.inputs[i].dtype == "int8") writeI8(m.inputs[i])!!.invoke(m.inputs[i], b.toByteArray())
                else m.inputs[i].writeFloat(b.toFloatArray())
            }
            val t1 = System.nanoTime()
            m.model.run(m.inputs, m.outputs)
            val t2 = System.nanoTime()
            outputs.forEachIndexed { i, o ->
                o.clear()
                if (m.io.outputs[i].dtype == "int8") (readI8(m.outputs[i])!!.invoke(m.outputs[i]) as ByteArray).let { o.put(it, 0, minOf(it.size, o.capacity())) }
                else m.outputs[i].readFloat().forEach { if (o.remaining() >= 4) o.putFloat(it) }
                o.flip()
            }
            lastTiming = Timing((t1 - t0) / 1e6, (t2 - t1) / 1e6, (System.nanoTime() - t2) / 1e6)
        }
    }

    data class Timing(val copyInMs: Double, val runMs: Double, val copyOutMs: Double)
    @Volatile var lastTiming: Timing? = null; private set

    /** Raw accelerator list for diagnostics. */
    fun availableAccelerators(): String = runCatching { env?.getAvailableAccelerators()?.joinToString { it.toString() } ?: "env=null" }
        .getOrElse { "error: ${it.message}" }

    /** Close order as in SrBackend.kt:83-89: inputs, outputs, model. Environment lives for the bridge lifetime. */
    override fun close(h: EnnBridge.ModelHandle) {
        val m = models.remove(h.id) ?: return
        synchronized(NPU_LOCK) {
            m.inputs.forEach { runCatching { it.close() } }
            m.outputs.forEach { runCatching { it.close() } }
            runCatching { m.model.close() }
        }
    }

    // ---- helpers ----

    private fun sidecarFor(path: String): IoSidecar {
        val f = File(path.removeSuffix(".tflite") + ".io.json")
        require(f.exists()) { "missing I/O sidecar ${f.name} (generate with io_sidecar.py in the AOT step)" }
        return json.decodeFromString(IoSidecar.serializer(), f.readText())
    }

    private fun toSpec(s: IoSidecar.Spec): EnnBridge.TensorSpec {
        val elem = when (s.dtype.lowercase()) { "int8", "uint8" -> 1; "int16", "float16" -> 2; else -> 4 }
        return EnnBridge.TensorSpec(s.name, s.shape.toIntArray(), s.dtype, s.shape.fold(1) { a, b -> a * b } * elem, s.scale, s.zero_point)
    }

    private fun writeI8(b: TensorBuffer): Method? = b.javaClass.methods.firstOrNull { it.name == "writeInt8" && it.parameterTypes.contentEquals(arrayOf(ByteArray::class.java)) }
    private fun readI8(b: TensorBuffer): Method? = b.javaClass.methods.firstOrNull { it.name == "readInt8" && it.parameterTypes.isEmpty() }

    private fun ByteBuffer.toByteArray(): ByteArray { val d = duplicate().apply { rewind() }; return ByteArray(d.remaining()).also { d.get(it) } }
    private fun ByteBuffer.toFloatArray(): FloatArray { val d = duplicate().apply { rewind() }.asFloatBuffer(); return FloatArray(d.remaining()).also { d.get(it) } }

    companion object {
        /** One NPU job at a time, process-wide (mirrors NpuEnv.npuLock). */
        val NPU_LOCK = Any()
    }
}
