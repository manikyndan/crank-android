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

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 30")
    fun getHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getHistoryList(limit: Int): List<HistoryEntity>

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

    // Multiply by 1.0 first so SQLite promotes to REAL and we keep the fractional
    // minutes. A bare `SUM(durationMs) / 60000` on an INTEGER column performs
    // integer division, which silently truncates every partial minute.
    @Query("SELECT CAST(COALESCE(SUM(durationMs), 0) AS REAL) / 60000.0 FROM playback_history")
    suspend fun getTotalListeningMinutes(): Double

    @Query("SELECT artistName FROM playback_history GROUP BY artistName ORDER BY COUNT(*) DESC LIMIT 1")
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
