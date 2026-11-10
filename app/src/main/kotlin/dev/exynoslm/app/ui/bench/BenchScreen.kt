package dev.exynoslm.app.ui.bench

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.R
import dev.exynoslm.app.ui.components.LxBadge
import dev.exynoslm.app.ui.components.LxBar
import dev.exynoslm.app.ui.components.LxBarChart
import dev.exynoslm.app.ui.components.LxButton
import dev.exynoslm.app.ui.components.LxButtonKind
import dev.exynoslm.app.ui.components.LxCard
import dev.exynoslm.app.ui.components.LxCopyButton
import dev.exynoslm.app.ui.components.LxEmptyState
import dev.exynoslm.app.ui.components.LxProgressBar
import dev.exynoslm.app.ui.components.LxScreenHeader
import dev.exynoslm.app.ui.components.LxSectionTitle
import dev.exynoslm.app.ui.components.LxSegmented
import dev.exynoslm.app.ui.components.LxStatTile
import dev.exynoslm.app.ui.components.LxToastHost
import dev.exynoslm.app.ui.components.LxTone
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMono
import dev.exynoslm.app.ui.theme.LxShapes
import kotlinx.coroutines.delay
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

private enum class BenchMetric { Decode, Prefill, Ttft }

private val BackendOrder = listOf("NPU_ENN", "GPU", "CPU")

private fun String.backendLabel(): String = if (this == "NPU_ENN") "NPU" else this

@Composable
fun BenchScreen(vm: BenchViewModel = hiltViewModel()) {
    val results by vm.results.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val report by vm.reportText.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val c = Lx.colors
    var copied by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var metric by remember { mutableStateOf(BenchMetric.Decode) }
    val copiedToast = stringResource(R.string.bench_copied_toast)
    val metricDecode = stringResource(R.string.bench_metric_decode)
    val metricPrefill = stringResource(R.string.bench_metric_prefill)
    val metricTtft = stringResource(R.string.bench_metric_ttft)
    LaunchedEffect(copied) { if (copied) { delay(2200); copied = false } }

    // Latest result per backend (the DAO returns newest first), in NPU / GPU / CPU order.
    val latest = results.groupBy { it.backend }.map { it.value.first() }
        .sortedBy { r -> BackendOrder.indexOf(r.backend).let { i -> if (i < 0) BackendOrder.size else i } }
    val raw = latest.map { r ->
        val v = when (metric) {
            BenchMetric.Decode -> r.decodeTokPerSec.toFloat()
            BenchMetric.Prefill -> r.prefillTokPerSec.toFloat()
            BenchMetric.Ttft -> r.ttftMs.toFloat()
        }
        r.backend.backendLabel() to v
    }
    // Lower TTFT is better; for the speed metrics the highest bar wins.
    val best: Float? = if (metric == BenchMetric.Ttft) raw.map { it.second }.filter { it > 0f }.minOrNull() else raw.maxOfOrNull { it.second }
    val bars = raw.map { (label, v) ->
        val text = when (metric) {
            BenchMetric.Decode -> "%.1f".format(v)
            BenchMetric.Prefill -> "%.0f".format(v)
            BenchMetric.Ttft -> "${v.toLong()} ms"
        }
        LxBar(label, v, text, highlight = best != null && v == best)
    }
    val avgFmt = if (metric == BenchMetric.Ttft) "%.0f" else "%.1f"
    val avgPrefix = stringResource(R.string.bench_avg)
    val avgText: String? = if (bars.isEmpty()) null else avgPrefix + " " + avgFmt.format(bars.map { it.value }.average())

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LxScreenHeader(title = stringResource(R.string.nav_bench), kicker = stringResource(R.string.app_kicker))
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Device report ("Đo máy"): measure + the prominent copy button.
                item(key = "device") {
                    LxCard(Modifier.fillMaxWidth()) {
                        LxSectionTitle(stringResource(R.string.bench_device_title))
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LxButton(
                                stringResource(R.string.bench_measure), { copied = false; vm.measureDevice() }, Modifier.weight(1f),
                                kind = if (report.isBlank()) LxButtonKind.Primary else LxButtonKind.Secondary,
                                icon = Icons.Default.Speed, enabled = !running,
                            )
                            LxCopyButton(
                                label = stringResource(R.string.bench_copy),
                                copiedLabel = stringResource(R.string.bench_copied),
                                copied = copied,
                                onClick = { clipboard.setText(AnnotatedString(report)); copied = true; toast = copiedToast },
                                modifier = Modifier.weight(1f),
                                enabled = report.isNotBlank() && !running,
                            )
                        }
                        if (running) LxProgressBar(null, Modifier.padding(top = 12.dp))
                        if (report.isNotBlank()) {
                            Text(
                                report,
                                Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp)
                                    .clip(LxShapes.Tile)
                                    .background(c.surface2)
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp),
                                color = c.ink2, fontFamily = LxMono, fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight(600),
                            )
                        }
                    }
                }
                // LLM benchmark controls + live log.
                item(key = "llm") {
                    LxCard(Modifier.fillMaxWidth()) {
                        LxSectionTitle(stringResource(R.string.bench_llm_title))
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LxButton(
                                stringResource(R.string.bench_run), { vm.run() }, Modifier.weight(1f),
                                icon = Icons.Default.PlayArrow, enabled = !running,
                            )
                            if (running) LxButton(stringResource(R.string.stop), { vm.stop() }, kind = LxButtonKind.Danger, icon = Icons.Default.Stop)
                        }
                        if (log.isNotEmpty()) {
                            Column(
                                Modifier.padding(top = 12.dp).fillMaxWidth().clip(LxShapes.Tile).background(c.surface2).padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                log.forEach { Text(it, color = c.ink2, fontFamily = LxMono, fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight(600)) }
                            }
                        }
                    }
                }
                if (latest.isEmpty()) {
                    item(key = "empty") { LxEmptyState(stringResource(R.string.bench_chart_title), stringResource(R.string.bench_no_results)) }
                } else {
                    // Animated bar chart: speed per backend for the selected metric.
                    item(key = "chart") {
                        LxCard(Modifier.fillMaxWidth()) {
                            LxSectionTitle(stringResource(R.string.bench_chart_title))
                            LxSegmented(
                                options = BenchMetric.entries.toList(),
                                selected = metric,
                                onSelect = { metric = it },
                                label = {
                                    when (it) {
                                        BenchMetric.Decode -> metricDecode
                                        BenchMetric.Prefill -> metricPrefill
                                        BenchMetric.Ttft -> metricTtft
                                    }
                                },
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            LxBarChart(bars, Modifier.padding(top = 14.dp), avgLabel = avgText)
                        }
                    }
                    item(key = "historyTitle") { LxSectionTitle(stringResource(R.string.bench_history), Modifier.padding(top = 6.dp)) }
                    items(results.take(30), key = { it.id }) { r ->
                        LxCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LxBadge(r.backend.backendLabel(), tone = LxTone.Accent)
                                Text(
                                    r.modelId.substringAfter(':'), Modifier.weight(1f), color = c.ink, fontSize = 13.5.sp,
                                    fontWeight = FontWeight(750), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LxStatTile(metricDecode, "%.1f".format(r.decodeTokPerSec), Modifier.weight(1f), valueColor = c.accent, sub = "tok/s")
                                LxStatTile(metricPrefill, "%.0f".format(r.prefillTokPerSec), Modifier.weight(1f), sub = "tok/s")
                                LxStatTile(metricTtft, "${r.ttftMs} ms", Modifier.weight(1f))
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LxStatTile(stringResource(R.string.bench_load), "${r.loadMs} ms", Modifier.weight(1f))
                                LxStatTile(stringResource(R.string.perf_pss), "${r.peakPssMb} MB", Modifier.weight(1f))
                                LxStatTile(stringResource(R.string.perf_battery), "-${r.batteryDropPct}%", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        LxToastHost(toast, onDismiss = { toast = null }, modifier = Modifier.align(Alignment.BottomCenter), tone = LxTone.Success)
    }
}
