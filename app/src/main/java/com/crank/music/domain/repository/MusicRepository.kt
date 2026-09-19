package com.crank.music.domain.repository

import com.crank.music.data.remote.StreamData
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song

data class LyricsSearchResult(
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    /** Length of the matched recording in milliseconds, or `null` when the source omits it. */
    val durationMs: Long?,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

interface MusicRepository {
    suspend fun search(query: String): List<Song>
    suspend fun getHomeRecommendations(): List<Album>

    /**
     * The real YouTube Music tracklist for an album or playlist, addressed by its `browseId`.
     *
     * Distinct from [search]: that matches free text and returns a bag of loosely-related songs;
     * this hits the `browse` endpoint and returns the actual songs that belong to the collection.
     * Callers (e.g. the album detail screen) should try this first and only fall back to [search]
     * when the id is not a real YouTube browse id (local/offline albums) or the browse comes back
     * empty.
     */
    suspend fun browseCollection(browseId: String): List<Song>
    suspend fun getSongStreamUrl(songId: String, songTitle: String = "", artistName: String = ""): StreamData
    suspend fun getSongDetails(songId: String): Song
    /**
     * Best lyrics match for a track described by [trackName] / [artistName].
     *
     * [durationMs] is the length of the track actually playing. It is optional but worth passing:
     * it is the only signal that separates an original from its remix, and without it a fuzzy
     * title search can return the wrong version's lyrics. Pass `0` when unknown.
     */
    suspend fun searchLyrics(
        trackName: String,
        artistName: String,
        durationMs: Long = 0L,
    ): LyricsSearchResult?

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
