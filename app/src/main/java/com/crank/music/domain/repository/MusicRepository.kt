package com.crank.music.domain.repository

import com.crank.music.data.remote.StreamData
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song

data class LyricsSearchResult(
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val duration: Long?,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

interface MusicRepository {
    suspend fun search(query: String): List<Song>
    suspend fun getHomeRecommendations(): List<Album>
    suspend fun getSongStreamUrl(songId: String, songTitle: String = "", artistName: String = ""): StreamData
    suspend fun getSongDetails(songId: String): Song
    suspend fun searchLyrics(trackName: String, artistName: String): LyricsSearchResult?
}
