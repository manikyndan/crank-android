package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DeviceInfo(
    val model: String = "Pixel 8 Pro",
    val osVersion: String = "Android 14",
    val storageUsed: String = "12.4 GB",
    val storageAvailable: String = "48.2 GB",
    val appVersion: String = "1.0.0",
    val buildNumber: String = "Build 1",
    val installDate: String = "Jan 15, 2025"
)

data class TrustedDevice(
    val id: String,
    val name: String,
    val lastSeen: String,
    val isCurrent: Boolean = false
)

enum class PrivateSessionDuration(val label: String, val minutes: Int) {
    MIN_15("15 min", 15),
    MIN_30("30 min", 30),
    HR_1("1 hour", 60),
    HR_4("4 hours", 240)
}

data class PrivacyUiState(
    val listeningHistoryEnabled: Boolean = true,
    val personalizedRecsEnabled: Boolean = true,
    val analyticsEnabled: Boolean = true,
    val showActivityToFriends: Boolean = true,
    val incognitoMode: Boolean = false,
    val privateSession: Boolean = false,
    val privateSessionDuration: PrivateSessionDuration = PrivateSessionDuration.HR_1,
    val privateSessionRemainingMs: Long = 0L,
    val isDeviceVerified: Boolean = false,
    val isVerifying: Boolean = false,
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val trustedDevices: List<TrustedDevice> = listOf(
        TrustedDevice("1", "Pixel 8 Pro", "Just now", true),
        TrustedDevice("2", "Samsung Galaxy S24", "2 hours ago"),
        TrustedDevice("3", "MacBook Pro", "Yesterday")
    ),
    val showClearHistoryDialog: Boolean = false,
    val showDeleteSuccess: Boolean = false,
    val isHistoryClearing: Boolean = false
)

@HiltViewModel
class PrivacyViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    fun toggleListeningHistory(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(listeningHistoryEnabled = enabled)
    }

    fun togglePersonalizedRecs(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(personalizedRecsEnabled = enabled)
    }

    fun toggleAnalytics(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(analyticsEnabled = enabled)
    }

    fun toggleShowActivity(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(showActivityToFriends = enabled)
    }

    fun toggleIncognito(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(incognitoMode = enabled)
    }

    fun togglePrivateSession(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            privateSession = enabled,
            privateSessionRemainingMs = if (enabled) _uiState.value.privateSessionDuration.minutes * 60_000L else 0L
        )
    }

    fun setPrivateSessionDuration(duration: PrivateSessionDuration) {
        _uiState.value = _uiState.value.copy(
            privateSessionDuration = duration,
            privateSessionRemainingMs = if (_uiState.value.privateSession) duration.minutes * 60_000L else 0L
        )
    }

    fun verifyDevice() {
        _uiState.value = _uiState.value.copy(isVerifying = true)
        _uiState.value = _uiState.value.copy(
            isVerifying = false,
            isDeviceVerified = true
        )
    }

    fun removeTrustedDevice(deviceId: String) {
        _uiState.value = _uiState.value.copy(
            trustedDevices = _uiState.value.trustedDevices.filter { it.id != deviceId }
        )
    }

    fun showClearHistoryDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = show)
    }

    fun clearListeningHistory() {
        _uiState.value = _uiState.value.copy(isHistoryClearing = true)
        _uiState.value = _uiState.value.copy(
            isHistoryClearing = false,
            showClearHistoryDialog = false,
            showDeleteSuccess = true
        )
        _uiState.value = _uiState.value.copy(showDeleteSuccess = false)
    }
}
