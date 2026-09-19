package com.crank.music.ui.theme

import android.util.Log
import com.crank.music.data.local.SessionEntity
import com.crank.music.data.local.SongDao
import com.crank.music.ui.viewmodel.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Holds the app's theme choice so that the setting actually applies.
 *
 * ## The bug this exists to fix
 *
 * `AppearanceSettingsScreen` has offered Dark / OLED / Light / Auto for some time, and
 * `AppearanceSettingsViewModel.setThemeMode` wrote the choice into its own `uiState`. Nothing
 * read it and nothing persisted it. `CrankTheme` took no arguments and always built
 * `DarkColorScheme`. So the picker moved a highlight and changed a preview swatch, while the
 * app stayed dark through every restart — a control that reported success and did nothing,
 * which is worse than an absent one because the user blames themselves.
 *
 * Two things were missing and both are here:
 *
 * 1. **Persistence.** The choice has to outlive the process, or "Auto" and "Light" reset on
 *    every launch.
 * 2. **A single observable source.** `MainActivity` needs to read the mode to decide which
 *    colour scheme to install. Previously the only copy lived inside one screen's ViewModel,
 *    unreachable from the theme root.
 *
 * ## Why `runBlocking` appears here
 *
 * [initialMode] is read during composition to avoid a first-frame flash of the wrong theme —
 * a light-mode user must not see a black frame on every cold start. Reading through the
 * suspending DAO during composition would require either a placeholder (which is that flash)
 * or a blocking read. The read is a single indexed row on an already-open Room database, and
 * a failure returns the default rather than propagating. The alternative — defaulting to dark
 * and swapping after the first frame — is the visible bug we are fixing.
 *
 * Once [mode] has been initialised it is served from memory, so this cost is paid once.
 */
@Singleton
class ThemePreference
@Inject
constructor(
    private val songDao: SongDao,
) {

    private val _mode = MutableStateFlow<ThemeMode?>(null)

    /** The current mode, or `null` until [load] has run. */
    val mode: StateFlow<ThemeMode?> = _mode.asStateFlow()

    /**
     * Loads the persisted mode, at most once per process.
     *
     * Returns the effective mode, falling back to [ThemeMode.DARK] when nothing is stored or
     * the read fails. Safe to call repeatedly and from any thread.
     */
    fun initialMode(): ThemeMode {
        _mode.value?.let { return it }

        val loaded = try {
            runBlocking { readPersisted() }
        } catch (e: Exception) {
            // A preference read must never take the app down; the default is a valid choice.
            Log.w(TAG, "Failed to read theme preference: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

        val resolved = loaded ?: ThemeMode.DARK
        _mode.value = resolved
        return resolved
    }

    /**
     * Records [mode] and persists it.
     *
     * Updates the in-memory flow before the write completes so the UI switches immediately;
     * a slow disk must not make the toggle feel broken.
     */
    suspend fun setMode(mode: ThemeMode) {
        _mode.value = mode
        withContext(Dispatchers.IO) {
            runCatching {
                songDao.putSessionValue(SessionEntity(key = KEY, value = mode.name))
            }.onFailure {
                Log.w(TAG, "Failed to persist theme preference: ${it.message}")
            }
        }
    }

    private suspend fun readPersisted(): ThemeMode? =
        withContext(Dispatchers.IO) {
            songDao.getSessionValue(KEY)
                ?.let { stored ->
                    // An unrecognised value means the enum changed under a stale install.
                    // Falling back to the default is correct; throwing on a removed constant
                    // would brick startup.
                    ThemeMode.entries.firstOrNull { it.name == stored }
                }
        }

    companion object {
        private const val TAG = "CRANK_THEME"

        /** Storage key in `session_values`. Namespaced to avoid colliding with session keys. */
        private const val KEY = "ui_theme_mode"
    }
}

/**
 * Whether [ThemeMode] should render the light colour scheme.
 *
 * [ThemeMode.AUTO] is resolved by the caller, which is the only place that can see the system
 * setting. [ThemeMode.OLED] maps to dark: it is a *darker* dark, not a light scheme, and the
 * pure-black background it promises is already the dark scheme's background token.
 */
fun ThemeMode.isLight(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.LIGHT -> true
    ThemeMode.DARK, ThemeMode.OLED -> false
    ThemeMode.AUTO -> !systemInDarkTheme
}
