package com.crank.music.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expiry handling for Gaana's Akamai-signed stream URLs.
 *
 * ## The bug these cover
 *
 * `isRottingUrl` decided whether `isExpired` was consulted at all, and it only recognised YouTube
 * hosts. Gaana's CDN (`*.akamaized.net`) therefore fell through to the "trust it" branch, so
 * `isDirectlyPlayable` returned `true` for a Gaana URL unconditionally — including days after its
 * token had died.
 *
 * `isExpired` compounded it: it looked only for a `?expire=` parameter, while Gaana carries the
 * deadline inside an Akamai token as `?hdnts=st=..~exp=..~acl=..~hmac=..`.
 *
 * The URLs below are the real shape, measured against a running GaanaPy server.
 * Token lifetime is exactly 14400s (4 hours): the `st`/`exp` pair always differs by that much.
 */
class StreamResolverGaanaTest {

    private val now = 1_700_000_000L

    private companion object {
        /** Measured directly from the server: exp - st is always exactly this. */
        const val TOKEN_LIFETIME_SECONDS = 14_400L
    }

    private fun gaanaUrl(expirySeconds: Long) =
        "https://vodhlsgaana-ebw.akamaized.net/hls/81/1594081/16663500/320.mp4.master.m3u8" +
            "?hdnts=st=${expirySeconds - TOKEN_LIFETIME_SECONDS}" +
            "~exp=$expirySeconds" +
            "~acl=/hls/81/1594081/16663500/*" +
            "~hmac=4be6e509d1bae716527840a79b4768bc64c72f9233c82f4ce9e31877e7c449de"

    // --- isRottingUrl ------------------------------------------------------

    @Test
    fun `gaana cdn host is recognised as rotting`() {
        assertTrue(StreamResolver.isRottingUrl(gaanaUrl(now + TOKEN_LIFETIME_SECONDS)))
    }

    /**
     * The host check must stay anchored on a dot boundary for the new entry too, or a
     * look-alike domain would be re-resolved as if it were Gaana's CDN.
     */
    @Test
    fun `lookalike akamaized host is not rotting`() {
        assertFalse(StreamResolver.isRottingUrl("https://notakamaized.net/audio.mp3"))
        assertFalse(StreamResolver.isRottingUrl("https://akamaized.net.evil.example/audio.mp3"))
    }

    // --- isExpired ---------------------------------------------------------

    @Test
    fun `gaana url past its hdnts expiry is expired`() {
        assertTrue(StreamResolver.isExpired(gaanaUrl(now - 1), now))
    }

    @Test
    fun `gaana url comfortably inside its window is not expired`() {
        assertFalse(StreamResolver.isExpired(gaanaUrl(now + 13_000), now))
    }

    @Test
    fun `gaana url expiring within the slack window counts as expired`() {
        assertTrue(StreamResolver.isExpired(gaanaUrl(now + 5), now))
    }

    /**
     * `st` (the token's start time) is always in the past, so reading the wrong field would make
     * every freshly minted URL look expired. Here `st` is already behind [now] while `exp` is a
     * full four hours ahead: correctly read, this is not expired.
     */
    @Test
    fun `the token start value is not mistaken for the expiry`() {
        val url = gaanaUrl(now + TOKEN_LIFETIME_SECONDS)
        // Guard the premise: st really is in the past.
        assertTrue(url.contains("st=${now + TOKEN_LIFETIME_SECONDS - TOKEN_LIFETIME_SECONDS}"))
        assertFalse(StreamResolver.isExpired(url, now))
    }

    /**
     * The `exp=` anchor exists so a differently-named field cannot be read as the deadline.
     * `~explist=` must not match, and with no real `exp=` the URL has no known deadline at all.
     */
    @Test
    fun `a field merely starting with exp is not read as the expiry`() {
        val url = "https://vodhlsgaana-ebw.akamaized.net/hls/a/b/c.mp4.m3u8?hdnts=st=1~explist=9999999999"
        assertFalse(StreamResolver.isExpired(url, now))
    }

    // --- isDirectlyPlayable (the regression itself) ------------------------

    /** Before the fix this returned `true`, so a dead URL was persisted and replayed. */
    @Test
    fun `expired gaana url is not directly playable`() {
        assertFalse(StreamResolver.isDirectlyPlayable(gaanaUrl(now - 1), now))
    }

    @Test
    fun `fresh gaana url is directly playable`() {
        assertTrue(StreamResolver.isDirectlyPlayable(gaanaUrl(now + 13_000), now))
    }

    /** An unsigned Gaana URL carries no deadline, so there are no grounds to reject it. */
    @Test
    fun `gaana url without a token is trusted`() {
        val url = "https://vodhlsgaana-ebw.akamaized.net/hls/81/1594081/16663500/320.mp4.master.m3u8"
        assertTrue(StreamResolver.isDirectlyPlayable(url, now))
    }

    /** The Gaana track id itself is not a URL, so it must be resolved rather than played. */
    @Test
    fun `a gaana track id is not directly playable`() {
        assertFalse(StreamResolver.isDirectlyPlayable("gaana:sanam-re", now))
    }
}
