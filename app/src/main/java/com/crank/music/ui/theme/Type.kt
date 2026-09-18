package com.crank.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════
// CRANK TYPOGRAPHY — MIDNIGHT GOLD
// Headings: Elegant Serif | Body: Clean Sans-Serif
// ═══════════════════════════════════════════════════════════════

val PlayfairDisplay = androidx.compose.ui.text.font.FontFamily.Serif
val InterFont = androidx.compose.ui.text.font.FontFamily.Default

val CrankTypography = Typography(
    displayLarge = CrankTokens.H1,
    displayMedium = CrankTokens.H2,
    displaySmall = CrankTokens.H3,
    headlineLarge = CrankTokens.H1,
    headlineMedium = CrankTokens.H2,
    headlineSmall = CrankTokens.H3,
    titleLarge = CrankTokens.H3,
    titleMedium = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyLarge = CrankTokens.BodyLarge,
    bodyMedium = CrankTokens.BodyMedium,
    bodySmall = CrankTokens.BodySmall,
    labelLarge = CrankTokens.Button,
    labelMedium = CrankTokens.Caption,
    labelSmall = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.05.em
    )
)
