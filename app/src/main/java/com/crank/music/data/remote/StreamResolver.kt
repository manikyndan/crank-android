package com.crank.music.data.remote

import android.util.Log
import com.crank.music.data.remote.innertube.ClientAttempt
import com.crank.music.data.remote.innertube.StreamCascadeResolver
import com.crank.music.data.remote.innertube.StreamUnavailableException
import com.crank.music.data.remote.gaana.GaanaRemoteDataSource
import com.crank.music.data.remote.potoken.PoTokenGenerator
import com.crank.music.domain.model.ContentSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/** A resolved, directly playable stream. */
data class StreamData(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /**
     * How long the URL remains valid, when the source reports it.
     *
     * Carried out of the resolver so callers can decide whether the URL is worth persisting.
     * A YouTube URL persisted past its expiry is permanently dead and must be re-resolved — see
     * [isExpired].
     */
    val expiresInSeconds: Int? = null,
    /** Which client identity produced this stream. Useful in logs when quality varies. */
    val sourceClient: String? = null,
)

/**
 * The single entry point for turning a track id into a playable stream URL.
 *
 * ## What changed, and why this class still exists
 *
 * Previously this delegated to `YouTubeStreamResolver`, which tried eight independent strategies
 * with no shared state and no record of what happened. That has been replaced by
 * [StreamCascadeResolver], which mints a Proof-of-Origin token once and then walks a measured
 * client chain. This class remains as the seam because it owns two things the cascade should not
 * know about:
 *
 * 1. **URL expiry semantics** — the pure functions below. These are unchanged and still correct;
 *    they were the one part of the old implementation that held up under test.
 * 2. **Visitor data lifetime** — a long-lived id that must be fetched once and reused, not per
 *    request.
 */
@Singleton
class StreamResolver @Inject constructor(
    private val cascadeResolver: StreamCascadeResolver,
    private val poTokenGenerator: PoTokenGenerator,
    private val visitorDataProvider: VisitorDataProvider,
    private val gaanaSource: GaanaRemoteDataSource,
) {

    /**
     * Warms the pieces that are expensive on first use: the visitor id, and the BotGuard WebView.
     *
     * Both are safe to call repeatedly and both are best-effort. A failure here degrades which
     * clients can serve a stream; it must not prevent the app from starting.
     */
    suspend fun init() {
        runCatching { visitorDataProvider.visitorData() }
            .onFailure { Log.w(TAG, "visitorData prefetch failed: ${it.message}") }

        // Warms the WebView in the background so the first play does not pay the cold-start cost.
        poTokenGenerator.initialize()
    }

    /** True when the BotGuard asset needed for Proof-of-Origin tokens is present. */
    fun isPoTokenAvailable(): Boolean = poTokenGenerator.isAvailable()

    /**
     * Resolves [videoId] to a directly playable URL.
     *
     * Throws [StreamUnavailableException] when the whole cascade fails. That exception carries a
     * per-client attempt record, so a caller reporting the failure can say *why* rather than
     * just *that* it failed.
     */
    suspend fun resolveStreamUrl(
        videoId: String,
        songTitle: String = "",
        artistName: String = "",
        maxBitrateKbps: Int? = null,
    ): StreamData {
        // A value that is already a URL needs no resolving. Persisted stream URLs are stored in
        // this column, so this branch is hit on every replay of a previously-resolved track.
        if (videoId.startsWith("http://") || videoId.startsWith("https://")) {
            if (isDirectlyPlayable(videoId)) {
                return StreamData(url = videoId, headers = MEDIA_HEADERS)
            }
            // Expired: fall through only if we have an id to re-resolve with, which we do not
            // when the value is a bare URL. Surfacing it as unavailable is correct here.
            throw StreamUnavailableException(
                videoId = videoId,
                attempts = emptyList(),
                hint = "The stored stream URL has expired and no video id is available to " +
                    "re-resolve it. The track needs to be looked up again.",
            )
        }

        // A Gaana id is not a YouTube video id, so it must never reach the cascade: asking YouTube
        // about a `seokey` produces a misleading "no playable stream" that blames the track rather
        // than the routing. Gaana mints and signs its own URL, so it resolves through its own path.
        ContentSource.gaanaTrackSeokeyOrNull(videoId)?.let { seokey ->
            return gaanaSource.resolveStream(seokey, maxBitrateKbps)
                ?: throw StreamUnavailableException(
                    videoId = videoId,
                    attempts = emptyList(),
                    hint = "Gaana has no playable stream for '$seokey'. Either the track is not " +
                        "streamable, or the Gaana source is not configured.",
                )
        }

        return resolveWithRetry(videoId, songTitle, artistName, maxBitrateKbps)
    }

    /**
     * Walks the cascade, retrying the whole chain when it fails for a *transient* reason.
     *
     * A screen-off transition (or a brief network handover) can stall DNS just long enough for
     * every client in the chain to die on a transport error, which used to surface immediately as
     * "No playable stream … UnknownHostException". Those failures are environmental and usually
     * clear within a second, so the chain is worth re-running rather than reported as a dead track.
     *
     * Only retries transient failures. A definitive refusal — `UNPLAYABLE`, `LOGIN_REQUIRED`,
     * `NO_FORMATS`, `TOKEN_MISSING` — is a stable answer about the track or the build, and
     * hammering it three times would just delay the honest error and waste requests.
     *
     * Backoff is linear and short (the first retry is the one that matters), and the whole thing is
     * bounded so a genuinely offline device fails fast instead of hanging the player.
     */
    private suspend fun resolveWithRetry(
        videoId: String,
        songTitle: String,
        artistName: String,
        maxBitrateKbps: Int? = null,
    ): StreamData {
        val visitorData = runCatching { visitorDataProvider.visitorData() }.getOrNull()
        val dataSyncId = visitorDataProvider.dataSyncId()
        val isLoggedIn = dataSyncId != null

        var lastFailure: StreamUnavailableException? = null

        for (attempt in 1..MAX_RESOLVE_ATTEMPTS) {
            try {
                val resolution = cascadeResolver.resolve(
                    videoId = videoId,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    isLoggedIn = isLoggedIn,
                    maxBitrateKbps = maxBitrateKbps,
                )

                Log.d(
                    TAG,
                    "Resolved $videoId via ${resolution.client.label} " +
                        "(expires=${resolution.expiresInSeconds}s, pot=${resolution.hasPoToken}" +
                        if (attempt > 1) ", attempt=$attempt" else "" + ")",
                )

                return StreamData(
                    url = resolution.url,
                    headers = MEDIA_HEADERS,
                    expiresInSeconds = resolution.expiresInSeconds,
                    sourceClient = resolution.client.label,
                )
            } catch (e: StreamUnavailableException) {
                lastFailure = e
                if (!e.isTransient() || attempt == MAX_RESOLVE_ATTEMPTS) throw e
                Log.w(
                    TAG,
                    "Attempt $attempt/$MAX_RESOLVE_ATTEMPTS for $videoId failed transiently " +
                        "(${e.attempts.joinToString()}); retrying in ${RETRY_DELAY_MS * attempt}ms",
                )
                delay(RETRY_DELAY_MS * attempt)
            } catch (e: IOException) {
                // A DNS/socket failure can escape the cascade without being wrapped, because the
                // very first request never reaches the point where a client attempt is recorded.
                if (attempt == MAX_RESOLVE_ATTEMPTS) {
                    throw StreamUnavailableException(
                        videoId = videoId,
                        attempts = emptyList(),
                        hint = "Network error while resolving: ${e.message ?: e.javaClass.simpleName}",
                    )
                }
                Log.w(
                    TAG,
                    "Attempt $attempt/$MAX_RESOLVE_ATTEMPTS for $videoId hit a network error " +
                        "(${e.message}); retrying in ${RETRY_DELAY_MS * attempt}ms",
                )
                delay(RETRY_DELAY_MS * attempt)
            }
        }

        // Unreachable: the loop either returns or throws on its final iteration.
        throw lastFailure
            ?: StreamUnavailableException(videoId, emptyList(), hint = "Resolution produced no result.")
    }

    /**
     * True when the failure is environmental rather than a statement about the track.
     *
     * A transport/decode error on any client means the request itself did not complete — the
     * signature of a stalled or dropped network — so the chain is worth re-running.
     */
    private fun StreamUnavailableException.isTransient(): Boolean =
        attempts.any { it.outcome == ClientAttempt.Outcome.ERROR }

    companion object {
        private const val TAG = "CRANK_STREAM"

        /**
         * How many times the whole cascade is walked before giving up.
         *
         * Three is deliberate: the first retry absorbs the common screen-off / handover stall, the
         * second covers a slower reconnection, and beyond that the device is genuinely offline and
         * the user should see the error rather than wait.
         */
        private const val MAX_RESOLVE_ATTEMPTS = 3

        /** Base backoff between attempts. Linear (×attempt), so 400ms then 800ms. */
        private const val RETRY_DELAY_MS = 400L

        /**
         * Headers sent when fetching media from the CDN.
         *
         * These are deliberately minimal. A signed googlevideo URL carries its own
         * authorization, and adding the client's own user agent or extra query parameters was
         * measured to change nothing — including for URLs that 403 for reasons unrelated to
         * headers. What matters is that the request looks like a media fetch rather than a
         * browser navigation.
         */
        private val MEDIA_HEADERS =
            mapOf(
                "User-Agent" to
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/",
            )

        /**
         * Hosts that serve signed, expiring stream URLs.
         *
         * Two sources sign differently and both must be listed here, because [isRottingUrl] is the
         * gate deciding whether [isExpired] is consulted **at all** — a host missing from this list
         * is trusted unconditionally.
         *
         *  - YouTube's CDN signs with `?expire=<unixSeconds>`.
         *  - Gaana's CDN is Akamai, signing with `?hdnts=st=..~exp=..~acl=..~hmac=..`; the token
         *    lifetime was measured at exactly 14400s (4 hours).
         *
         * Gaana's host was absent, so a URL that stopped working four hours after it was minted was
         * still classified as directly playable days later and handed straight to ExoPlayer.
         */
        private val SIGNING_MEDIA_HOSTS =
            listOf(
                "googlevideo.com",
                "youtube.com",
                "ytimg.com",
                "akamaized.net",
            )

        /**
         * `exp=<seconds>` inside an Akamai `hdnts` token.
         *
         * Anchored on `^` or `~` so the `st=` start value, or any other field whose name merely
         * ends in `exp`, cannot be mistaken for the expiry — `sn-abc~explist=5` must not match.
         */
        private val AKAMAI_EXP = Regex("(?:^|~)exp=(\\d+)")

        /**
         * True when [url] carries its own expiry and that moment has passed.
         *
         * A CDN hands out signed URLs with a deadline, and a URL past that instant is answered
         * with HTTP 403 — permanently, no matter how many times it is retried. Detecting it up
         * front turns an opaque player error into a re-resolve.
         *
         * URLs with no recognisable expiry (plain file URLs, stable sources) are treated as
         * usable; there are no grounds to reject them here.
         */
        fun isExpired(url: String, nowSeconds: Long = System.currentTimeMillis() / 1000L): Boolean {
            if (!url.startsWith("http")) return false

            val expiry = expirySecondsOf(url) ?: return false

            // A little slack so a URL about to lapse mid-handshake is treated as already gone
            // rather than starting a request it will lose.
            return expiry <= nowSeconds + 30L
        }

        /**
         * The instant [url] stops working, in unix seconds, or `null` when it carries no
         * recognisable deadline.
         *
         * Two shapes are understood, because the two sources sign differently:
         *
         *  - YouTube — `?expire=1730000000`
         *  - Akamai / Gaana — `?hdnts=st=1730000000~exp=1730014400~acl=..~hmac=..`
         *
         * Public so a source that mints its own URLs can report `StreamData.expiresInSeconds`
         * without re-implementing the parsing — two parsers is how the YouTube-only assumption
         * that caused the original expiry bug would creep back in.
         */
        fun expirySecondsOf(url: String): Long? {
            extractQueryParam(url, "expire")?.toLongOrNull()?.let { return it }

            val hdnts = extractQueryParam(url, "hdnts") ?: return null
            return AKAMAI_EXP.find(hdnts)?.groupValues?.get(1)?.toLongOrNull()
        }

        /**
         * True when [url] points at a source whose signed URLs rot and therefore cannot be
         * trusted across app restarts.
         *
         * Only YouTube media is in scope. A URL pointing anywhere else — a podcast feed, a local
         * file — is left alone, because re-resolving those would be wrong.
         *
         * The host check is anchored on a dot boundary, so a look-alike such as
         * `notgooglevideo.com` is not misclassified as a YouTube host.
         */
        fun isRottingUrl(url: String): Boolean {
            if (!url.startsWith("http")) return false
            val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
            return SIGNING_MEDIA_HOSTS.any { domain -> host == domain || host.endsWith(".$domain") }
        }

        /**
         * True when [streamUrl] can be handed to the player as-is.
         *
         * A blank value, a non-URL identifier (a bare video id needs resolving), or a YouTube URL
         * that has expired all mean "resolve this again".
         *
         * [nowSeconds] exists so callers — and tests — can supply their own clock.
         */
        fun isDirectlyPlayable(
            streamUrl: String,
            nowSeconds: Long = System.currentTimeMillis() / 1000L,
        ): Boolean {
            if (!streamUrl.startsWith("http")) return false
            if (!isRottingUrl(streamUrl)) return true
            return !isExpired(streamUrl, nowSeconds)
        }

        private fun extractQueryParam(url: String, name: String): String? {
            val query = url.substringAfter('?', "").substringBefore('#')
            if (query.isBlank()) return null
            return query.split("&").firstNotNullOfOrNull { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0 && pair.substring(0, idx) == name) pair.substring(idx + 1) else null
            }
        }
    }
}

/**
 * Supplies the visitor id and account id the cascade needs.
 *
 * An interface rather than a direct dependency so that the cascade can be exercised in tests
 * without a network, and so the storage choice (DataStore today) is not baked into playback.
 */
interface VisitorDataProvider {
    /**
     * The visitor data id.
     *
     * Required: the clients that can serve a whole file answer `LOGIN_REQUIRED` with zero
     * formats when this is absent. Fetched once and cached; it is long-lived.
     */
    suspend fun visitorData(): String?

    /**
     * The account-scoped data-sync id, or `null` when signed out.
     *
     * When present it binds Proof-of-Origin tokens to the account rather than to the visitor,
     * and enables the login-gated clients.
     */
    suspend fun dataSyncId(): String?
}
