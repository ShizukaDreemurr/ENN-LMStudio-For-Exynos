// Lucian UX/UI Prompt #008 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#008
// Lucian UX/UI Prompt #086 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#086
package dev.exynoslm.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween

enum class LxButtonKind { Primary, Secondary, Danger, Ghost }

/**
 * Text button. Primary = Exynos blue gradient (#086 `.u086-ctl`), Secondary = surface-2 tile, Danger = danger fill,
 * Ghost = text only. All of them squash to .96 on press with the Lucian spring (300 ms).
 */
@Composable
fun LxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: LxButtonKind = LxButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    val c = Lx.colors
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source, 0.96f)
    val shape = LxShapes.Button
    val fg = when (kind) {
        LxButtonKind.Primary -> c.onGrad
        LxButtonKind.Secondary -> c.ink
        LxButtonKind.Danger -> if (c.isDark) c.accentInk else Color.White
        LxButtonKind.Ghost -> c.accent
    }
    val bgModifier = when (kind) {
        LxButtonKind.Primary -> Modifier.background(c.grad)
        LxButtonKind.Secondary -> Modifier.background(c.surface2).border(1.dp, c.line2, shape)
        LxButtonKind.Danger -> Modifier.background(c.danger)
        LxButtonKind.Ghost -> Modifier
    }
    Box(
        modifier
            .defaultMinSize(minHeight = height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(shape)
            .then(bgModifier)
            .lxClickable(source, shape, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight(750), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/**
 * #008 icon button: 46 dp tile, 13 dp radius, surface-2 background, `scale(.88)` on press with the spring curve.
 */
@Composable
fun LxIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Lx.colors.ink2,
    container: Color = Lx.colors.surface2,
    size: Dp = 46.dp,
) {
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source, 0.88f)
    val shape = LxShapes.IconButton
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(shape)
            .background(container)
            .lxClickable(source, shape, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/**
 * #008 "copy button that turns into a check": the copy glyph shrinks/rotates away (scale .4, rotate 30 deg), the check is
 * drawn stroke-by-stroke and the whole button floods mint. [copied] is owned by the caller (screen state).
 */
@Composable
fun LxCopyButton(
    label: String,
    copiedLabel: String,
    copied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val source = remember { MutableInteractionSource() }
    val scale by rememberPressScale(source, 0.96f)
    val shape = LxShapes.Button
    val mintAlpha by animateFloatAsState(if (copied) 1f else 0f, lxTween<Float>(reduced, LxMotion.BaseMs, LxMotion.Ease), label = "copyBg")
    val copyP by animateFloatAsState(if (copied) 0f else 1f, lxTween<Float>(reduced, LxMotion.SlowMs, LxMotion.Spring), label = "copyIcon")
    val checkP by animateFloatAsState(
        if (copied) 1f else 0f,
        lxTween<Float>(reduced, 400, LxMotion.Ease, delayMs = if (copied) 100 else 0),
        label = "copyCheck",
    )
    val fgCopied = if (c.isDark) c.accentInk else Color.White
    val fg by animateColorAsState(if (copied) fgCopied else c.onGrad, lxTween(reduced, LxMotion.BaseMs, LxMotion.Ease), label = "copyFg")
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(shape)
            .background(c.grad)
            .background(c.mint.copy(alpha = mintAlpha))
            .lxClickable(source, shape, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(21.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.ContentCopy, null, tint = fg,
                    modifier = Modifier
                        .size(19.dp)
                        .graphicsLayer {
                            val s = 0.4f + 0.6f * copyP
                            scaleX = s
                            scaleY = s
                            rotationZ = 30f * (1f - copyP)
                            alpha = copyP.coerceIn(0f, 1f)
                        },
                )
                LxCheck(progress = checkP, color = fg, modifier = Modifier.size(21.dp), strokeUnits = 2.6f)
            }
            Text(if (copied) copiedLabel else label, color = fg, fontSize = 14.sp, fontWeight = FontWeight(750), maxLines = 1)
        }
    }
}
