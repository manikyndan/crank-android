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
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("trackCount") val trackCount: Int? = null
)

interface RemoteDataSource {
    suspend fun searchMusic(query: String): List<Song>
    suspend fun searchAlbums(query: String): List<Album>
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

    override suspend fun searchAlbums(query: String): List<Album> {
        if (query.isBlank()) return emptyList()
        return try {
            val response: ITunesSearchResponse = client.get("https://itunes.apple.com/search") {
                parameter("term", query)
                parameter("media", "music")
                parameter("entity", "album")
                parameter("limit", 10)
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
        // iTunes dates read "2019-05-17T07:00:00Z"; the leading four characters are the year
        // and the only part the UI shows. A missing date is blank, not a stand-in: "2024" put
        // a real-looking year on every album iTunes had no date for, indistinguishable from
        // one it did. Same rule as the durationMs default above.
        val year = releaseDate?.take(4) ?: ""

        return Album(
            id = collectionId?.toString() ?: UUID.randomUUID().toString(),
            title = collectionName ?: "Unknown Album",
            artistName = artistName ?: "Unknown Artist",
            releaseYear = year,
            artworkUrl = artwork,
            // The real count, which iTunes reports on the collection. The literal 10 that stood
            // here was applied to every album regardless of how many tracks it held, so the
            // number on screen was invented even though the field looked populated.
            trackCount = trackCount ?: 0
        )
    }
}
