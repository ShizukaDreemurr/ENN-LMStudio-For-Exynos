// Lucian UX/UI Prompt #269 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#269
package dev.exynoslm.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMono
import dev.exynoslm.app.ui.theme.LxMotion

/**
 * #269 confirmation dialog. The card pops in with translateY(16)/scale(.96) -> rest in 350 ms on cubic-bezier(.3,1.3,.5,1)
 * (opacity in 200 ms), has a tinted icon badge in the title row and two equal buttons. With [requireText] the confirm
 * button stays disabled until the user types that word (letters light up as they match, like the project-name field).
 */
@Composable
fun LxConfirmDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    tone: LxTone = LxTone.Danger,
    icon: ImageVector = if (tone == LxTone.Danger) Icons.Default.Delete else Icons.Default.Warning,
    message: String? = null,
    chip: String? = null,
    requireText: String? = null,
    confirmKind: LxButtonKind = if (tone == LxTone.Danger) LxButtonKind.Danger else LxButtonKind.Primary,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val col = tone.color()
    val p = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (p.value < 1f) p.animateTo(1f, tween(350, easing = LxMotion.Pop)) }
    var typed by rememberSaveable { mutableStateOf("") }
    val ok = requireText == null || typed.trim().equals(requireText, ignoreCase = true)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val v = p.value
                    translationY = (1f - v) * 16.dp.toPx()
                    val s = 0.96f + 0.04f * v
                    scaleX = s
                    scaleY = s
                    alpha = (v * 1.75f).coerceIn(0f, 1f)
                }
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.line2, shape)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(col.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = col, modifier = Modifier.size(16.dp)) }
                Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight(900), modifier = Modifier.weight(1f))
            }
            if (message != null) {
                Text(message, Modifier.padding(top = 10.dp), color = c.ink2, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight(600))
            }
            if (chip != null && requireText == null) {
                Text(
                    chip,
                    Modifier
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.surface2)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    color = c.ink2, fontFamily = LxMono, fontSize = 13.sp, fontWeight = FontWeight(800),
                )
            }
            content?.invoke(this)
            if (requireText != null) {
                val annotated = buildAnnotatedString {
                    requireText.forEachIndexed { i, ch ->
                        val hit = i < typed.length && typed[i].equals(ch, ignoreCase = true)
                        withStyle(SpanStyle(color = if (hit) col else c.ink3)) { append(ch) }
                    }
                }
                Text(
                    annotated,
                    Modifier
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.surface2)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    fontFamily = LxMono, fontSize = 13.sp, fontWeight = FontWeight(800),
                )
                val inShape = RoundedCornerShape(11.dp)
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.ink, fontFamily = LxMono, fontSize = 13.sp, fontWeight = FontWeight(700)),
                    cursorBrush = SolidColor(col),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(inShape)
                        .background(c.surface2)
                        .border(1.5.dp, if (typed.isNotEmpty()) col.copy(alpha = if (ok) 1f else 0.5f) else c.line, inShape)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LxButton(dismissLabel, onDismiss, Modifier.weight(1f), kind = LxButtonKind.Secondary, height = 44.dp)
                LxButton(confirmLabel, onConfirm, Modifier.weight(1f), kind = confirmKind, enabled = ok, height = 44.dp)
            }
        }
    }
}
