package com.crank.music.domain.model

/**
 * Live state of an in-flight download.
 *
 * This replaces the previous arrangement where the offline screen invented a progress
 * fraction, a transfer speed and a status for downloads that did not exist. Media3 reports
 * all three; they are surfaced here instead of being guessed in the view model.
 *
 * [downloadedBytes] and [totalBytes] are both 0 when the size is not yet known, which the UI
 * renders as indeterminate rather than as 0%.
 */
data class DownloadProgress(
    val songId: String,
    val title: String,
    val artistName: String,
    val artworkUrl: String,
    val state: DownloadState,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    /**
     * True only while the engine reports an *active* transfer, as opposed to an entry that is
     * merely queued behind another download.
     *
     * Both cases map to [DownloadState.DOWNLOADING], so [state] alone cannot tell them apart —
     * but only this one is known to be moving bytes. That distinction is what lets the download
     * control hold a steady "Downloading" state through a brief network lull instead of
     * flickering back to the Download arrow on a poll that happened to sample the same byte
     * count twice.
     */
    val isTransferring: Boolean = false,
) {
    /** 0f when the total is unknown, so callers cannot mistake it for a real 0%. */
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val isActive: Boolean
        get() = state == DownloadState.DOWNLOADING

    /**
     * The fraction a download control should draw, or null when it should not draw a determinate
     * arc at all.
     *
     * Null is a real answer here, not a missing one: it means "no known total", which the UI
     * renders as an indeterminate sweep. Returning 0f instead would draw a ring frozen at empty,
     * and an empty ring is indistinguishable from a transfer that has stalled.
     *
     * This lives on the model rather than in the view model because it is a rule about what a
     * download's numbers mean, and it has exactly one right answer regardless of which surface
     * asks — the player control and the sheet both read it.
     */
    val uiFraction: Float?
        get() = when {
            state == DownloadState.COMPLETED -> 1f
            state == DownloadState.DOWNLOADING && totalBytes > 0L -> fraction
            else -> null
        }
}
