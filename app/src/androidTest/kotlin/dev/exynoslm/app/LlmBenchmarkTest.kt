package dev.exynoslm.app

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.exynoslm.model.Backend
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.model.Quantization
import dev.exynoslm.runtime.*
import dev.exynoslm.runtime.cpu.MediaPipeLlmEngine
import dev.exynoslm.telemetry.DeviceMonitor
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device benchmark. Push a model first:
 *   adb push gemma3-1b-it-int4.task /data/local/tmp/bench.task
 *   ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.exynoslm.app.LlmBenchmarkTest \
 *       -Pandroid.testInstrumentationRunnerArguments.minutes=10
 * Results are printed to logcat with tag "BENCH" (TTFT, tok/s, peak PSS, battery drain).
 */
@RunWith(AndroidJUnit4::class)
class LlmBenchmarkTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val modelFile = File(args.getString("model") ?: "/data/local/tmp/bench.task")

    private fun model() = LoadedModel("bench", "bench", modelFile.path, ModelFormat.MEDIAPIPE_TASK, Quantization.guess(modelFile.name), modelFile.length())

    @Test fun cpuVsGpu() = runBlocking {
        assumeTrue("model not pushed", modelFile.exists())
        for (b in listOf(Backend.GPU, Backend.CPU)) bench(MediaPipeLlmEngine(ctx, b), minutes = 0)
    }

    @Test fun batteryDrain() = runBlocking {
        assumeTrue("model not pushed", modelFile.exists())
        val minutes = args.getString("minutes")?.toInt() ?: 10
        bench(MediaPipeLlmEngine(ctx, Backend.valueOf(args.getString("backend") ?: "GPU")), minutes)
    }

    private suspend fun bench(engine: InferenceEngine, minutes: Int) {
        val mon = DeviceMonitor(ctx)
        val bat0 = mon.snapshot().batteryPct
        val load = engine.load(model(), InferenceConfig(maxContextTokens = 2048))
        if (load is LoadResult.Failure) { log("${engine.backend} load failed: ${load.reason}"); return }
        val end = System.currentTimeMillis() + minutes * 60_000L
        var runs = 0; var peak = 0
        do {
            engine.generate(GenerationRequest(listOf(ChatMessage(Role.USER, "Write a short paragraph about the ocean.")),
                SamplingParams(temperature = 0f, topK = 1, maxNewTokens = 128))).collect { ev ->
                if (ev is GenerationEvent.TokenGenerated) peak = maxOf(peak, Debug.getPss().toInt() / 1024)
                if (ev is GenerationEvent.Usage && runs == 0)
                    log("${engine.backend} load=${(load as LoadResult.Success).loadTimeMs}ms ttft=${ev.usage.timeToFirstTokenMs}ms decode=%.1f tok/s".format(ev.usage.tokensPerSecond))
            }
            runs++
        } while (System.currentTimeMillis() < end)
        engine.unload()
        log("${engine.backend} runs=$runs peakPss=${peak}MB battery=-${bat0 - mon.snapshot().batteryPct}% thermal=${mon.snapshot().thermalStatus}")
    }

    private fun log(s: String) { android.util.Log.i("BENCH", s); println("BENCH $s") }
}
