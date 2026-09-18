package com.crank.music.domain.repository

import com.crank.music.domain.model.DownloadState
import com.crank.music.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    suspend fun downloadSong(song: Song)
    suspend fun removeDownload(songId: String)
    fun getDownloadedSongs(): Flow<List<Song>>
    fun getDownloadState(songId: String): Flow<DownloadState>
}
