// Lucian UX/UI Prompt #083 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#083
package dev.exynoslm.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween

/**
 * #083 "queue bar turning into progress". With [progress] == null two segments stretch and slide (1.6 s, curves
 * cubic-bezier(.65,.05,.35,1) and (.25,.6,.4,1), the second delayed .55 s); once a value arrives the same bar
 * cross-fades (400 ms) into a filling bar (500 ms ease) that turns mint when [done].
 */
@Composable
fun LxProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = Lx.colors.accent,
    done: Boolean = false,
    height: Dp = 6.dp,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "queueBar")
    val a by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes<Float> {
                durationMillis = 1600
                0f at 0 using LxMotion.BarA
                1f at 960
            },
        ),
        label = "segA",
    )
    val b by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes<Float> {
                durationMillis = 1600
                0f at 0 using LxMotion.BarB
                1f at 960
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(550),
        ),
        label = "segB",
    )
    val segAlpha by animateFloatAsState(if (progress == null) 1f else 0f, lxTween<Float>(reduced, LxMotion.BaseMs, LxMotion.Ease), label = "segAlpha")
    val frac by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), lxTween<Float>(reduced, LxMotion.SlowMs, LxMotion.Ease), label = "fill")
    val fillColor by animateColorAsState(if (done) c.mint else color, lxTween(reduced, 400, LxMotion.Ease), label = "fillColor")

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(LxShapes.Pill)
            .background(c.surface3)
            .semantics {
                progressBarRangeInfo =
                    if (progress == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val r = CornerRadius(h / 2f, h / 2f)
            if (segAlpha > 0.01f) {
                val pa = if (reduced) 0.5f else a
                val leftA = (-0.35f + (1.0f - -0.35f) * pa) * w
                val widthA = (0.35f + (0.9f - 0.35f) * pa) * w
                drawRoundRect(color.copy(alpha = segAlpha), Offset(leftA, 0f), Size(widthA.coerceAtLeast(0f), h), r)
                if (!reduced) {
                    val leftB = (-2.0f + (1.07f - -2.0f) * b) * w
                    val widthB = (2.0f + (0.01f - 2.0f) * b) * w
                    drawRoundRect(color.copy(alpha = segAlpha), Offset(leftB, 0f), Size(widthB.coerceAtLeast(0f), h), r)
                }
            }
            if (segAlpha < 0.99f && frac > 0f) {
                drawRoundRect(fillColor.copy(alpha = 1f - segAlpha), Offset.Zero, Size((w * frac).coerceAtLeast(h), h), r)
            }
        }
    }
}

/** #083 `.u083-st` spinner: 2 dp ring with a transparent quarter, 0.7 s linear rotation. */
@Composable
fun LxSpinner(modifier: Modifier = Modifier, color: Color = Lx.colors.accent, size: Dp = 14.dp) {
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "spinAngle")
    Canvas(modifier.size(size)) {
        val sw = 2.dp.toPx()
        rotate(if (reduced) 0f else angle) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(sw / 2f, sw / 2f),
                size = Size(this.size.width - sw, this.size.height - sw),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
    }
}

enum class LxStepState { Running, Done, Failed, Idle }

/** #083 log-line status glyph: spinner while running, mint check once done, danger cross on failure. */
@Composable
fun LxStepIcon(state: LxStepState, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val checkP by animateFloatAsState(if (state == LxStepState.Done) 1f else 0f, lxTween<Float>(reduced, 400, LxMotion.Ease), label = "stepCheck")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when (state) {
            LxStepState.Running -> LxSpinner(color = c.accent, size = size - 2.dp)
            LxStepState.Done -> LxCheck(progress = checkP, color = c.mint, modifier = Modifier.size(size), strokeUnits = 2.8f)
            LxStepState.Failed -> Canvas(Modifier.size(size - 4.dp)) {
                val sw = 2.2.dp.toPx()
                drawLine(c.danger, Offset(0f, 0f), Offset(this.size.width, this.size.height), sw, StrokeCap.Round)
                drawLine(c.danger, Offset(this.size.width, 0f), Offset(0f, this.size.height), sw, StrokeCap.Round)
            }
            LxStepState.Idle -> Box(Modifier.size(7.dp).background(c.line2, LxShapes.Circle))
        }
    }
}
