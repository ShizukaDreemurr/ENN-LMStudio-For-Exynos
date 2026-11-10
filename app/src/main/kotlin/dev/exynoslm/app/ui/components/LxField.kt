// Lucian UX/UI Prompt #025 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#025
package dev.exynoslm.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.LxShapes
import dev.exynoslm.app.ui.theme.lxTween

/**
 * #025 notched floating-label field: 16 dp outline that parts around the rising label, accent border (2 dp) on focus,
 * danger on error with the sliding-in helper line, and the mint check badge that pops in (spring 450 ms) when [ok].
 * The label travel itself is Material's (150 ms), the border / badge / colors follow Lucian.
 */
@Composable
fun LxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    errorText: String? = null,
    ok: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    val badge by animateFloatAsState(if (ok) 1f else 0f, lxTween<Float>(reduced, 450, LxMotion.Spring), label = "okBadge")
    val tick by animateFloatAsState(if (ok) 1f else 0f, lxTween<Float>(reduced, 350, LxMotion.Ease, delayMs = if (ok) 150 else 0), label = "okTick")
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        shape = LxShapes.Field,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight(560)),
        trailingIcon = if (ok || badge > 0.01f) {
            {
                Box(
                    Modifier
                        .size(26.dp)
                        .graphicsLayer {
                            scaleX = badge
                            scaleY = badge
                        }
                        .clip(LxShapes.Circle)
                        .background(c.mint),
                    contentAlignment = Alignment.Center,
                ) { LxCheck(progress = tick, color = c.accentInk, modifier = Modifier.size(14.dp), strokeUnits = 3f) }
            }
        } else null,
        supportingText = if (isError && errorText != null) {
            { Text(errorText, color = c.danger, fontWeight = FontWeight(600)) }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.ink,
            unfocusedTextColor = c.ink,
            errorTextColor = c.ink,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            errorContainerColor = c.surface,
            cursorColor = c.accent,
            errorCursorColor = c.danger,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.line2,
            errorBorderColor = c.danger,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.ink3,
            errorLabelColor = c.danger,
            focusedPlaceholderColor = c.ink3,
            unfocusedPlaceholderColor = c.ink3,
        ),
    )
}

/** Search input (Hub): pill-shaped #025-styled field with magnifier, spinner while loading and a clear button. */
@Composable
fun LxSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val c = Lx.colors
    val reduced = LocalReducedMotion.current
    var focused by remember { mutableStateOf(false) }
    val edge by animateColorAsState(if (focused) c.accent else c.line2, lxTween(reduced, 250, LxMotion.Ease), label = "searchEdge")
    val shape = LxShapes.Pill
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(c.surface)
            .border(if (focused) 2.dp else 1.5.dp, edge, shape)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Search, null, tint = if (focused) c.accent else c.ink3, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(color = c.ink, fontSize = 16.sp, fontWeight = FontWeight(560)),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = c.ink3, fontSize = 16.sp, maxLines = 1)
                    inner()
                }
            },
        )
        if (loading) {
            LxSpinner(size = 18.dp)
            Box(Modifier.size(4.dp))
        } else if (value.isNotEmpty()) {
            LxIconButton(
                Icons.Default.Close, "×", { onValueChange("") },
                tint = c.ink2, container = Color.Transparent, size = 40.dp,
            )
        }
    }
}
