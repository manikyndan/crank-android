package com.crank.music.domain.model

data class Album(
    val id: String,
    val title: String,
    val artistName: String,
    val releaseYear: String,
    val artworkUrl: String,
    val trackCount: Int
)

/**
 * An album card plus the release kind its search card declared ("Single",
 * "EP", or "" for full albums and cards that declare none). The kind is read
 * once where the subtitle exists and travels with the album so downstream
 * screens can split discographies without re-parsing anything.
 */
data class AlbumWithKind(
    val album: Album,
    val kind: String,
)
