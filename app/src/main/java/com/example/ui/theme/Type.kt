package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun getScaledTypography(scaleFactor: Float = 1.0f): Typography {
    return Typography(
        displayLarge = TextStyle(fontSize = (57 * scaleFactor).sp, lineHeight = (64 * scaleFactor).sp),
        displayMedium = TextStyle(fontSize = (45 * scaleFactor).sp, lineHeight = (52 * scaleFactor).sp),
        displaySmall = TextStyle(fontSize = (36 * scaleFactor).sp, lineHeight = (44 * scaleFactor).sp),
        headlineLarge = TextStyle(fontSize = (32 * scaleFactor).sp, lineHeight = (40 * scaleFactor).sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontSize = (28 * scaleFactor).sp, lineHeight = (36 * scaleFactor).sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = (24 * scaleFactor).sp, lineHeight = (32 * scaleFactor).sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = (22 * scaleFactor).sp, lineHeight = (28 * scaleFactor).sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = (16 * scaleFactor).sp, lineHeight = (24 * scaleFactor).sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontSize = (14 * scaleFactor).sp, lineHeight = (20 * scaleFactor).sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (16 * scaleFactor).sp,
            lineHeight = (24 * scaleFactor).sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * scaleFactor).sp,
            lineHeight = (20 * scaleFactor).sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (12 * scaleFactor).sp,
            lineHeight = (16 * scaleFactor).sp,
            letterSpacing = 0.4.sp,
        ),
        labelLarge = TextStyle(fontSize = (14 * scaleFactor).sp, lineHeight = (20 * scaleFactor).sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = (12 * scaleFactor).sp, lineHeight = (16 * scaleFactor).sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = (11 * scaleFactor).sp, lineHeight = (16 * scaleFactor).sp, fontWeight = FontWeight.Medium)
    )
}
