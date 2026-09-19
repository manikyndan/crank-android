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
) {
    /** 0f when the total is unknown, so callers cannot mistake it for a real 0%. */
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val isActive: Boolean
        get() = state == DownloadState.DOWNLOADING
}
