package com.crank.music.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════
// CRANK DESIGN SYSTEM TOKENS — MIDNIGHT GOLD
// Structured tokens for spacing, radius, shadow, animation
// ═══════════════════════════════════════════════════════════════

// ─── Spacing Tokens (8dp grid) ───
object CrankSpacing {
    val XS = 4.dp
    val S = 8.dp
    val M = 16.dp
    val L = 24.dp
    val XL = 32.dp
    val XXL = 48.dp
}

// ─── Border Radius Tokens ───
object CrankRadius {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XL = 24.dp
    val Full = 999.dp
}

// ─── Shadow Tokens (Navy-tinted) ───
object CrankShadow {
    const val CardElevation = 4f
    const val CardBlur = 8f
    const val CardColor = 0x4D1A2238      // Navy blue 30%

    const val FABElevation = 8f
    const val FABBlur = 16f
    const val FABColor = 0x4DFFD700       // Gold 30%

    const val ModalElevation = 12f
    const val ModalBlur = 24f
    const val ModalColor = 0x80000000     // Black 50%
}

// ─── Animation Tokens ───
object CrankAnimation {
    const val Fast = 150
    const val Normal = 300
    const val Slow = 500

    val EnteringDampingRatio = 0.75f
    val EnteringStiffness = 300f
    val ExitingEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
}

// ─── Typography Tokens ───
// Display sizes are larger and heavier than before, with negative
// tracking. That combination is what separates a screen title from
// body text at a glance; adding size alone just looks zoomed in.
object CrankTokens {
    val H1 = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.03).em
    )
    val H2 = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.02).em
    )
    val H3 = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.01).em
    )
    val BodyLarge = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val BodyMedium = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    val BodySmall = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    val Button = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.05.em
    )
    val Caption = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    )
    val ArtistName = TextStyle(
        fontFamily = InterFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = MetallicGoldStart
    )
}
