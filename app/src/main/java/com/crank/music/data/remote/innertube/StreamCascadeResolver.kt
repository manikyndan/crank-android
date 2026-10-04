package com.crank.music.data.remote.innertube

import android.net.Uri
import android.util.Log
import com.crank.music.data.remote.potoken.PoTokenGenerator
import com.crank.music.data.remote.potoken.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager

/**
 * Walks the client cascade until one produces a playable stream URL.
 *
 * Adapted from the Echo Music project (GPL-3.0).
 *
 * ## What this replaces
 *
 * CRANK's previous resolver tried eight strategies in a fixed order with no shared state and no
 * record of what happened. Every strategy independently re-derived a URL, every failure was
 * logged as its own line, and the terminal exception said only that nothing worked. The result
 * was that a total failure and a near-miss looked identical, and the actual cause — a missing
 * Proof-of-Origin token — was invisible.
 *
 * ## The shape of the fix
 *
 * 1. A PoToken is minted once, up front, and threaded through every client that can use one.
 * 2. Clients are tried in a measured order (see [YouTubeClients.fallbackChain]).
 * 3. Each attempt is recorded as a [ClientAttempt], so a failure is self-describing.
 * 4. The `n` transform and `pot=` append happen in one place, applied consistently.
 */
class StreamCascadeResolver(
    private val playerApi: InnerTubePlayerApi,
    private val poTokenGenerator: PoTokenGenerator,
) {

    /**
     * Resolves a playable stream URL for [videoId].
     *
     * @param isLoggedIn whether a session cookie is available. Determines whether login-gated
     *   clients are attempted, and whether the token is bound to the account or to the visitor.
     * @param visitorData required by the clients that can serve whole files. Without it they
     *   answer `LOGIN_REQUIRED` with zero formats.
     * @param dataSyncId the account-scoped id, used when signed in.
     * @param playlistId used to detect uploaded tracks, which only TVHTML5 will serve.
     * @param maxBitrateKbps the user's quality ceiling for this request, or null for "best
     *   available". Passed through to [selectAudioFormat].
     */
    suspend fun resolve(
        videoId: String,
        visitorData: String?,
        dataSyncId: String?,
        isLoggedIn: Boolean,
        playlistId: String? = null,
        maxBitrateKbps: Int? = null,
    ): StreamResolution {
        val attempts = mutableListOf<ClientAttempt>()

        // An uploaded track is identified by its playlist, and only TVHTML5 serves these.
        val isUploadedTrack = playlistId?.contains("MLPT") == true

        // Log the phases as they *start*, not only the cascade's final verdict.
        //
        // A device run showed `getSongStreamUrl` printing its request line and then 90+ seconds of
        // total silence: no cascade line, no HTTP status, no exception. Nothing in this class could
        // say which phase was stalled, because the only log line was `formatCascade(...)` at the
        // very end. Any early return, slow metadata call, or hang before the loop was invisible.
        // Phases are now announced on entry so a stall is localised to one of them.
        Log.i(TAG, "resolve start: $videoId uploaded=$isUploadedTrack visitorData=${visitorData != null} loggedIn=$isLoggedIn")

        val poToken = mintPoToken(videoId, visitorData, dataSyncId, isLoggedIn)
        Log.i(TAG, "resolve: poToken ${if (poToken == null) "unavailable" else "minted"}")

        // Metadata always comes from the main client, regardless of which client supplies audio.
        // A failure here is fatal: without videoDetails we cannot tell an uploaded track from a
        // catalogue one, and without a playability status there is nothing to report.
        Log.i(TAG, "resolve: requesting metadata via ${YouTubeClients.MAIN_CLIENT.clientName}")
        val mainResponse =
            runCatching {
                playerApi.player(
                    videoId = videoId,
                    ytClient = YouTubeClients.MAIN_CLIENT,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    signatureTimestamp = null,
                    poToken = poToken?.playerRequestPoToken,
                    playlistId = playlistId,
                )
            }.getOrElse { e ->
                attempts += ClientAttempt(
                    YouTubeClients.MAIN_CLIENT,
                    ClientAttempt.Outcome.ERROR,
                    e.javaClass.simpleName,
                )
                // Name the cause in logcat as well as in the exception. This path is the only one
                // that fails before the client loop, so without a log line here a metadata failure
                // and a genuinely hung request are indistinguishable on a device.
                Log.e(TAG, "resolve: metadata request failed for $videoId: ${e.javaClass.name}: ${e.message}", e)
                throw StreamUnavailableException(
                    videoId = videoId,
                    attempts = attempts,
                    hint = "The metadata request failed before any stream could be attempted: " +
                        "${e.javaClass.simpleName}: ${e.message}",
                )
            }

        val videoDetails = mainResponse.videoDetails
        val isPrivateTrack = videoDetails?.isPrivatelyOwned == true
        Log.i(TAG, "resolve: metadata ok, playability=${mainResponse.playabilityStatus?.status ?: "none"} private=$isPrivateTrack")

        val startIndex =
            when {
                isUploadedTrack || isPrivateTrack -> YouTubeClients.PRIVATE_TRACK_STREAM_START_INDEX
                else -> YouTubeClients.NORMAL_CONTENT_STREAM_START_INDEX
            }

        for (index in startIndex until YouTubeClients.fallbackChain.size) {
            val client = YouTubeClients.fallbackChain[index]

            // Skip clients that cannot work in this session, without spending a request.
            if (client.loginRequired && !isLoggedIn) {
                attempts += ClientAttempt(
                    client,
                    ClientAttempt.Outcome.SKIPPED_LOGIN_REQUIRED,
                    "not signed in",
                )
                continue
            }
            if (client.useWebPoTokens && poToken == null) {
                // Measured behaviour: a token-gated client without a token returns formats that
                // 403 on the first byte. Trying it anyway costs a round trip and produces a
                // misleading "stream found but broken" result.
                attempts += ClientAttempt(
                    client,
                    ClientAttempt.Outcome.TOKEN_MISSING,
                    "no poToken",
                )
                continue
            }

            Log.i(TAG, "resolve: trying ${client.clientName} ($index/${YouTubeClients.fallbackChain.size - 1})")

            val outcome = tryClient(
                videoId = videoId,
                client = client,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
                poToken = poToken,
                playlistId = playlistId,
                maxBitrateKbps = maxBitrateKbps,
            )
            Log.i(TAG, "resolve: ${client.clientName} -> ${outcome::class.simpleName}")

            when (outcome) {
                is ClientOutcome.Success -> {
                    attempts += ClientAttempt(
                        client,
                        ClientAttempt.Outcome.ACCEPTED,
                        "${outcome.expiresInSeconds}s, pot=${outcome.hasPoToken}",
                    )
                    Log.i(TAG, formatCascade(videoId, attempts))
                    return StreamResolution(
                        url = outcome.url,
                        client = client,
                        format = outcome.format,
                        expiresInSeconds = outcome.expiresInSeconds,
                        hasPoToken = outcome.hasPoToken,
                        attempts = attempts,
                    )
                }

                is ClientOutcome.Failure -> {
                    attempts += ClientAttempt(client, outcome.outcome, outcome.detail)
                }
            }
        }

        Log.e(TAG, formatCascade(videoId, attempts))
        throw StreamUnavailableException(
            videoId = videoId,
            attempts = attempts,
            hint = diagnoseFailure(attempts),
        )
    }

    /**
     * Mints a token pair, or returns `null` when none is available.
     *
     * `null` is a normal outcome: it degrades which clients can be used, but a non-token client
     * may still succeed. It must never fail the resolve outright.
     */
    private suspend fun mintPoToken(
        videoId: String,
        visitorData: String?,
        dataSyncId: String?,
        isLoggedIn: Boolean,
    ): PoTokenResult? {
        if (!poTokenGenerator.isAvailable()) return null

        // When signed in the account-scoped id binds the token; otherwise the visitor id does.
        // An empty binding produces a token that can never validate, so it is rejected here.
        val sessionId =
            when {
                isLoggedIn && !dataSyncId.isNullOrEmpty() -> dataSyncId
                !visitorData.isNullOrEmpty() -> visitorData
                else -> null
            } ?: return null

        // The generator bounds itself and converts its own failures to `null`, so there is no
        // `runCatching` here. Swallowing exceptions at this level too would hide the distinction
        // between "no token available" and "the mint threw", which is exactly the ambiguity that
        // took a device ANR to uncover.
        return poTokenGenerator.getWebClientPoToken(videoId, sessionId)
    }

    /** The result of trying one client. */
    private sealed interface ClientOutcome {
        data class Success(
            val url: String,
            val format: InnerTubePlayerResponse.Format,
            val expiresInSeconds: Int?,
            val hasPoToken: Boolean,
        ) : ClientOutcome

        data class Failure(
            val outcome: ClientAttempt.Outcome,
            val detail: String?,
        ) : ClientOutcome
    }

    /**
     * Fetches and validates a stream from one client.
     *
     * Validation matters and is not optional: a stream URL can be returned successfully and
     * still be refused by the CDN. The preview-length clients do exactly that — they hand back a
     * URL that works for the first megabyte and 403s afterwards — so accepting a URL without
     * checking it produces playback that dies roughly a minute in with no explanation.
     */
    private suspend fun tryClient(
        videoId: String,
        client: YouTubeClient,
        visitorData: String?,
        dataSyncId: String?,
        poToken: PoTokenResult?,
        playlistId: String?,
        maxBitrateKbps: Int? = null,
    ): ClientOutcome {
        val response =
            try {
                playerApi.player(
                    videoId = videoId,
                    ytClient = client,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    // Sent only for clients that declare it; the metadata response supplies it
                    // for the main client.
                    signatureTimestamp = null,
                    poToken = poToken?.playerRequestPoToken,
                    playlistId = playlistId,
                )
            } catch (e: Exception) {
                return ClientOutcome.Failure(ClientAttempt.Outcome.ERROR, e.javaClass.simpleName)
            }

        val status = response.playabilityStatus
        if (status?.isOk != true) {
            val outcome =
                when {
                    status?.isLoginOrAgeGated == true && status.status == "LOGIN_REQUIRED" ->
                        ClientAttempt.Outcome.LOGIN_REQUIRED
                    status?.isLoginOrAgeGated == true -> ClientAttempt.Outcome.AGE_RESTRICTED
                    else -> ClientAttempt.Outcome.UNPLAYABLE
                }
            return ClientOutcome.Failure(outcome, status?.status)
        }

        val streamingData = response.streamingData
        if (streamingData == null) {
            return ClientOutcome.Failure(ClientAttempt.Outcome.UNPLAYABLE, "no streamingData")
        }

        val expiresIn = streamingData.expiresInSeconds
        if (expiresIn == null) {
            // Without an expiry the URL's lifetime is unknown, so it cannot be safely cached or
            // persisted. Treated as a refusal rather than accepted blindly.
            return ClientOutcome.Failure(ClientAttempt.Outcome.NO_EXPIRY, null)
        }

        val format = selectAudioFormat(streamingData.allFormats, maxBitrateKbps)
            ?: return ClientOutcome.Failure(ClientAttempt.Outcome.NO_FORMATS, null)

        val rawUrl =
            resolveFormatUrl(format, videoId, client)
                ?: return ClientOutcome.Failure(
                    ClientAttempt.Outcome.INVALID_URL,
                    "no url or cipher",
                )

        // The `n` transform and `pot=` append apply to the same set of clients, so they are
        // decided once. See [needsNTransform].
        val needsNTransform = needsNTransform(client, response.videoDetails?.isPrivatelyOwned == true)
        val token = poToken?.streamingDataPoToken

        var finalUrl = rawUrl
        if (needsNTransform) {
            finalUrl = transformNParam(finalUrl, videoId)
            if (client.useWebPoTokens && token != null) {
                finalUrl = appendPoToken(finalUrl, token)
            }
        }

        return ClientOutcome.Success(
            url = finalUrl,
            format = format,
            expiresInSeconds = expiresIn,
            hasPoToken = needsNTransform && client.useWebPoTokens && token != null,
        )
    }

    /**
     * Picks the best audio format from the available streams.
     *
     * Prefers audio-only. A muxed format is accepted only as a fallback, because it carries
     * video that an audio app will never display but will still download.
     *
     * @param maxBitrateKbps the user's effective quality ceiling, from the audio-quality setting and
     *   the active network. When set, the highest-bitrate format *at or below* it wins. Previously
     *   this parameter did not exist and the highest bitrate always won, so choosing "Low" or
     *   enabling Data Saver changed nothing about what was actually downloaded.
     *
     *   The ceiling is a preference, not a hard filter: if nothing is available under it (formats
     *   frequently report 0 or omit the bitrate entirely) the highest available is used instead of
     *   failing the play. Silence about the real bitrate is better than a track that will not play.
     *
     * Among equally-scored candidates the highest bitrate wins, with `averageBitrate` as a
     * tie-break — `bitrate` alone is frequently absent or identical across formats.
     */
    private fun selectAudioFormat(
        formats: List<InnerTubePlayerResponse.Format>,
        maxBitrateKbps: Int? = null,
    ): InnerTubePlayerResponse.Format? {
        val audioOnly = formats.filter { it.isAudioOnly }
        val candidates = audioOnly.ifEmpty { formats.filter { it.hasAudio } }

        fun bitrateOf(format: InnerTubePlayerResponse.Format): Long =
            (format.bitrate ?: format.averageBitrate ?: 0).toLong()

        // Formats without a usable URL are ranked last rather than filtered out, so that a
        // client offering only ciphered formats still gets a chance to decipher them.
        fun rank(format: InnerTubePlayerResponse.Format): Long =
            if (format.url == null && !format.isCiphered) -1L else bitrateOf(format)

        val underCeiling = maxBitrateKbps
            ?.let { ceiling -> candidates.filter { rank(it) in 1..(ceiling * 1000L) } }
            ?.takeIf { it.isNotEmpty() }

        return (underCeiling ?: candidates)
            .maxByOrNull { rank(it) }
            ?.takeIf { it.url != null || it.isCiphered }
    }

    /**
     * Produces a usable URL for a format.
     *
     * A format either carries a direct [InnerTubePlayerResponse.Format.url] or is wrapped in a
     * signature cipher whose `s` parameter must be run through the player's decipher routine.
     * Both paths are real and both appear in practice; the cipher path is the one that is
     * untestable without a live response, which is why the decipher call is isolated here.
     */
    private fun resolveFormatUrl(
        format: InnerTubePlayerResponse.Format,
        videoId: String,
        client: YouTubeClient,
    ): String? {
        format.url?.takeIf { it.isNotBlank() }?.let { return it }

        val cipherBlob = format.cipherBlob ?: return null
        return decodeSignatureCipher(cipherBlob, videoId, client)
    }

    /**
     * Decodes a `signatureCipher` blob into a playable URL.
     *
     * The blob is URL-encoded key/value pairs: `s` holds the obfuscated signature, `url` the
     * stream URL without one. The signature must be run through the player script's decipher
     * function, which NewPipe extracts and evaluates for us.
     *
     * Returns `null` when the blob is malformed or deciphering fails, so the caller can move to
     * the next client rather than propagating a malformed URL to the player.
     */
    private fun decodeSignatureCipher(
        cipherBlob: String,
        videoId: String,
        client: YouTubeClient,
    ): String? {
        return runCatching {
            val params =
                cipherBlob.split("&").mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
                }.toMap()

            val scrambledSig = params["s"] ?: return@runCatching null
            val baseUrl = params["url"] ?: return@runCatching null
            val signatureParam = params["sp"] ?: "signature"

            val deobfuscated =
                YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, scrambledSig)
                    ?: return@runCatching null

            val separator = if (baseUrl.contains('?')) "&" else "?"
            "$baseUrl$separator$signatureParam=${Uri.encode(deobfuscated)}"
        }.getOrElse { e ->
            Log.w(
                TAG,
                "Cipher decode failed for $videoId via ${client.label}: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
            null
        }
    }

    /**
     * Whether a client's URL needs the `n` transform applied.
     *
     * Web-based clients and privately-owned tracks are throttled through the `n` parameter;
     * leaving it untransformed yields a URL that is valid but bandwidth-limited to the point of
     * being unusable. Mobile clients do not use it.
     */
    private fun needsNTransform(client: YouTubeClient, isPrivatelyOwned: Boolean): Boolean =
        client.useWebPoTokens ||
            client.clientName in WEB_LIKE_CLIENTS ||
            isPrivatelyOwned

    /**
     * Applies the `n` parameter transform, returning the input unchanged on failure.
     *
     * The video id is required as well as the URL: NewPipe's implementation extracts and caches
     * the throttling-deobfuscation function from that video's player script, so it cannot work
     * from the URL alone.
     *
     * Failing soft is deliberate: an untransformed URL plays, just slowly. Returning `null` here
     * would reject a stream that is genuinely usable.
     */
    private fun transformNParam(url: String, videoId: String): String {
        return runCatching {
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
                ?: url
        }.getOrElse { e ->
            Log.w(TAG, "n-transform failed, using untransformed URL: ${e.message}")
            url
        }
    }

    /**
     * Appends the streaming Proof-of-Origin token as `pot=`.
     *
     * Without this the CDN answers 403 on the very first byte for token-gated clients, even
     * when the URL's signature and `n` parameter are both correct.
     */
    private fun appendPoToken(url: String, token: String): String {
        val separator = if (url.contains('?')) "&" else "?"
        return "$url$separator" + "pot=" + Uri.encode(token)
    }

    private companion object {
        private const val TAG = "CRANK_CASCADE"

        /** Clients whose stream URLs are subject to the `n` throttling transform. */
        private val WEB_LIKE_CLIENTS = setOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")
    }
}
