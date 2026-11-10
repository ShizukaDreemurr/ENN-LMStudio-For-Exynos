package dev.exynoslm.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** System sans (One UI Sans on Samsung) with Lucian's heavier weights and tight tracking (-0.01em .. -0.05em). */
val LxMono: FontFamily = FontFamily.Monospace

val LxTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight(850), letterSpacing = (-0.05).em),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight(850), letterSpacing = (-0.045).em),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight(850), letterSpacing = (-0.04).em),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight(820), letterSpacing = (-0.03).em),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight(780), letterSpacing = (-0.02).em),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(750), letterSpacing = (-0.015).em),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(750), letterSpacing = (-0.01).em),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(720), letterSpacing = 0.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight(720), letterSpacing = 0.sp),
)
