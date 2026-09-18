package com.crank.music.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_position")
data class PlaybackPositionEntity(
    @PrimaryKey
    val id: Int = 1,
    val songId: String,
    val songTitle: String = "",
    val songArtist: String = "",
    val songArtwork: String = "",
    val songAlbumId: String? = null,
    val songDurationMs: Long = 0L,
    val songStreamUrl: String = "",
    val positionMs: Long,
    val queueJson: String = "",
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false
)
