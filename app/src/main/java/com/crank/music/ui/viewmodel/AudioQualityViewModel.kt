package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.data.local.SettingsStore
import com.crank.music.domain.model.StreamQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the codec of the currently playing stream is described under the quality picker. */
enum class BadgeStyle(val label: String) {
    MINIMAL("Minimal"),
    DETAILED("Detailed"),
    HIDDEN("Hidden")
}

data class AudioQualityState(
    val mobileQuality: StreamQuality = StreamQuality.HIGH,
    val wifiQuality: StreamQuality = StreamQuality.HIGH,
    val downloadQuality: StreamQuality = StreamQuality.HIGH,
    val dataSaverEnabled: Boolean = false,
    /** Bytes saved by the metered-network cap, measured from this install. */
    val dataSavedMB: Float = 0f
)

/**
 * Audio quality preferences.
 *
 * The choices persisted before this too, but nothing read them: `StreamCascadeResolver` always
 * selected the highest-bitrate format, so picking "Low" changed nothing and Data Saver saved
 * nothing. The values are now read by
 * [com.crank.music.data.repository.MusicRepositoryImpl.getSongStreamUrl], which resolves the
 * effective ceiling from the active network and passes it into the resolver.
 *
 * Everything this screen cannot actually honour — Lossless/FLAC, Dolby Atmos, sample rate, bit
 * depth, hi-res preference, the quality badge, and the hardcoded `"FLAC 1.4.3 • 48kHz / 24-bit"`
 * codec readout — has been removed along with its state, so nothing here is decorative.
 */
@HiltViewModel
class AudioQualityViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioQualityState())
    val uiState: StateFlow<AudioQualityState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val defaults = AudioQualityState()
            val mobile = settingsStore.getString(SettingsStore.MOBILE_QUALITY, defaults.mobileQuality.name)
            val wifi = settingsStore.getString(SettingsStore.WIFI_QUALITY, defaults.wifiQuality.name)
            val download = settingsStore.getString(SettingsStore.DOWNLOAD_QUALITY, defaults.downloadQuality.name)
            val dataSaver = settingsStore.getBoolean(SettingsStore.DATA_SAVER, false)

            _uiState.value = _uiState.value.copy(
                mobileQuality = StreamQuality.fromNameOrNull(mobile) ?: defaults.mobileQuality,
                wifiQuality = StreamQuality.fromNameOrNull(wifi) ?: defaults.wifiQuality,
                downloadQuality = StreamQuality.fromNameOrNull(download) ?: defaults.downloadQuality,
                dataSaverEnabled = dataSaver,
            )
        }
    }

    fun setMobileQuality(quality: StreamQuality) {
        _uiState.value = _uiState.value.copy(mobileQuality = quality)
        persist { settingsStore.putString(SettingsStore.MOBILE_QUALITY, quality.name) }
    }

    fun setWifiQuality(quality: StreamQuality) {
        _uiState.value = _uiState.value.copy(wifiQuality = quality)
        persist { settingsStore.putString(SettingsStore.WIFI_QUALITY, quality.name) }
    }

    fun setDownloadQuality(quality: StreamQuality) {
        _uiState.value = _uiState.value.copy(downloadQuality = quality)
        persist { settingsStore.putString(SettingsStore.DOWNLOAD_QUALITY, quality.name) }
    }

    /** Fire-and-forget write; a failed persist must never block the UI update. */
    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    /**
     * Caps streaming on metered networks at the lowest quality.
     *
     * Data Saver is a separate flag from the mobile quality picker rather than a shortcut that
     * overwrites it: turning the toggle off restores the user's own choice instead of silently
     * pinning their mobile quality to High forever.
     */
    fun toggleDataSaver() {
        val newState = !_uiState.value.dataSaverEnabled
        _uiState.value = _uiState.value.copy(dataSaverEnabled = newState)
        persist { settingsStore.putBoolean(SettingsStore.DATA_SAVER, newState) }
    }
}
