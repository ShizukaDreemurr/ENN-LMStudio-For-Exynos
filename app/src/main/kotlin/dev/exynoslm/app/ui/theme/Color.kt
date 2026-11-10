package dev.exynoslm.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Exynos / Samsung / Xclipse blue identity.
 *
 * Samsung deep blue (#1428A0) anchors the gradient start, #1259C3 is the light-theme primary and the electric
 * Xclipse blues (#4D8DFF / #22C1FF) carry the dark theme. Dynamic color is intentionally NOT used (brand identity).
 */
object ExynosPalette {
    val SamsungBlue = Color(0xFF1428A0)
    val ExynosBlue = Color(0xFF1259C3)
    val XclipseBlue = Color(0xFF2F80FF)
    val XclipseElectric = Color(0xFF4D8DFF)
    val XclipseCyan = Color(0xFF22C1FF)
}

/**
 * Lucian `--ux-*` tokens mapped to Compose. Material3 roles are covered by the ColorScheme in Theme.kt; these are the
 * extra roles Lucian elements need (ink-2/ink-3, hairlines, mint/warn/danger, soft accent, gradient).
 */
@Immutable
class LxColors(
    val isDark: Boolean,
    val page: Color,         // --ux-page
    val surface: Color,      // --ux-surface
    val surface2: Color,     // --ux-surface-2
    val surface3: Color,     // --ux-surface-3
    val ink: Color,          // --ux-ink
    val ink2: Color,         // --ux-ink-2
    val ink3: Color,         // --ux-ink-3
    val line: Color,         // --ux-line
    val line2: Color,        // --ux-line-2
    val accent: Color,       // --ux-accent
    val accent2: Color,      // --ux-accent-2
    val accentSoft: Color,   // --ux-accent-soft
    val accentInk: Color,    // --ux-accent-ink
    val mint: Color,         // --ux-mint (success)
    val warn: Color,         // --ux-warn
    val danger: Color,       // --ux-danger
    val shadowStrong: Color, // --ux-shadow-strong
    val gradStart: Color,    // --ux-grad stops
    val gradMid: Color,
    val gradEnd: Color,
) {
    /** --ux-grad (Samsung blue -> Exynos blue -> Xclipse blue). White content sits on top of it in both themes. */
    val grad: Brush get() = Brush.linearGradient(listOf(gradStart, gradMid, gradEnd))
    val onGrad: Color get() = Color.White
}

val DarkLx = LxColors(
    isDark = true,
    page = Color(0xFF050914),
    surface = Color(0xFF0B1226),
    surface2 = Color(0xFF111A33),
    surface3 = Color(0xFF1A2548),
    ink = Color(0xFFEAF1FF),
    ink2 = Color(0xFFEAF1FF).copy(alpha = 0.68f),
    ink3 = Color(0xFFEAF1FF).copy(alpha = 0.44f),
    line = Color.White.copy(alpha = 0.08f),
    line2 = Color.White.copy(alpha = 0.16f),
    accent = Color(0xFF4D8DFF),
    accent2 = Color(0xFF22C1FF),
    accentSoft = Color(0xFF4D8DFF).copy(alpha = 0.16f),
    accentInk = Color(0xFF03102B),
    mint = Color(0xFF5CE6B8),
    warn = Color(0xFFFFB26B),
    danger = Color(0xFFFF7D8C),
    shadowStrong = Color.Black.copy(alpha = 0.72f),
    gradStart = ExynosPalette.SamsungBlue,
    gradMid = ExynosPalette.ExynosBlue,
    gradEnd = ExynosPalette.XclipseBlue,
)

val LightLx = LxColors(
    isDark = false,
    page = Color(0xFFF2F5FC),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEEF2FB),
    surface3 = Color(0xFFDFE7F6),
    ink = Color(0xFF0B1230),
    ink2 = Color(0xFF4A5578),
    ink3 = Color(0xFF6B7799),
    line = Color(0xFF0B1230).copy(alpha = 0.09f),
    line2 = Color(0xFF0B1230).copy(alpha = 0.17f),
    accent = ExynosPalette.ExynosBlue,
    accent2 = Color(0xFF0088CC),
    accentSoft = ExynosPalette.ExynosBlue.copy(alpha = 0.12f),
    accentInk = Color.White,
    mint = Color(0xFF0F8F68),
    warn = Color(0xFFC2620F),
    danger = Color(0xFFD93A55),
    shadowStrong = Color(0xFF0B1230).copy(alpha = 0.30f),
    gradStart = ExynosPalette.SamsungBlue,
    gradMid = ExynosPalette.ExynosBlue,
    gradEnd = ExynosPalette.XclipseBlue,
)

val LocalLxColors = staticCompositionLocalOf { DarkLx }
