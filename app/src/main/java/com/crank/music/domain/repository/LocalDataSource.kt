package com.crank.music.domain.repository

import com.crank.music.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface LocalDataSource {
    fun getLikedSongs(): Flow<List<Song>>
    suspend fun toggleLikeSong(song: Song)
    suspend fun getDownloadedSongs(): List<Song>
}
