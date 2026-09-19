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

    /**
     * Lyrics for the track with [videoId], or `null` when there are none.
     *
     * Separate from [searchLyrics] because the two answer different questions and have different
     * failure modes. This one asks YouTube Music by track id, so it cannot return the wrong
     * song's lyrics — but it only has lyrics for catalogue tracks. [searchLyrics] matches on
     * title and artist, so it covers more tracks but can match the wrong recording.
     *
     * Callers should prefer this and fall back to [searchLyrics].
     */
    suspend fun getLyricsByVideoId(videoId: String): String?
}
