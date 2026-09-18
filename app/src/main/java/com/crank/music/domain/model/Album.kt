package com.crank.music.domain.model

data class Album(
    val id: String,
    val title: String,
    val artistName: String,
    val releaseYear: String,
    val artworkUrl: String,
    val trackCount: Int
)
