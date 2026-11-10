package dev.exynoslm.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.exynoslm.app.ui.components.LxPerfStrip
import dev.exynoslm.telemetry.DeviceSnapshot

fun Long.mb() = "${this shr 20} MB"
fun Long.gb() = "%.2f GB".format(this / 1073741824.0)

/** Live perf strip: backend, tokens/s, TTFT, RAM, PSS, thermal, battery (see [LxPerfStrip]). */
@Composable
fun PerfBar(backend: String?, tokPerSec: Double?, ttftMs: Long?, snap: DeviceSnapshot?) {
    LxPerfStrip(backend, tokPerSec, ttftMs, snap, Modifier)
}
