package com.crank.music.data.repository

import android.util.Log
import com.crank.music.data.local.SongDao
import com.crank.music.data.local.toDomainModel
import com.crank.music.data.remote.RemoteDataSource
import com.crank.music.data.remote.StreamData
import com.crank.music.data.remote.StreamResolver
import com.crank.music.data.remote.innertube.InnerTubeApi
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.LyricsSearchResult
import com.crank.music.domain.repository.MusicRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val remoteDataSource: RemoteDataSource,
    private val streamResolver: StreamResolver,
    private val innerTubeApi: InnerTubeApi,
    private val httpClient: HttpClient
) : MusicRepository {

    override suspend fun search(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        // Cancellation must propagate: this runs inside a debounced, cancellable search job, so
        // swallowing CancellationException here left the coroutine alive past its cancellation
        // point and searches never settled. See CoroutineDiscipline.
        val remoteResults = try {
            remoteDataSource.searchMusic(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CRANK_INTEGRATION", "Search failed: ${e.message}", e)
            emptyList()
        }

        if (remoteResults.isNotEmpty()) {
            return remoteResults
        }

        val downloaded = songDao.getDownloadedSongs()
        val filtered = downloaded.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artistName.contains(query, ignoreCase = true)
        }.map { it.toDomainModel() }

        return filtered
    }

    override suspend fun getHomeRecommendations(): List<Album> {
        // No fabricated fallback. Returning a hardcoded "Featured Album" here made a total network
        // failure indistinguishable from success and put fiction on screen. An empty list is the
        // honest answer, and the UI already renders an empty state.
        return try {
            remoteDataSource.getHomeData()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CRANK_INTEGRATION", "Home recommendations failed: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun getSongStreamUrl(songId: String, songTitle: String, artistName: String): StreamData {
        Log.d("CRANK_INTEGRATION", "MusicRepositoryImpl.getSongStreamUrl for: $songId ($songTitle - $artistName)")
        val streamData = streamResolver.resolveStreamUrl(songId, songTitle, artistName)
        Log.d("CRANK_INTEGRATION", "MusicRepositoryImpl resolved URL: ${streamData.url.take(100)} for: $songId")
        return streamData
    }

    override suspend fun getSongDetails(songId: String): Song {
        val localSong = songDao.getSongById(songId)
        return localSong?.let {
            Song(
                id = it.id,
                title = it.title,
                artistName = it.artistName,
                albumId = it.albumId,
                durationMs = it.durationMs,
                artworkUrl = it.artworkUrl,
                isLocal = it.isLocal
            )
        } ?: Song(
            id = songId,
            title = "Unknown Song",
            artistName = "Unknown Artist",
            albumId = null,
            durationMs = 0L,
            artworkUrl = "",
            isLocal = false
        )
    }

    override suspend fun getLyricsByVideoId(videoId: String): String? {
        if (videoId.isBlank()) return null
        return try {
            innerTubeApi.fetchLyrics(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("CRANK_LYRICS", "YouTube Music lyrics failed for $videoId: ${e.message}")
            null
        }
    }

    override suspend fun searchLyrics(trackName: String, artistName: String): LyricsSearchResult? {
        return try {
            val url = "https://lrclib.net/api/search?track_name=${java.net.URLEncoder.encode(trackName, "UTF-8")}&artist_name=${java.net.URLEncoder.encode(artistName, "UTF-8")}"

            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "CrankMusic/1.0")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val code = conn.responseCode
            if (code != 200) {
                Log.d("CRANK_LYRICS", "LRCLIB returned $code for '$trackName'")
                conn.disconnect()
                return null
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            if (text.isBlank() || text == "[]") return null

            val response = kotlinx.serialization.json.Json.parseToJsonElement(text) as? JsonArray ?: return null
            val first = response.firstOrNull() ?: return null
            val obj = first.jsonObject
            LyricsSearchResult(
                trackName = obj["trackName"]?.jsonPrimitive?.content,
                artistName = obj["artistName"]?.jsonPrimitive?.content,
                albumName = obj["albumName"]?.jsonPrimitive?.content,
                duration = obj["duration"]?.jsonPrimitive?.long,
                plainLyrics = obj["plainLyrics"]?.jsonPrimitive?.content,
                syncedLyrics = obj["syncedLyrics"]?.jsonPrimitive?.content
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CRANK_LYRICS", "LRCLIB search failed: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }
}
