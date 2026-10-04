package com.crank.music.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per song the user has played.
 *
 * The primary key is [songId], so a replay updates this row rather than adding one — which is what
 * made the statistics wrong: "Songs Played" was really "distinct songs ever played", and a skip
 * after ten seconds was indistinguishable from a full listen.
 *
 * [playCount] and [listenedMs] exist to fix that. [playCount] counts every play including repeats,
 * and [listenedMs] accumulates *actually listened* time (the seconds playback was running on this
 * track), so a track skipped after ten seconds contributes ten seconds, not its full duration.
 */
@Entity(tableName = "playback_history")
data class HistoryEntity(
    @PrimaryKey
    val songId: String,
    val title: String,
    val artistName: String,
    val albumId: String?,
    val durationMs: Long,
    val artworkUrl: String,
    val streamUrl: String,
    val playedAt: Long = System.currentTimeMillis(),
    /** How many times this song has been played. Starts at 1 on first play. */
    val playCount: Int = 1,
    /** Milliseconds of this song actually listened to, accumulated across all plays. */
    val listenedMs: Long = 0L
)
