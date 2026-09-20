package com.crank.music.domain.repository

import com.crank.music.domain.model.DownloadProgress
import com.crank.music.domain.model.DownloadState
import com.crank.music.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    suspend fun downloadSong(song: Song)
    suspend fun removeDownload(songId: String)
    fun getDownloadedSongs(): Flow<List<Song>>
    fun getDownloadState(songId: String): Flow<DownloadState>

    /**
     * One-shot view of a single download: engine state plus real byte counts.
     *
     * Null when the manager tracks nothing for [songId]. Lets callers tell a
     * genuinely progressing download apart from a stale queued entry whose
     * URL expired long ago.
     */
    suspend fun getDownloadSnapshot(songId: String): DownloadProgress?

    /**
     * Live progress for a single download, sampled fast enough to animate.
     *
     * [getDownloadSnapshot] answers "where is this now" but only when asked, and the screen's
     * state poll runs every few seconds — far too coarse to drive a progress ring. This stream
     * samples the transfer directly instead, and also reacts immediately to engine state changes
     * so a completion is not held back until the next sample.
     *
     * Note that it cannot simply forward the engine's own notifications: Media3's
     * `DownloadManager` notifies on *state* changes, not on byte progress, so a listener alone
     * reports the start and the end of a transfer and nothing in between.
     *
     * Emits null when the engine tracks nothing for [songId], which is the same distinction
     * [getDownloadSnapshot] makes.
     */
    fun getDownloadProgress(songId: String): Flow<DownloadProgress?>

    /**
     * Every download the manager is currently tracking, with real byte progress.
     *
     * Backed by Media3's `DownloadManager`, so the fractions and states are the transfer
     * engine's own rather than values invented by the UI. Like [getDownloadProgress] this samples
     * the index on an interval rather than relying on engine notifications, which report state
     * changes and not byte progress.
     */
    fun getActiveDownloads(): Flow<List<DownloadProgress>>

    /**
     * Media3 1.4.1 only supports pausing downloads globally, not per item. These two mirror
     * the underlying API instead of offering a per-row control the engine cannot honour.
     */
    suspend fun pauseAllDownloads()
    suspend fun resumeAllDownloads()

    fun downloadsArePaused(): Boolean

    /**
     * Clears a failed download from the index so it can be re-requested.
     *
     * The caller is responsible for re-issuing [downloadSong] with the full [Song], because
     * the stream URL is not retained here and would otherwise be re-resolved against a stale
     * request.
     */
    suspend fun retryDownload(songId: String)
}
