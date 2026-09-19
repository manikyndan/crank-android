package com.crank.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
// 3. **Every token resolves per-theme.** They are `val`s with @Composable getters backed by
//    [LocalCrankPalette], so a screen that names `ChampagneGold` or `ObsidianBlack` follows
//    light/dark automatically. Light values live in [LightPalette] in Theme.kt.
// ═══════════════════════════════════════════════════════════════

private val pal: CrankPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalCrankPalette.current

// ─── Surfaces ───
val ObsidianBlack: Color @Composable @ReadOnlyComposable get() = pal.background
val DeepSpaceNavy: Color @Composable @ReadOnlyComposable get() = pal.background
val MidnightBlue: Color @Composable @ReadOnlyComposable get() = pal.surface
val CharcoalSurface: Color @Composable @ReadOnlyComposable get() = pal.surface
val CharcoalElevated: Color @Composable @ReadOnlyComposable get() = pal.surfaceElevated
val DarkerNavy: Color @Composable @ReadOnlyComposable get() = pal.surfaceElevated
val NavyBlue: Color @Composable @ReadOnlyComposable get() = pal.navyBlue
val DarkGray: Color @Composable @ReadOnlyComposable get() = pal.surface

// ─── Text ───
val TextPrimary: Color @Composable @ReadOnlyComposable get() = pal.textPrimary
val WarmWhite: Color @Composable @ReadOnlyComposable get() = pal.textPrimary
val TextSecondarySoft: Color @Composable @ReadOnlyComposable get() = pal.textSecondary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = pal.textSecondary
val TextTertiary: Color @Composable @ReadOnlyComposable get() = pal.textDisabled
val TextDisabled: Color @Composable @ReadOnlyComposable get() = pal.textDisabled

// ─── Accent ───
val CrankGold: Color @Composable @ReadOnlyComposable get() = pal.accent
val ChampagneGold: Color @Composable @ReadOnlyComposable get() = pal.accent
val GoldMetallic: Color @Composable @ReadOnlyComposable get() = pal.accent
val WaveformActive: Color @Composable @ReadOnlyComposable get() = pal.accent
val MetallicGoldStart: Color @Composable @ReadOnlyComposable get() = pal.accent
val MetallicGoldEnd: Color @Composable @ReadOnlyComposable get() = pal.accent
val GoldGradientMid: Color @Composable @ReadOnlyComposable get() = pal.accent
val CrankGoldBright: Color @Composable @ReadOnlyComposable get() = pal.accentBright
val GoldGradientEnd: Color @Composable @ReadOnlyComposable get() = pal.accentBright
val GoldGradientStart: Color @Composable @ReadOnlyComposable get() = pal.accent
val AmberGlow: Color @Composable @ReadOnlyComposable get() = pal.accentBright
val GoldDark: Color @Composable @ReadOnlyComposable get() = pal.accent
val GoldMuted: Color @Composable @ReadOnlyComposable get() = pal.accent
val BronzeDark: Color @Composable @ReadOnlyComposable get() = pal.accent
val BronzeShadow: Color @Composable @ReadOnlyComposable get() = pal.accent

// ─── Structure ───
val Divider: Color @Composable @ReadOnlyComposable get() = pal.divider
val OverlayBlack: Color @Composable @ReadOnlyComposable get() = pal.overlay
val GlassSurface: Color @Composable @ReadOnlyComposable get() = pal.glassSurface
val GlassBorder: Color @Composable @ReadOnlyComposable get() = pal.glassBorder
val CardBorderGold: Color @Composable @ReadOnlyComposable get() = pal.cardBorder
val NavyShadow: Color @Composable @ReadOnlyComposable get() = pal.shadow
val CardGradientTop: Color @Composable @ReadOnlyComposable get() = pal.cardGradientTop
val CardGradientBottom: Color @Composable @ReadOnlyComposable get() = pal.cardGradientBottom
val WaveformInactive: Color @Composable @ReadOnlyComposable get() = pal.navyBlue

// ─── Semantic status colours ───
val ErrorRed: Color @Composable @ReadOnlyComposable get() = pal.error
val SuccessGreen: Color @Composable @ReadOnlyComposable get() = pal.success
val HeartRed: Color @Composable @ReadOnlyComposable get() = pal.heart
