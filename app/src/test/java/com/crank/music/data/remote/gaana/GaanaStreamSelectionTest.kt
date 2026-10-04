package com.crank.music.data.remote.gaana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mapping the user's Audio Quality ceiling onto Gaana's four tiers.
 *
 * This is what makes the audio-quality setting mean something for the Gaana source. Before it,
 * every source took the highest bitrate available and the setting was persisted and ignored.
 */
class GaanaStreamSelectionTest {

    private val allTiers = GaanaStreamQualitiesDto(
        veryHigh = "https://cdn/320.mp4.master.m3u8",
        high = "https://cdn/128.mp4.master.m3u8",
        medium = "https://cdn/64.mp4.master.m3u8",
        low = "https://cdn/16.mp4.master.m3u8",
    )

    @Test
    fun `no ceiling takes the highest tier`() {
        assertEquals("https://cdn/320.mp4.master.m3u8", GaanaStreamSelection.select(allTiers, null))
    }

    @Test
    fun `a 320 ceiling takes the very high tier`() {
        assertEquals("https://cdn/320.mp4.master.m3u8", GaanaStreamSelection.select(allTiers, 320))
    }

    @Test
    fun `a 128 ceiling takes the high tier`() {
        assertEquals("https://cdn/128.mp4.master.m3u8", GaanaStreamSelection.select(allTiers, 128))
    }

    /** A ceiling between two tiers takes the lower one — it must never overshoot. */
    @Test
    fun `a ceiling between tiers rounds down`() {
        assertEquals("https://cdn/128.mp4.master.m3u8", GaanaStreamSelection.select(allTiers, 129))
        assertEquals("https://cdn/64.mp4.master.m3u8", GaanaStreamSelection.select(allTiers, 65))
    }

    /**
     * A ceiling below the lowest tier still returns the lowest. Returning null here would render
     * as "this song is unavailable", which is a different and false statement.
     */
    @Test
    fun `a ceiling below the lowest tier falls back to the lowest`() {
        assertEquals("https://cdn/16.mp4.master.m3u8", GaanaStreamSelection.select(allTiers, 8))
    }

    @Test
    fun `absent tiers are skipped`() {
        val onlyLow = GaanaStreamQualitiesDto(low = "https://cdn/16.mp4.master.m3u8")
        assertEquals("https://cdn/16.mp4.master.m3u8", GaanaStreamSelection.select(onlyLow, 320))
    }

    @Test
    fun `intermediate tiers are skipped when the top two are missing`() {
        val sparse = GaanaStreamQualitiesDto(
            veryHigh = "https://cdn/320.mp4.master.m3u8",
            medium = "https://cdn/64.mp4.master.m3u8",
        )
        assertEquals("https://cdn/64.mp4.master.m3u8", GaanaStreamSelection.select(sparse, 128))
    }

    /** Blank is what a scraper produces when a field exists but has no value; treat it as absent. */
    @Test
    fun `blank tier values are treated as absent`() {
        val blanked = GaanaStreamQualitiesDto(
            veryHigh = "",
            high = "   ",
            medium = null,
            low = "https://cdn/16.mp4.master.m3u8",
        )
        assertEquals("https://cdn/16.mp4.master.m3u8", GaanaStreamSelection.select(blanked, null))
    }

    /**
     * No tiers at all is a real answer — a track Gaana will not stream — and must surface as a
     * failure rather than as an invented URL.
     */
    @Test
    fun `no availability yields null`() {
        assertNull(GaanaStreamSelection.select(null, null))
        assertNull(GaanaStreamSelection.select(GaanaStreamQualitiesDto(), null))
        assertNull(GaanaStreamSelection.select(GaanaStreamQualitiesDto(), 320))
    }
}
