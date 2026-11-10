// Lucian UX/UI Prompt #039 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#039
// Lucian UX/UI Prompt #019 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#019
package dev.exynoslm.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween

/**
 * #039 sliding pill tabs used as a segmented control: one gradient pill glides (500 ms, spring curve) over equal-width
 * tabs; label colors cross-fade in 350 ms. Used for theme / language / backend / chart metric.
 */
@Composable
fun <T> LxSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val idx = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(LxShapes.Pill)
            .background(c.surface)
            .border(1.dp, c.line, LxShapes.Pill)
            .padding(4.dp),
    ) {
        val tabW = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(tabW * idx, lxTween(reduced, LxMotion.PillMs, LxMotion.Spring), label = "pill")
        Box(
            Modifier
                .offset(x = x)
                .width(tabW)
                .height(40.dp)
                .clip(LxShapes.Pill)
                .background(c.grad),
        )
        Row {
            options.forEachIndexed { i, opt ->
                val sel = i == idx
                val col by animateColorAsState(if (sel) c.onGrad else c.ink2, lxTween(reduced, LxMotion.BaseMs, LxMotion.Ease), label = "tabText")
                Box(
                    Modifier
                        .width(tabW)
                        .height(40.dp)
                        .clip(LxShapes.Pill)
                        .lxFocusRing(LxShapes.Pill)
                        .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(opt) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(opt), color = col, fontSize = 13.5.sp, fontWeight = FontWeight(700),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * #019 multi-select chip: on select the fill floods with color and a check slides out from the left
 * (width 0 -> 18 dp, margin 0 -> 6 dp, 450 ms spring); press squashes to .94. Used for filters and agent tools.
 */
@Composable
fun LxFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source, 0.94f)
    val bg by animateColorAsState(if (selected) c.accent else c.surface, lxTween(reduced, LxMotion.BaseMs, LxMotion.Ease), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.accentInk else c.ink2, lxTween(reduced, 300, LxMotion.Ease), label = "chipFg")
    val edge by animateColorAsState(if (selected) Color.Transparent else c.line2, lxTween(reduced, 300, LxMotion.Ease), label = "chipEdge")
    val checkW by animateDpAsState(if (selected) 18.dp else 0.dp, lxTween(reduced, 450, LxMotion.Spring), label = "chipCheckW")
    val gap by animateDpAsState(if (selected) 6.dp else 0.dp, lxTween(reduced, 450, LxMotion.Spring), label = "chipGap")
    val checkA by animateFloatAsState(if (selected) 1f else 0f, lxTween(reduced, 250, LxMotion.Ease), label = "chipCheckA")
    val padStart by animateDpAsState(if (selected) 12.dp else 16.dp, lxTween(reduced, 450, LxMotion.Spring), label = "chipPad")
    val shape = LxShapes.Pill
    Row(
        modifier
            .height(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .border(BorderStroke(1.dp, edge), shape)
            .lxFocusRing(shape)
            .toggleable(value = selected, interactionSource = source, indication = null, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(start = padStart, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(checkW).height(18.dp).graphicsLayer { alpha = checkA }) {
            LxCheck(progress = if (selected) 1f else 0f, color = fg, modifier = Modifier.size(18.dp), strokeUnits = 2.6f)
        }
        Box(Modifier.width(gap))
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight(640), maxLines = 1)
    }
}
