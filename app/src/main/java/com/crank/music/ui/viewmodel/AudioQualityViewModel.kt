package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.data.local.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StreamQuality(val label: String, val kbps: String, val dataPerHour: String, val isHighUsage: Boolean) {
    LOW("Low", "96 kbps", "~43 MB/hr", false),
    MEDIUM("Medium", "160 kbps", "~72 MB/hr", false),
    HIGH("High", "320 kbps", "~144 MB/hr", true),
    LOSSLESS("Lossless", "FLAC", "~430 MB/hr", true)
}

enum class SampleRate(val label: String) {
    K44("44.1 kHz"),
    K48("48 kHz"),
    K96("96 kHz"),
    K192("192 kHz")
}

enum class BitDepth(val label: String) {
    B16("16-bit"),
    B24("24-bit"),
    B32("32-bit float")
}

enum class BadgeStyle(val label: String) {
    MINIMAL("Minimal"),
    DETAILED("Detailed"),
    HIDDEN("Hidden")
}

data class AudioQualityState(
    val mobileQuality: StreamQuality = StreamQuality.HIGH,
    val wifiQuality: StreamQuality = StreamQuality.LOSSLESS,
    val downloadQuality: StreamQuality = StreamQuality.HIGH,
    val showQualityBadge: Boolean = true,
    val badgeStyle: BadgeStyle = BadgeStyle.MINIMAL,
    val preferHiRes: Boolean = true,
    val dolbyAtmos: Boolean = false,
    val sampleRate: SampleRate = SampleRate.K48,
    val bitDepth: BitDepth = BitDepth.B24,
    val dataSaverEnabled: Boolean = false,
    val dataSavedMB: Float = 247.5f,
    val formatsExpanded: Boolean = false,
    val currentFormat: String = "FLAC",
    val codecInfo: String = "FLAC 1.4.3 • 48kHz / 24-bit • Stereo"
)

@HiltViewModel
class AudioQualityViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioQualityState())
    val uiState: StateFlow<AudioQualityState> = _uiState.asStateFlow()

    init {
        // Restore the persisted choices. These used to reset on every screen visit, which made the
        // whole screen feel broken — you pick Lossless, come back, and it says High again.
        viewModelScope.launch {
            val mobile = settingsStore.getString(
                SettingsStore.MOBILE_QUALITY,
                AudioQualityState().mobileQuality.name,
            )
            val wifi = settingsStore.getString(
                SettingsStore.WIFI_QUALITY,
                AudioQualityState().wifiQuality.name,
            )
            val download = settingsStore.getString(
                SettingsStore.DOWNLOAD_QUALITY,
                AudioQualityState().downloadQuality.name,
            )
            val dataSaver = settingsStore.getBoolean(SettingsStore.DATA_SAVER, false)

            _uiState.value = _uiState.value.copy(
                mobileQuality = mobile.toQualityOrNull() ?: _uiState.value.mobileQuality,
                wifiQuality = wifi.toQualityOrNull() ?: _uiState.value.wifiQuality,
                downloadQuality = download.toQualityOrNull() ?: _uiState.value.downloadQuality,
                dataSaverEnabled = dataSaver,
            )
        }
    }

    /** Enum names are the stored form; an unknown value falls back to the caller's default. */
    private fun String.toQualityOrNull(): StreamQuality? =
        StreamQuality.entries.firstOrNull { it.name == this }

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

    fun toggleQualityBadge() {
        _uiState.value = _uiState.value.copy(showQualityBadge = !_uiState.value.showQualityBadge)
    }

    fun setBadgeStyle(style: BadgeStyle) {
        _uiState.value = _uiState.value.copy(badgeStyle = style)
    }

    fun toggleHiRes() {
        _uiState.value = _uiState.value.copy(preferHiRes = !_uiState.value.preferHiRes)
    }

    fun toggleDolbyAtmos() {
        _uiState.value = _uiState.value.copy(dolbyAtmos = !_uiState.value.dolbyAtmos)
    }

    fun setSampleRate(rate: SampleRate) {
        _uiState.value = _uiState.value.copy(sampleRate = rate)
    }

    fun setBitDepth(depth: BitDepth) {
        _uiState.value = _uiState.value.copy(bitDepth = depth)
    }

    fun toggleDataSaver() {
        val newState = !_uiState.value.dataSaverEnabled
        // Data saver genuinely changes behaviour: it forces the lowest mobile bitrate, which is
        // the whole point of the toggle. Both the flag and the resulting quality are persisted.
        _uiState.value = _uiState.value.copy(
            dataSaverEnabled = newState,
            mobileQuality = if (newState) StreamQuality.LOW else StreamQuality.HIGH
        )
        persist {
            settingsStore.putBoolean(SettingsStore.DATA_SAVER, newState)
            settingsStore.putString(SettingsStore.MOBILE_QUALITY, _uiState.value.mobileQuality.name)
        }
    }

    fun toggleFormatsExpanded() {
        _uiState.value = _uiState.value.copy(formatsExpanded = !_uiState.value.formatsExpanded)
    }
}
