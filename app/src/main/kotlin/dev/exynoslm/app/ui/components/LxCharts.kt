// Lucian UX/UI Prompt #095 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#095
// Lucian UX/UI Prompt #098 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#098
package dev.exynoslm.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween
import kotlinx.coroutines.delay

class LxBar(val label: String, val value: Float, val valueText: String, val highlight: Boolean = false)

/**
 * #095 bar chart port: dashed grid (solid baseline), bars grow with the 650 ms spring curve and a 28 ms-per-bar
 * stagger, the best bar is gradient filled, tapping a bar swaps its value label for an ink tooltip pill, and a dashed
 * accent average line carries its badge in the right gutter (44 dp).
 */
@Composable
fun LxBarChart(
    bars: List<LxBar>,
    modifier: Modifier = Modifier,
    avgLabel: String? = null,
    height: Dp = 180.dp,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    var selected by remember { mutableIntStateOf(-1) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        started = true
    }
    val maxV = (bars.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(0.0001f)
    val avg = if (bars.isEmpty()) 0f else bars.map { it.value }.average().toFloat()
    val labelH = 24.dp
    val zone = height - labelH
    val fill = 0.84f
    val avgY = zone - zone * (fill * (avg / maxV))

    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.matchParentSize()) {
            val zonePx = (this.size.height - labelH.toPx())
            for (k in 0..3) {
                val y = zonePx - zonePx * fill * (k / 3f)
                drawLine(
                    c.line2, Offset(0f, y), Offset(this.size.width - 44.dp.toPx(), y), 1.dp.toPx(),
                    pathEffect = if (k == 0) null else PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
            if (bars.size > 1) {
                val ay = zonePx - zonePx * fill * (avg / maxV)
                drawLine(
                    c.accent, Offset(0f, ay), Offset(this.size.width - 44.dp.toPx(), ay), 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
        }
        if (bars.size > 1 && avgLabel != null) {
            Text(
                avgLabel,
                Modifier.align(Alignment.TopEnd).offset(y = avgY - 10.dp).clip(RoundedCornerShape(6.dp)).background(c.accent).padding(horizontal = 6.dp, vertical = 3.dp),
                color = c.accentInk, fontSize = 10.sp, fontWeight = FontWeight(800), maxLines = 1,
            )
        }
        Row(Modifier.matchParentSize().padding(end = 44.dp)) {
            bars.forEachIndexed { i, b ->
                key(b.label) {
                    val frac by animateFloatAsState(
                        if (started) (b.value / maxV).coerceIn(0f, 1f) else 0f,
                        lxTween<Float>(reduced, 650, LxMotion.Spring, delayMs = i * 28),
                        label = "bar",
                    )
                    val isSel = selected == i
                    val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 4.dp, bottomStart = 4.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button,
                            ) { selected = if (isSel) -1 else i }
                            .semantics { contentDescription = b.label + " " + b.valueText },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                            val barH = maxHeight * fill * frac
                            Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    b.valueText,
                                    Modifier
                                        .padding(bottom = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) c.ink else Color.Transparent)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = if (isSel) c.page else c.ink2,
                                    fontSize = 11.sp, fontWeight = FontWeight(800), maxLines = 1,
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(barH)
                                        .clip(shape)
                                        .then(
                                            when {
                                                isSel -> Modifier.background(c.accent)
                                                b.highlight -> Modifier.background(c.grad)
                                                else -> Modifier.background(c.accent.copy(alpha = 0.38f))
                                            },
                                        ),
                                )
                            }
                        }
                        Text(
                            b.label,
                            Modifier.height(labelH).wrapContentHeight(Alignment.CenterVertically),
                            color = if (isSel) c.ink else c.ink3,
                            fontSize = 11.sp, fontWeight = FontWeight(700), maxLines = 1, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * #098 rolling digits: every digit sits in a 0-9 column that slides to the new value (900 ms, cubic-bezier(.3,1.25,.5,1),
 * 45 ms per place), so only digits that change spin. Non-digits are plain glyphs.
 */
@Composable
fun LxRollingNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val lineSp: TextUnit = when {
        style.lineHeight != TextUnit.Unspecified -> style.lineHeight
        style.fontSize != TextUnit.Unspecified -> style.fontSize * 1.25f
        else -> 18.sp
    }
    val lineH = with(density) { lineSp.toDp() }
    val s = style.copy(lineHeight = lineSp)
    Row(modifier.height(lineH).clipToBounds(), verticalAlignment = Alignment.Top) {
        text.forEachIndexed { i, ch ->
            key(text.length - i) {
                if (ch.isDigit()) {
                    val d = ch - '0'
                    val off by animateDpAsState(
                        -lineH * d,
                        lxTween(reduced, LxMotion.RollMs, LxMotion.RollEase, delayMs = i * 45),
                        label = "roll",
                    )
                    Box(Modifier.height(lineH)) {
                        Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).offset(y = off)) {
                            for (n in 0..9) Text(n.toString(), Modifier.height(lineH), style = s, color = color, maxLines = 1)
                        }
                    }
                } else {
                    Text(ch.toString(), Modifier.height(lineH), style = s, color = color, maxLines = 1)
                }
            }
        }
    }
}
