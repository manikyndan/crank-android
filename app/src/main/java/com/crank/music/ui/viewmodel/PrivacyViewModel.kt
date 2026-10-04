package com.crank.music.ui.viewmodel

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.BuildConfig
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.data.local.SettingsStore
import com.crank.music.data.local.SongDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * What the device actually is.
 *
 * Every field previously defaulted to an invented value — "Pixel 8 Pro", "Android 14",
 * "12.4 GB", "Build 1" — so the screen confidently described a phone the user may not own. The
 * defaults are now a dash: a value that failed to read shows as unknown rather than as a
 * plausible-looking lie.
 */
data class DeviceInfo(
    val model: String = "—",
    val osVersion: String = "—",
    val storageUsed: String = "—",
    val storageFree: String = "—",
    val appVersion: String = "—",
    val buildNumber: String = "—",
    val installDate: String = "—"
)

data class PrivacyUiState(
    val listeningHistoryEnabled: Boolean = true,
    val personalizedRecsEnabled: Boolean = true,
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val historyEntryCount: Int = 0,
    val showClearHistoryDialog: Boolean = false,
    val showDeleteSuccess: Boolean = false,
    val isHistoryClearing: Boolean = false
)

/**
 * Privacy and device information.
 *
 * ## What was removed, and why
 *
 * This screen used to present a large amount of invented state as fact: three "trusted devices"
 * ("MacBook Pro", "Galaxy S24") that no user had ever paired, a "Verify Device" action that
 * completed instantly without doing anything, an analytics toggle for an app that ships no
 * analytics SDK, activity-sharing toggle for a social graph that does not exist, and incognito /
 * private-session switches with no implementation behind them. `clearListeningHistory()` only
 * flipped UI flags — the playback history was never touched.
 *
 * Each of those is either now real or gone:
 *
 * - **Real:** listening-history and personalised-recommendations toggles are persisted and enforced
 *   (`PlayerViewModel.recordHistory` and the home feed's history-derived sections both consult
 *   them); "Clear listening history" performs an actual delete and reports whether it succeeded;
 *   the device card reads `Build`, `StatFs` and the package manager.
 * - **Gone:** device verification, trusted devices, analytics, activity sharing, incognito and
 *   private session. A control that cannot do anything is worse than no control, because it
 *   reports success.
 */
@HiltViewModel
class PrivacyViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
        loadDeviceInfo()
        loadHistoryCount()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            val history = settingsStore.getBoolean(SettingsStore.PRIVACY_LISTENING_HISTORY, true)
            val recs = settingsStore.getBoolean(SettingsStore.PRIVACY_PERSONALIZED, true)
            _uiState.value = _uiState.value.copy(
                listeningHistoryEnabled = history,
                personalizedRecsEnabled = recs
            )
        }
    }

    private fun loadHistoryCount() {
        viewModelScope.launch {
            val count = runCatching { withContext(Dispatchers.IO) { songDao.getHistoryCount() } }
            count.onFailure { it.rethrowIfCancellation() }
            _uiState.value = _uiState.value.copy(historyEntryCount = count.getOrDefault(0))
        }
    }

    private fun loadDeviceInfo() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { readDeviceInfo() }
            _uiState.value = _uiState.value.copy(deviceInfo = info)
        }
    }

    /**
     * Reads the real device facts.
     *
     * Storage is measured on the volume the app's files live on, via [StatFs] — not a formatted
     * pair of strings that used to be concatenated for display, which produced values like
     * "12.4 GB / 60.6 GB" out of two numbers that were never added together.
     */
    private fun readDeviceInfo(): DeviceInfo {
        val storage = runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            val used = stat.totalBytes - stat.availableBytes
            used to stat.availableBytes
        }.getOrNull()

        val installTime = runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .firstInstallTime
        }.getOrNull()

        return DeviceInfo(
            model = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "—" },
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            storageUsed = storage?.let { formatBytes(it.first) } ?: "—",
            storageFree = storage?.let { formatBytes(it.second) } ?: "—",
            appVersion = BuildConfig.VERSION_NAME,
            buildNumber = "Build ${BuildConfig.VERSION_CODE}",
            installDate = installTime?.let { formatDate(it) } ?: "—"
        )
    }

    fun toggleListeningHistory(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(listeningHistoryEnabled = enabled)
        viewModelScope.launch { settingsStore.putBoolean(SettingsStore.PRIVACY_LISTENING_HISTORY, enabled) }
    }

    fun togglePersonalizedRecs(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(personalizedRecsEnabled = enabled)
        viewModelScope.launch { settingsStore.putBoolean(SettingsStore.PRIVACY_PERSONALIZED, enabled) }
    }

    fun showClearHistoryDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = show)
    }

    fun dismissDeleteSuccess() {
        _uiState.value = _uiState.value.copy(showDeleteSuccess = false)
    }

    /**
     * Deletes the playback history and reports the outcome.
     *
     * The dialog stays closed either way, but the success overlay only appears when the delete
     * actually succeeded — a failure leaves the count unchanged so the screen cannot claim the
     * history is gone while the rows are still there.
     */
    fun clearListeningHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isHistoryClearing = true)

            val result = runCatching { withContext(Dispatchers.IO) { songDao.clearHistory() } }
            result.onFailure {
                it.rethrowIfCancellation()
                Log.e(TAG, "Failed to clear listening history: ${it.message}")
            }

            _uiState.value = _uiState.value.copy(
                isHistoryClearing = false,
                showClearHistoryDialog = false,
                showDeleteSuccess = result.isSuccess,
                historyEntryCount = if (result.isSuccess) 0 else _uiState.value.historyEntryCount
            )
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMillis))

    private companion object {
        const val TAG = "CRANK_PRIVACY"
    }
}
