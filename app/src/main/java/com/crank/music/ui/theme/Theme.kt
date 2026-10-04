package com.crank.music.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════
// CRANK THEME
//
// Two real colour schemes, selected by an explicit mode rather
// than hardcoded. `AppearanceSettingsScreen` has advertised Light
// and Auto since before this file had a light scheme at all; see
// ThemePreference for that history.
// ═══════════════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFA2D48),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6223C),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFE6223C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1C1C1E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFF6480),
    onTertiary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFA1A1A6),
    error = Color(0xFFE5484D),
    onError = Color.White,
    outline = Color(0x1FFFFFFF),
    outlineVariant = Color(0x14FFFFFF)
)

/**
 * Light scheme.
 *
 * Not a mechanical inversion of the dark one. Two values are chosen rather than flipped:
 *
 * - The accent is darkened to [MetallicGoldStart]. The dark scheme's `#FA2D48` on white is
 *   legible as a large fill but fails contrast as text or a thin icon, which is how it is used
 *   in list rows and the mini-player. The darker red holds up at small sizes.
 * - `background` is pure white while `surface` is a light grey. Collapsing both to white makes
 *   every card dissolve into the page and the layout loses its structure; keeping the same
 *   relationship the dark scheme uses (surface distinct from background) preserves it in both.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE6223C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DE),
    onPrimaryContainer = Color(0xFF7A0F1E),
    secondary = Color(0xFFE6223C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFFC9182F),
    onTertiary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6C6C70),
    error = Color(0xFFD70015),
    onError = Color.White,
    outline = Color(0x1F000000),
    outlineVariant = Color(0x14000000)
)

// ═══════════════════════════════════════════════════════════════
// THEME-AWARE PALETTE
//
// ~40 files still name the palette tokens directly (ChampagneGold, ObsidianBlack,
// WarmWhite, ...). Those tokens used to be fixed `val`s, so the app could only ever be dark:
// the scheme switched, the tokens did not. They are now @Composable getters backed by the
// composition local declared here, which means every existing call site follows light/dark
// with no edits at the call site.
//
// [DarkPalette] and [LightPalette] hold raw colours rather than referencing the tokens, and
// the two Material colour schemes above hold raw colours too — a getter cannot be read from
// a top-level `val`, so those layers have to stay literal. Because of that, the palette and
// the scheme are kept in step by review rather than by the compiler; the accent and surface
// values here are the same ones in LightColorScheme/DarkColorScheme.
//
// One limit worth stating plainly: a composable *default argument* cannot read a composition
// local either. Where a token is used as a default parameter value it must stay a literal.
// ═══════════════════════════════════════════════════════════════

/**
 * The mutable surface of the appearance. [DarkPalette] and [LightPalette] are the two
 * instances; nothing else should construct one.
 */
@Immutable
internal data class CrankPalette(
    val accent: Color,
    val accentBright: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val divider: Color,
    val overlay: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val cardBorder: Color,
    val shadow: Color,
    val cardGradientTop: Color,
    val cardGradientBottom: Color,
    val navyBlue: Color,
    // ─── Semantic status colours, kept here so they can differ per theme too ───
    val error: Color,
    /**
     * Tinted background for a destructive/error card.
     *
     * A translucent wash of [error] over the theme background, so a red danger card is a pale pink
     * panel in light mode instead of a near-black one — which is what a literal `Color(0xFF2A1010)`
     * produced, and why those cards were unreadable with the Light theme selected.
     */
    val errorSurface: Color,
    /** Softer [error] for secondary text inside a danger card, still legible on [errorSurface]. */
    val errorMuted: Color,
    /** Border for a danger card, at the alpha the design calls for. */
    val errorBorder: Color,
    /** Caution: high-data quality, a paused download, an elevated-band warning. */
    val warning: Color,
    val success: Color,
    val heart: Color
)

internal val DarkPalette = CrankPalette(
    accent = Color(0xFFFA2D48),
    accentBright = Color(0xFFFF3752),
    background = Color(0xFF000000),
    surface = Color(0xFF121212),
    surfaceElevated = Color(0xFF1C1C1E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA1A1A6),
    textDisabled = Color(0x80FFFFFF),
    divider = Color(0x14FFFFFF),
    overlay = Color(0x80000000),
    glassSurface = Color(0x1FFFFFFF),
    glassBorder = Color(0x1FFFFFFF),
    cardBorder = Color(0x1FFFFFFF),
    shadow = Color(0x66000000),
    cardGradientTop = Color(0xFF1C1C1E),
    cardGradientBottom = Color(0xFF000000),
    navyBlue = Color(0xFF2C2C2E),
    error = Color(0xFFE5484D),
    // On black, the tint has to add light rather than remove it, so this is a red-black, not a wash.
    errorSurface = Color(0xFF2A1010),
    errorMuted = Color(0xFFFF8A80),
    errorBorder = Color(0x4DE5484D),
    warning = Color(0xFFFF9F0A),
    success = Color(0xFF30D158),
    heart = Color(0xFFE5484D)
)

internal val LightPalette = CrankPalette(
    // Mirrors the darker accent LightColorScheme uses for legibility on white.
    accent = Color(0xFFE6223C),
    accentBright = Color(0xFFFA2D48),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F2F7),
    surfaceElevated = Color(0xFFE5E5EA),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF6C6C70),
    textDisabled = Color(0x61000000),
    divider = Color(0x14000000),
    overlay = Color(0x33000000),
    glassSurface = Color(0x0F000000),
    glassBorder = Color(0x14000000),
    cardBorder = Color(0x1F000000),
    shadow = Color(0x1A000000),
    cardGradientTop = Color(0xFFF2F2F7),
    cardGradientBottom = Color(0xFFFFFFFF),
    navyBlue = Color(0xFFE5E5EA),
    error = Color(0xFFD70015),
    // On white, a light pink wash: same role, opposite direction.
    errorSurface = Color(0xFFFFEBEC),
    errorMuted = Color(0xFFB3261E),
    errorBorder = Color(0x33D70015),
    warning = Color(0xFFB25000),
    success = Color(0xFF248A3D),
    heart = Color(0xFFD70015)
)

internal val LocalCrankPalette = staticCompositionLocalOf<CrankPalette> { DarkPalette }

/**
 * Builds the palette for [dark] with the user's accent substituted in.
 *
 * Only the accent-family entries change. Everything else — surfaces, text, dividers, status colours —
 * is theme-dependent, not accent-dependent, so leaving those alone is what keeps a light accent from
 * breaking contrast. [accentBright] is derived by lightening rather than taking the accent verbatim so
 * gradients and highlights keep their relationship to the base colour.
 */
internal fun paletteFor(dark: Boolean, accent: Color?): CrankPalette {
    val base = if (dark) DarkPalette else LightPalette
    if (accent == null) return base
    return base.copy(
        accent = accent,
        accentBright = lerp(base.accentBright, Color.White, if (dark) 0.18f else 0.1f),
        heart = accent
    )
}

/**
 * Builds the Material scheme with the accent substituted in.
 *
 * `primary`/`secondary` carry the accent because that is what Material components read for switches,
 * sliders, ripples and the segmented-button selection indicator. Without this the palette tokens
 * would change colour while every Material widget stayed the old red — the two layers have to move
 * together.
 */
private fun schemeFor(dark: Boolean, accent: Color?): ColorScheme {
    val base = if (dark) DarkColorScheme else LightColorScheme
    if (accent == null) return base
    return base.copy(
        primary = accent,
        secondary = accent,
        primaryContainer = accent,
        tertiary = if (dark) lerp(accent, Color.White, 0.35f) else lerp(accent, Color.Black, 0.2f),
    )
}

@Composable
fun CrankandroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * The user's accent choice, or null to use the theme's own accent.
     *
     * This is the parameter whose absence made every appearance control decorative: the screen
     * offered six presets and an HSL picker, wrote them into a ViewModel, and the theme never read
     * them — so picking blue changed a preview swatch and nothing else.
     */
    accentArgb: Int? = null,
    /**
     * Multiplier applied to text size. 1f is the user's system setting.
     *
     * Implemented by scaling [LocalDensity.fontScale] rather than editing every `TextStyle`, which
     * keeps the scaling correct for text that comes from Material components too — and means the
     * setting cannot silently miss a style that forgot to opt in.
     */
    typographyScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val accent = accentArgb?.let { Color(it) }

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? ComponentActivity ?: return@SideEffect
            // The system bar style has to follow the scheme. Previously both were pinned to
            // `SystemBarStyle.dark`, which forces light status-bar icons — correct on black,
            // invisible on white. That is why light mode needed more than a colour swap.
            activity.enableEdgeToEdge(
                statusBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                },
                navigationBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    )
                }
            )
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val density = LocalDensity.current
    val scaledDensity = remember(density, typographyScale) {
        Density(density.density, density.fontScale * typographyScale.coerceIn(MIN_SCALE, MAX_SCALE))
    }

    CompositionLocalProvider(
        LocalCrankPalette provides paletteFor(darkTheme, accent),
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = schemeFor(darkTheme, accent),
            typography = CrankTypography,
            content = content
        )
    }
}

/**
 * Clamps for the typography scale.
 *
 * The low bound stops a future mis-set value from collapsing text to nothing; the high bound matches
 * roughly the largest accessibility font size, past which text starts clipping in this app's fixed
 * layouts (the bottom bar labels and the Now Playing transport row are the first to go).
 */
private const val MIN_SCALE = 0.85f
private const val MAX_SCALE = 1.6f

@Composable
fun CrankTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentArgb: Int? = null,
    typographyScale: Float = 1f,
    content: @Composable () -> Unit
) {
    CrankandroidTheme(
        darkTheme = darkTheme,
        accentArgb = accentArgb,
        typographyScale = typographyScale,
        content = content
    )
}
