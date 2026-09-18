package com.crank.music.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val playedAt: Long = System.currentTimeMillis()
)
