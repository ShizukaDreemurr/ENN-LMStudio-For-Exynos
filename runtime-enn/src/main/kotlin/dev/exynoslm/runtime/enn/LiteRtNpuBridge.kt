package dev.exynoslm.runtime.enn

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Real NPU path on Exynos, as validated on SM-S947B (Galaxy S26+):
 *
 *   App ─► LiteRT 2.3.2 CompiledModel(Accelerator.NPU)
 *        ─► libLiteRtDispatch_Samsung.so (self-built from LiteRT tag v2.3.2, packaged in APK)
 *        ─► libenn_public_api_cpp.so (system lib on the device, NOT packaged; dlopen'ed by the dispatch)
 *
 * We do NOT call ENN directly. Source reviewed: google-ai-edge/litert @ b6b64443 (2026-11-01),
 * litert/vendors/samsung/dispatch/{enn_manager.*, litert_dispatch_*_context.cc}.
 *
 * LiteRT 2.x Kotlin API is accessed via reflection on purpose: the exact Kotlin signatures were not verified
 * against published docs, and reflection means a mismatch degrades to isAvailable()=false instead of a build break.
 * Names used (VERIFY against your SrBackend.kt / NpuEnv.kt): Environment.create(), getAvailableAccelerators(),
 * Accelerator.NPU, CompiledModel.Options(Accelerator), CompiledModel.create(path, options, env),
 * createInputBuffers(), createOutputBuffers(), run(List,List), TensorBuffer.writeInt8/readInt8, close().
 *
 * Data path is COPY-based (ByteBuffer -> TensorBuffer.writeInt8). The dispatch only accepts DMA-BUF buffers
 * internally; zero-copy (dmabuf fd / AHardwareBuffer) is not implemented. Copy cost dominates NPU time for large I/O.
 */
class LiteRtNpuBridge(private val context: Context) : EnnBridge {

    /** Tensor specs: ENN's EnnGetBuffersInfo only returns COUNTS, so specs come from a sidecar written by the AOT script. */
    @Serializable
    data class IoSidecar(val inputs: List<Spec>, val outputs: List<Spec>) {
        @Serializable data class Spec(val name: String, val shape: List<Int>, val dtype: String)
    }

    private class Loaded(val model: Any, val inputs: List<Any>, val outputs: List<Any>, val io: IoSidecar)

    private val json = Json { ignoreUnknownKeys = true }
    private val models = ConcurrentHashMap<Long, Loaded>()
    private val ids = AtomicLong(1)
    private val env: Any? by lazy { runCatching { createEnvironment() }.getOrNull() }
    @Volatile private var lastError: String? = null

    override fun isAvailable(): Boolean = runCatching {
        val e = env ?: error("LiteRT Environment unavailable (LiteRT 2.x not on classpath?)")
        val accs = call(e, "getAvailableAccelerators").toString()
        accs.contains("NPU").also { if (!it) lastError = "no NPU accelerator (got $accs)" }
    }.getOrElse { lastError = it.message; false }

    override fun describe() = "LiteRT 2.3.2 + Samsung dispatch (AOT target E9965)" + (lastError?.let { " — $it" } ?: "")

    override fun openModel(path: String): EnnBridge.ModelHandle = synchronized(NPU_LOCK) {
        val io = sidecarFor(path)
        val accel = Class.forName("$PKG.Accelerator").getMethod("valueOf", String::class.java).invoke(null, "NPU")
        val optsCls = Class.forName("$PKG.CompiledModel\$Options")
        val opts = optsCls.constructors.first { it.parameterCount == 1 }.let { c ->
            // Options(vararg Accelerator) compiles to an array param in some versions.
            if (c.parameterTypes[0].isArray) c.newInstance(java.lang.reflect.Array.newInstance(accel.javaClass, 1).also { java.lang.reflect.Array.set(it, 0, accel) })
            else c.newInstance(accel)
        }
        val model = invokeFactory(Class.forName("$PKG.CompiledModel"), "create", path, opts, env)
        val loaded = Loaded(model, call(model, "createInputBuffers") as List<Any>, call(model, "createOutputBuffers") as List<Any>, io)
        val id = ids.getAndIncrement(); models[id] = loaded
        EnnBridge.ModelHandle(id)
    }

    override fun inputSpecs(h: EnnBridge.ModelHandle) = models.getValue(h.id).io.inputs.map(::toSpec)
    override fun outputSpecs(h: EnnBridge.ModelHandle) = models.getValue(h.id).io.outputs.map(::toSpec)

    override fun execute(h: EnnBridge.ModelHandle, inputs: List<ByteBuffer>, outputs: List<ByteBuffer>) {
        val m = models.getValue(h.id)
        require(inputs.size == m.inputs.size && outputs.size == m.outputs.size) { "I/O count mismatch" }
        // Global lock: ENN thread constraints are unverified; the reference app serialises every run.
        synchronized(NPU_LOCK) {
            inputs.forEachIndexed { i, b -> call(m.inputs[i], "writeInt8", b.toByteArray()) } // INT8 graphs only for now
            call(m.model, "run", m.inputs, m.outputs)
            outputs.forEachIndexed { i, o ->
                val bytes = call(m.outputs[i], "readInt8") as ByteArray
                o.clear(); o.put(bytes, 0, minOf(bytes.size, o.capacity())); o.flip()
            }
        }
    }

    override fun close(h: EnnBridge.ModelHandle) {
        val m = models.remove(h.id) ?: return
        synchronized(NPU_LOCK) { (m.inputs + m.outputs).forEach { runCatching { call(it, "close") } }; runCatching { call(m.model, "close") } }
    }

    // ---- helpers ----

    private fun sidecarFor(path: String): IoSidecar {
        val f = File(path.removeSuffix(".tflite") + ".io.json")
        require(f.exists()) { "missing I/O sidecar ${f.name} (generate it in the AOT compile step)" }
        return json.decodeFromString(IoSidecar.serializer(), f.readText())
    }

    private fun toSpec(s: IoSidecar.Spec): EnnBridge.TensorSpec {
        val elem = when (s.dtype.lowercase()) { "int8", "uint8" -> 1; "int16", "float16" -> 2; else -> 4 }
        return EnnBridge.TensorSpec(s.name, s.shape.toIntArray(), s.dtype, s.shape.fold(1) { a, b -> a * b } * elem)
    }

    private fun ByteBuffer.toByteArray(): ByteArray { val d = duplicate().apply { rewind() }; return ByteArray(d.remaining()).also { d.get(it) } }

    private fun createEnvironment(): Any {
        val cls = Class.forName("$PKG.Environment")
        cls.methods.firstOrNull { it.name == "create" && it.parameterCount == 0 && java.lang.reflect.Modifier.isStatic(it.modifiers) }?.let { return it.invoke(null)!! }
        val companion = cls.getField("Companion").get(null)
        // Kotlin default args: prefer zero-arg; otherwise the "$default" synthetic isn't reachable here, so fail loudly.
        return companion.javaClass.methods.first { it.name == "create" && it.parameterCount == 0 }.invoke(companion)!!
    }

    /** Finds a static or companion `create(String path, Options, Environment?)`-shaped factory. */
    private fun invokeFactory(cls: Class<*>, name: String, path: String, opts: Any, env: Any?): Any {
        val targets: List<Pair<Any?, Method>> = cls.methods.filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { null to it } +
            runCatching { cls.getField("Companion").get(null) }.getOrNull()?.let { c -> c.javaClass.methods.map { c to it } }.orEmpty()
        for ((recv, m) in targets.filter { it.second.name == name && it.second.parameterTypes.firstOrNull() == String::class.java }) {
            val args: Array<Any?> = when (m.parameterCount) { 2 -> arrayOf(path, opts); 3 -> arrayOf(path, opts, env); else -> continue }
            if (runCatching { m.parameterTypes.zip(args).all { (t, a) -> a == null || t.isInstance(a) } }.getOrDefault(false))
                return m.invoke(recv, *args)!!
        }
        error("CompiledModel.create(String, Options[, Environment]) not found — check LiteRT version")
    }

    private fun call(target: Any, name: String, vararg args: Any?): Any? =
        target.javaClass.methods.first { it.name == name && it.parameterCount == args.size }.invoke(target, *args)

    companion object {
        private const val PKG = "com.google.ai.edge.litert"
        /** One NPU job at a time, process-wide (mirrors NpuEnv.npuLock in the reference app). */
        val NPU_LOCK = Any()
    }
}
