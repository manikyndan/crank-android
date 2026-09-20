package com.crank.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins what a download's numbers mean to the UI.
 *
 * The interesting case is the null: an unknown total is not the same as zero progress, and the
 * whole point of [DownloadProgress.uiFraction] is to keep that distinction available to the
 * indicator so it can sweep indeterminately instead of drawing an empty ring that looks stalled.
 */
class DownloadProgressTest {

    private fun progress(
        state: DownloadState,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
    ) = DownloadProgress(
        songId = "song",
        title = "Title",
        artistName = "Artist",
        artworkUrl = "",
        state = state,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
    )

    // ── fraction ────────────────────────────────────────────────────────────────────

    @Test
    fun `fraction is the ratio of bytes to total`() {
        assertEquals(0.25f, progress(DownloadState.DOWNLOADING, 250L, 1000L).fraction, 0.0001f)
        assertEquals(0.5f, progress(DownloadState.DOWNLOADING, 500L, 1000L).fraction, 0.0001f)
    }

    @Test
    fun `fraction is zero when the total is unknown`() {
        // Documented existing behaviour: callers cannot mistake this for a real 0%.
        assertEquals(0f, progress(DownloadState.DOWNLOADING, 500L, 0L).fraction, 0.0001f)
    }

    // ── uiFraction: what the control should draw ────────────────────────────────────

    @Test
    fun `a known total yields a determinate fraction`() {
        assertEquals(
            0.25f,
            progress(DownloadState.DOWNLOADING, 250L, 1000L).uiFraction ?: -1f,
            0.0001f,
        )
    }

    @Test
    fun `an unknown total yields null so the ring can be indeterminate`() {
        assertNull(
            "An empty ring is indistinguishable from a stalled transfer",
            progress(DownloadState.DOWNLOADING, 0L, 0L).uiFraction,
        )
    }

    @Test
    fun `media3's minus-one content length yields null`() {
        // Media3 reports contentLength = -1 until the server answers, which is the first moment
        // of every transfer — the exact window where an indeterminate sweep is correct.
        assertNull(progress(DownloadState.DOWNLOADING, 0L, -1L).uiFraction)
    }

    @Test
    fun `a completed download reads as full`() {
        assertEquals(1f, progress(DownloadState.COMPLETED, 0L, 0L).uiFraction)
        assertEquals(1f, progress(DownloadState.COMPLETED, 1000L, 1000L).uiFraction)
    }

    @Test
    fun `idle and failed downloads draw no determinate arc`() {
        assertNull(progress(DownloadState.IDLE).uiFraction)
        assertNull(progress(DownloadState.FAILED).uiFraction)
    }

    @Test
    fun `a download that overshot its total still reads as full`() {
        // The engine can report more bytes than the announced length. The ring must not
        // over-sweep past a full circle.
        assertEquals(1f, progress(DownloadState.DOWNLOADING, 1200L, 1000L).uiFraction)
    }
}
