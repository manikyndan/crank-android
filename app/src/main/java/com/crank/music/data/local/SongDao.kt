package com.crank.music.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: LocalSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<LocalSongEntity>)

    @Update
    suspend fun updateSong(song: LocalSongEntity)

    @Delete
    suspend fun deleteSong(song: LocalSongEntity)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): LocalSongEntity?

    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY dateAdded DESC")
    fun getLikedSongs(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM songs WHERE isLiked = 1 ORDER BY dateAdded DESC")
    suspend fun getLikedSongsList(): List<LocalSongEntity>

    @Query("SELECT * FROM songs WHERE isLocal = 1")
    suspend fun getDownloadedSongs(): List<LocalSongEntity>

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSongById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Query("SELECT * FROM queue_items ORDER BY queueOrder ASC")
    fun getQueueItems(): Flow<List<QueueItemEntity>>

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(history: HistoryEntity)

    /**
     * Records another play of an already-known song, returning the number of rows updated.
     *
     * A zero result means the song has never been played, so the caller inserts a fresh row with
     * `playCount = 1`. Doing it in this order — update first, insert only on a miss — keeps a first
     * play from being counted twice, which a plain "insert then increment" would do.
     *
     * `playCount = playCount + 1` is evaluated by SQLite against the stored value, so concurrent
     * calls cannot lose an increment the way a read-modify-write from Kotlin would. Deliberately not
     * an `ON CONFLICT ... DO UPDATE` upsert: that needs SQLite 3.24, and this app supports API 24.
     */
    @Query(
        "UPDATE playback_history SET playCount = playCount + 1, playedAt = :playedAt " +
            "WHERE songId = :songId"
    )
    suspend fun bumpHistory(songId: String, playedAt: Long): Int

    /**
     * Adds actually-listened time to a song's running total.
     *
     * Accumulated rather than replaced: this is the sum of the seconds playback was running on this
     * track across every play, so it grows monotonically and a skip adds only what was heard.
     */
    @Query("UPDATE playback_history SET listenedMs = listenedMs + :deltaMs WHERE songId = :songId")
    suspend fun addListenedMs(songId: String, deltaMs: Long)

    /**
     * Total *listened* milliseconds across all history.
     *
     * Replaces a `SUM(durationMs)`, which counted a song's full nominal length even when the user
     * skipped it — the reason "Total Listening" could exceed the time the app had been installed.
     */
    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM playback_history")
    suspend fun getTotalListenedMs(): Long

    /**
     * Total plays including repeats.
     *
     * The correct numerator for "Songs Played". `COUNT(*)` would report distinct songs, because the
     * table is keyed on `songId`.
     */
    @Query("SELECT COALESCE(SUM(playCount), 0) FROM playback_history")
    suspend fun getTotalPlayCount(): Int

    /** Distinct songs ever played — a genuinely different figure from [getTotalPlayCount]. */
    @Query("SELECT COUNT(*) FROM playback_history")
    suspend fun getDistinctSongCount(): Int

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 30")
    fun getHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getHistoryList(limit: Int): List<HistoryEntity>

    /**
     * History ordered by how often each song was played.
     *
     * `getHistoryList` orders by `playedAt`, which is correct for "Recently played" but was also
     * being used for the library's "Most played" tab — so that tab actually showed the most recent
     * songs, in the same order as the tab above it. Only now that `playCount` exists can the two be
     * told apart.
     */
    @Query("SELECT * FROM playback_history ORDER BY playCount DESC, playedAt DESC LIMIT :limit")
    suspend fun getMostPlayedHistory(limit: Int): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE `query` = :searchQuery")
    suspend fun deleteSearchQuery(searchQuery: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    // ── User playlist membership ────────────────────────────────────────────
    // PlaylistSongCrossRef has existed since v4 with no DAO path; these read
    // and write it. No migration: no table changes, only new queries.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(ref: PlaylistSongCrossRef)

    @Query("SELECT songId FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY songOrder ASC")
    suspend fun getSongIdsForPlaylist(playlistId: String): List<String>

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT COUNT(*) FROM playback_history")
    suspend fun getHistoryCount(): Int

    /**
     * Deletes every playback-history row.
     *
     * Existed only as a UI promise before this: the Privacy screen flipped a flag to "cleared" and
     * told the user their history was gone, while the rows — and everything derived from them, such
     * as Music DNA — stayed exactly as they were.
     */
    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun getSearchHistoryCount(): Int

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("SELECT artistName FROM playback_history GROUP BY artistName ORDER BY SUM(playCount) DESC LIMIT 1")
    suspend fun getTopArtist(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackState(position: PlaybackPositionEntity)

    @Query("SELECT * FROM playback_position WHERE id = 1")
    suspend fun getPlaybackState(): PlaybackPositionEntity?

    @Query("DELETE FROM playback_position")
    suspend fun clearPlaybackState()

    // ── Session values ────────────────────────────────────────────────────────
    // Session-scoped strings that outlive a process but are not library data.
    // Currently holds the YouTube visitor id.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSessionValue(entry: SessionEntity)

    @Query("SELECT value FROM session_values WHERE `key` = :key")
    suspend fun getSessionValue(key: String): String?

    @Query("DELETE FROM session_values WHERE `key` = :key")
    suspend fun deleteSessionValue(key: String)
}
