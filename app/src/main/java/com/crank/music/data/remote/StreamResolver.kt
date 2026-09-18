package com.crank.music.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

data class StreamData(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

@Singleton
class StreamResolver @Inject constructor(
    private val youtubeStreamResolver: YouTubeStreamResolver
) {
    suspend fun init() {
        try {
            youtubeStreamResolver.fetchVisitorData()
        } catch (e: Exception) {
            Log.e("CRANK_STREAM", "Failed to pre-fetch visitorData: ${e.message}")
        }
    }

    suspend fun resolveStreamUrl(videoId: String, songTitle: String = "", artistName: String = ""): StreamData {
        Log.d("CRANK_STREAM", "Resolving: $videoId ($songTitle - $artistName)")
        return youtubeStreamResolver.getSongStreamUrl(videoId, songTitle, artistName)
    }
}
