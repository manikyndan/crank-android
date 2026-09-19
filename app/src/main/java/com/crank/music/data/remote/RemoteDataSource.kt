package com.crank.music.data.remote

import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject

@Serializable
data class ITunesSearchResponse(
    @SerialName("resultCount") val resultCount: Int = 0,
    @SerialName("results") val results: List<ITunesTrackDto> = emptyList()
)

@Serializable
data class ITunesTrackDto(
    @SerialName("trackId") val trackId: Long? = null,
    @SerialName("trackName") val trackName: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("collectionId") val collectionId: Long? = null,
    @SerialName("collectionName") val collectionName: String? = null,
    @SerialName("trackTimeMillis") val trackTimeMillis: Long? = null,
    @SerialName("artworkUrl100") val artworkUrl100: String? = null,
    @SerialName("previewUrl") val previewUrl: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null
)

interface RemoteDataSource {
    suspend fun searchMusic(query: String): List<Song>
    suspend fun getHomeData(): List<Album>
}

class RemoteDataSourceImpl @Inject constructor(
    private val client: HttpClient
) : RemoteDataSource {

    override suspend fun searchMusic(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try {
            val response: ITunesSearchResponse = client.get("https://itunes.apple.com/search") {
                parameter("term", query)
                parameter("media", "music")
                parameter("limit", 20)
            }.body()

            response.results.mapNotNull { it.toSong() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getHomeData(): List<Album> {
        return try {
            val response: ITunesSearchResponse = client.get("https://itunes.apple.com/search") {
                parameter("term", "top hits")
                parameter("media", "music")
                parameter("limit", 15)
            }.body()

            response.results
                .filter { !it.collectionName.isNullOrBlank() }
                .distinctBy { it.collectionId ?: it.collectionName }
                .map { it.toAlbum() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun ITunesTrackDto.toSong(): Song? {
        val name = trackName ?: return null
        val stream = previewUrl ?: return null
        val artwork = ArtworkUrl.upgrade(artworkUrl100.orEmpty())

        return Song(
            id = trackId?.toString() ?: UUID.randomUUID().toString(),
            title = name,
            artistName = artistName ?: "Unknown Artist",
            albumId = collectionId?.toString(),
            // Unknown, not invented: a default of three minutes renders as "3:00" for every track
            // and cannot be distinguished from a real duration.
            durationMs = trackTimeMillis ?: 0L,
            artworkUrl = artwork,
            isLocal = false,
            streamUrl = stream
        )
    }

    private fun ITunesTrackDto.toAlbum(): Album {
        val artwork = ArtworkUrl.upgrade(artworkUrl100.orEmpty())
        val year = releaseDate?.take(4) ?: "2024"

        return Album(
            id = collectionId?.toString() ?: UUID.randomUUID().toString(),
            title = collectionName ?: "Unknown Album",
            artistName = artistName ?: "Unknown Artist",
            releaseYear = year,
            artworkUrl = artwork,
            trackCount = 10
        )
    }
}
