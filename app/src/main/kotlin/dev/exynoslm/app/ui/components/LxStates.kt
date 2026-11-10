// Lucian UX/UI Prompt #088 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#088
// Lucian UX/UI Prompt #090 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#090
// Lucian UX/UI Prompt #091 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#091
package dev.exynoslm.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMono
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween
import kotlinx.coroutines.delay

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

// ───────────────────────── #088 empty state ─────────────────────────

private val FolderBack = svgPath("M16 26a10 10 0 0 1 10-10h34a8 8 0 0 1 6 2.6l8 8.4h68a10 10 0 0 1 10 10v66a10 10 0 0 1-10 10H26a10 10 0 0 1-10-10z")
private val FolderFront = svgPath("M10 50a10 10 0 0 1 10-10h128a10 10 0 0 1 10 10.8l-5 52a10 10 0 0 1-10 9.2H25a10 10 0 0 1-10-9.2z")
private val PaperLinesA = svgPath("M42 40h48M42 50h62M42 60h36")
private val PaperLinesB = svgPath("M62 46h52M62 56h64M62 66h30")

/**
 * #088 floating folder. Float loop (5 s ease-in-out, -7), soft shadow squeezing to .8, papers peeking out and the
 * front flap tilting when [open] (spring, .55 s). Recolored to Exynos blues.
 */
@Composable
fun LxFolderArt(open: Boolean, modifier: Modifier = Modifier) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "folder")
    val float by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = LxMotion.InOut), RepeatMode.Reverse), label = "float")
    val peek by animateFloatAsState(if (open) 1f else 0f, lxTween<Float>(reduced, 550, LxMotion.Spring), label = "peek")
    val backColor = if (c.isDark) Color(0xFF123A9E) else Color(0xFF1A4FBF)
    val shadowColor = c.ink.copy(alpha = 0.16f)
    Canvas(modifier.size(168.dp, 138.dp)) {
        val k = this.size.width / 168f
        val f = if (reduced) 0f else float
        scale(k, k, pivot = Offset.Zero) {
            // soft ground shadow
            scale(1f - 0.2f * f, 1f, pivot = Offset(84f, 133f)) {
                drawOval(shadowColor.copy(alpha = shadowColor.alpha * (1f - 0.4f * f)), Offset(34f, 128f), Size(100f, 10f))
            }
            translate(top = -7f * f) {
                drawPath(FolderBack, backColor)
                rotate(-5f * peek, pivot = Offset(76f, 98f)) {
                    translate(top = -10f * peek) {
                        drawRoundRect(Color(0xFFE3EAFB), Offset(30f, 26f), Size(92f, 72f), CornerRadius(6f, 6f))
                        drawPath(PaperLinesA, Color(0xFFC4D0EA), style = Stroke(3f, cap = StrokeCap.Round))
                    }
                }
                rotate(4f * peek, pivot = Offset(95f, 100f)) {
                    translate(top = -6f * peek) {
                        drawRoundRect(Color.White, Offset(50f, 32f), Size(90f, 68f), CornerRadius(6f, 6f))
                        drawPath(PaperLinesB, Color(0xFFD3DAE6), style = Stroke(3f, cap = StrokeCap.Round))
                    }
                }
                scale(1f, 1f - 0.14f * peek, pivot = Offset(84f, 112f)) {
                    drawPath(FolderFront, c.grad)
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(22f, 47f), Offset(146f, 47f), 2f, StrokeCap.Round)
                    drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(66f, 72f), Size(36f, 8f), CornerRadius(4f, 4f))
                }
            }
        }
    }
}

/** Empty state: folder art (tap to peek), title, hint and an optional action. */
@Composable
fun LxEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Lx.colors
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(600)
        open = true
    }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LxFolderArt(open = open, modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
        ) { open = !open })
        Text(title, Modifier.padding(top = 12.dp), color = c.ink, fontSize = 18.5.sp, fontWeight = FontWeight(850), textAlign = TextAlign.Center)
        Text(message, Modifier.padding(top = 6.dp).widthIn(max = 300.dp), color = c.ink3, fontSize = 13.5.sp, lineHeight = 20.sp, fontWeight = FontWeight(600), textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            LxButton(actionLabel, onAction, Modifier.padding(top = 18.dp), kind = LxButtonKind.Secondary, height = 44.dp)
        }
    }
}

// ───────────────────────── #090 error state ─────────────────────────

enum class LxPlugState { Disconnected, Connecting, Connected }

private val CordLeft = svgPath("M-14 54C24 54 40 35 72 35")
private val CordRight = svgPath("M162 35C188 35 194 16 236 16")
private val Prongs = svgPath("M100 28h14M100 42h14")
private val Holes = svgPath("M124 28h6M124 42h6")
private val Sparks = svgPath("M121 20l-2-7M129 24l5-5M113 24l-5-5M121 50l-2 7M129 46l5 5M113 46l-5 5")

/** #090 unplugged-cable art: the left plug slides (spring .55 s) toward the socket, jiggles while retrying. */
@Composable
fun LxPlugArt(state: LxPlugState, modifier: Modifier = Modifier) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val targetL = when (state) { LxPlugState.Disconnected -> -10f; LxPlugState.Connecting -> 9f; LxPlugState.Connected -> 16f }
    val targetR = when (state) { LxPlugState.Disconnected -> 8f; LxPlugState.Connecting -> -2f; LxPlugState.Connected -> -4f }
    val l by animateFloatAsState(targetL, lxTween<Float>(reduced, 550, LxMotion.Spring), label = "plugL")
    val r by animateFloatAsState(targetR, lxTween<Float>(reduced, 550, LxMotion.Spring), label = "plugR")
    val t = rememberInfiniteTransition(label = "plug")
    val jig by t.animateFloat(0f, 1f, infiniteRepeatable(tween(280, delayMillis = 0, easing = LinearEasing), RepeatMode.Reverse), label = "jig")
    val blink by t.animateFloat(0f, 1f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Restart), label = "blink")
    val zap by t.animateFloat(0f, 1f, infiniteRepeatable(tween(550, easing = LxMotion.Ease), RepeatMode.Restart), label = "zap")
    val body = c.ink
    val cord = c.ink3
    val prong = c.ink2
    val hole = c.surface
    Canvas(modifier.size(220.dp, 70.dp)) {
        val k = this.size.width / 220f
        val connecting = state == LxPlugState.Connecting && !reduced
        val ledColor = when (state) {
            LxPlugState.Disconnected -> c.danger
            LxPlugState.Connecting -> if (connecting && blink > 0.5f) c.surface3 else c.warn
            LxPlugState.Connected -> c.mint
        }
        scale(k, k, pivot = Offset.Zero) {
            translate(left = l - if (connecting) 3f * jig else 0f) {
                drawPath(CordLeft, cord, style = Stroke(4f, cap = StrokeCap.Round))
                drawPath(Prongs, prong, style = Stroke(4.5f, cap = StrokeCap.Round))
                drawRoundRect(body, Offset(68f, 19f), Size(34f, 32f), CornerRadius(8f, 8f))
            }
            if (connecting) {
                scale(0.4f + 0.85f * zap, pivot = Offset(121f, 35f)) {
                    drawPath(Sparks, c.warn.copy(alpha = 1f - zap), style = Stroke(2.6f, cap = StrokeCap.Round))
                }
            }
            translate(left = r) {
                drawRoundRect(body, Offset(124f, 17f), Size(38f, 36f), CornerRadius(9f, 9f))
                drawPath(Holes, hole, style = Stroke(3f, cap = StrokeCap.Round))
                drawCircle(ledColor, 3f, Offset(153f, 25f))
                drawPath(CordRight, cord, style = Stroke(4f, cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * #090 error widget: plug art + title + reason, optional collapsible technical details (grid-rows 0fr -> 1fr, 350 ms)
 * and a retry button. Use for "load failed" screens; [state] = Connecting while a retry is in flight.
 */
@Composable
fun LxErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    state: LxPlugState = LxPlugState.Disconnected,
    details: String? = null,
    detailsLabel: String = "",
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, lxTween<Float>(reduced, 300, LxMotion.Ease), label = "chev")
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LxPlugArt(state)
        Text(title, Modifier.padding(top = 16.dp), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight(850), textAlign = TextAlign.Center)
        Text(message, Modifier.padding(top = 5.dp).widthIn(max = 320.dp), color = c.ink3, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight(600), textAlign = TextAlign.Center)
        if (details != null) {
            Row(
                Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(detailsLabel, color = c.ink3, fontSize = 12.sp, fontWeight = FontWeight(750))
                Icon(Icons.Default.ExpandMore, null, tint = c.ink3, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = chevron })
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(lxTween<IntSize>(reduced, 350, LxMotion.Ease)) + fadeIn(),
                exit = shrinkVertically(lxTween<IntSize>(reduced, 350, LxMotion.Ease)) + fadeOut(),
            ) {
                Text(
                    details,
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    color = c.ink2, fontSize = 11.5.sp, lineHeight = 19.sp, fontFamily = LxMono, fontWeight = FontWeight(600),
                )
            }
        }
        if (retryLabel != null && onRetry != null) {
            LxButton(retryLabel, onRetry, Modifier.padding(top = 16.dp), icon = Icons.Default.Refresh, enabled = state != LxPlugState.Connecting)
        }
    }
}

// ───────────────────────── #091 offline banner / notice ─────────────────────────

private val WifiOuter = svgPath("M2.8 9.2a13.2 13.2 0 0 1 18.4 0")
private val WifiMid = svgPath("M5.7 12.4a9 9 0 0 1 12.6 0")
private val WifiInner = svgPath("M8.6 15.6a4.8 4.8 0 0 1 6.8 0")
private val WifiSlash = svgPath("M4.5 3.5l15 17")

private fun scanAlpha(phase: Float, delaySec: Float): Float {
    val q = (((phase - delaySec / 1.8f) % 1f) + 1f) % 1f
    return when {
        q <= 0.3f -> 0.25f + 0.75f * (q / 0.3f)
        q <= 0.7f -> 1f - 0.75f * ((q - 0.3f) / 0.4f)
        else -> 0.25f
    }
}

/** #091 Wi-Fi glyph: arcs blink in turn (1.8 s) while [scanning]; the slash draws over it when [offline]. */
@Composable
fun LxWifiIcon(scanning: Boolean, offline: Boolean, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "wifi")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "wifiPhase")
    val slash by animateFloatAsState(if (offline) 1f else 0f, lxTween<Float>(reduced, 450, LxMotion.Ease), label = "slash")
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        val animate = scanning && !reduced
        scale(s, s, pivot = Offset.Zero) {
            val aOuter = if (animate) scanAlpha(phase, 0.4f) else 1f
            val aMid = if (animate) scanAlpha(phase, 0.2f) else 1f
            val aInner = if (animate) scanAlpha(phase, 0f) else 1f
            drawPath(WifiOuter, tint.copy(alpha = aOuter), style = Stroke(2.2f, cap = StrokeCap.Round))
            drawPath(WifiMid, tint.copy(alpha = aMid), style = Stroke(2.2f, cap = StrokeCap.Round))
            drawPath(WifiInner, tint.copy(alpha = aInner), style = Stroke(2.2f, cap = StrokeCap.Round))
            drawCircle(tint, 1.3f, Offset(12f, 19f))
            if (slash > 0f) {
                val pm = PathMeasure()
                pm.setPath(WifiSlash, false)
                val seg = Path()
                pm.getSegment(0f, pm.length * slash, seg, true)
                drawPath(seg, tint, style = Stroke(2.2f, cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * #091 banner (`.u091-bc`): tinted rounded card with a 34 dp icon tile, bold title and a softer sub-line. It opens
 * with the grid-rows 0fr -> 1fr expand (450 ms ease). Used for fallback reasons, load failures and warnings.
 */
@Composable
fun LxNotice(
    text: String,
    modifier: Modifier = Modifier,
    tone: LxTone = LxTone.Accent,
    title: String? = null,
    icon: (@Composable () -> Unit)? = null,
    visible: Boolean = true,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val col = tone.color()
    val appear = remember { MutableTransitionState(false) }
    appear.targetState = visible
    val shape = RoundedCornerShape(17.dp)
    AnimatedVisibility(
        visibleState = appear,
        modifier = modifier,
        enter = expandVertically(lxTween<IntSize>(reduced, 450, LxMotion.Ease)) + fadeIn(),
        exit = shrinkVertically(lxTween<IntSize>(reduced, 300, LxMotion.Ease)) + fadeOut(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(col.copy(alpha = 0.09f))
                .border(1.dp, col.copy(alpha = 0.28f), shape)
                .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(col.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) icon() else {
                    val vec: ImageVector = when (tone) {
                        LxTone.Danger -> Icons.Default.Error
                        LxTone.Warn -> Icons.Default.Warning
                        LxTone.Success -> Icons.Default.CheckCircle
                        else -> Icons.Default.Info
                    }
                    Icon(vec, null, tint = col, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight(800))
                Text(text, color = if (title != null) c.ink2 else c.ink, fontSize = if (title != null) 12.sp else 13.sp, lineHeight = 17.sp, fontWeight = FontWeight(650))
            }
        }
    }
}

/** Convenience: offline banner with the scanning Wi-Fi glyph. */
@Composable
fun LxOfflineNotice(title: String, text: String, modifier: Modifier = Modifier, scanning: Boolean = true) {
    val col = Lx.colors.warn
    LxNotice(
        text = text, title = title, tone = LxTone.Warn, modifier = modifier,
        icon = { LxWifiIcon(scanning = scanning, offline = true, tint = col) },
    )
}
