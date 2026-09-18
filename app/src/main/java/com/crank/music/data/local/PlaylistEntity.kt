package com.crank.music.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val songCount: Int,
    val artworkUrl: String,
    val createdAt: Long = System.currentTimeMillis()
)
