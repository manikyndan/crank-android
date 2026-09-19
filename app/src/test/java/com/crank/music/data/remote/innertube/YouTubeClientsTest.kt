package com.crank.music.data.remote.innertube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the stream fallback chain's ordering and index invariants.
 *
 * ## Why these tests exist
 *
 * Upstream (Echo Music) recorded a real regression in this exact structure: the start index for
 * normal content was expressed as `indexOf(ANDROID_VR_1_43_32)`. Under one ordering that
 * happened to equal the intended value. When the chain was reordered, the same expression
 * silently resolved to index **4** — skipping visionOS, ANDROID_VR 1.65.10 and both TVHTML5
 * entries on *every* normal playback. It still compiled, still looked right, and would have
 * quietly disabled the entire fix.
 *
 * These tests make that class of change fail loudly instead. They are deliberately about
 * *structure*, not about behaviour against YouTube, because behaviour needs a device and a
 * network while structure can be verified here and now.
 */
class YouTubeClientsTest {

    @Test
    fun `normal content start index is pinned to zero and is not index-derived`() {
        // The whole point: normal content must begin at the top of the chain, so that
        // visionOS and the working ANDROID_VR pin are actually tried.
        assertEquals(0, YouTubeClients.NORMAL_CONTENT_STREAM_START_INDEX)
    }

    @Test
    fun `normal content start index actually points at the leading client`() {
        // Cross-checks the constant against the chain, so that inserting a client at the head
        // without revisiting the start index is caught here rather than on device.
        assertEquals(
            YouTubeClients.fallbackChain[YouTubeClients.NORMAL_CONTENT_STREAM_START_INDEX],
            YouTubeClients.VISIONOS,
        )
    }

    @Test
    fun `fallback chain has exactly the seven measured clients`() {
        assertEquals(7, YouTubeClients.fallbackChain.size)
    }

    @Test
    fun `fallback chain is in the measured order`() {
        // Order encodes on-device measurements, not preference. A change here is a claim that
        // the measurements no longer hold, and should come with new ones.
        assertEquals(
            listOf(
                YouTubeClients.VISIONOS,
                YouTubeClients.ANDROID_VR_1_65_10,
                YouTubeClients.TVHTML5,
                YouTubeClients.ANDROID_VR_1_43_32,
                YouTubeClients.IPADOS,
                YouTubeClients.IOS,
                YouTubeClients.WEB_CREATOR,
            ),
            YouTubeClients.fallbackChain,
        )
    }

    @Test
    fun `visionos leads the chain because it is the only whole-file client`() {
        assertEquals(YouTubeClients.VISIONOS, YouTubeClients.fallbackChain.first())
    }

    @Test
    fun `preview length clients are never reached before a working one`() {
        // IOS and IPADOS serve only a ~1 MiB prefix. They must sit behind every client that can
        // serve a complete file, otherwise playback truncates at roughly 60 seconds.
        val chain = YouTubeClients.fallbackChain
        val iosIndex = chain.indexOf(YouTubeClients.IOS)
        val ipadosIndex = chain.indexOf(YouTubeClients.IPADOS)

        assertTrue("VISIONOS must precede IOS", chain.indexOf(YouTubeClients.VISIONOS) < iosIndex)
        assertTrue(
            "ANDROID_VR 1.65.10 must precede IOS",
            chain.indexOf(YouTubeClients.ANDROID_VR_1_65_10) < iosIndex,
        )
        assertTrue(
            "ANDROID_VR 1.65.10 must precede IPADOS",
            chain.indexOf(YouTubeClients.ANDROID_VR_1_65_10) < ipadosIndex,
        )
    }

    @Test
    fun `private track start index resolves to tvhtml5 by identity`() {
        val index = YouTubeClients.PRIVATE_TRACK_STREAM_START_INDEX
        assertTrue("private track index must be in range", index in YouTubeClients.fallbackChain.indices)
        assertEquals(
            "private tracks must start at TVHTML5",
            YouTubeClients.TVHTML5,
            YouTubeClients.fallbackChain[index],
        )
    }

    @Test
    fun `private track index is not the hardcoded value that used to be correct`() {
        // Documents the trap: TVHTML5 is currently at index 2, not 1. If a reorder moves it,
        // the identity-based resolution above still holds and this assertion updates with it.
        assertNotEquals(
            1,
            YouTubeClients.PRIVATE_TRACK_STREAM_START_INDEX,
        )
    }

    @Test
    fun `every fallback client has a distinct identity`() {
        // Duplicates would mean a client is tried twice, wasting a round trip, while another is
        // never tried at all.
        val identities = YouTubeClients.fallbackChain.map { it.clientName to it.clientVersion }
        assertEquals(identities.size, identities.toSet().size)
    }

    @Test
    fun `po token gated clients advertise the flag that makes the token get attached`() {
        // useWebPoTokens is what causes `pot=` to be appended to the stream URL. WEB_CREATOR
        // shipped without it once and produced a correctly signed URL that still 403'd on the
        // first byte, so the flag is asserted rather than assumed.
        assertTrue("WEB_REMIX must declare useWebPoTokens", YouTubeClients.WEB_REMIX.useWebPoTokens)
        assertTrue("WEB_CREATOR must declare useWebPoTokens", YouTubeClients.WEB_CREATOR.useWebPoTokens)
        assertTrue("TVHTML5 must declare useWebPoTokens", YouTubeClients.TVHTML5.useWebPoTokens)
    }

    @Test
    fun `main client is web remix and the token binding depends on it`() {
        // PoTokenGenerator sends a session-bound token as the player-request token specifically
        // because WEB_REMIX is special-cased by yt-dlp. Changing the main client silently breaks
        // that binding, so it is pinned here as a tripwire.
        assertEquals(YouTubeClients.WEB_REMIX, YouTubeClients.MAIN_CLIENT)
    }

    @Test
    fun `login required clients are marked so anonymous runs skip them`() {
        assertTrue(YouTubeClients.WEB_CREATOR.loginRequired)
        assertTrue(YouTubeClients.TVHTML5.loginRequired)
    }

    @Test
    fun `visionos and android vr pin do not claim login support`() {
        // They must work anonymously; that is the reason they lead a chain used by signed-out
        // sessions. If either starts reporting login support, the cascade logic changes meaning.
        assertTrue(!YouTubeClients.VISIONOS.loginSupported)
        assertTrue(!YouTubeClients.ANDROID_VR_1_65_10.loginSupported)
    }

    @Test
    fun `client id is carried separately from the client name`() {
        // X-YouTube-Client-Name carries the numeric id, not the name. Keeping both on the model
        // is what lets the header be built correctly; conflating them produced malformed requests.
        assertEquals("67", YouTubeClients.WEB_REMIX.clientId)
        assertEquals("WEB_REMIX", YouTubeClients.WEB_REMIX.clientName)
    }
}
