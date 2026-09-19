package com.crank.music.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
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
    success = Color(0xFF248A3D),
    heart = Color(0xFFD70015)
)

internal val LocalCrankPalette = staticCompositionLocalOf<CrankPalette> { DarkPalette }

@Composable
fun CrankandroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
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

    CompositionLocalProvider(
        LocalCrankPalette provides if (darkTheme) DarkPalette else LightPalette
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = CrankTypography,
            content = content
        )
    }
}

@Composable
fun CrankTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CrankandroidTheme(darkTheme = darkTheme, content = content)
}
