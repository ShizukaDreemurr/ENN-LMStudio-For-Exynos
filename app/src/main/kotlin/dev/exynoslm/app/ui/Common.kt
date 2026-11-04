package dev.exynoslm.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.exynoslm.telemetry.DeviceSnapshot

fun Long.mb() = "${this shr 20} MB"
fun Long.gb() = "%.2f GB".format(this / 1073741824.0)

/** Live perf strip: tokens/s, TTFT, RAM, thermal, battery, backend. */
@Composable
fun PerfBar(backend: String?, tokPerSec: Double?, ttftMs: Long?, snap: DeviceSnapshot?) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val st = MaterialTheme.typography.labelSmall
            Text(backend ?: "—", style = st)
            Text(tokPerSec?.let { "%.1f tok/s".format(it) } ?: "– tok/s", style = st)
            Text(ttftMs?.let { "TTFT ${it}ms" } ?: "TTFT –", style = st)
            snap?.let {
                Text("RAM ${it.availRamBytes.mb()} free", style = st)
                Text("PSS ${it.appPssKb / 1024}MB", style = st)
                Text("T${it.thermalStatus} ${it.batteryTempC}°C", style = st, color = if (it.thermalSevere) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text("🔋${it.batteryPct}%", style = st)
            }
        }
    }
}
