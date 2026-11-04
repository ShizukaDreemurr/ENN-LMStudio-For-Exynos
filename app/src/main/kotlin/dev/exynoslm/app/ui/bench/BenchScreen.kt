package dev.exynoslm.app.ui.bench

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.exynoslm.app.data.AppDatabase
import dev.exynoslm.app.data.BenchmarkEntity
import dev.exynoslm.app.data.InferenceHub
import dev.exynoslm.model.Backend
import dev.exynoslm.runtime.*
import dev.exynoslm.telemetry.DeviceMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs the same prompt on each backend that can load the current model.
 * Prefill tok/s ≈ promptTokens / TTFT; decode tok/s from the engine's Usage event.
 * For the 10-minute battery test, use the instrumented benchmark (see README "Benchmarking").
 */
@HiltViewModel
class BenchViewModel @Inject constructor(
    private val engines: Map<Backend, @JvmSuppressWildcards InferenceEngine>,
    private val hub: InferenceHub,
    private val monitor: DeviceMonitor,
    private val db: AppDatabase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: android.content.Context,
    private val store: dev.exynoslm.storage.ModelStore,
) : ViewModel() {
    private val report = DeviceReport(ctx, (engines[Backend.NPU_ENN] as dev.exynoslm.runtime.enn.EnnEngine).bridge, monitor)
    val reportText = MutableStateFlow("")

    /** Device info + NPU bench + saved LLM benchmarks, as one copyable text. */
    fun measureDevice() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
        running.value = true
        reportText.value = "Đang đo…"
        val npuModel = store.list().firstOrNull { it.format == dev.exynoslm.model.ModelFormat.ENN_COMPILED }
        val text = buildString {
            append(runCatching { report.basic() }.getOrElse { "basic FAILED: $it\n" })
            append(runCatching { report.npuBench(npuModel) }.getOrElse { "npu FAILED: $it\n" })
            append("--- LLM benchmarks (saved) ---\n")
            results.value.take(12).forEach { r ->
                append("${r.backend} ${r.modelId.substringAfter(':')} load=${r.loadMs}ms ttft=${r.ttftMs}ms prefill=%.0f decode=%.1f tok/s pss=${r.peakPssMb}MB bat=-${r.batteryDropPct}%%\n"
                    .format(r.prefillTokPerSec, r.decodeTokPerSec))
            }
            if (results.value.isEmpty()) append("(none — load a .task model and press Run NPU / GPU / CPU)\n")
        }
        reportText.value = text
        running.value = false
    }
    val results = db.benchmarks().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val log = MutableStateFlow<List<String>>(emptyList())
    val running = MutableStateFlow(false)
    private var job: Job? = null

    fun run(prompt: String = PROMPT, maxNew: Int = 128) {
        val model = hub.active.value.model ?: run { log.value = listOf("Load a model first."); return }
        job = viewModelScope.launch {
            running.value = true; log.value = emptyList()
            hub.unloadAll() // free RAM so backends don't compete
            for ((b, engine) in engines) {
                val dev = monitor.snapshot()
                val compat = Compatibility.check(model, engine.capabilities(), dev.toDeviceState())
                if (!compat.ok) { log.update { it + "$b skipped: ${compat.reasons.joinToString()}" }; continue }
                val bat0 = dev.batteryPct
                when (val r = engine.load(model, InferenceConfig(maxContextTokens = 2048))) {
                    is LoadResult.Failure -> { log.update { it + "$b load failed: ${r.reason}" }; continue }
                    is LoadResult.Success -> {
                        var usage: Usage? = null; var peakPss = 0
                        engine.generate(GenerationRequest(listOf(ChatMessage(Role.USER, prompt)), SamplingParams(temperature = 0f, topK = 1, maxNewTokens = maxNew, seed = 0)))
                            .collect { ev ->
                                if (ev is GenerationEvent.TokenGenerated) peakPss = maxOf(peakPss, monitor.snapshot().appPssKb / 1024)
                                if (ev is GenerationEvent.Usage) usage = ev.usage
                            }
                        engine.unload()
                        val u = usage ?: continue
                        val prefill = if (u.timeToFirstTokenMs > 0) u.promptTokens * 1000.0 / u.timeToFirstTokenMs else 0.0
                        db.benchmarks().insert(BenchmarkEntity(modelId = model.id, backend = b.name, loadMs = r.loadTimeMs, ttftMs = u.timeToFirstTokenMs,
                            prefillTokPerSec = prefill, decodeTokPerSec = u.tokensPerSecond, peakPssMb = peakPss, batteryDropPct = bat0 - monitor.snapshot().batteryPct))
                        log.update { it + "$b done: %.1f tok/s".format(u.tokensPerSecond) }
                    }
                }
            }
            running.value = false
        }
    }
    fun stop() { job?.cancel(); running.value = false }

    companion object { const val PROMPT = "Explain in three sentences why on-device AI improves privacy." }
}

@Composable
fun BenchScreen(vm: BenchViewModel = hiltViewModel()) {
    val results by vm.results.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val report by vm.reportText.collectAsStateWithLifecycle()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Đo máy thật / Device report", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button({ copied = false; vm.measureDevice() }, enabled = !running) { Text("Đo máy") }
            OutlinedButton({ clipboard.setText(androidx.compose.ui.text.AnnotatedString(report)); copied = true }, enabled = report.isNotBlank() && !running) {
                Text(if (copied) "Đã copy ✓" else "Copy báo cáo")
            }
        }
        if (report.isNotBlank()) Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            Text(report, Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(6.dp),
                style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("LLM benchmark", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button({ vm.run() }, enabled = !running) { Text("Run NPU / GPU / CPU") }
            if (running) OutlinedButton({ vm.stop() }) { Text("Stop") }
        }
        log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        LazyColumn {
            items(results) { r ->
                Text("${r.backend} · ${r.modelId.substringAfter(':')} · load ${r.loadMs}ms · TTFT ${r.ttftMs}ms · prefill %.0f · decode %.1f tok/s · PSS ${r.peakPssMb}MB · 🔋-${r.batteryDropPct}%%"
                    .format(r.prefillTokPerSec, r.decodeTokPerSec), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
