package com.crank.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the download control's state rules.
 *
 * These are the exact situations the control has to get right, written down so a later change
 * to the precedence order cannot quietly reintroduce the bug they were added for: a song that
 * had already been downloaded offering the Download arrow again.
 *
 * The whole point of [DownloadStateResolver] being pure is that each of these is a plain call
 * rather than a hand-run sequence of taps against a live transfer engine.
 */
class DownloadStateResolverTest {

    private fun resolve(
        managerState: DownloadState?,
        downloadedOnDisk: Boolean = false,
        engineTransferring: Boolean = false,
        tappedRecently: Boolean = false,
        previousBytes: Long? = null,
        currentBytes: Long = 0L,
    ) = DownloadStateResolver.resolve(
        managerState = managerState,
        downloadedOnDisk = downloadedOnDisk,
        engineTransferring = engineTransferring,
        tappedRecently = tappedRecently,
        previousBytes = previousBytes,
        currentBytes = currentBytes,
    )

    // ── Not downloaded → Download arrow ──────────────────────────────────────────────

    @Test
    fun `a song the engine does not track shows the download arrow`() {
        assertEquals(
            "No record at all means nothing has been downloaded for this song",
            DownloadState.IDLE,
            resolve(managerState = null),
        )
    }

    @Test
    fun `a song with an idle engine record shows the download arrow`() {
        assertEquals(
            DownloadState.IDLE,
            resolve(managerState = DownloadState.IDLE),
        )
    }

    @Test
    fun `a failed transfer offers the arrow so it can be retried`() {
        // Deliberately not its own state: tapping Download is the retry, so the arrow is the
        // affordance that performs it.
        assertEquals(
            DownloadState.IDLE,
            resolve(managerState = DownloadState.FAILED),
        )
    }

    // ── In progress → Downloading ───────────────────────────────────────────────────

    @Test
    fun `a fresh tap shows downloading even before bytes arrive`() {
        assertEquals(
            "A just-pressed download must not look like it did nothing",
            DownloadState.DOWNLOADING,
            resolve(
                managerState = DownloadState.DOWNLOADING,
                tappedRecently = true,
                previousBytes = null,
                currentBytes = 0L,
            ),
        )
    }

    @Test
    fun `a transfer that is gaining bytes shows downloading`() {
        assertEquals(
            DownloadState.DOWNLOADING,
            resolve(
                managerState = DownloadState.DOWNLOADING,
                tappedRecently = false,
                previousBytes = 100L,
                currentBytes = 250L,
            ),
        )
    }

    @Test
    fun `a first sighting of a transfer is given the benefit of the doubt`() {
        // previousBytes == null means this is the first pass, so "not moving yet" is not
        // evidence of a stall — it is evidence of having just started.
        assertEquals(
            DownloadState.DOWNLOADING,
            resolve(
                managerState = DownloadState.DOWNLOADING,
                tappedRecently = false,
                previousBytes = null,
                currentBytes = 0L,
            ),
        )
    }

    @Test
    fun `a queued entry stuck on unchanged bytes decays back to the arrow`() {
        // A queued entry that never starts — an expired stream URL never resumes — must not
        // spin forever. Note this is NOT transferring: the engine has it queued, not active.
        assertEquals(
            DownloadState.IDLE,
            resolve(
                managerState = DownloadState.DOWNLOADING,
                engineTransferring = false,
                tappedRecently = false,
                previousBytes = 512L,
                currentBytes = 512L,
            ),
        )
    }

    @Test
    fun `an actively transferring download holds the downloading state through a still sample`() {
        // The anti-flicker rule. Polling samples a byte counter every few seconds, and a
        // healthy transfer can present the same count twice in a row across a brief lull.
        // Because the engine reports it as actively transferring, that must not be read as a
        // stall — otherwise the control blinks back to the Download arrow mid-download.
        assertEquals(
            "An active transfer must not decay on a single unchanged byte sample",
            DownloadState.DOWNLOADING,
            resolve(
                managerState = DownloadState.DOWNLOADING,
                engineTransferring = true,
                tappedRecently = false,
                previousBytes = 2048L,
                currentBytes = 2048L,
            ),
        )
    }

    @Test
    fun `an active transfer holds the downloading state even long after the tap`() {
        // The tap-freshness window is short; a large song outlives it many times over. Once the
        // window has passed, active transfer status alone has to keep the state honest.
        assertEquals(
            DownloadState.DOWNLOADING,
            resolve(
                managerState = DownloadState.DOWNLOADING,
                engineTransferring = true,
                tappedRecently = false,
                previousBytes = 9_000_000L,
                currentBytes = 9_000_000L,
            ),
        )
    }

    @Test
    fun `an actively transferring song that is already on disk still shows the checkmark`() {
        // Bytes on disk outranks everything, including an engine that still has a transfer
        // open for the same song.
        assertEquals(
            DownloadState.COMPLETED,
            resolve(
                managerState = DownloadState.DOWNLOADING,
                downloadedOnDisk = true,
                engineTransferring = true,
                previousBytes = 1024L,
                currentBytes = 1024L,
            ),
        )
    }

    // ── Finished → Checkmark ────────────────────────────────────────────────────────

    @Test
    fun `the engine reporting completion shows the checkmark`() {
        assertEquals(
            DownloadState.COMPLETED,
            resolve(managerState = DownloadState.COMPLETED),
        )
    }

    // ── Already downloaded must never fall back ─────────────────────────────────────

    @Test
    fun `an already downloaded song shows the checkmark when the engine has no record`() {
        // The normal shape after a restart, before the engine's index is rebuilt.
        assertEquals(
            DownloadState.COMPLETED,
            resolve(managerState = null, downloadedOnDisk = true),
        )
    }

    @Test
    fun `an already downloaded song shows the checkmark even when the engine says queued`() {
        // The regression this resolver was extracted for. A force-stop mid-verify leaves the
        // engine's ephemeral record QUEUED (mapped to DOWNLOADING) while the audio is complete
        // on disk. The durable flag has to win, or the user is offered a Download arrow for a
        // song they already have.
        assertEquals(
            "Bytes on disk outrank an ephemeral engine record",
            DownloadState.COMPLETED,
            resolve(
                managerState = DownloadState.DOWNLOADING,
                downloadedOnDisk = true,
                tappedRecently = false,
                previousBytes = 900L,
                currentBytes = 900L,
            ),
        )
    }

    @Test
    fun `an already downloaded song shows the checkmark even after a failed engine record`() {
        assertEquals(
            DownloadState.COMPLETED,
            resolve(managerState = DownloadState.FAILED, downloadedOnDisk = true),
        )
    }

    @Test
    fun `an already downloaded song shows the checkmark even when the engine says idle`() {
        assertEquals(
            DownloadState.COMPLETED,
            resolve(managerState = DownloadState.IDLE, downloadedOnDisk = true),
        )
    }

    @Test
    fun `a downloaded song stays downloaded across repeated passes`() {
        // The 4s poll re-resolves the same song many times; none of those passes may flip it
        // back to the arrow.
        repeat(5) { pass ->
            assertEquals(
                "Pass $pass must still report the checkmark",
                DownloadState.COMPLETED,
                resolve(
                    managerState = DownloadState.DOWNLOADING,
                    downloadedOnDisk = true,
                    previousBytes = 1024L,
                    currentBytes = 1024L,
                ),
            )
        }
    }

    // ── Switching songs ─────────────────────────────────────────────────────────────

    @Test
    fun `switching to an undownloaded song shows the arrow`() {
        // Same inputs as a downloaded song except the durable flag, which is what must
        // distinguish the two after a skip.
        assertEquals(
            DownloadState.IDLE,
            resolve(
                managerState = null,
                downloadedOnDisk = false,
                previousBytes = 4096L,
                currentBytes = 4096L,
            ),
        )
    }

    @Test
    fun `a downloaded song and an undownloaded song resolve differently`() {
        // Guards the skip path directly: two songs, identical engine conditions, opposite
        // controls.
        val downloaded = resolve(managerState = null, downloadedOnDisk = true)
        val notDownloaded = resolve(managerState = null, downloadedOnDisk = false)

        assertEquals(DownloadState.COMPLETED, downloaded)
        assertEquals(DownloadState.IDLE, notDownloaded)
    }
}
