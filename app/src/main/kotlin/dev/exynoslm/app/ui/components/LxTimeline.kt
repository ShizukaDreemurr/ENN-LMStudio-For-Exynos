// Lucian UX/UI Prompt #087 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#087
// Lucian UX/UI Prompt #083 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#083
package dev.exynoslm.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMono
import dev.exynoslm.app.ui.theme.LxMotion

/**
 * #087 vertical timeline: 8 dp dot on a 1 dp rail (halo `0 0 0 4px` on the newest / running entry) and entries that
 * drop in (translateY -14 -> 0, 550 ms spring). #083's spinner replaces the dot while a step is in flight.
 */
@Composable
fun LxTimelineItem(
    title: String,
    body: String,
    tone: LxTone,
    modifier: Modifier = Modifier,
    badge: String? = null,
    running: Boolean = false,
    newest: Boolean = false,
    last: Boolean = false,
    mono: Boolean = true,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val col = tone.color()
    val rail = c.line2
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (enter.value < 1f) enter.animateTo(1f, tween(550, easing = LxMotion.Spring)) }
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .graphicsLayer {
                val v = enter.value
                alpha = v.coerceIn(0f, 1f)
                translationY = -14.dp.toPx() * (1f - v)
            },
    ) {
        Box(
            Modifier
                .width(22.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = 8.dp.toPx()
                    if (!last) drawLine(rail, Offset(x, 22.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                    if (!running) {
                        if (newest) drawCircle(col.copy(alpha = 0.22f), 8.dp.toPx(), Offset(x, 10.dp.toPx()))
                        drawCircle(col, 4.dp.toPx(), Offset(x, 10.dp.toPx()))
                    }
                },
        ) {
            if (running) LxSpinner(Modifier.padding(start = 1.dp, top = 3.dp), color = col, size = 14.dp)
        }
        Column(Modifier.weight(1f).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight(780), modifier = Modifier.weight(1f, fill = false))
                if (badge != null) LxBadge(badge, tone = tone)
            }
            if (body.isNotBlank()) {
                Text(
                    body,
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(horizontal = 10.dp, vertical = 8.dp),
                    color = c.ink2, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight(600),
                    fontFamily = if (mono) LxMono else null,
                )
            }
        }
    }
}
