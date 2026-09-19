package com.crank.music.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
    primary = CrankGold,
    onPrimary = Color.White,
    primaryContainer = MetallicGoldStart,
    onPrimaryContainer = TextPrimary,
    secondary = MetallicGoldStart,
    onSecondary = Color.White,
    secondaryContainer = DarkerNavy,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberGlow,
    onTertiary = Color.Black,
    background = DeepSpaceNavy,
    onBackground = TextPrimary,
    surface = MidnightBlue,
    onSurface = TextPrimary,
    surfaceVariant = DarkerNavy,
    onSurfaceVariant = TextSecondarySoft,
    error = ErrorRed,
    onError = Color.White,
    outline = CardBorderGold,
    outlineVariant = Divider
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
    primary = MetallicGoldStart,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DE),
    onPrimaryContainer = BronzeShadow,
    secondary = MetallicGoldStart,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = BronzeDark,
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

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = CrankTypography,
        content = content
    )
}

@Composable
fun CrankTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CrankandroidTheme(darkTheme = darkTheme, content = content)
}
