package com.crank.music.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════
// CRANK THEME — MIDNIGHT GOLD
// Material 3 color scheme with luxury gold + deep navy palette
// ═══════════════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    primary = CrankGold,
    onPrimary = DeepSpaceNavy,
    primaryContainer = MetallicGoldStart,
    onPrimaryContainer = TextPrimary,
    secondary = MetallicGoldStart,
    onSecondary = DeepSpaceNavy,
    secondaryContainer = DarkerNavy,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberGlow,
    onTertiary = DeepSpaceNavy,
    background = DeepSpaceNavy,
    onBackground = TextPrimary,
    surface = MidnightBlue,
    onSurface = TextPrimary,
    surfaceVariant = DarkerNavy,
    onSurfaceVariant = TextSecondarySoft,
    error = ErrorRed,
    onError = TextPrimary,
    outline = CardBorderGold,
    outlineVariant = Divider
)

@Composable
fun CrankandroidTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? ComponentActivity ?: return@SideEffect
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            )
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = CrankTypography,
        content = content
    )
}

@Composable
fun CrankTheme(
    content: @Composable () -> Unit
) {
    CrankandroidTheme(content = content)
}
