package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class AudioQualityViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AudioQualityState())
    val uiState: StateFlow<AudioQualityState> = _uiState.asStateFlow()

    fun setMobileQuality(quality: StreamQuality) {
        _uiState.value = _uiState.value.copy(mobileQuality = quality)
    }

    fun setWifiQuality(quality: StreamQuality) {
        _uiState.value = _uiState.value.copy(wifiQuality = quality)
    }

    fun setDownloadQuality(quality: StreamQuality) {
        _uiState.value = _uiState.value.copy(downloadQuality = quality)
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
        _uiState.value = _uiState.value.copy(
            dataSaverEnabled = newState,
            mobileQuality = if (newState) StreamQuality.LOW else StreamQuality.HIGH
        )
    }

    fun toggleFormatsExpanded() {
        _uiState.value = _uiState.value.copy(formatsExpanded = !_uiState.value.formatsExpanded)
    }
}
