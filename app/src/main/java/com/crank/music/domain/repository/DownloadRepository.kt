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
     * Every download the manager is currently tracking, with real byte progress.
     *
     * Backed by Media3's `DownloadManager`, so the fractions and states are the transfer
     * engine's own rather than values invented by the UI.
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
