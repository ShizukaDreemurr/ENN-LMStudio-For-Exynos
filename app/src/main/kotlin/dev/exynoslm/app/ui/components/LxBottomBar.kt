// Lucian UX/UI Prompt #041 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#041
package dev.exynoslm.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween
import kotlin.math.abs
import kotlin.math.roundToInt

class LxTab(val label: String, val icon: ImageVector)

/**
 * #041 notched bottom tab bar. The selected tab's icon lifts into a gradient bubble (spring, 550 ms), the label of the
 * selected tab fades in below it (only the selected one is shown, which is what fixes the old cramped/truncated
 * six-label bar), and the bar's top edge dips into a matching notch that follows the bubble with the original spring
 * integration (`a = (target-x)*.16 - v*.42` per 60 Hz frame). Scaled by ~.86 (bar 72 dp, bubble 54 dp, notch r 35 dp).
 */
@Composable
fun LxBottomBar(
    tabs: List<LxTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val barH = 72.dp
    val rise = 31.dp
    val bubble = 54.dp
    val notchR = 35.dp
    val sidePad = 10.dp

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .height(barH + rise),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val sidePx = with(density) { sidePad.toPx() }
        val tabW = (widthPx - 2 * sidePx) / tabs.size.coerceAtLeast(1)
        fun centerOf(i: Int) = sidePx + tabW * (i + 0.5f)

        var x by remember(tabs.size, widthPx) { mutableFloatStateOf(centerOf(selectedIndex)) }
        LaunchedEffect(selectedIndex, widthPx, tabs.size) {
            val target = centerOf(selectedIndex)
            if (reduced) {
                x = target
                return@LaunchedEffect
            }
            val eps = 0.3f * density.density
            var v = 0f
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (abs(target - x) > eps || abs(v) > eps) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val steps = ((now - last) / 16_666_667f).roundToInt().coerceIn(1, 4)
                last = now
                repeat(steps) {
                    val a = (target - x) * 0.16f - v * 0.42f
                    v += a
                    x += v
                }
            }
            x = target
        }

        // Bar with the notch cut out.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(barH)
                .align(Alignment.BottomCenter),
        ) {
            val r = notchR.toPx()
            val cy = -4.dp.toPx()
            val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(28.dp.toPx()))) }
            val notch = Path().apply { addOval(Rect(Offset(x - r, cy - r), Size(2 * r, 2 * r))) }
            val shape = Path.combine(PathOperation.Difference, body, notch)
            drawPath(shape, c.surface)
            drawPath(shape, c.line2, style = Stroke(width = 1.dp.toPx()))
        }

        // Bubble that rides in the notch.
        Box(
            Modifier
                .offset { IntOffset((x - bubble.toPx() / 2f).roundToInt(), 0) }
                .size(bubble)
                .clip(LxShapes.Circle)
                .background(c.grad),
        )

        // Tabs (full height so the bubble area is tappable too).
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = sidePad),
        ) {
            tabs.forEachIndexed { i, tab ->
                val selected = i == selectedIndex
                val lift by animateDpAsState(
                    if (selected) rise - 4.dp - 12.dp else rise + 31.dp - 12.dp,
                    lxTween(reduced, 550, LxMotion.Spring), label = "tabLift",
                )
                val tint by animateColorAsState(if (selected) c.onGrad else c.ink3, lxTween(reduced, 300, LxMotion.Ease), label = "tabTint")
                val labelA by animateFloatAsState(if (selected) 1f else 0f, lxTween(reduced, 300, LxMotion.Ease), label = "tabLabelA")
                val labelY by animateDpAsState(if (selected) 0.dp else 6.dp, lxTween(reduced, 450, LxMotion.Spring), label = "tabLabelY")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .lxFocusRing(LxShapes.Tile)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(i) }),
                ) {
                    Icon(
                        tab.icon, contentDescription = null, tint = tint,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = lift)
                            .size(24.dp),
                    )
                    Text(
                        tab.label,
                        color = c.ink,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight(700),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .offset(y = labelY)
                            .graphicsLayer { alpha = labelA },
                    )
                }
            }
        }
    }
}
