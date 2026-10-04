package com.crank.music.data.local

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistence for user settings, on top of the `session_values` table.
 *
 * ## Why this exists
 *
 * Every settings screen updated an in-memory `MutableStateFlow` and nothing else, so each choice
 * was lost the moment the screen was destroyed — toggling "Data saver", leaving the screen and
 * coming back showed the default again. This gives those screens somewhere to write.
 *
 * ## Why `session_values` rather than DataStore
 *
 * The app already owns a Room database with a real migration chain, and `session_values` is
 * already a generic key/value store (it holds the visitor id and the Browse fetch stamp). Adding a
 * second persistence mechanism for a handful of booleans would mean two stores to keep consistent
 * and two things to wipe on "clear data". Reads and writes are suspend and off the main thread.
 *
 * Values are stored as strings so a single table covers booleans, enums and numbers; the typed
 * accessors below keep the encoding in one place.
 */
@Singleton
class SettingsStore @Inject constructor(
    private val songDao: SongDao,
) {

    /** Reads a boolean, returning [default] when it has never been set. */
    suspend fun getBoolean(key: String, default: Boolean): Boolean =
        read(key)?.toBooleanStrictOrNull() ?: default

    /** Reads a string, returning [default] when it has never been set. */
    suspend fun getString(key: String, default: String): String = read(key) ?: default

    /** Reads a float, returning [default] when it has never been set or is unparsable. */
    suspend fun getFloat(key: String, default: Float): Float =
        read(key)?.toFloatOrNull() ?: default

    /** Reads an int, returning [default] when it has never been set or is unparsable. */
    suspend fun getInt(key: String, default: Int): Int =
        read(key)?.toIntOrNull() ?: default

    /** Returns whether the value reached the database. */
    suspend fun putBoolean(key: String, value: Boolean): Boolean = write(key, value.toString())

    suspend fun putString(key: String, value: String): Boolean = write(key, value)

    suspend fun putFloat(key: String, value: Float): Boolean = write(key, value.toString())

    suspend fun putInt(key: String, value: Int): Boolean = write(key, value.toString())

    private suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { songDao.getSessionValue(key) }.getOrNull()
    }

    /**
     * Writes a value, reporting whether it succeeded.
     *
     * This used to swallow every failure silently. A setting that appears to save and then reverts on
     * the next screen is exactly the class of bug this store was written to remove, and swallowing the
     * error made it impossible to tell a failed write from a successful one — so the failure is logged
     * here and returned to the caller instead.
     */
    private suspend fun write(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            songDao.putSessionValue(SessionEntity(key = key, value = value))
        }.onFailure {
            Log.w(TAG, "Could not persist setting '$key'", it)
        }.isSuccess
    }

    companion object {
        private const val TAG = "CRANK_SETTINGS"

        // Keys are namespaced so they cannot collide with the non-settings entries already in
        // this table (the visitor id, the Browse fetch stamp).
        const val MOBILE_QUALITY = "settings.audio.mobile_quality"
        const val WIFI_QUALITY = "settings.audio.wifi_quality"
        const val DOWNLOAD_QUALITY = "settings.audio.download_quality"
        const val DATA_SAVER = "settings.data_saver"
        const val PLAYBACK_SPEED = "settings.playback.speed"
        const val NOTIFICATIONS_ENABLED = "settings.notifications_enabled"
        const val PRIVACY_LISTENING_HISTORY = "settings.privacy.listening_history"
        const val PRIVACY_PERSONALIZED = "settings.privacy.personalized_recs"
        const val PRIVACY_ANALYTICS = "settings.privacy.analytics"

        // Base URL of a self-hosted GaanaPy server, e.g. "http://192.168.1.42:8000".
        //
        // Empty by default and deliberately with no fallback value: there is no public GaanaPy
        // instance to point at, so an unreachable guess would make the Gaana source look broken
        // rather than unconfigured. Empty means "this source is switched off", which the UI can
        // state honestly. Keys and defaults are the only place this value is read or written.
        const val GAANA_BASE_URL = "settings.source.gaana_base_url"
    }
}
