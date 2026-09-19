package com.crank.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════
// CRANK TYPOGRAPHY
//
// Retargeted from a serif-display / sans-body pairing to a single
// bold sans scale.
//
// The old headings were `FontFamily.Serif` (Playfair Display by
// intent, but the platform serif in practice, since no font file was
// ever bundled). A serif display face reads as editorial/luxury; a
// music player wants the opposite — heavy, tight, plain sans that
// lets the artwork carry the character. So display styles are now
// the default sans at a heavier weight and negative tracking, which
// is what makes a title read as deliberate rather than merely large.
//
// The two `val`s are kept because DesignTokens and several screens
// reference them by name. `PlayfairDisplay` now resolves to the
// sans family, so any screen still asking for it gets the new voice
// without being edited.
// ═══════════════════════════════════════════════════════════════

val PlayfairDisplay = androidx.compose.ui.text.font.FontFamily.Default
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = (-0.01).em
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
        fontSize = 11.sp,
        letterSpacing = 0.02.em
    )
)
