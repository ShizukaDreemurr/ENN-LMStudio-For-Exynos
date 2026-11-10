// Lucian UX/UI Prompt #013 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#013
package dev.exynoslm.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * #013 stretchy switch, scaled from 140x76 to 60x34 dp. While it travels the knob stretches 1.4x ("rubber") and snaps
 * back after 170 ms, the glyph redraws from X to a check, and the track floods with the Exynos gradient. Draggable.
 */
@Composable
fun LxSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val trackW = 60.dp
    val trackH = 34.dp
    val pad = 4.dp
    val knobBase = 26.dp
    val travelPx = with(density) { (trackW - pad * 2 - knobBase).toPx() }

    val progress = remember { Animatable(if (checked) 1f else 0f) }
    var stretching by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (progress.value != target) {
            if (!reduced) {
                stretching = true
                launch { delay(170); stretching = false }
            }
            progress.animateTo(target, lxTween<Float>(reduced, LxMotion.SwitchMs, LxMotion.Spring))
        }
    }

    val stretch by animateFloatAsState(
        targetValue = if (stretching) 1f else 0f,
        animationSpec = lxTween<Float>(reduced, if (stretching) 180 else 320, LxMotion.Ease),
        label = "stretch",
    )
    val tick by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = lxTween<Float>(reduced, 350, LxMotion.Ease, delayMs = if (checked) 180 else 0),
        label = "tick",
    )

    val knobW = knobBase * (1f + 0.4f * stretch)
    val knobWPx = with(density) { knobW.toPx() }
    val padPx = with(density) { pad.toPx() }
    val trackWPx = with(density) { trackW.toPx() }
    val source = remember { MutableInteractionSource() }
    val dragState = rememberDraggableState { delta ->
        scope.launch { progress.snapTo((progress.value + delta / travelPx).coerceIn(0f, 1f)) }
    }
    val trackShape = LxShapes.Pill

    Box(
        modifier
            .size(trackW, trackH)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(trackShape)
            .background(c.surface3)
            .background(c.grad, alpha = progress.value.coerceIn(0f, 1f))
            .lxFocusRing(trackShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = source,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = {
                    val want = progress.value > 0.5f
                    scope.launch { progress.animateTo(if (want) 1f else 0f, lxTween<Float>(reduced, LxMotion.SwitchMs, LxMotion.Spring)) }
                    if (want != checked) onCheckedChange(want)
                },
            ),
    ) {
        Box(
            Modifier
                .offset {
                    val p = progress.value
                    val end = trackWPx - padPx - knobWPx
                    IntOffset((padPx + (end - padPx) * p).roundToInt(), padPx.roundToInt())
                }
                .size(knobW, knobBase)
                .shadow(3.dp, LxShapes.Pill)
                .clip(LxShapes.Pill)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            // X (fades/shrinks) -> check (drawn), see `.u013-x` / `.u013-v`.
            Canvas(Modifier.size(14.dp)) {
                val s = size.minDimension / 24f
                val a = (1f - tick).coerceIn(0f, 1f)
                if (a > 0f) {
                    val col = Color(0xFFA3A0AA).copy(alpha = a)
                    val st = Stroke(width = 3f * s, cap = StrokeCap.Round)
                    drawLine(col, Offset(7.5f * s, 7.5f * s), Offset(16.5f * s, 16.5f * s), st.width, StrokeCap.Round)
                    drawLine(col, Offset(16.5f * s, 7.5f * s), Offset(7.5f * s, 16.5f * s), st.width, StrokeCap.Round)
                }
            }
            LxCheck(progress = tick, color = c.accent, modifier = Modifier.size(14.dp), strokeUnits = 3f)
        }
    }
}

private fun Modifier.alpha(a: Float): Modifier = this.graphicsLayer { alpha = a }
