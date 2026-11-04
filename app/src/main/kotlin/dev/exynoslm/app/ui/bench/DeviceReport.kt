package dev.exynoslm.app.ui.bench

import android.content.Context
import android.os.Build
import dev.exynoslm.app.BuildConfig
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.runtime.enn.EnnBridge
import dev.exynoslm.runtime.enn.LiteRtNpuBridge
import dev.exynoslm.telemetry.DeviceMonitor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds a plain-text report the user can copy and paste back to the developer. Everything stays local until they paste it. */
class DeviceReport(private val ctx: Context, private val bridge: EnnBridge, private val monitor: DeviceMonitor) {

    fun basic(): String = buildString {
        line("=== Exynos LM Studio device report ===")
        line("app ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) enn.enabled=${dev.exynoslm.runtime.enn.BuildConfig.ENN_ENABLED}")
        line("device ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) android ${Build.VERSION.RELEASE} sdk ${Build.VERSION.SDK_INT}")
        line("soc ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL} hw=${Build.HARDWARE} board=${Build.BOARD} abi=${Build.SUPPORTED_ABIS.joinToString()}")
        line("fingerprint ${Build.FINGERPRINT}")
        val cpus = (0 until Runtime.getRuntime().availableProcessors()).map { i ->
            runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000 }.getOrDefault(-1)
        }
        line("cpu ${cpus.size} cores maxMHz=${cpus.joinToString(",")}")
        val s = monitor.snapshot()
        line("ram total=${s.totalRamBytes shr 20}MB avail=${s.availRamBytes shr 20}MB low=${s.lowMemory} appPss=${s.appPssKb / 1024}MB")
        line("thermal status=${s.thermalStatus} headroom=${s.thermalHeadroom} battery=${s.batteryPct}% ${s.batteryTempC}C charging=${s.charging} powerSave=${s.powerSave}")

        line("--- native libs in APK (${ctx.applicationInfo.nativeLibraryDir}) ---")
        File(ctx.applicationInfo.nativeLibraryDir).listFiles().orEmpty().sortedBy { it.name }
            .forEach { line("  ${it.name} ${it.length()} B") }
        line("--- system ENN libs ---")
        for (n in listOf("libenn_public_api_cpp.so", "libenn_public_api_cpp_lib.so", "libenn_user.samsung_slsi.so", "libenn_user_lib.so")) {
            val found = listOf("/vendor/lib64", "/system/lib64", "/system_ext/lib64", "/odm/lib64").map { File(it, n) }.filter { it.exists() }
            line("  $n: ${if (found.isEmpty()) "not found in vendor/system lib64" else found.joinToString { "${it.path} ${it.length()}B" }}")
        }
        line("--- NPU ---")
        line("bridge ${bridge.javaClass.simpleName} available=${runCatching { bridge.isAvailable() }.getOrElse { "ERR ${it.message}" }}")
        line("describe ${bridge.describe()}")
        if (bridge is LiteRtNpuBridge) line("accelerators ${bridge.availableAccelerators()}")
        line("--- TensorBuffer API ---")
        runCatching { Class.forName("com.google.ai.edge.litert.TensorBuffer") }.onSuccess { c ->
            c.methods.filter { it.declaringClass != Any::class.java }.map { m -> "${m.name}(${m.parameterTypes.joinToString { it.simpleName }}): ${m.returnType.simpleName}" }
                .distinct().sorted().forEach { line("  $it") }
        }.onFailure { line("  class not found: ${it.message}") }
    }

    /** 20 runs with zero input on an AOT model (+ sidecar). Mirrors NpuEnv.srBench: in / run / out / total ms. */
    fun npuBench(model: LoadedModel?, runs: Int = 20): String = buildString {
        line("--- NPU bench ---")
        if (model == null || model.format != ModelFormat.ENN_COMPILED) { line("skip: no *.e9965.tflite model in My Models (import it together with its .io.json)"); return@buildString }
        line("model ${File(model.localPath).name} ${model.sizeBytes shr 20}MB")
        val t0 = System.nanoTime()
        val h = runCatching { bridge.openModel(model.localPath) }.getOrElse { line("open FAILED: ${it.javaClass.simpleName}: ${it.message}"); return@buildString }
        line("open ${"%.1f".format((System.nanoTime() - t0) / 1e6)} ms")
        try {
            val ins = bridge.inputSpecs(h); val outs = bridge.outputSpecs(h)
            ins.forEach { line("  in  ${it.name} ${it.shape.contentToString()} ${it.dtype} scale=${it.scale} zp=${it.zeroPoint}") }
            outs.forEach { line("  out ${it.name} ${it.shape.contentToString()} ${it.dtype} scale=${it.scale} zp=${it.zeroPoint}") }
            val inB = ins.map { ByteBuffer.allocateDirect(it.byteSize).order(ByteOrder.nativeOrder()) }
            val outB = outs.map { ByteBuffer.allocateDirect(it.byteSize).order(ByteOrder.nativeOrder()) }
            val samples = mutableListOf<LiteRtNpuBridge.Timing>(); var wall = 0.0
            repeat(runs + 2) { i ->
                val s = System.nanoTime()
                bridge.execute(h, inB, outB)
                val w = (System.nanoTime() - s) / 1e6
                if (i >= 2) { wall += w; (bridge as? LiteRtNpuBridge)?.lastTiming?.let(samples::add) } // first 2 = warm-up
            }
            if (samples.isNotEmpty()) line("avg of $runs: in %.2f / run %.2f / out %.2f / total %.2f ms".format(
                samples.map { it.copyInMs }.average(), samples.map { it.runMs }.average(), samples.map { it.copyOutMs }.average(), wall / runs))
            else line("avg wall %.2f ms".format(wall / runs))
            line("min run %.2f ms, max run %.2f ms".format(samples.minOfOrNull { it.runMs } ?: 0.0, samples.maxOfOrNull { it.runMs } ?: 0.0))
            line("thermal after ${monitor.snapshot().thermalStatus}")
        } catch (e: Throwable) {
            line("run FAILED: ${e.javaClass.simpleName}: ${e.message}")
        } finally { runCatching { bridge.close(h) } }
    }

    private fun StringBuilder.line(s: String) { append(s).append('\n') }
}
