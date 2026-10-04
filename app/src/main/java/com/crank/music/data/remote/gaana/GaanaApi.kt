package com.crank.music.data.remote.gaana

import android.util.Log
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.data.local.SettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin client over a self-hosted [GaanaPy](https://github.com/ZingyTomato/GaanaPy) server.
 *
 * ## Why a base URL and not a hardcoded host
 *
 * GaanaPy is a server, not a library: it wraps Gaana's website with FastAPI and must be run
 * somewhere the device can reach. There is no public instance to point at, so the host is a user
 * setting ([SettingsStore.GAANA_BASE_URL]) and its absence means "this source is switched off"
 * rather than "this source is broken".
 *
 * ## Error handling
 *
 * GaanaPy answers **404 for "no results"** — a search that matched nothing and a track that does
 * not exist are the same status. Since `NetworkModule` sets `expectSuccess = true`, that surfaces
 * as a thrown `ResponseException`; it is caught here and turned into an empty answer, because
 * "no results" is a legitimate result and must not be reported to the user as a failure.
 *
 * Every other failure returns `null`/empty too. A dead server degrades the Gaana source; it must
 * not take the rest of the app with it. Cancellation is always rethrown, per `CoroutineDiscipline`.
 *
 * ## Paths
 *
 * The trailing slash is not cosmetic — FastAPI registers `/songs/search/` and `/trending` exactly
 * as written, and a mismatch 307-redirects rather than resolving.
 */
@Singleton
class GaanaApi @Inject constructor(
    private val client: HttpClient,
    private val settingsStore: SettingsStore,
) {

    /**
     * The configured server, normalised, or `null` when unset or not a usable URL.
     *
     * The accept/reject rules live in [validateGaanaBaseUrl] so this and the settings screen can
     * never disagree about what is configured. Empty means "this source is switched off".
     *
     * Note this is an `http://` URL in the usual LAN case, which the manifest's
     * `network_security_config` permits only for the hosts listed there.
     */
    suspend fun baseUrl(): String? =
        (validateGaanaBaseUrl(settingsStore.getString(SettingsStore.GAANA_BASE_URL, ""))
            as? GaanaUrlValidation.Ok)?.value

    /**
     * Whether the server answers at all — the check behind the screen's "Test connection" button.
     *
     * A health probe rather than a real search: it asks the smallest question that distinguishes
     * "reachable and speaking GaanaPy" from "not reachable", without depending on the upstream
     * catalogue being up, or on the search query being well-formed.
     *
     * Returns `false` for any failure, because the caller is showing a status, not reporting an
     * error. Cancellation still propagates via [getOrNull].
     */
    suspend fun isHealthy(baseUrl: String): Boolean =
        getOrNull<GaanaHealthDto>(baseUrl, "/health", emptyMap())?.status.equals("ok", ignoreCase = true)

    suspend fun searchSongs(baseUrl: String, query: String, limit: Int): List<GaanaSongDto> =
        getOrEmpty(baseUrl, "/songs/search/", mapOf("query" to query, "limit" to limit))

    suspend fun searchAlbums(baseUrl: String, query: String, limit: Int): List<GaanaAlbumDto> =
        getOrEmpty(baseUrl, "/albums/search/", mapOf("query" to query, "limit" to limit))

    /** Full details for one track, including a freshly minted `stream_urls` block. */
    suspend fun songInfo(baseUrl: String, seokey: String): GaanaSongDto? =
        getOrNull(baseUrl, "/songs/info/", mapOf("seokey" to seokey))

    /** An album with its tracklist, each track carrying its own `stream_urls`. */
    suspend fun albumInfo(baseUrl: String, seokey: String): GaanaAlbumDto? =
        getOrNull(baseUrl, "/albums/info/", mapOf("seokey" to seokey))

    suspend fun trending(baseUrl: String, language: String, limit: Int): List<GaanaSongDto> =
        getOrEmpty(baseUrl, "/trending", mapOf("language" to language, "limit" to limit))

    suspend fun newReleases(baseUrl: String, language: String, limit: Int): GaanaNewReleasesDto? =
        getOrNull(baseUrl, "/newreleases", mapOf("language" to language, "limit" to limit))

    private suspend inline fun <reified T> getOrNull(
        baseUrl: String,
        path: String,
        params: Map<String, Any?>,
    ): T? = try {
        client.get(baseUrl + path) {
            params.forEach { (key, value) -> if (value != null) parameter(key, value) }
        }.body<T>()
    } catch (e: ResponseException) {
        // Documented GaanaPy behaviour: 404 means "nothing matched", not "the server failed".
        Log.d(TAG, "Gaana answered ${e.response.status.value} for $path — treating as no results")
        null
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        Log.w(TAG, "Gaana request failed for $path: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    private suspend inline fun <reified T> getOrEmpty(
        baseUrl: String,
        path: String,
        params: Map<String, Any?>,
    ): List<T> = getOrNull<List<T>>(baseUrl, path, params) ?: emptyList()

    companion object {
        private const val TAG = "CRANK_GAANA"
    }
}
