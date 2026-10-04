package com.crank.music.ui.theme

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.crank.music.data.local.SongDao
import com.crank.music.ui.viewmodel.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

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
 * ## Why this is `SharedPreferences` and not Room
 *
 * The theme has to be known *before the first frame* — a light-mode user must not see a black
 * frame on every cold start — and `MainActivity.onCreate` is not a place that can wait.
 *
 * The previous implementation resolved that with `runBlocking { songDao.getSessionValue(KEY) }`
 * inside `onCreate`. That is fine for a warm database and very much not fine for a cold one:
 * `session_values` lives in the same Room database as the rest of the app, so on the first launch
 * after an update this read also performed database creation and migrations 1→6, synchronously, on
 * the main thread, before the first frame. On a slow device that is an ANR risk for a value that
 * changes once in a blue moon.
 *
 * `SharedPreferences` is synchronous by design, so the read is a small, bounded parse of one XML
 * file the platform already has open. This class is the *only* consumer of that file, and it holds
 * three values: the mode, the accent and the text scale. Everything else stays in Room.
 *
 * ### Existing installs
 *
 * Older builds wrote the mode to `session_values`. Rather than silently resetting those users to
 * the default, [migrateFromRoomIfNeeded] reads the old value once on an IO coroutine and mirrors it
 * into preferences. It runs off the main thread, so the only cost of the upgrade is that one launch
 * may show the default scheme before the choice is restored — which is exactly the behaviour the old
 * blocking read was buying, minus the ANR risk.
 */
@Singleton
class ThemePreference @Inject constructor(
    @ApplicationContext context: Context,
    private val songDao: SongDao,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Outlives every caller; used only for the one-time legacy migration. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _mode = MutableStateFlow<ThemeMode?>(null)

    /** The current mode, or `null` until [initialMode] has run. */
    val mode: StateFlow<ThemeMode?> = _mode.asStateFlow()

    /**
     * Accent and text-scale choices, as one flow.
     *
     * Kept beside the mode rather than in the appearance ViewModel because the theme root has to read
     * them: `AppearanceSettingsViewModel` held the accent, the typography scale and six other
     * preferences in a local `MutableStateFlow` that nothing outside the screen could observe. The
     * controls moved their own highlight and changed nothing about the app, and both values were lost
     * on the next visit to the screen.
     */
    private val _appearance = MutableStateFlow(AppearancePreference())
    val appearance: StateFlow<AppearancePreference> = _appearance.asStateFlow()

    init {
        migrateFromRoomIfNeeded()
    }

    /**
     * The persisted mode, read synchronously from preferences.
     *
     * Idempotent and safe from any thread: the underlying read is a map lookup after the platform's
     * own one-time file parse, so there is nothing here worth guarding with a double-checked lock —
     * two concurrent callers get the same answer.
     */
    fun initialMode(): ThemeMode {
        _mode.value?.let { return it }

        val resolved = prefs.getString(KEY_MODE, null)
            ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.DARK

        _mode.value = resolved
        return resolved
    }

    /**
     * Accent and scale, read synchronously.
     *
     * A malformed stored value degrades per field rather than as a whole: an unreadable accent falls
     * back to the theme's own accent while a valid text scale still applies.
     */
    fun initialAppearance(): AppearancePreference {
        _appearance.value.let { current ->
            if (current.isLoaded) return current
        }

        val resolved = AppearancePreference(
            accentArgb = prefs.getInt(KEY_ACCENT, NO_ACCENT).takeIf { it != NO_ACCENT },
            typographyScale = prefs.getFloat(KEY_TYPOGRAPHY_SCALE, 1f),
            isLoaded = true,
        )

        _appearance.value = resolved
        return resolved
    }

    /**
     * Records [mode] and persists it.
     *
     * The in-memory flow and the stored preference are updated together so the UI and the next cold
     * start cannot disagree; `apply` keeps the disk write off the calling thread.
     */
    suspend fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit { putString(KEY_MODE, mode.name) }
    }

    suspend fun setAccentArgb(argb: Int?) {
        _appearance.value = _appearance.value.copy(accentArgb = argb)
        prefs.edit { putInt(KEY_ACCENT, argb ?: NO_ACCENT) }
    }

    suspend fun setTypographyScale(scale: Float) {
        _appearance.value = _appearance.value.copy(typographyScale = scale)
        prefs.edit { putFloat(KEY_TYPOGRAPHY_SCALE, scale) }
    }

    /**
     * Copies a mode stored by an older build out of `session_values`, once.
     *
     * A no-op when preferences already hold a value or when the stored enum constant no longer
     * exists. Failures are logged and ignored: losing a colour preference is not worth a crash, and
     * the default is a valid choice.
     */
    private fun migrateFromRoomIfNeeded() {
        if (prefs.contains(KEY_MODE)) return

        scope.launch {
            runCatching { songDao.getSessionValue(LEGACY_KEY_MODE) }
                .onFailure { Log.w(TAG, "Could not read legacy theme preference", it) }
                .getOrNull()
                ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?.let { legacy ->
                    // Only claim the key if nothing has written one in the meantime.
                    if (!prefs.contains(KEY_MODE)) {
                        prefs.edit { putString(KEY_MODE, legacy.name) }
                        _mode.value = legacy
                    }
                }
        }
    }

    companion object {
        private const val TAG = "CRANK_THEME"

        /** Only this class reads this file. */
        private const val PREFS_NAME = "crank_ui_preferences"

        private const val KEY_MODE = "ui_theme_mode"
        private const val KEY_ACCENT = "ui_accent_argb"
        private const val KEY_TYPOGRAPHY_SCALE = "ui_typography_scale"

        /** Old key in `session_values`, read once to migrate existing installs. */
        private const val LEGACY_KEY_MODE = "ui_theme_mode"

        /** Stored in place of a null accent, which `SharedPreferences` cannot represent. */
        private const val NO_ACCENT = Int.MIN_VALUE
    }
}

/**
 * The appearance choices that have to be observable from the theme root.
 *
 * Only the two that actually change what the app renders live here. The remaining appearance settings
 * (Material You dynamic colour, album-art shape, background style, reduce motion, lyrics blur) had no
 * implementation behind them at all and have been removed from the screen, so there is nothing to
 * carry.
 */
@Immutable
data class AppearancePreference(
    /** `null` means "use the theme's own accent". */
    val accentArgb: Int? = null,
    /** Multiplier applied to text size. 1f leaves the user's system font scale alone. */
    val typographyScale: Float = 1f,
    /** False until the persisted values have been read, so the flow is not acted on prematurely. */
    val isLoaded: Boolean = false
)

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
