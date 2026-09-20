package com.crank.music.ui.components

import com.crank.music.domain.model.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shared download-control mapping.
 *
 * The player control and the three-dot menu both read these three functions, so a change here
 * changes both. The tests assert the two properties that matter for the reported bug:
 * a downloaded song resolves to a checkmark and to a *different* glyph than the Download arrow,
 * and a downloaded or in-flight song is not tappable (so it cannot be re-downloaded or
 * double-queued).
 */
class DownloadControlVisualsTest {

    @Test
    fun `an undownloaded song offers the download arrow`() {
        assertEquals("Download", DownloadControlVisuals.label(DownloadState.IDLE))
        assertTrue(
            "The arrow is the only state that should accept a tap",
            DownloadControlVisuals.isActionable(DownloadState.IDLE),
        )
    }

    @Test
    fun `a failed transfer is offered as a retryable download`() {
        assertEquals(
            "A failed transfer is retried by tapping Download, so it must read as Download",
            "Download",
            DownloadControlVisuals.label(DownloadState.FAILED),
        )
        assertTrue(DownloadControlVisuals.isActionable(DownloadState.FAILED))
    }

    @Test
    fun `an in-flight download reads as downloading and is not tappable`() {
        assertEquals("Downloading…", DownloadControlVisuals.label(DownloadState.DOWNLOADING))
        assertFalse(
            "Tapping a running download would queue a duplicate transfer",
            DownloadControlVisuals.isActionable(DownloadState.DOWNLOADING),
        )
    }

    @Test
    fun `a downloaded song reads as downloaded and is not tappable`() {
        assertEquals("Downloaded", DownloadControlVisuals.label(DownloadState.COMPLETED))
        assertFalse(
            "Tapping a completed download would re-fetch audio already on disk",
            DownloadControlVisuals.isActionable(DownloadState.COMPLETED),
        )
    }

    @Test
    fun `the downloaded glyph is the checkmark, not the download arrow`() {
        // The whole point of the completed state: the arrow must be gone, not merely tinted or
        // relabelled. Comparing vector identity catches a copy-paste that maps COMPLETED back
        // to Icons.Default.Download.
        val completed = DownloadControlVisuals.icon(DownloadState.COMPLETED)
        val idle = DownloadControlVisuals.icon(DownloadState.IDLE)

        assertNotEquals(
            "A downloaded song must not show the Download arrow",
            idle,
            completed,
        )
        assertEquals("Filled.Check", completed.name)
    }

    @Test
    fun `the three states use three distinct glyphs`() {
        val glyphs = listOf(
            DownloadState.IDLE,
            DownloadState.DOWNLOADING,
            DownloadState.COMPLETED,
        ).map { DownloadControlVisuals.icon(it).name }

        assertEquals(
            "Each state must be visually distinguishable: $glyphs",
            3,
            glyphs.toSet().size,
        )
    }

    @Test
    fun `every state has a label and an icon`() {
        // Guards against a new DownloadState being added without extending the mapping, which
        // would otherwise crash the player screen at runtime.
        DownloadState.entries.forEach { state ->
            assertTrue(
                "$state has no label",
                DownloadControlVisuals.label(state).isNotBlank(),
            )
            assertTrue(
                "$state has no icon",
                DownloadControlVisuals.icon(state).name.isNotBlank(),
            )
        }
    }

    // ── Percentage label ────────────────────────────────────────────────────────────

    @Test
    fun `the percentage reads as the fractions the user expects`() {
        assertEquals("0%", DownloadControlVisuals.percentLabel(0f))
        assertEquals("25%", DownloadControlVisuals.percentLabel(0.25f))
        assertEquals("50%", DownloadControlVisuals.percentLabel(0.5f))
        assertEquals("75%", DownloadControlVisuals.percentLabel(0.75f))
        assertEquals("100%", DownloadControlVisuals.percentLabel(1f))
    }

    @Test
    fun `a fraction past the end never renders over one hundred percent`() {
        // The engine can report more bytes than the announced total, and the label is derived
        // from an interpolated float that can overshoot. "101%" would be visible nonsense.
        assertEquals("100%", DownloadControlVisuals.percentLabel(1.0001f))
        assertEquals("100%", DownloadControlVisuals.percentLabel(1.5f))
    }

    @Test
    fun `a negative fraction never renders below zero percent`() {
        assertEquals("0%", DownloadControlVisuals.percentLabel(-0.2f))
    }

    @Test
    fun `the percentage rounds to the nearest whole number`() {
        // The label is fed the interpolated fraction, so it must round rather than truncate —
        // truncation would make the count visibly lag the ring beside it.
        // Values are kept off exact .5 boundaries: those sit on Float rounding edges and would
        // make the test about binary representation rather than about the rounding rule.
        assertEquals("76%", DownloadControlVisuals.percentLabel(0.756f))
        assertEquals("75%", DownloadControlVisuals.percentLabel(0.754f))
        assertEquals("33%", DownloadControlVisuals.percentLabel(0.3333f))
    }
}
