package com.crank.music.data.remote.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the cascade's failure reporting and format-shape helpers.
 *
 * These are the parts of the cascade that do not need a network. They exist because the
 * *reporting* of a cascade failure is what makes an on-device failure actionable: a resolver
 * that says only "no stream found" costs a whole debugging session, whereas one that says which
 * clients were tried and why each was dropped usually identifies the cause on first read.
 */
class StreamResolutionTest {

    private fun attempt(
        client: YouTubeClient,
        outcome: ClientAttempt.Outcome,
        detail: String? = null,
    ) = ClientAttempt(client, outcome, detail)

    // region Attempt formatting

    @Test
    fun `attempt renders client and outcome`() {
        val attempt = attempt(
            YouTubeClients.VISIONOS,
            ClientAttempt.Outcome.UNPLAYABLE,
        )
        assertEquals("visionOS=UNPLAYABLE", attempt.toString())
    }

    @Test
    fun `attempt includes detail when present`() {
        val attempt = attempt(
            YouTubeClients.VISIONOS,
            ClientAttempt.Outcome.UNPLAYABLE,
            "UNPLAYABLE",
        )
        assertEquals("visionOS=UNPLAYABLE(UNPLAYABLE)", attempt.toString())
    }

    @Test
    fun `cascade formats every attempt on one line`() {
        val line =
            formatCascade(
                "abc123",
                listOf(
                    attempt(YouTubeClients.VISIONOS, ClientAttempt.Outcome.NO_FORMATS),
                    attempt(
                        YouTubeClients.ANDROID_VR_1_65_10,
                        ClientAttempt.Outcome.LOGIN_REQUIRED,
                    ),
                    attempt(YouTubeClients.TVHTML5, ClientAttempt.Outcome.ACCEPTED, "3600s"),
                ),
            )

        assertTrue("must name the video", line.contains("abc123"))
        assertTrue("must report the attempt count", line.contains("tried=3"))
        assertTrue("must include each client", line.contains("visionOS"))
        assertTrue(line.contains("Android VR 1.65"))
        assertTrue("must include the winning client", line.contains("ACCEPTED"))
    }

    // endregion

    // region Failure diagnosis

    @Test
    fun `diagnosis names the missing token asset when a client was skipped for it`() {
        val hint =
            diagnoseFailure(
                listOf(
                    attempt(
                        YouTubeClients.WEB_CREATOR,
                        ClientAttempt.Outcome.TOKEN_MISSING,
                        "no poToken",
                    ),
                    attempt(YouTubeClients.VISIONOS, ClientAttempt.Outcome.NO_FORMATS),
                )
            )

        assertTrue("must mention the asset", hint?.contains("po_token.html") == true)
        assertTrue("must give the fix location", hint?.contains("app/src/main/assets/") == true)
    }

    @Test
    fun `diagnosis flags a whole-chain refusal as a likely version problem`() {
        val hint =
            diagnoseFailure(
                listOf(
                    attempt(YouTubeClients.VISIONOS, ClientAttempt.Outcome.UNPLAYABLE),
                    attempt(
                        YouTubeClients.ANDROID_VR_1_65_10,
                        ClientAttempt.Outcome.UNPLAYABLE,
                    ),
                    attempt(YouTubeClients.TVHTML5, ClientAttempt.Outcome.LOGIN_REQUIRED),
                )
            )

        assertTrue("must mention client versions", hint?.contains("client versions") == true)
    }

    @Test
    fun `diagnosis is silent for a mixed failure with no clear cause`() {
        // A transport error on one client and no formats on another does not indicate anything
        // systemic, so inventing a hint would be misleading.
        val hint =
            diagnoseFailure(
                listOf(
                    attempt(YouTubeClients.VISIONOS, ClientAttempt.Outcome.ERROR, "SocketTimeout"),
                    attempt(YouTubeClients.TVHTML5, ClientAttempt.Outcome.NO_FORMATS),
                )
            )

        assertNull(hint)
    }

    @Test
    fun `diagnosis reports when nothing was attempted at all`() {
        val hint = diagnoseFailure(emptyList())
        assertEquals("No clients were attempted.", hint)
    }

    // endregion

    // region Exception message

    @Test
    fun `exception message carries the attempt list`() {
        val attempts =
            listOf(
                attempt(YouTubeClients.VISIONOS, ClientAttempt.Outcome.NO_FORMATS),
                attempt(YouTubeClients.IPADOS, ClientAttempt.Outcome.INVALID_URL, "403"),
            )

        val exception = StreamUnavailableException("vid", attempts)

        assertTrue(exception.message?.contains("vid") == true)
        assertTrue("must count attempts", exception.message?.contains("2 client") == true)
        assertTrue("must name a client", exception.message?.contains("visionOS") == true)
    }

    @Test
    fun `exception message appends the hint when one exists`() {
        val exception =
            StreamUnavailableException(
                "vid",
                listOf(attempt(YouTubeClients.VISIONOS, ClientAttempt.Outcome.UNPLAYABLE)),
                hint = "something specific",
            )

        assertTrue(exception.message?.contains("something specific") == true)
    }

    @Test
    fun `exception exposes the attempts for callers that want to inspect them`() {
        val attempts =
            listOf(attempt(YouTubeClients.VISIONOS, ClientAttempt.Outcome.NO_FORMATS))
        val exception = StreamUnavailableException("vid", attempts)

        assertEquals(attempts, exception.attempts)
    }

    // endregion

    // region Format classification

    @Test
    fun `audio only mime is classified as audio only`() {
        val format = InnerTubePlayerResponse.Format(mimeType = "audio/webm; codecs=\"opus\"")
        assertTrue(format.isAudioOnly)
        assertTrue(format.hasAudio)
    }

    @Test
    fun `muxed mp4 with audio is audio bearing but not audio only`() {
        val format =
            InnerTubePlayerResponse.Format(mimeType = "video/mp4; codecs=\"avc1.42E01E, mp4a.40.2\"")
        assertTrue("muxed streams still carry audio", format.hasAudio)
        assertTrue("but must not outrank a dedicated audio stream", !format.isAudioOnly)
    }

    @Test
    fun `video only format is not audio bearing`() {
        val format = InnerTubePlayerResponse.Format(mimeType = "video/webm; codecs=\"vp9\"")
        assertTrue(!format.hasAudio)
    }

    @Test
    fun `a format with a direct url is not treated as ciphered`() {
        val format =
            InnerTubePlayerResponse.Format(
                url = "https://example.test/stream",
                mimeType = "audio/webm",
            )
        assertTrue(!format.isCiphered)
    }

    @Test
    fun `signature cipher is detected as ciphered`() {
        val format =
            InnerTubePlayerResponse.Format(
                signatureCipher = "s=abc&url=https%3A%2F%2Fexample.test",
                mimeType = "audio/webm",
            )
        assertTrue(format.isCiphered)
        assertEquals("s=abc&url=https%3A%2F%2Fexample.test", format.cipherBlob)
    }

    @Test
    fun `legacy cipher field is detected too`() {
        val format =
            InnerTubePlayerResponse.Format(
                cipher = "s=abc&url=https%3A%2F%2Fexample.test",
                mimeType = "audio/webm",
            )
        assertTrue(format.isCiphered)
    }

    @Test
    fun `an empty signature cipher does not count as ciphered`() {
        // YouTube sometimes emits the key with an empty value alongside a working url. Treating
        // that as ciphered would send a usable url down the decipher path and lose it.
        val format =
            InnerTubePlayerResponse.Format(
                url = "https://example.test/stream",
                signatureCipher = "",
                mimeType = "audio/webm",
            )
        assertTrue(!format.isCiphered)
        assertNull(format.cipherBlob)
    }

    // endregion

    // region Playability classification

    @Test
    fun `ok status is recognised`() {
        val status = InnerTubePlayerResponse.PlayabilityStatus(status = "OK")
        assertTrue(status.isOk)
        assertTrue(!status.isLoginOrAgeGated)
    }

    @Test
    fun `login required is recognised as gated`() {
        val status = InnerTubePlayerResponse.PlayabilityStatus(status = "LOGIN_REQUIRED")
        assertTrue(!status.isOk)
        assertTrue(status.isLoginOrAgeGated)
    }

    @Test
    fun `unplayable is a refusal but not a login gate`() {
        // The distinction matters: UNPLAYABLE is the missing-token signature, so it must not be
        // lumped in with the statuses that mean "sign in and retry".
        val status = InnerTubePlayerResponse.PlayabilityStatus(status = "UNPLAYABLE")
        assertTrue(!status.isOk)
        assertTrue(!status.isLoginOrAgeGated)
    }

    @Test
    fun `age check statuses are treated as gated`() {
        listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED",
        ).forEach { status ->
            val parsed = InnerTubePlayerResponse.PlayabilityStatus(status = status)
            assertTrue("$status should be gated", parsed.isLoginOrAgeGated)
        }
    }

    @Test
    fun `privately owned track is detected from music video type`() {
        val uploaded =
            InnerTubePlayerResponse.VideoDetails(
                musicVideoType = "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
            )
        val catalogue =
            InnerTubePlayerResponse.VideoDetails(
                musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
            )

        assertTrue(uploaded.isPrivatelyOwned)
        assertTrue(!catalogue.isPrivatelyOwned)
    }

    // endregion

    // region Streaming data

    @Test
    fun `all formats combines adaptive and muxed lists`() {
        val adaptive = InnerTubePlayerResponse.Format(mimeType = "audio/webm")
        val muxed = InnerTubePlayerResponse.Format(mimeType = "video/mp4")

        val data =
            InnerTubePlayerResponse.StreamingData(
                adaptiveFormats = listOf(adaptive),
                formats = listOf(muxed),
            )

        assertEquals(listOf(adaptive, muxed), data.allFormats)
    }

    @Test
    fun `streaming data is safe to read when YouTube omits format lists`() {
        // A refusal response omits these entirely; reading them must not throw.
        val data = InnerTubePlayerResponse.StreamingData()
        assertTrue(data.allFormats.isEmpty())
        assertNull(data.expiresInSeconds)
    }

    // endregion
}
