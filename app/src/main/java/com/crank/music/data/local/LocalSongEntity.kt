package com.crank.music.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.crank.music.domain.model.Song

@Entity(tableName = "songs")
data class LocalSongEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artistName: String,
    val albumId: String?,
    val durationMs: Long,
    val artworkUrl: String,
    val isLocal: Boolean,
    val streamUrl: String = "",
    val isLiked: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
)

fun LocalSongEntity.toDomainModel(): Song {
    return Song(
        id = id,
        title = title,
        artistName = artistName,
        albumId = albumId,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        isLocal = isLocal,
        streamUrl = streamUrl
    )
}

fun Song.toEntity(isLiked: Boolean = false, dateAdded: Long = System.currentTimeMillis()): LocalSongEntity {
    return LocalSongEntity(
        id = id,
        title = title,
        artistName = artistName,
        albumId = albumId,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        isLocal = isLocal,
        streamUrl = streamUrl,
        isLiked = isLiked,
        dateAdded = dateAdded
    )
}
