package dev.exynoslm.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF4D8DFF),
    onPrimary = Color(0xFF03102B),
    primaryContainer = Color(0xFF0F2A78),
    onPrimaryContainer = Color(0xFFD6E4FF),
    inversePrimary = Color(0xFF1259C3),
    secondary = Color(0xFF22C1FF),
    onSecondary = Color(0xFF00212D),
    secondaryContainer = Color(0xFF0A3A52),
    onSecondaryContainer = Color(0xFFC6EEFF),
    tertiary = Color(0xFF8AA4FF),
    onTertiary = Color(0xFF0B1A5C),
    tertiaryContainer = Color(0xFF1F2F7A),
    onTertiaryContainer = Color(0xFFDDE3FF),
    background = DarkLx.page,
    onBackground = DarkLx.ink,
    surface = DarkLx.surface,
    onSurface = DarkLx.ink,
    surfaceVariant = DarkLx.surface2,
    onSurfaceVariant = Color(0xFFA9B5D4),
    surfaceTint = Color(0xFF4D8DFF),
    inverseSurface = Color(0xFFEAF1FF),
    inverseOnSurface = Color(0xFF0B1226),
    error = DarkLx.danger,
    onError = Color(0xFF3B0610),
    errorContainer = Color(0xFF5A1422),
    onErrorContainer = Color(0xFFFFDADF),
    outline = Color(0xFF5A6890),
    outlineVariant = Color(0xFF1F2B4D),
    scrim = Color.Black,
    surfaceBright = Color(0xFF1A2548),
    surfaceDim = DarkLx.page,
    surfaceContainerLowest = Color(0xFF040712),
    surfaceContainerLow = Color(0xFF080F20),
    surfaceContainer = DarkLx.surface,
    surfaceContainerHigh = DarkLx.surface2,
    surfaceContainerHighest = DarkLx.surface3,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = ExynosPalette.ExynosBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF001B4D),
    inversePrimary = Color(0xFF4D8DFF),
    secondary = Color(0xFF006E9E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7EBFF),
    onSecondaryContainer = Color(0xFF001E2C),
    tertiary = ExynosPalette.SamsungBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE1FF),
    onTertiaryContainer = Color(0xFF000F5C),
    background = LightLx.page,
    onBackground = LightLx.ink,
    surface = LightLx.surface,
    onSurface = LightLx.ink,
    surfaceVariant = Color(0xFFE8EEFA),
    onSurfaceVariant = LightLx.ink2,
    surfaceTint = ExynosPalette.ExynosBlue,
    inverseSurface = Color(0xFF0B1226),
    inverseOnSurface = Color(0xFFEAF1FF),
    error = LightLx.danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF410010),
    outline = Color(0xFF7A86A8),
    outlineVariant = Color(0xFFCBD5EA),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDDE4F2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FE),
    surfaceContainer = Color(0xFFEEF2FB),
    surfaceContainerHigh = Color(0xFFE8EEFA),
    surfaceContainerHighest = LightLx.surface3,
)

/** Entry point to the Lucian-token accessors: `Lx.colors.accent`. */
object Lx {
    val colors: LxColors
        @Composable get() = LocalLxColors.current
}

/**
 * Exynos blue identity theme. [mode] is the persisted setting: "system" | "light" | "dark".
 * Dynamic color is deliberately disabled so the brand palette is identical on every device.
 */
@Composable
fun AppTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(
        LocalLxColors provides (if (dark) DarkLx else LightLx),
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = LxTypography,
            shapes = LxMaterialShapes,
            content = content,
        )
    }
}
