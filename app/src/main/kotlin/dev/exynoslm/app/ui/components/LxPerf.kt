// Lucian UX/UI Prompt #098 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#098
// Lucian UX/UI Prompt #083 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#083
package dev.exynoslm.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.exynoslm.app.R
import dev.exynoslm.app.ui.mb
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.telemetry.DeviceSnapshot

/**
 * Perf strip (replaces the old one-line PerfBar): a row of #098 stat tiles - backend, rolling tok/s, TTFT, RAM with a
 * #083 meter, app PSS, thermal state and battery. Scrolls horizontally on narrow screens.
 */
@Composable
fun LxPerfStrip(
    backend: String?,
    tokPerSec: Double?,
    ttftMs: Long?,
    snap: DeviceSnapshot?,
    modifier: Modifier = Modifier,
) {
    val c = Lx.colors
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LxStatTile(stringResource(R.string.perf_backend), backend ?: "—", Modifier.width(88.dp), valueColor = if (backend != null) c.accent else c.ink3)
        LxStatTile(stringResource(R.string.perf_toks), tokPerSec?.let { "%.1f".format(it) } ?: "–", Modifier.width(88.dp), rolling = tokPerSec != null)
        LxStatTile(stringResource(R.string.perf_ttft), ttftMs?.let { "$it ms" } ?: "–", Modifier.width(88.dp))
        if (snap != null) {
            val usedFrac = if (snap.totalRamBytes > 0) 1f - snap.availRamBytes.toFloat() / snap.totalRamBytes.toFloat() else 0f
            LxStatTile(stringResource(R.string.perf_ram_free), snap.availRamBytes.mb(), Modifier.width(104.dp)) {
                LxProgressBar(usedFrac, color = if (usedFrac > 0.85f) c.danger else c.accent, height = 4.dp, modifier = Modifier.padding(top = 4.dp))
            }
            LxStatTile(stringResource(R.string.perf_pss), "${snap.appPssKb / 1024} MB", Modifier.width(88.dp))
            LxStatTile(
                stringResource(R.string.perf_thermal), "T${snap.thermalStatus} · ${snap.batteryTempC.toInt()}°C", Modifier.width(104.dp),
                valueColor = if (snap.thermalSevere) c.danger else c.mint,
            )
            LxStatTile(stringResource(R.string.perf_battery), "${snap.batteryPct}%", Modifier.width(88.dp)) {
                LxProgressBar(
                    snap.batteryPct / 100f,
                    color = if (snap.batteryPct <= 15 && !snap.charging) c.danger else c.mint,
                    height = 4.dp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
