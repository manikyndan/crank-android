package com.crank.music.domain.model

data class Playlist(
    val id: String,
    val title: String,
    val songCount: Int,
    val artworkUrl: String,
    val songs: List<Song> = emptyList()
)
