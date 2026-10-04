package com.crank.music.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.BuildConfig
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.data.remote.UpdateChecker
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
 * Every state this screen can honestly be in.
 *
 * `CANNOT_CHECK` is the important one. The previous screen had no failure state at all: it always
 * reported an update to v2.0.0 with a fabricated changelog, and "installing" always succeeded. A
 * screen that cannot be wrong is not a working feature.
 */
enum class UpdatePhase { IDLE, CHECKING, UPDATE_AVAILABLE, UP_TO_DATE, CANNOT_CHECK }

/**
 * Update information as published by the release endpoint.
 *
 * No default values. Every field here previously defaulted to an invented one — version "2.0.0",
 * "Build 128", an 18.3 MB download size and a six-item changelog describing features that did not
 * exist — all of which the screen displayed before any check occurred.
 */
data class AvailableRelease(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val changelog: List<String>,
    val sizeBytes: Long?
)

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    /** The installed version, read from the package rather than declared. */
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val installDate: String = "—",
    val appSize: String = "—",
    val minSdk: String = "—",
    val availableRelease: AvailableRelease? = null,
    val hasUpdateBadge: Boolean = false,
    /** Set only for a real failure, so the screen can say why it could not check. */
    val errorMessage: String? = null
)

/**
 * Update checking.
 *
 * ## What this replaced
 *
 * The previous implementation injected nothing at all and called no network. `checkForUpdates()`
 * synchronously declared an update to v2.0.0 and surfaced a hardcoded changelog; the download progress
 * was driven by `Math.random()` for the transfer speed against a fixed seven-minute estimate; and
 * `installUpdate()` reported INSTALLED without installing anything. The screen also carried an unused
 * `UpdateNotificationHelper::class.java` reference (a `Class`, never an instance), which is why no
 * update notification was ever posted.
 *
 * This version performs a real network check against [UpdateChecker], compares version codes, and —
 * when the manifest cannot be reached or is not configured — says so. The download and install flow is
 * gone entirely: this app has no release endpoint or signing setup to install from, so offering a
 * download button would be theatre.
 */
@HiltViewModel
class UpdateCheckerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    init {
        loadInstalledPackageInfo()
        checkForUpdates()
    }

    /** Real package facts: install date, on-disk APK size and minimum supported API. */
    private fun loadInstalledPackageInfo() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val apkSize = context.packageManager
                        .getApplicationInfo(context.packageName, 0)
                        .sourceDir
                        ?.let { java.io.File(it).length() }
                    Triple(packageInfo.firstInstallTime, apkSize, packageInfo.applicationInfo?.minSdkVersion)
                }.getOrNull()
            }

            _uiState.value = _uiState.value.copy(
                installDate = info?.first?.let { formatDate(it) } ?: "—",
                appSize = info?.second?.takeIf { it > 0 }?.let { formatBytes(it) } ?: "—",
                minSdk = info?.third?.let { "API $it+" } ?: "—",
            )
        }
    }

    /**
     * Fetches the published release manifest and compares it with the installed build.
     *
     * A manifest whose version code is not greater than this build's is reported as
     * [UpdatePhase.UP_TO_DATE] — not as an update. That comparison is the one piece of logic here
     * worth testing, and it is deliberately strict (`>` rather than `!=`): treating a *lower*
     * published version as an update would invite a downgrade.
     */
    fun checkForUpdates() {
        if (_uiState.value.phase == UpdatePhase.CHECKING) return
        _uiState.value = _uiState.value.copy(phase = UpdatePhase.CHECKING, errorMessage = null)

        viewModelScope.launch {
            try {
                val release = updateChecker.fetchLatestRelease()

                if (release == null) {
                    // No endpoint configured, or the manifest could not be parsed. Both are reported
                    // as "cannot check" rather than being dressed up as a successful check.
                    _uiState.value = _uiState.value.copy(
                        phase = UpdatePhase.CANNOT_CHECK,
                        hasUpdateBadge = false,
                        errorMessage = "Couldn't reach the update server.",
                    )
                    return@launch
                }

                val isNewer = release.versionCode > _uiState.value.currentVersionCode
                _uiState.value = _uiState.value.copy(
                    phase = if (isNewer) UpdatePhase.UPDATE_AVAILABLE else UpdatePhase.UP_TO_DATE,
                    availableRelease = release.takeIf { isNewer },
                    hasUpdateBadge = isNewer,
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.w(TAG, "Update check failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    phase = UpdatePhase.CANNOT_CHECK,
                    errorMessage = "Couldn't reach the update server.",
                )
            }
        }
    }

    fun clearBadge() {
        _uiState.value = _uiState.value.copy(hasUpdateBadge = false)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMillis))

    private companion object {
        const val TAG = "CRANK_UPDATE"
    }
}
