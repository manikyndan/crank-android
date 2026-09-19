package com.crank.music.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// CRANK — COLOR TOKENS
//
// Retargeted from the previous "Midnight Gold" palette to a
// neutral, high-contrast system with a single warm accent.
//
// Two properties are deliberate, and matter more than the specific
// hex values:
//
// 1. **Every token name is unchanged.** The 22 components under
//    `ui/components` and 23 screens reference these names, so the
//    palette can be retargeted without touching a single call site.
//    Renaming them would have rippled through 26k lines of UI for
//    no visual gain.
// 2. **Surfaces are neutral, not tinted.** The old surfaces were
//    navy (blue channel ~40 points above red), which tinted every
//    card and thumbnail. Real album artwork is the only thing in a
//    music player that should carry colour, so the greys here are
//    balanced and the accent is reserved for interactive state.
//
// Light-mode equivalents live in [LightColorScheme] in Theme.kt.
// These top-level values are the dark-mode ones.
// ═══════════════════════════════════════════════════════════════

// ─── Accent: single warm ramp ───
val CrankGold = Color(0xFFFA2D48)
val CrankGoldBright = Color(0xFFFF3752)
val MetallicGoldStart = Color(0xFFE6223C)
val MetallicGoldEnd = Color(0xFFFA2D48)
val AmberGlow = Color(0xFFFF6480)
val BronzeDark = Color(0xFFC9182F)
val BronzeShadow = Color(0xFF7A0F1E)

// ─── Backgrounds: true black → neutral greys ───
val DeepSpaceNavy = Color(0xFF000000)
val MidnightBlue = Color(0xFF121212)
val DarkerNavy = Color(0xFF1C1C1E)
val NavyBlue = Color(0xFF2C2C2E)

// ─── Card Gradient: elevated grey → black ───
val CardGradientTop = Color(0xFF1C1C1E)
val CardGradientBottom = Color(0xFF000000)

// ─── Text ───
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondarySoft = Color(0xFFA1A1A6)
val TextDisabled = Color(0x80FFFFFF)

// ─── Semantic ───
val ErrorRed = Color(0xFFE5484D)
val SuccessGreen = Color(0xFF30D158)

// ─── UI ───
val OverlayBlack = Color(0x80000000)
val GlassSurface = Color(0x1FFFFFFF)
val GlassBorder = Color(0x1FFFFFFF)
val Divider = Color(0x14FFFFFF)

// ─── Accent ramp (kept as a ramp for gradients) ───
val GoldGradientStart = Color(0xFFE6223C)
val GoldGradientMid = Color(0xFFFA2D48)
val GoldGradientEnd = Color(0xFFFF6480)

// ─── Card Border (light hairline, not a coloured ring) ───
val CardBorderGold = Color(0x1FFFFFFF)

// ─── Shadow for Cards ───
val NavyShadow = Color(0x66000000)

// ─── Legacy Aliases (used by existing screens) ───
val ObsidianBlack = DeepSpaceNavy
val DarkGray = MidnightBlue
val CharcoalSurface = MidnightBlue
val CharcoalElevated = DarkerNavy
val ChampagneGold = CrankGold
val GoldMuted = BronzeDark
val GoldDark = BronzeDark
val GoldMetallic = CrankGold
val WarmWhite = TextPrimary
val TextSecondary = TextSecondarySoft
val TextTertiary = TextDisabled
val HeartRed = ErrorRed
val WaveformActive = CrankGold
val WaveformInactive = NavyBlue
