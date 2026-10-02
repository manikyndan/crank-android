package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.crank.music.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class UpdatePhase { CHECKING, UPDATE_AVAILABLE, DOWNLOADING, DOWNLOAD_COMPLETE, INSTALLING, INSTALLED, NO_UPDATE }

data class UpdateInfo(
    val versionName: String = "2.0.0",
    val versionCode: Int = 2,
    val buildNumber: String = "Build 128",
    val lastUpdateDate: String = "Aug 15, 2025",
    val appSize: String = "42.8 MB",
    val updateSize: String = "18.3 MB",
    val changelog: List<String> = listOf(
        "New offline download manager with auto-download rules",
        "Privacy & Security dashboard with biometric verification",
        "Advanced equalizer with 10-bar spectrum analyzer",
        "Crank AI chat with context-aware backgrounds",
        "Music DNA stats with animated radar chart",
        "Improved playback stability and bug fixes"
    )
)

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.CHECKING,
    // Read from the build rather than hardcoded: this used to claim the installed version was
    // "1.0.0" while the app was actually on 1.0.3, so the screen was wrong about its own premise.
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val currentBuild: String = "Build ${BuildConfig.VERSION_CODE}",
    val lastUpdateDate: String = "Jan 15, 2025",
    val appSize: String = "38.2 MB",
    val updateInfo: UpdateInfo? = null,
    val downloadProgress: Float = 0f,
    val downloadSpeed: String = "0 MB/s",
    val timeRemaining: String = "Calculating...",
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val hasUpdateBadge: Boolean = false,
    val showWhatIsNew: Boolean = false
)

@HiltViewModel
class UpdateCheckerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates() {
        _uiState.value = _uiState.value.copy(phase = UpdatePhase.CHECKING)

        _uiState.value = _uiState.value.copy(
            phase = UpdatePhase.UPDATE_AVAILABLE,
            updateInfo = UpdateInfo(),
            hasUpdateBadge = true,
            showWhatIsNew = true
        )
    }

    fun dismissWhatIsNew() {
        _uiState.value = _uiState.value.copy(showWhatIsNew = false)
    }

    fun startDownload() {
        _uiState.value = _uiState.value.copy(
            phase = UpdatePhase.DOWNLOADING,
            isDownloading = true,
            isPaused = false,
            downloadProgress = 0f,
            downloadSpeed = "2.4 MB/s",
            timeRemaining = "7 min"
        )
    }

    fun pauseDownload() {
        _uiState.value = _uiState.value.copy(
            isPaused = true,
            downloadSpeed = "Paused",
            timeRemaining = "Paused"
        )
    }

    fun resumeDownload() {
        _uiState.value = _uiState.value.copy(
            isPaused = false,
            downloadSpeed = "2.4 MB/s",
            timeRemaining = "${((1f - _uiState.value.downloadProgress) * 7).toInt()} min"
        )
    }

    fun updateDownloadProgress(progress: Float) {
        val remaining = ((1f - progress) * 7).toInt()
        _uiState.value = _uiState.value.copy(
            downloadProgress = progress,
            downloadSpeed = if (progress >= 1f) "Complete" else "${(2.0 + Math.random()).roundTo(1)} MB/s",
            timeRemaining = if (progress >= 1f) "Complete" else "$remaining min"
        )
        if (progress >= 1f) {
            _uiState.value = _uiState.value.copy(
                phase = UpdatePhase.DOWNLOAD_COMPLETE,
                isDownloading = false,
                downloadSpeed = "Complete",
                timeRemaining = "Complete"
            )
        }
    }

    fun installUpdate() {
        _uiState.value = _uiState.value.copy(phase = UpdatePhase.INSTALLING)
        _uiState.value = _uiState.value.copy(
            phase = UpdatePhase.INSTALLED,
            hasUpdateBadge = false
        )
    }

    fun remindLater() {
        _uiState.value = _uiState.value.copy(
            showWhatIsNew = false,
            hasUpdateBadge = true
        )
    }

    fun cancelCheck() {
        _uiState.value = _uiState.value.copy(phase = UpdatePhase.NO_UPDATE)
    }

    fun clearBadge() {
        _uiState.value = _uiState.value.copy(hasUpdateBadge = false)
    }
}

private fun Double.roundTo(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}
