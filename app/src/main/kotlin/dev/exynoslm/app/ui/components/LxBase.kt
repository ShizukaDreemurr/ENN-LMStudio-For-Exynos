// Lucian UX/UI Prompt #098 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#098
// Lucian UX/UI Prompt #039 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#039
// Lucian UX/UI Prompt #083 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#083
// Lucian UX/UI Prompt #008 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#008
package dev.exynoslm.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween

/** Semantic color roles used by badges, notices, toasts, pills and dialogs. */
enum class LxTone { Accent, Success, Warn, Danger, Neutral }

@Composable
fun LxTone.color(): Color {
    val c = Lx.colors
    return when (this) {
        LxTone.Accent -> c.accent
        LxTone.Success -> c.mint
        LxTone.Warn -> c.warn
        LxTone.Danger -> c.danger
        LxTone.Neutral -> c.ink3
    }
}

/** `:active{transform:scale(.94)}` with the Lucian spring curve (300 ms). */
@Composable
fun rememberPressScale(source: InteractionSource, pressedScale: Float = 0.94f): State<Float> {
    val isPressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    return animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = lxTween<Float>(reduced, LxMotion.PressMs, LxMotion.Spring),
        label = "press",
    )
}

/** Keyboard / d-pad focus ring (Lucian: `:focus-visible{outline:2px solid var(--ux-accent)}`). */
fun Modifier.lxFocusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val accent = Lx.colors.accent
    this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, accent, shape) else Modifier)
}

/** Ripple-less clickable (Lucian feedback is the press-scale) that still shows a focus ring. */
fun Modifier.lxClickable(
    source: MutableInteractionSource,
    shape: Shape,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = this
    .lxFocusRing(shape)
    .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)

/**
 * Check mark drawn stroke-by-stroke (#008 / #013 `stroke-dashoffset` reveal). [progress] 0..1.
 * Path in a 24-unit box: M5.5 12.5 l4.2 4.2 L18.5 8.
 */
@Composable
fun LxCheck(progress: Float, color: Color, modifier: Modifier = Modifier, strokeUnits: Float = 2.6f) {
    Canvas(modifier) {
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return@Canvas
        val s = size.minDimension / 24f
        val path = Path().apply {
            moveTo(5.5f * s, 12.5f * s)
            lineTo(9.7f * s, 16.7f * s)
            lineTo(18.5f * s, 8f * s)
        }
        val pm = PathMeasure()
        pm.setPath(path, false)
        val seg = Path()
        pm.getSegment(0f, pm.length * p, seg, true)
        drawPath(seg, color, style = Stroke(width = strokeUnits * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** #083 status pill: tinted capsule + blinking dot (`u083-blink` 1.2 s, opacity 1 -> .3 at 50%). */
@Composable
fun LxStatusPill(text: String, color: Color, modifier: Modifier = Modifier, blink: Boolean = true) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "pill")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LxMotion.InOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pillBlink",
    )
    Row(
        modifier
            .height(26.dp)
            .clip(LxShapes.Pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .graphicsLayer { alpha = if (blink && !reduced) pulse else 1f }
                .background(color, LxShapes.Circle),
        )
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight(800), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** #039 `.u039-badge`: 10.5 sp / 800 weight tag, 6 dp radius. */
@Composable
fun LxBadge(text: String, modifier: Modifier = Modifier, tone: LxTone = LxTone.Accent) {
    val c = tone.color()
    Text(
        text,
        modifier
            .clip(LxShapes.Tag)
            .background(if (tone == LxTone.Neutral) Lx.colors.surface3 else c.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = if (tone == LxTone.Neutral) Lx.colors.ink2 else c,
        fontSize = 10.5.sp,
        fontWeight = FontWeight(800),
        maxLines = 1,
    )
}

/** Surface card (`box-shadow:0 0 0 1px var(--ux-line)`), optionally pressable with the spring press-scale. */
@Composable
fun LxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = LxShapes.Card,
    color: Color = Lx.colors.surface,
    borderColor: Color = Lx.colors.line,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source, 0.98f)
    val base = modifier
        .graphicsLayer {
            if (onClick != null) {
                scaleX = scale
                scaleY = scale
            }
        }
        .clip(shape)
        .background(color)
        .border(BorderStroke(1.dp, borderColor), shape)
    val clickable = if (onClick != null) base.lxClickable(source, shape, role = Role.Button, onClick = onClick) else base
    Column(clickable.padding(contentPadding), content = content)
}

/** #098 `.u098-tb` stat tile: label above, bold tabular value below, optional sub-line and rolling digits. */
@Composable
fun LxStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    sub: String? = null,
    rolling: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = Lx.colors
    Column(
        modifier
            .clip(LxShapes.Tile)
            .background(c.surface2)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(label, color = c.ink3, fontSize = 11.5.sp, fontWeight = FontWeight(750), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight(850))
        val vc = if (valueColor == Color.Unspecified) c.ink else valueColor
        if (rolling) LxRollingNumber(value, style = style, color = vc)
        else Text(value, style = style, color = vc, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, color = c.ink3, fontSize = 11.sp, fontWeight = FontWeight(800), maxLines = 1)
        content?.invoke(this)
    }
}

/** Small section label above a group (Settings, Bench). */
@Composable
fun LxSectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Lx.colors.ink2, fontSize = 12.5.sp, fontWeight = FontWeight(800), modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Horizontal gap helper used in Rows. */
@Composable
fun LxGap(w: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.width(w))
