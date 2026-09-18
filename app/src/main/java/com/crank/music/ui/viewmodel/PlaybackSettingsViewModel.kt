package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class NormalizationMode(val label: String) {
    ALBUM("Album Mode"),
    TRACK("Track Mode")
}

enum class SleepTimerPreset(val label: String, val minutes: Int) {
    MIN_15("15 min", 15),
    MIN_30("30 min", 30),
    MIN_45("45 min", 45),
    HR_1("1 hour", 60),
    HR_2("2 hours", 120)
}

enum class MaxQueueSize(val label: String, val size: Int) {
    SMALL("50 songs", 50),
    MEDIUM("100 songs", 100),
    LARGE("250 songs", 250),
    UNLIMITED("Unlimited", Int.MAX_VALUE)
}

data class PlaybackSettingsState(
    val crossfadeEnabled: Boolean = true,
    val crossfadeDuration: Float = 4f,
    val isPreviewPlaying: Boolean = false,
    val gaplessEnabled: Boolean = true,
    val normalizationEnabled: Boolean = true,
    val normalizationMode: NormalizationMode = NormalizationMode.TRACK,
    val targetLoudness: Float = -14f,
    val playbackSpeed: Float = 1.0f,
    val pitchPreserved: Boolean = true,
    val autoPlaySimilar: Boolean = true,
    val addToHistoryQueue: Boolean = false,
    val maxQueueSize: MaxQueueSize = MaxQueueSize.MEDIUM,
    val sleepTimerActive: Boolean = false,
    val sleepTimerPreset: SleepTimerPreset? = null,
    val sleepTimerRemainingMs: Long = 0L,
    val fadeOutVolume: Boolean = true,
    val endOfSongOption: Boolean = false,
    val carModeEnabled: Boolean = false,
    val carModeAutoEnable: Boolean = true
)

@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PlaybackSettingsState())
    val uiState: StateFlow<PlaybackSettingsState> = _uiState.asStateFlow()

    fun setCrossfadeEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(crossfadeEnabled = enabled)
    }

    fun setCrossfadeDuration(duration: Float) {
        _uiState.value = _uiState.value.copy(crossfadeDuration = duration)
    }

    fun togglePreview() {
        _uiState.value = _uiState.value.copy(isPreviewPlaying = !_uiState.value.isPreviewPlaying)
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(gaplessEnabled = enabled)
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(normalizationEnabled = enabled)
    }

    fun setNormalizationMode(mode: NormalizationMode) {
        _uiState.value = _uiState.value.copy(normalizationMode = mode)
    }

    fun setTargetLoudness(loudness: Float) {
        _uiState.value = _uiState.value.copy(targetLoudness = loudness)
    }

    fun setPlaybackSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun setPitchPreserved(preserved: Boolean) {
        _uiState.value = _uiState.value.copy(pitchPreserved = preserved)
    }

    fun setAutoPlaySimilar(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoPlaySimilar = enabled)
    }

    fun setAddToHistoryQueue(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(addToHistoryQueue = enabled)
    }

    fun setMaxQueueSize(size: MaxQueueSize) {
        _uiState.value = _uiState.value.copy(maxQueueSize = size)
    }

    fun setSleepTimerPreset(preset: SleepTimerPreset) {
        _uiState.value = _uiState.value.copy(
            sleepTimerActive = true,
            sleepTimerPreset = preset,
            sleepTimerRemainingMs = preset.minutes * 60_000L
        )
    }

    fun cancelSleepTimer() {
        _uiState.value = _uiState.value.copy(
            sleepTimerActive = false,
            sleepTimerPreset = null,
            sleepTimerRemainingMs = 0L
        )
    }

    fun setFadeOutVolume(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(fadeOutVolume = enabled)
    }

    fun setEndOfSongOption(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(endOfSongOption = enabled)
    }

    fun setCarModeEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(carModeEnabled = enabled)
    }

    fun setCarModeAutoEnable(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(carModeAutoEnable = enabled)
    }
}
