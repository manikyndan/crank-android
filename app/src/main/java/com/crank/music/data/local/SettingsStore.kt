package com.crank.music.data.local

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

    suspend fun putBoolean(key: String, value: Boolean) = write(key, value.toString())

    suspend fun putString(key: String, value: String) = write(key, value)

    suspend fun putFloat(key: String, value: Float) = write(key, value.toString())

    private suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { songDao.getSessionValue(key) }.getOrNull()
    }

    private suspend fun write(key: String, value: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                songDao.putSessionValue(SessionEntity(key = key, value = value))
            }
        }
    }

    companion object {
        // Keys are namespaced so they cannot collide with the non-settings entries already in
        // this table (the visitor id, the Browse fetch stamp).
        const val MOBILE_QUALITY = "settings.audio.mobile_quality"
        const val WIFI_QUALITY = "settings.audio.wifi_quality"
        const val DOWNLOAD_QUALITY = "settings.audio.download_quality"
        const val DATA_SAVER = "settings.data_saver"
        const val NOTIFICATIONS_ENABLED = "settings.notifications_enabled"
        const val PRIVACY_LISTENING_HISTORY = "settings.privacy.listening_history"
        const val PRIVACY_PERSONALIZED = "settings.privacy.personalized_recs"
        const val PRIVACY_ANALYTICS = "settings.privacy.analytics"
    }
}
