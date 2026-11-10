// Lucian UX/UI Prompt #237 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#237
package dev.exynoslm.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMono
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import kotlinx.coroutines.delay

/**
 * #237 line-by-line mask reveal, applied to screen titles: the headline slides up from under an invisible mask
 * (translateY 108% + rotate 4 deg -> rest, 900 ms cubic-bezier(.2,.9,.2,1)) and an accent underline is then drawn
 * (700 ms cubic-bezier(.6,0,.2,1)). The mono kicker above mirrors `.u237-k`.
 */
@Composable
fun LxScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val rise = remember { Animatable(if (reduced) 1f else 0f) }
    val underline = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(60)
            rise.animateTo(1f, tween(LxMotion.RevealMs, easing = LxMotion.RevealEase))
            underline.animateTo(1f, tween(700, easing = CubicBezierEasing(0.6f, 0f, 0.2f, 1f)))
        }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (kicker != null) {
            Text(
                kicker.uppercase(),
                color = c.ink3, fontFamily = LxMono, fontSize = 11.sp, fontWeight = FontWeight(600), letterSpacing = 1.5.sp,
                maxLines = 1,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).clipToBounds()) {
                Text(
                    title,
                    Modifier.graphicsLayer {
                        val v = rise.value
                        translationY = (1f - v) * size.height * 1.08f
                        rotationZ = (1f - v) * 4f
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke(this)
        }
        Box(
            Modifier
                .padding(top = 6.dp)
                .height(4.dp)
                .width(52.dp * underline.value.coerceIn(0f, 1f))
                .clip(LxShapes.Pill)
                .background(c.grad),
        )
        if (subtitle != null) {
            Text(subtitle, Modifier.padding(top = 8.dp), color = c.ink3, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight(600))
        }
    }
}
