package com.crank.music.data.remote.innertube

import kotlinx.serialization.Serializable

/**
 * One InnerTube client identity.
 *
 * ## Why this is a data class and not a string
 *
 * CRANK previously hardcoded `clientName = "WEB_REMIX"` with a single version string in one
 * place, which meant every request went out as the same identity and there was no way to
 * express "try this client, and if YouTube refuses it, try that one". Playing a track
 * reliably *requires* trying several identities in a measured order, because which clients
 * YouTube will serve changes over time and without warning — see [YouTubeClients.fallbackChain].
 *
 * ## Field provenance
 *
 * The values here are not invented. Each one is what the live YouTube client for that
 * platform actually sends, and they matter: a `clientVersion` that is too old causes
 * `LOGIN_REQUIRED` / "Sign in to confirm you're not a bot" while the *identical* request with
 * a current version returns OK. That is a server-side version gate, not a malformed request,
 * so there is no way to detect it other than by sending a version YouTube currently accepts.
 *
 * @property clientId the numeric id sent as the `X-YouTube-Client-Name` **header**. Note that
 *   the header name says "Name" but its value is the client *number*. This is not a typo in
 *   this code; it is genuinely what the header carries.
 * @property useWebPoTokens whether this client's player response is gated behind a Proof-of-
 *   Origin token. When true, the request must carry `serviceIntegrityDimensions.poToken` and
 *   the resulting stream URL must carry `pot=` or YouTube answers 403 on the first byte.
 * @property loginRequired this client only returns formats for a signed-in session. Anonymous
 *   use is skipped outright rather than wasting a round trip.
 */
@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val packageName: String? = null,
    val friendlyName: String? = null,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val isEmbedded: Boolean = false,
    val useWebPoTokens: Boolean = false,
) {
    /** Human-readable label for logs, falling back to the wire name. */
    val label: String get() = friendlyName ?: clientName

    companion object {
        const val USER_AGENT_WEB =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/"
        const val API_URL_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/youtubei/v1/"
    }
}
