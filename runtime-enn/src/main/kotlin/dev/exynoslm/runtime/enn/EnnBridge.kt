package dev.exynoslm.runtime.enn

import java.nio.ByteBuffer

/**
 * The ONLY seam between the app and Samsung's ENN / Exynos NPU SDK.
 *
 * This interface is deliberately minimal and vendor-neutral ("open compiled graph, bind buffers, run").
 * It is NOT a transcription of any Samsung API — the real SDK's names, threading model and buffer rules
 * must be verified against the SDK documentation you obtain from Samsung (see docs/ENN_INTEGRATION.md).
 * Implement [EnnBridge] in a `RealEnnBridge` class that wraps the vendor AAR/.so, guarded by BuildConfig.ENN_ENABLED.
 */
interface EnnBridge {
    /** True only if the vendor runtime is present and the SoC exposes an NPU. */
    fun isAvailable(): Boolean
    fun describe(): String
    fun openModel(path: String): ModelHandle
    fun inputSpecs(h: ModelHandle): List<TensorSpec>
    fun outputSpecs(h: ModelHandle): List<TensorSpec>
    /** Synchronous execution on the NPU; buffers must be direct and match [TensorSpec.byteSize]. */
    fun execute(h: ModelHandle, inputs: List<ByteBuffer>, outputs: List<ByteBuffer>)
    fun close(h: ModelHandle)

    @JvmInline value class ModelHandle(val id: Long)
    data class TensorSpec(val name: String, val shape: IntArray, val dtype: String, val byteSize: Int)
}

/** Default implementation used when the SDK is absent or `enn.enabled=false`. Always reports unavailable. */
class StubEnnBridge(private val reason: String = "Samsung ENN SDK not integrated (enn.enabled=false)") : EnnBridge {
    override fun isAvailable() = false
    override fun describe() = reason
    override fun openModel(path: String): EnnBridge.ModelHandle = throw UnsupportedOperationException(reason)
    override fun inputSpecs(h: EnnBridge.ModelHandle) = emptyList<EnnBridge.TensorSpec>()
    override fun outputSpecs(h: EnnBridge.ModelHandle) = emptyList<EnnBridge.TensorSpec>()
    override fun execute(h: EnnBridge.ModelHandle, inputs: List<java.nio.ByteBuffer>, outputs: List<java.nio.ByteBuffer>) = throw UnsupportedOperationException(reason)
    override fun close(h: EnnBridge.ModelHandle) = Unit
}

/**
 * Deterministic in-memory bridge for tests/UI: "runs" a graph by copying inputs to outputs.
 * Lets the NPU code path, benchmarks and fallback be exercised without hardware.
 */
class MockEnnBridge : EnnBridge {
    private var next = 1L
    override fun isAvailable() = true
    override fun describe() = "Mock ENN (no hardware)"
    override fun openModel(path: String) = EnnBridge.ModelHandle(next++)
    override fun inputSpecs(h: EnnBridge.ModelHandle) = listOf(EnnBridge.TensorSpec("in", intArrayOf(1, 16), "int8", 16))
    override fun outputSpecs(h: EnnBridge.ModelHandle) = listOf(EnnBridge.TensorSpec("out", intArrayOf(1, 16), "int8", 16))
    override fun execute(h: EnnBridge.ModelHandle, inputs: List<java.nio.ByteBuffer>, outputs: List<java.nio.ByteBuffer>) {
        inputs.zip(outputs).forEach { (i, o) -> o.clear(); i.rewind(); o.put(i); o.flip() }
    }
    override fun close(h: EnnBridge.ModelHandle) = Unit
}
