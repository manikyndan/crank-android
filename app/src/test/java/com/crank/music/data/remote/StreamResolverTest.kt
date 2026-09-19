package com.crank.music.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the "can I hand this URL to ExoPlayer?" decision.
 *
 * This is the logic behind a real bug: a persisted YouTube URL was replayed
 * after its `expire` instant had passed, the CDN answered 403, and the player
 * responded by silently skipping the track. Every case below is about not
 * repeating that.
 */
class StreamResolverTest {

    private val now = 1_700_000_000L

    private fun mediaUrl(expireSeconds: Long) =
        "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=$expireSeconds" +
            "&ei=xyz&ip=1.2.3.4&id=o-abc&itag=140&mime=audio%2Fmp4&sig=deadbeef"

    // --- isExpired ---------------------------------------------------------

    @Test
    fun `url well past its expiry is expired`() {
        assertTrue(StreamResolver.isExpired(mediaUrl(now - 3600), now))
    }

    @Test
    fun `url comfortably before its expiry is not expired`() {
        assertFalse(StreamResolver.isExpired(mediaUrl(now + 3600), now))
    }

    /**
     * A URL expiring two seconds from now will almost certainly lapse during the
     * TCP/TLS handshake and the first byte request, producing exactly the 403 we
     * are trying to avoid. Treating the near future as already expired is the
     * point of the slack window.
     */
    @Test
    fun `url expiring within the slack window counts as expired`() {
        assertTrue(StreamResolver.isExpired(mediaUrl(now + 5), now))
    }

    @Test
    fun `url with no expire parameter is not treated as expired`() {
        val url = "https://example.com/audio/track.mp3"
        assertFalse(StreamResolver.isExpired(url, now))
    }

    @Test
    fun `malformed expire value is not treated as expired`() {
        val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=not-a-number"
        assertFalse(StreamResolver.isExpired(url, now))
    }

    @Test
    fun `expire after a fragment is still found`() {
        val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?itag=140#frag"
        assertFalse(StreamResolver.isExpired(url, now))

        val expired = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=${now - 10}#frag"
        assertTrue(StreamResolver.isExpired(expired, now))
    }

    // --- isRottingUrl ------------------------------------------------------

    @Test
    fun `googlevideo subdomains are recognised as rotting`() {
        assertTrue(StreamResolver.isRottingUrl(mediaUrl(now + 3600)))
        assertTrue(StreamResolver.isRottingUrl("https://googlevideo.com/videoplayback?expire=1"))
        assertTrue(StreamResolver.isRottingUrl("https://youtube.com/watch?v=abc"))
    }

    /**
     * The host check must be anchored on a dot boundary. A bare `endsWith`
     * would classify `evil-googlevideo.com` as YouTube media and start
     * re-resolving URLs we have no business touching.
     */
    @Test
    fun `lookalike host is not mistaken for youtube media`() {
        assertFalse(StreamResolver.isRottingUrl("https://notgooglevideo.com/audio.mp3"))
        assertFalse(StreamResolver.isRottingUrl("https://googlevideo.com.evil.example/audio.mp3"))
    }

    @Test
    fun `non-youtube hosts are not rotting`() {
        assertFalse(StreamResolver.isRottingUrl("https://example.com/audio/track.mp3"))
        assertFalse(StreamResolver.isRottingUrl("https://pipedapi.kavin.rocks/streams/abc"))
    }

    @Test
    fun `non-url inputs are never rotting`() {
        assertFalse(StreamResolver.isRottingUrl("dQw4w9WgXcQ"))
        assertFalse(StreamResolver.isRottingUrl("audd:unresolved:Foo:Bar"))
        assertFalse(StreamResolver.isRottingUrl(""))
    }

    // --- isDirectlyPlayable ------------------------------------------------

    @Test
    fun `fresh youtube url is directly playable`() {
        assertTrue(StreamResolver.isDirectlyPlayable(mediaUrl(now + 3600), now))
    }

    /** The regression this whole change exists for. */
    @Test
    fun `expired youtube url is not directly playable`() {
        assertFalse(StreamResolver.isDirectlyPlayable(mediaUrl(now - 1), now))
    }

    @Test
    fun `blank and identifier inputs are not directly playable`() {
        assertFalse(StreamResolver.isDirectlyPlayable("", now))
        assertFalse(StreamResolver.isDirectlyPlayable("dQw4w9WgXcQ", now))
        assertFalse(StreamResolver.isDirectlyPlayable("audd:unresolved:Foo:Bar", now))
    }

    /**
     * A non-YouTube URL with an `expire` parameter is left alone. Some CDNs use
     * that name for unrelated purposes, and we only have evidence that YouTube
     * URLs need re-minting — guessing here would break working playback.
     */
    @Test
    fun `non-youtube url is trusted even with an expire parameter`() {
        assertTrue(StreamResolver.isDirectlyPlayable("https://example.com/a.mp3?expire=${now - 999}", now))
    }

    @Test
    fun `youtube url without an expire parameter is trusted`() {
        assertTrue(StreamResolver.isDirectlyPlayable("https://www.youtube.com/watch?v=dQw4w9WgXcQ", now))
    }
}
