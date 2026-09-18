package com.crank.music.domain.model

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String,
    val followerCount: Long
)
