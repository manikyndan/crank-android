package com.crank.music.domain.model

/**
 * Decides what the download control should show for the song that is currently on screen.
 *
 * ## Why this is a pure function
 *
 * The rule set is small but has to hold across three surfaces at once — the player control,
 * the three-dot menu, and the offline screen — and it has to survive skipping between songs
 * and reopening the app. Written inline against a live `DownloadManager` it could only be
 * checked by hand, one song at a time. As a pure function every combination is a test case.
 *
 * ## The two sources of truth, and which one wins
 *
 * There are two independent facts about a song, and they disagree more often than they look:
 *
 * - **The transfer engine** (`managerState`) knows about the current session's transfer. It is
 *   fast and live, but it is *ephemeral*: its index is rebuilt per process, and a record left
 *   mid-verify by a force-stop reads as `QUEUED`/`RESTARTING` even though the audio is sitting
 *   complete on disk.
 * - **The library row** (`downloadedOnDisk`) is the durable fact. It is set to true only by
 *   `markComplete`, which runs when the bytes are fully written, and is reset to false only
 *   when a fresh download is enqueued. It survives restarts because it is a database column.
 *
 * The engine is consulted first only for the case it alone can answer — a transfer that has
 * genuinely just finished. After that [downloadedOnDisk] is **authoritative**: if the audio is
 * on disk the control shows the checkmark, full stop.
 *
 * That precedence is the fix for the reported bug. Previously a downloaded song could resolve
 * back to `IDLE` whenever the engine held a non-completed record for it — for instance after a
 * restart left the entry `QUEUED`, or once the stalled-transfer decay below expired. The user
 * saw an already-downloaded song offer the Download arrow again, which is precisely the state
 * they asked never to see.
 *
 * ## The stalled-transfer decay
 *
 * A queued entry can sit on stale bytes forever — an expired stream URL never resumes. Rather
 * than spinning indefinitely, a *queued* entry is only reported as
 * [DownloadState.DOWNLOADING] while it is either freshly tapped or actually moving bytes.
 * [previousBytes] of null means "first sighting", which gets the benefit of the doubt so a
 * real download is not hidden on its very first frame.
 *
 * That decay deliberately does not apply to a transfer the engine reports as *active*. Polling
 * samples a byte counter every few seconds, and a transfer that briefly stalls and resumes —
 * which is normal on a real network — can easily present the same count on two consecutive
 * samples. Decaying on that would flicker the control back to the Download arrow in the middle
 * of a healthy download, which is exactly what the control is supposed to be communicating.
 * Movement is therefore only used to judge entries that are not yet transferring.
 */
internal object DownloadStateResolver {

    /**
     * @param managerState the transfer engine's view, or null when it tracks nothing for this
     *   song. Null is meaningfully different from [DownloadState.IDLE]: it means "no record",
     *   not "record present and idle".
     * @param downloadedOnDisk the library row's `isLocal` flag — bytes fully written.
     * @param engineTransferring true only while the engine reports an active transfer, as
     *   opposed to an entry that is queued and may never start.
     * @param tappedRecently whether the user just pressed Download for this song.
     * @param previousBytes byte count observed on the previous pass, or null on first sighting.
     * @param currentBytes byte count reported now.
     */
    fun resolve(
        managerState: DownloadState?,
        downloadedOnDisk: Boolean,
        engineTransferring: Boolean = false,
        tappedRecently: Boolean = false,
        previousBytes: Long? = null,
        currentBytes: Long = 0L,
    ): DownloadState {
        // The engine's own completion signal is trusted first: it is what flips `isLocal`, and
        // reporting it immediately avoids a frame of stale arrow between finish and the write.
        if (managerState == DownloadState.COMPLETED) return DownloadState.COMPLETED

        // Durable ground truth. A song whose bytes are on disk is downloaded, regardless of
        // what an ephemeral engine record claims. This is what keeps the checkmark on screen
        // when skipping back to a downloaded song or reopening the player.
        if (downloadedOnDisk) return DownloadState.COMPLETED

        // Actively transferring: hold the Downloading state steadily. No decay here — see the
        // note above on why a sampled byte count is not evidence of a stall mid-transfer.
        if (engineTransferring) return DownloadState.DOWNLOADING

        if (managerState == DownloadState.DOWNLOADING) {
            val moving = previousBytes == null || currentBytes > previousBytes
            if (tappedRecently || moving) return DownloadState.DOWNLOADING
        }

        // Covers IDLE, FAILED and "no record at all": all three offer the Download action.
        // A failed download is deliberately not surfaced as its own state here — tapping
        // Download again is the retry, so the arrow is the honest affordance.
        return DownloadState.IDLE
    }
}
