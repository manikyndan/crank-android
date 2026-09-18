package com.crank.music.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    OLED("OLED Pure Black"),
    LIGHT("Light"),
    AUTO("Auto")
}

enum class AlbumArtShape(val label: String) {
    ROUNDED_SQUARE("Rounded Square"),
    CIRCLE("Circle"),
    SQUIRCLE("Squircle")
}

enum class BackgroundStyle(val label: String) {
    SOLID("Solid Color"),
    GRADIENT("Gradient"),
    DYNAMIC("Dynamic")
}

enum class TypographyScale(val label: String) {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large"),
    EXTRA_LARGE("Extra Large")
}

data class AppearanceUiState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: Color = Color(0xFFFFD700),
    val accentColorName: String = "Gold",
    val isDynamicMaterialYou: Boolean = false,
    val typographyScale: TypographyScale = TypographyScale.MEDIUM,
    val albumArtShape: AlbumArtShape = AlbumArtShape.ROUNDED_SQUARE,
    val backgroundStyle: BackgroundStyle = BackgroundStyle.SOLID,
    val showLyricsBlur: Boolean = true,
    val animatedTransitions: Boolean = true,
    val reduceMotion: Boolean = false,
    val customHue: Float = 45f,
    val customSaturation: Float = 100f,
    val customLightness: Float = 50f,
    val showColorPicker: Boolean = false
)

@HiltViewModel
class AppearanceSettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AppearanceUiState())
    val uiState: StateFlow<AppearanceUiState> = _uiState.asStateFlow()

    val presetColors = listOf(
        Triple("Gold", Color(0xFFFFD700), 45f),
        Triple("Blue", Color(0xFF2196F3), 207f),
        Triple("Purple", Color(0xFF9C27B0), 300f),
        Triple("Green", Color(0xFF4CAF50), 122f),
        Triple("Red", Color(0xFFF44336), 4f),
        Triple("Orange", Color(0xFFFF9800), 36f)
    )

    fun setThemeMode(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setAccentColor(name: String, color: Color) {
        _uiState.value = _uiState.value.copy(
            accentColor = color,
            accentColorName = name,
            customHue = presetColors.find { it.first == name }?.third ?: 45f
        )
    }

    fun setCustomColor(hue: Float, saturation: Float, lightness: Float) {
        val color = Color.hsl(hue, saturation / 100f, lightness / 100f)
        _uiState.value = _uiState.value.copy(
            accentColor = color,
            accentColorName = "Custom",
            customHue = hue,
            customSaturation = saturation,
            customLightness = lightness
        )
    }

    fun toggleDynamicMaterialYou(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDynamicMaterialYou = enabled)
    }

    fun setTypographyScale(scale: TypographyScale) {
        _uiState.value = _uiState.value.copy(typographyScale = scale)
    }

    fun setAlbumArtShape(shape: AlbumArtShape) {
        _uiState.value = _uiState.value.copy(albumArtShape = shape)
    }

    fun setBackgroundStyle(style: BackgroundStyle) {
        _uiState.value = _uiState.value.copy(backgroundStyle = style)
    }

    fun toggleLyricsBlur(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showLyricsBlur = enabled)
    }

    fun toggleAnimatedTransitions(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(animatedTransitions = enabled)
    }

    fun toggleReduceMotion(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(reduceMotion = enabled)
    }

    fun toggleColorPicker(show: Boolean) {
        _uiState.value = _uiState.value.copy(showColorPicker = show)
    }

    fun resetToDefaults() {
        _uiState.value = AppearanceUiState()
    }
}
