package com.crank.music.data.remote

import android.util.Log
import com.crank.music.data.remote.innertube.InnerTubeApi
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRemoteDataSource @Inject constructor(
    private val innerTubeApi: InnerTubeApi
) : RemoteDataSource {

    override suspend fun searchMusic(query: String): List<Song> {
        if (query.isBlank()) return emptyList()

        return try {
            val results = innerTubeApi.searchMusic(query)
            if (results.isNotEmpty()) {
                results
            } else {
                searchMusicFallback(query)
            }
        } catch (e: Exception) {
            Log.e("CRANK_INTEGRATION", e.message ?: "InnerTube search error, trying fallback", e)
            searchMusicFallback(query)
        }
    }

    private suspend fun searchMusicFallback(query: String): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                val searchExtractor = ServiceList.YouTube.getSearchExtractor(
                    query,
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                    ""
                )
                searchExtractor.fetchPage()

                val items = searchExtractor.initialPage.items
                items.filterIsInstance<StreamInfoItem>().map { item ->
                    val videoId = item.url.substringAfter("v=").substringBefore("&")
                    Song(
                        id = videoId,
                        title = item.name ?: "Unknown Track",
                        artistName = item.uploaderName ?: "Unknown Artist",
                        albumId = null,
                        durationMs = if (item.duration > 0) item.duration * 1000L else 180_000L,
                        artworkUrl = item.thumbnails.lastOrNull()?.url ?: "",
                        isLocal = false,
                        streamUrl = videoId
                    )
                }
            } catch (e: Exception) {
                Log.e("CRANK_INTEGRATION", e.message ?: "Fallback search failed", e)
                emptyList()
            }
        }
    }

    override suspend fun getHomeData(): List<Album> {
        return try {
            val results = innerTubeApi.getHomeData()
            if (results.isNotEmpty()) {
                results
            } else {
                getHomeDataFallback()
            }
        } catch (e: Exception) {
            Log.e("CRANK_INTEGRATION", e.message ?: "InnerTube home error, trying fallback", e)
            getHomeDataFallback()
        }
    }

    private suspend fun getHomeDataFallback(): List<Album> {
        return withContext(Dispatchers.IO) {
            try {
                val searchExtractor = ServiceList.YouTube.getSearchExtractor(
                    "Top Music Albums",
                    listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS),
                    ""
                )
                searchExtractor.fetchPage()

                val items = searchExtractor.initialPage.items
                items.filterIsInstance<PlaylistInfoItem>().map { item ->
                    val browseId = item.url.substringAfter("list=").substringBefore("&")
                    Album(
                        id = browseId,
                        title = item.name ?: "Unknown Album",
                        artistName = item.uploaderName ?: "Various Artists",
                        releaseYear = "2024",
                        artworkUrl = item.thumbnails.lastOrNull()?.url ?: "",
                        trackCount = item.streamCount.toInt()
                    )
                }
            } catch (e: Exception) {
                Log.e("CRANK_INTEGRATION", e.message ?: "Fallback home failed", e)
                emptyList()
            }
        }
    }
}
