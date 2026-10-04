package com.crank.music.data.remote

import android.util.Log
import com.crank.music.BuildConfig
import com.crank.music.ui.viewmodel.AvailableRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the published release manifest and turns it into an [AvailableRelease].
 *
 * ## Why there is no default endpoint baked in
 *
 * The app has no release host today: no GitHub releases, no Play listing, no update server. The
 * previous screen worked around that by inventing the whole result locally — a hardcoded v2.0.0, a
 * fabricated changelog, `Math.random()` download speeds and an "installed" state that installed
 * nothing. That is why a user could never tell whether the feature worked.
 *
 * Instead, an endpoint can be supplied without a code change via the `CRANK_UPDATE_MANIFEST_URL`
 * build config field (see `app/build.gradle.kts`, sourced from `local.properties`). When it is absent
 * [fetchLatestRelease] returns null and the UI reports "couldn't check" — which is true, and is far
 * more useful than a confident lie.
 *
 * ## Manifest format
 *
 * ```json
 * {
 *   "versionName": "1.2.0",
 *   "versionCode": 7,
 *   "downloadUrl": "https://example.test/crank-1.2.0.apk",
 *   "sizeBytes": 41943040,
 *   "changelog": ["Fixed offline playback", "Faster search"]
 * }
 * ```
 *
 * `versionCode` is required and must be an integer: it is the only field the update decision uses, and
 * a manifest without it cannot be compared against the installed build.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    /**
     * The published release, or `null` when there is no endpoint or the manifest is unusable.
     *
     * Returns null rather than throwing for every expected failure (no endpoint, no connectivity,
     * HTTP error, malformed JSON). Only an unexpected condition propagates, and the caller treats
     * that the same way — as "cannot check".
     */
    suspend fun fetchLatestRelease(): AvailableRelease? {
        val endpoint = MANIFEST_URL
        if (endpoint.isBlank()) {
            Log.i(TAG, "No update manifest configured; reporting 'cannot check'.")
            return null
        }

        return withContext(Dispatchers.IO) {
            val body = runCatching { get(endpoint) }
                .onFailure { Log.w(TAG, "Update manifest request failed: ${it.message}") }
                .getOrNull()
                ?: return@withContext null

            runCatching { parse(body) }
                .onFailure { Log.w(TAG, "Update manifest unparseable: ${it.message}") }
                .getOrNull()
        }
    }

    private fun get(endpoint: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from update manifest")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parses the manifest.
     *
     * `changelog` is optional but validated: a non-array is treated as absent rather than crashing, and
     * non-string entries are dropped so a malformed manifest yields a partial result instead of
     * failing the whole check.
     */
    private fun parse(body: String): AvailableRelease? {
        val json = JSONObject(body)
        if (!json.has("versionCode")) return null

        val versionCode = json.getInt("versionCode")
        val versionName = json.optString("versionName").takeIf { it.isNotBlank() }
            ?: return null

        val changelog = json.optJSONArray("changelog")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        }.orEmpty()

        return AvailableRelease(
            versionName = versionName,
            versionCode = versionCode,
            downloadUrl = json.optString("downloadUrl"),
            changelog = changelog,
            // Absent or zero size is reported as unknown rather than as a made-up figure.
            sizeBytes = json.optLong("sizeBytes", 0L).takeIf { it > 0L },
        )
    }

    private companion object {
        const val TAG = "CRANK_UPDATE"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000

        /**
         * Build-time endpoint.
         *
         * `BuildConfig` is referenced defensively: the field is added by `app/build.gradle.kts`, so a
         * build that predates it returns an empty string instead of failing to link.
         */
        val MANIFEST_URL: String
            get() = runCatching { BuildConfig.CRANK_UPDATE_MANIFEST_URL }.getOrDefault("")
    }
}
