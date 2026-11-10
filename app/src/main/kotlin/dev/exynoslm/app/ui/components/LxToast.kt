// Lucian UX/UI Prompt #070 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#070
package dev.exynoslm.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.lxTween
import kotlinx.coroutines.delay

private class LastMessage(var value: String = "")

/**
 * #070 toast. Enters from 90 dp below with scale .9 (500 ms spring) and a 350 ms fade, a 2 dp line at the bottom runs
 * out over [durationMs], then it leaves (350 ms ease). Place at the bottom of a screen `Box`; `message == null` hides it.
 */
@Composable
fun LxToastHost(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tone: LxTone = LxTone.Accent,
    durationMs: Int = 4000,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val col = tone.color()
    val last = remember { LastMessage() }
    if (message != null) last.value = message
    val dismiss by rememberUpdatedState(onDismiss)
    val left = remember { Animatable(1f) }
    LaunchedEffect(message) {
        if (message != null) {
            if (reduced) {
                delay(durationMs.toLong())
            } else {
                left.snapTo(1f)
                left.animateTo(0f, tween(durationMs, easing = LinearEasing))
            }
            dismiss()
        }
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(lxTween<IntOffset>(reduced, LxMotion.ToastMs, LxMotion.Spring)) { (it * 1.2f).toInt() } +
                scaleIn(lxTween<Float>(reduced, LxMotion.ToastMs, LxMotion.Spring), initialScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                fadeIn(lxTween(reduced, 350, LxMotion.Ease)),
            exit = slideOutVertically(lxTween<IntOffset>(reduced, 350, LxMotion.Ease)) { it } + fadeOut(lxTween(reduced, 250, LxMotion.Ease)),
        ) {
            val shape = RoundedCornerShape(20.dp)
            val vec: ImageVector = when (tone) {
                LxTone.Danger -> Icons.Default.Error
                LxTone.Warn -> Icons.Default.Warning
                LxTone.Success -> Icons.Default.CheckCircle
                else -> Icons.Default.Info
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, shape, ambientColor = c.shadowStrong, spotColor = c.shadowStrong)
                    .clip(shape)
                    .background(c.surface)
                    .border(1.dp, c.line, shape)
                    .drawBehind {
                        if (!reduced) {
                            val inset = 16.dp.toPx()
                            drawRect(
                                col.copy(alpha = 0.5f),
                                topLeft = androidx.compose.ui.geometry.Offset(inset, size.height - 2.dp.toPx()),
                                size = Size((size.width - 2 * inset) * left.value, 2.dp.toPx()),
                            )
                        }
                    },
            ) {
                Row(
                    Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(vec, null, tint = col, modifier = Modifier.size(18.dp)) }
                    Text(
                        last.value,
                        Modifier.weight(1f),
                        color = c.ink, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight(700),
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    LxIconButton(
                        Icons.Default.Close, "×", onDismiss,
                        tint = c.ink3, container = androidx.compose.ui.graphics.Color.Transparent, size = 36.dp,
                    )
                }
            }
        }
    }
}
