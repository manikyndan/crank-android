package com.crank.music.ui.viewmodel

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class CustomPreset(
    val name: String,
    val icon: String,
    val bandLevels: List<Int>
)

data class EqualizerUiState(
    val isEnabled: Boolean = true,
    val currentPreset: String = "Flat",
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val animatedBandLevels: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val preampDb: Int = 0,
    val animatedPreampDb: Float = 0f,
    val isBassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val isNormalizationEnabled: Boolean = false,
    val isVisualizerEnabled: Boolean = false,
    val audioSessionId: Int? = null,
    val statusMessage: String = "Ready",
    val customPresets: List<CustomPreset> = emptyList(),
    val spectrumBars: List<Float> = List(10) { 0.15f }
)

@HiltViewModel
class EqualizerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    private var nativeEqualizer: Equalizer? = null
    private var nativeBassBoost: BassBoost? = null
    private var nativeLoudnessEnhancer: LoudnessEnhancer? = null

    val frequencies = listOf("60 Hz", "230 Hz", "910 Hz", "4 kHz", "14 kHz")

    val presets = linkedMapOf(
        "Flat" to listOf(0, 0, 0, 0, 0),
        "Bass Boost" to listOf(12, 8, 2, -1, -2),
        "Vocal" to listOf(-2, 2, 8, 6, 1),
        "Rock" to listOf(8, 5, -2, 4, 7),
        "Pop" to listOf(-1, 3, 6, 4, -2),
        "Jazz" to listOf(4, 2, -1, 5, 8),
        "Classical" to listOf(6, 4, 0, 2, 6)
    )

    fun attachAudioSession(sessionId: Int?) {
        if (sessionId == null || sessionId == 0 || sessionId == -1) {
            _uiState.value = _uiState.value.copy(
                audioSessionId = null,
                statusMessage = "Audio Session Pending"
            )
            return
        }

        if (_uiState.value.audioSessionId == sessionId && nativeEqualizer != null) return

        try {
            nativeEqualizer?.release()
            nativeBassBoost?.release()
            nativeLoudnessEnhancer?.release()

            nativeEqualizer = Equalizer(0, sessionId).apply {
                enabled = _uiState.value.isEnabled
            }

            nativeBassBoost = BassBoost(0, sessionId).apply {
                enabled = _uiState.value.isBassBoostEnabled
            }

            nativeLoudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = _uiState.value.isNormalizationEnabled
            }

            _uiState.value = _uiState.value.copy(
                audioSessionId = sessionId,
                statusMessage = "Connected"
            )

            applyBandLevelsToNative(_uiState.value.bandLevels)
            applyPreampToNative(_uiState.value.preampDb)
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                audioSessionId = sessionId,
                statusMessage = "Native Equalizer Active"
            )
        }
    }

    fun toggleEqualizer(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isEnabled = enabled)
        try {
            nativeEqualizer?.enabled = enabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onBandLevelChanged(bandIndex: Int, newDbLevel: Int) {
        val currentLevels = _uiState.value.bandLevels.toMutableList()
        if (bandIndex in currentLevels.indices) {
            currentLevels[bandIndex] = newDbLevel.coerceIn(-12, 12)
            _uiState.value = _uiState.value.copy(
                bandLevels = currentLevels,
                currentPreset = "Custom"
            )
            applyBandLevelToNative(bandIndex, newDbLevel)
        }
    }

    fun onPreampChanged(newDb: Int) {
        val clamped = newDb.coerceIn(-12, 12)
        _uiState.value = _uiState.value.copy(preampDb = clamped)
        applyPreampToNative(clamped)
    }

    fun selectPreset(presetName: String) {
        val allPresets = getAllPresets()
        val levels = allPresets[presetName] ?: return
        _uiState.value = _uiState.value.copy(
            currentPreset = presetName,
            bandLevels = levels.toList()
        )
        applyBandLevelsToNative(levels)
    }

    fun toggleBassBoost(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isBassBoostEnabled = enabled,
            bassBoostStrength = if (enabled) 800 else 0
        )
        try {
            nativeBassBoost?.enabled = enabled
            if (enabled) {
                nativeBassBoost?.setStrength(800.toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleNormalization(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isNormalizationEnabled = enabled)
        try {
            nativeLoudnessEnhancer?.enabled = enabled
            if (enabled) {
                nativeLoudnessEnhancer?.setTargetGain(300)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleVisualizer(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isVisualizerEnabled = enabled)
    }

    fun saveCustomPreset(name: String, icon: String) {
        val preset = CustomPreset(
            name = name,
            icon = icon,
            bandLevels = _uiState.value.bandLevels.toList()
        )
        val updated = _uiState.value.customPresets + preset
        _uiState.value = _uiState.value.copy(
            customPresets = updated,
            currentPreset = name
        )
    }

    fun deleteCustomPreset(name: String) {
        val updated = _uiState.value.customPresets.filter { it.name != name }
        _uiState.value = _uiState.value.copy(
            customPresets = updated,
            currentPreset = if (_uiState.value.currentPreset == name) "Flat" else _uiState.value.currentPreset
        )
    }

    fun updateSpectrumBars(bars: List<Float>) {
        _uiState.value = _uiState.value.copy(spectrumBars = bars)
    }

    fun getAllPresets(): Map<String, List<Int>> {
        val builtIn = presets.mapValues { it.value }
        val custom = _uiState.value.customPresets.associate { it.name to it.bandLevels }
        return builtIn + custom
    }

    private fun applyBandLevelToNative(bandIndex: Int, dbLevel: Int) {
        try {
            val millibels = (dbLevel * 100).coerceIn(-1200, 1200).toShort()
            nativeEqualizer?.setBandLevel(bandIndex.toShort(), millibels)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyBandLevelsToNative(levels: List<Int>) {
        levels.forEachIndexed { index, db ->
            applyBandLevelToNative(index, db)
        }
    }

    private fun applyPreampToNative(db: Int) {
        try {
            val gainMillibels = db * 100
            nativeLoudnessEnhancer?.setTargetGain(gainMillibels)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            nativeEqualizer?.release()
            nativeBassBoost?.release()
            nativeLoudnessEnhancer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
