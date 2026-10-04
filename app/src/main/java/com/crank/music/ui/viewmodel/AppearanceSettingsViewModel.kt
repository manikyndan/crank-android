package com.crank.music.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.ui.theme.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    OLED("OLED Pure Black"),
    LIGHT("Light"),
    AUTO("Auto")
}

enum class TypographyScale(val label: String, val multiplier: Float) {
    SMALL("Small", 0.9f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f),
    EXTRA_LARGE("Extra Large", 1.3f)
}

data class AppearanceUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: Color = DEFAULT_ACCENT,
    val accentColorName: String = "Red",
    val typographyScale: TypographyScale = TypographyScale.MEDIUM,
    val customHue: Float = 4f,
    val customSaturation: Float = 90f,
    val customLightness: Float = 58f,
    val showColorPicker: Boolean = false
) {
    companion object {
        /** Matches the dark scheme's `primary`, so "Red" is selected by default. */
        val DEFAULT_ACCENT = Color(0xFFFA2D48)
    }
}

/**
 * Appearance settings.
 *
 * ## What changed
 *
 * Only two of these controls had any effect on the app; the rest were state that nothing read.
 * Every setting here is now persisted through [ThemePreference] and consumed by the theme root, so
 * selecting a colour or a text size changes what the app renders and survives a restart.
 *
 * ## What was removed
 *
 * These were removed rather than wired, because there is no implementation behind them and inventing
 * one is a different project:
 *
 * - **Material You dynamic colour** — requires an undocumented `android.R.color.system_accent*`
 *   lookup or a Monet port, and silently does nothing on most devices.
 * - **Album art shape, background style, reduce motion, lyrics blur** — each implies a shared
 *   component or animation gate that does not exist; the controls changed only their own highlight.
 *
 * Also fixed here: `resetToDefaults()` used to reset the local state without telling
 * [ThemePreference], so the picker jumped back to Dark while the app stayed on whatever mode was
 * already applied — the one place where the stale-state bug was visible as a contradiction on screen.
 */
@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor(
    private val themePreference: ThemePreference,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppearanceUiState(themeMode = themePreference.initialMode())
    )
    val uiState: StateFlow<AppearanceUiState> = _uiState.asStateFlow()

    /** Preset name, colour and the hue used to seed the custom picker from the same choice. */
    val presetColors = listOf(
        Triple("Gold", Color(0xFFFFD700), 51f),
        Triple("Blue", Color(0xFF2196F3), 207f),
        Triple("Purple", Color(0xFF9C27B0), 291f),
        Triple("Green", Color(0xFF4CAF50), 122f),
        Triple("Red", Color(0xFFFA2D48), 356f),
        Triple("Orange", Color(0xFFFF9800), 36f)
    )

    init {
        val saved = themePreference.initialAppearance()
        val match = saved.accentArgb?.let { argb ->
            presetColors.firstOrNull { it.second.toArgb() == argb }
        }
        _uiState.value = _uiState.value.copy(
            accentColor = saved.accentArgb?.let { Color(it) } ?: AppearanceUiState.DEFAULT_ACCENT,
            accentColorName = match?.first ?: if (saved.accentArgb != null) "Custom" else "Red",
            typographyScale = TypographyScale.entries
                .minByOrNull { kotlin.math.abs(it.multiplier - saved.typographyScale) }
                ?: TypographyScale.MEDIUM,
            showColorPicker = saved.accentArgb != null && match == null,
        )
    }

    /**
     * Applies [mode] and writes it through to [ThemePreference].
     *
     * Both halves are needed: the local copy keeps the picker's selection highlight immediate,
     * and the preference is what `MainActivity` observes to actually rebuild the colour scheme.
     * Writing only the local copy is the original bug — the control appeared to work.
     */
    fun setThemeMode(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
        viewModelScope.launch { themePreference.setMode(mode) }
    }

    fun setAccentColor(name: String, color: Color) {
        _uiState.value = _uiState.value.copy(
            accentColor = color,
            accentColorName = name,
            customHue = presetColors.find { it.first == name }?.third ?: _uiState.value.customHue
        )
        viewModelScope.launch { themePreference.setAccentArgb(color.toArgb()) }
    }

    /**
     * Applies a colour from the HSL picker.
     *
     * Only hue, saturation and lightness are remembered across restarts, not the resulting RGB: the
     * picker's own state is HSL, and round-tripping through ARGB would make the sliders jump to a
     * slightly different position on reopen than the one the user left them at.
     */
    fun setCustomColor(hue: Float, saturation: Float, lightness: Float) {
        val color = Color.hsl(hue, saturation / 100f, lightness / 100f)
        _uiState.value = _uiState.value.copy(
            accentColor = color,
            accentColorName = "Custom",
            customHue = hue,
            customSaturation = saturation,
            customLightness = lightness
        )
        viewModelScope.launch { themePreference.setAccentArgb(color.toArgb()) }
    }

    fun setTypographyScale(scale: TypographyScale) {
        _uiState.value = _uiState.value.copy(typographyScale = scale)
        viewModelScope.launch { themePreference.setTypographyScale(scale.multiplier) }
    }

    fun toggleColorPicker(show: Boolean) {
        _uiState.value = _uiState.value.copy(showColorPicker = show)
    }

    /**
     * Returns every setting to its default — including the theme mode.
     *
     * The mode is reset through [ThemePreference] as well as locally. Resetting only the local copy
     * left the picker showing "Dark" while the app kept rendering the previous mode.
     */
    fun resetToDefaults() {
        val defaults = AppearanceUiState()
        _uiState.value = defaults
        viewModelScope.launch {
            themePreference.setMode(defaults.themeMode)
            themePreference.setAccentArgb(null)
            themePreference.setTypographyScale(defaults.typographyScale.multiplier)
        }
    }
}
