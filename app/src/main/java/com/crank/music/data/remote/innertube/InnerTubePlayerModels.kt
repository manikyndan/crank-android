package com.crank.music.data.remote.innertube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request and response shapes for the InnerTube `/player` endpoint.
 *
 * ## Why the response is modelled rather than read as raw JSON
 *
 * The playback cascade needs to make decisions from the response: which formats exist, whether
 * the URL is ciphered, how long the stream stays valid, and what the playability status says
 * when it refuses. Reading those out of an untyped `JsonObject` scattered the key paths across
 * the resolver and made a renamed field silently read as "absent" rather than as a parse error.
 *
 * Fields are optional throughout. YouTube omits `streamingData` entirely when it refuses, and
 * omits individual format fields inconsistently between clients — a required field here would
 * turn a refusal into a crash, which is the opposite of what we want.
 */

/** The `context` block sent with every request. */
@Serializable
data class InnerTubeContext(
    val client: InnerTubeClientContext,
    val user: InnerTubeUserContext? = null,
) {
    @Serializable
    data class InnerTubeClientContext(
        val clientName: String,
        val clientVersion: String,
        val hl: String = "en",
        val gl: String = "US",
        val visitorData: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: String? = null,
        val buildId: String? = null,
        val cronetVersion: String? = null,
    )

    @Serializable
    data class InnerTubeUserContext(
        @SerialName("onBehalfOfUser") val onBehalfOfUser: String? = null,
    )
}

/** The `/player` request body. */
@Serializable
data class InnerTubePlayerRequest(
    val context: InnerTubeContext,
    val videoId: String,
    val playlistId: String? = null,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
) {
    /**
     * Carries the signature timestamp. Only sent for clients that declare
     * `useSignatureTimestamp` — sending it to a client that does not expect it is at best
     * ignored and at worst a rejection.
     */
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext,
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: Int,
        )
    }

    /**
     * Carries the Proof-of-Origin token.
     *
     * This is the field that makes a `/player` request acceptable to YouTube's anti-bot system.
     * Omitting it is exactly why every CRANK strategy previously returned `UNPLAYABLE`: the
     * response body said so, and no amount of retrying or switching mirrors changed it.
     */
    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String,
    )
}

/** The `/player` response. */
@Serializable
data class InnerTubePlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    val playerConfig: PlayerConfig? = null,
    val playbackTracking: PlaybackTracking? = null,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
        /**
         * Present on refusals. Carries the machine-readable sub-reason, which is often more
         * specific than [reason] and is what distinguishes "needs a token" from "video is
         * genuinely gone" without guessing from prose.
         */
        val errorScreen: JsonObject? = null,
    ) {
        /** True when this response is usable. Anything else is a refusal of some kind. */
        val isOk: Boolean get() = status == "OK"

        /** Statuses that mean "sign in and try again" rather than "this cannot be played". */
        val isLoginOrAgeGated: Boolean
            get() = status in
                setOf(
                    "AGE_CHECK_REQUIRED",
                    "AGE_VERIFICATION_REQUIRED",
                    "LOGIN_REQUIRED",
                    "CONTENT_CHECK_REQUIRED",
                )
    }

    @Serializable
    data class StreamingData(
        val expiresInSeconds: Int? = null,
        val adaptiveFormats: List<Format> = emptyList(),
        val formats: List<Format> = emptyList(),
        val hlsManifestUrl: String? = null,
    ) {
        /**
         * Every format from both lists.
         *
         * `adaptiveFormats` holds audio-only streams and is what CRANK wants; `formats` holds
         * the combined muxed streams, which exist but waste bandwidth for an audio app. Both
         * are retained so a muxed stream can still be used as a fallback when no audio-only
         * format is offered.
         */
        val allFormats: List<Format> get() = adaptiveFormats + formats
    }

    /**
     * One media stream.
     *
     * Note the deliberate absence of an `itag`-only identity assumption: the same `itag` can
     * appear with different `mimeType`/`bitrate`, so selection is done on the full tuple.
     */
    @Serializable
    data class Format(
        val itag: Int? = null,
        val url: String? = null,
        /**
         * Present instead of [url] when the stream is behind YouTube's signature cipher. The
         * `s` parameter inside must be run through the player's decipher routine before the URL
         * is usable — see `decodeSignatureCipher` in the resolver.
         */
        val signatureCipher: String? = null,
        val cipher: String? = null,
        val mimeType: String? = null,
        val bitrate: Int? = null,
        val averageBitrate: Int? = null,
        val contentLength: String? = null,
        val audioQuality: String? = null,
        val audioSampleRate: String? = null,
        val audioChannels: Int? = null,
        val loudnessDb: Double? = null,
    ) {
        /**
         * True when this format carries audio.
         *
         * A muxed stream (`video/mp4` with audio) counts as audio-bearing, but an audio-only
         * stream is preferred. Checking `mimeType` rather than trusting `itag` avoids encoding
         * a table of magic numbers that YouTube changes.
         */
        val hasAudio: Boolean
            get() = mimeType?.let { it.startsWith("audio/") || it.contains("mp4a") || it.contains("opus") }
                ?: false

        val isAudioOnly: Boolean get() = mimeType?.startsWith("audio/") ?: false

        /** True when a URL must be deciphered before use. */
        val isCiphered: Boolean get() = !signatureCipher.isNullOrBlank() || !cipher.isNullOrBlank()

        /** The cipher blob, whichever field carried it. */
        val cipherBlob: String? get() = signatureCipher?.takeIf { it.isNotBlank() } ?: cipher
    }

    @Serializable
    data class VideoDetails(
        val videoId: String? = null,
        val title: String? = null,
        val author: String? = null,
        val lengthSeconds: String? = null,
        /**
         * Distinguishes an uploaded personal track from a catalogue track. Uploaded tracks are
         * only served by TVHTML5, which is why the cascade reads this field.
         */
        val musicVideoType: String? = null,
    ) {
        val isPrivatelyOwned: Boolean
            get() = musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
    }

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig? = null,
    ) {
        @Serializable
        data class AudioConfig(
            val loudnessDb: Double? = null,
            val perceptualLoudnessDb: Double? = null,
        )
    }

    @Serializable
    data class PlaybackTracking(
        val videostatsPlaybackUrl: TrackingUrl? = null,
        val videostatsWatchtimeUrl: TrackingUrl? = null,
    ) {
        @Serializable
        data class TrackingUrl(val baseUrl: String? = null)
    }
}
