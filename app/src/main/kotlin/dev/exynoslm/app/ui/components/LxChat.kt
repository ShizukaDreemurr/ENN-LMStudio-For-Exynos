// Lucian UX/UI Prompt #086 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#086
package dev.exynoslm.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween
import kotlin.math.PI
import kotlin.math.sin

/** `u086-pop`: opacity 0 / translateY(10) / scale(.95) -> rest in 450 ms with the Lucian spring. */
@Composable
fun Modifier.lxPopIn(enabled: Boolean = true): Modifier {
    val reduced = LocalReducedMotion.current
    val p = remember { Animatable(if (enabled && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (p.value < 1f) p.animateTo(1f, tween(LxMotion.PopInMs, easing = LxMotion.Spring))
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 10.dp.toPx()
        val s = 0.95f + 0.05f * v
        scaleX = s
        scaleY = s
    }
}

/** `u086-dots`: three 7 dp dots, 1.1 s bounce (-5 dp at 30%), staggered by .15 s. */
@Composable
fun LxTypingDots(modifier: Modifier = Modifier) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "typingPhase")
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "…" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        val raw = phase - i * 0.15f / 1.1f
                        val p = ((raw % 1f) + 1f) % 1f
                        val w = if (reduced || p >= 0.6f) 0f else sin(PI.toFloat() * p / 0.6f)
                        translationY = -5.dp.toPx() * w
                        alpha = 0.45f + 0.55f * w
                    }
                    .background(c.ink3, LxShapes.Circle),
            )
        }
    }
}

/** `u086-av`: 30 dp gradient tile with a white diamond; pulses a 5 dp halo (1.6 s) while generating. */
@Composable
fun LxAvatar(glow: Boolean, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "avatar")
    val halo by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(800, easing = LxMotion.InOut), RepeatMode.Reverse),
        label = "halo",
    )
    val soft = c.accentSoft
    Box(
        modifier
            .size(size)
            .drawBehind {
                if (glow && !reduced) {
                    val e = 5.dp.toPx() * halo
                    drawRoundRect(
                        soft,
                        Offset(-e, -e),
                        Size(this.size.width + 2 * e, this.size.height + 2 * e),
                        CornerRadius(10.dp.toPx() + e),
                    )
                }
            }
            .clip(RoundedCornerShape(10.dp))
            .background(c.grad),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .graphicsLayer { rotationZ = 45f }
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White),
        )
    }
}

/** User turn: right-aligned Exynos-gradient bubble (18/18/6/18 radii, #086 `.u086-q` / #091 `.me`). */
@Composable
fun LxUserBubble(text: String, modifier: Modifier = Modifier, animate: Boolean = false) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            Modifier
                .widthIn(max = 320.dp)
                .lxPopIn(animate)
                .clip(LxShapes.BubbleUser)
                .background(Lx.colors.grad)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            color = Color.White,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight(600),
        )
    }
}

/** Assistant turn: avatar + free-flowing body (no bubble), like `.u086-a` / `.u086-body`. */
@Composable
fun LxAssistantMessage(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    animate: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth().lxPopIn(animate), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LxAvatar(glow = glow)
        Column(Modifier.weight(1f).padding(top = 3.dp), content = content)
    }
}

/** Message body text with the `u086-caret` block cursor (1 s, hard blink) while [caret] is true. */
@Composable
fun LxMessageText(text: String, modifier: Modifier = Modifier, caret: Boolean = false) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "caret")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "caretPhase")
    val caretOn by remember { derivedStateOf { reduced || phase < 0.5f } }
    val caretColor = c.accent
    val annotated = buildAnnotatedString {
        append(text)
        if (caret) {
            withStyle(SpanStyle(color = if (caretOn) caretColor else Color.Transparent, fontWeight = FontWeight.Black)) { append("▌") }
        }
    }
    Text(annotated, modifier, color = c.ink, fontSize = 15.sp, lineHeight = 24.sp)
}

/**
 * Composer = `.u086-bar` (22 dp rounded field, 1.5 dp inset line, accent when focused) + `.u086-ctl`:
 * send is a gradient circle with an arrow, which swaps (scale .8 + fade) with the ink "Stop" pill while generating.
 */
@Composable
fun LxComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    generating: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    sendLabel: String,
    stopLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    var focused by remember { mutableStateOf(false) }
    val edge by animateColorAsState(if (focused) c.accent else c.line2, lxTween(reduced, 200, LxMotion.Ease), label = "composerEdge")
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.5.dp, edge, shape)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(color = c.ink, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(c.accent),
            maxLines = 5,
            keyboardOptions = KeyboardOptions.Default,
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = c.ink3, fontSize = 15.sp, maxLines = 1)
                    inner()
                }
            },
        )
        AnimatedContent(
            targetState = generating,
            transitionSpec = {
                if (reduced) EnterTransition.None togetherWith ExitTransition.None
                else (fadeIn(tween(250)) + scaleIn(tween(300, easing = LxMotion.Spring), initialScale = 0.8f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 0.8f))
            },
            label = "composerCtl",
        ) { gen ->
            if (gen) StopPill(stopLabel, onStop) else SendCircle(sendLabel, canSend, onSend)
        }
    }
}

@Composable
private fun SendCircle(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Lx.colors
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source, 0.9f)
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.35f
            }
            .clip(LxShapes.Circle)
            .background(c.grad)
            .lxClickable(source, LxShapes.Circle, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StopPill(label: String, onClick: () -> Unit) {
    val c = Lx.colors
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source, 0.94f)
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .height(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(c.ink)
            .lxClickable(source, shape, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c.page))
        Text(label, color = c.page, fontSize = 13.sp, fontWeight = FontWeight(750))
    }
}
