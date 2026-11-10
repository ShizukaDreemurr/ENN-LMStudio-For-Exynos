// Lucian UX/UI Prompt #013 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#013
// Lucian UX/UI Prompt #039 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#039
package dev.exynoslm.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.Lx

/** Wrapping row for badges and chips (6 dp gaps like the Lucian tag rows). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LxFlow(
    modifier: Modifier = Modifier,
    gap: Dp = 6.dp,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** Settings row: label (+ optional hint) on the left, the #013 switch on the right. */
@Composable
fun LxSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val c = Lx.colors
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight(700))
            if (hint != null) Text(hint, Modifier.padding(top = 2.dp), color = c.ink3, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight(600))
        }
        LxSwitch(checked, onCheckedChange)
    }
}

/** 1.2k / 3.4M style counters for the Hub cards. */
fun Long.compactCount(): String = when {
    this >= 1_000_000L -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000L -> "%.1fk".format(this / 1_000.0)
    else -> this.toString()
}
