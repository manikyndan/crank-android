package com.crank.music.data.remote.innertube

import android.util.Log
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnerTubeApi @Inject constructor(
    private val client: HttpClient,
    private val innerTubeConfig: InnerTubeConfig
) {
    private val TAG = "CRANK_INNERTUBE"

    private val innerTubeContext = buildJsonObject {
        put("client", buildJsonObject {
            put("clientName", JsonPrimitive("WEB_REMIX"))
            put("clientVersion", JsonPrimitive("1.20231212.00.00"))
            put("hl", JsonPrimitive("en"))
            put("gl", JsonPrimitive("US"))
        })
    }

    suspend fun searchMusic(query: String): List<Song> {
        if (query.isBlank()) return emptyList()

        return try {
            val requestBody = buildJsonObject {
                put("context", innerTubeContext)
                put("query", JsonPrimitive(query))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withMusicKey("search"))
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val contents = response["contents"]?.jsonObject
                ?.get("tabbedSearchResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?: return emptyList()

            val songs = mutableListOf<Song>()

            for (tab in contents) {
                val tabContent = tab.jsonObject
                    .get("tabRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?: continue

                val sectionList = tabContent
                    .get("sectionListRenderer")?.jsonObject
                    ?.get("contents")?.jsonArray
                    ?: continue

                for (section in sectionList) {
                    val musicShelf = section.jsonObject
                        .get("musicShelfRenderer")?.jsonObject
                        ?.get("contents")?.jsonArray
                        ?: continue

                    for (item in musicShelf) {
                        val listItem = item.jsonObject
                            .get("musicResponsiveListItemRenderer")?.jsonObject
                            ?: continue

                        val videoId = extractVideoId(listItem) ?: continue
                        val flexColumns = listItem.get("flexColumns")?.jsonArray ?: continue

                        var title = "Unknown Track"
                        var artistName = "Unknown Artist"
                        var durationMs = 180_000L

                        for (column in flexColumns) {
                            val runs = column.jsonObject
                                .get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                                ?.get("text")?.jsonObject
                                ?.get("runs")?.jsonArray
                                ?: continue

                            val text = runs.joinToString("") {
                                it.jsonObject.get("text")?.jsonPrimitive?.content ?: ""
                            }.trim()

                            when {
                                text.isBlank() -> continue
                                runs.size == 1 && text.contains(":") -> {
                                    durationMs = parseDuration(text)
                                }
                                else -> {
                                    val firstRunText = runs.firstOrNull()
                                        ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                                    if (title == "Unknown Track" && firstRunText.isNotBlank() && !firstRunText.contains(":")) {
                                        title = firstRunText
                                    } else if (artistName == "Unknown Artist" && runs.size > 1) {
                                        artistName = text
                                    }
                                }
                            }
                        }

                        val artworkUrl = extractArtworkUrl(listItem)

                        songs.add(
                            Song(
                                id = videoId,
                                title = title,
                                artistName = artistName,
                                albumId = null,
                                durationMs = durationMs,
                                artworkUrl = artworkUrl,
                                isLocal = false,
                                streamUrl = videoId
                            )
                        )
                    }
                }
            }
            songs
        } catch (e: Exception) {
            Log.e("CRANK_INTEGRATION", "InnerTube search failed: ${e.message}", e)
            Log.e("CRANK_INTEGRATION", "Search URL: ${innerTubeConfig.musicBaseUrl}/search")
            Log.e("CRANK_INTEGRATION", "Query: $query")
            emptyList()
        }
    }

    suspend fun getHomeData(): List<Album> {
        return try {
            val requestBody = buildJsonObject {
                put("context", innerTubeContext)
                put("browseId", JsonPrimitive("FEmusic_home"))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withMusicKey("browse"))
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val contents = response["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?: return emptyList()

            val albums = mutableListOf<Album>()

            for (section in contents) {
                val shelf = section.jsonObject
                    .get("musicShelfRenderer")?.jsonObject
                    ?: section.jsonObject.get("musicCarouselShelfRenderer")?.jsonObject
                    ?: continue

                val items = shelf.get("contents")?.jsonArray ?: continue

                for (item in items) {
                    val twoRowItem = item.jsonObject
                        .get("musicTwoRowItemRenderer")?.jsonObject
                        ?: continue

                    val browseId = twoRowItem
                        .get("navigationEndpoint")?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.content ?: continue

                    val title = twoRowItem
                        .get("title")?.jsonObject
                        ?.get("runs")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("text")?.jsonPrimitive?.content ?: "Unknown Album"

                    val subtitle = twoRowItem
                        .get("subtitle")?.jsonObject
                        ?.get("runs")?.jsonArray
                        ?.joinToString("") {
                            it.jsonObject.get("text")?.jsonPrimitive?.content ?: ""
                        } ?: ""

                    val artistName = subtitle.split("•").firstOrNull()?.trim() ?: "Unknown Artist"
                    val year = subtitle.split("•").lastOrNull()?.trim() ?: "2024"

                    val artworkUrl = extractArtworkUrl(twoRowItem)

                    albums.add(
                        Album(
                            id = browseId,
                            title = title,
                            artistName = artistName,
                            releaseYear = year,
                            artworkUrl = artworkUrl,
                            trackCount = 0
                        )
                    )
                }
            }
            albums
        } catch (e: Exception) {
            Log.e("CRANK_INTEGRATION", "InnerTube home failed: ${e.message}", e)
            Log.e("CRANK_INTEGRATION", "Browse URL: ${innerTubeConfig.musicBaseUrl}/browse")
            emptyList()
        }
    }

    /**
     * Real YouTube Music browse endpoint for a single album or playlist, addressed by its
     * [browseId] — the id the home/explore feeds already hand us for each card.
     *
     * ## Why this exists
     *
     * This is the missing half of album navigation. Previously the album screen had no way to ask
     * YouTube for an album's *tracklist* — `browseId` was thrown away at navigation and the screen
     * instead searched the album's *name* as free text and labelled whatever came back. That returns
     * a bag of vaguely-related songs, not the album. Here we hit the actual `browse` endpoint and
     * parse its track shelf, reusing the same video-id / artwork / duration extraction that the
     * search and home paths already rely on, so the album opens to the real songs.
     */
    suspend fun browsePlaylistOrAlbum(browseId: String): List<Song> {
        if (browseId.isBlank()) return emptyList()
        return try {
            val requestBody = buildJsonObject {
                put("context", innerTubeContext)
                put("browseId", JsonPrimitive(browseId))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withMusicKey("browse"))
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val contents = response["contents"]?.jsonObject
                ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
                ?.get("tabs")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("tabRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?: return emptyList()

            val songs = mutableListOf<Song>()
            for (section in contents) {
                val sectionObj = section.jsonObject
                // Albums expose tracks via musicShelfRenderer; playlists via musicPlaylistShelfRenderer.
                val shelf = sectionObj["musicShelfRenderer"]?.jsonObject
                    ?: sectionObj["musicPlaylistShelfRenderer"]?.jsonObject
                    ?: continue
                val items = shelf["contents"]?.jsonArray ?: continue
                for (item in items) {
                    val track = item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject ?: continue
                    val videoId = extractVideoId(track) ?: continue
                    val (title, artistName, durationMs) = parseTrackColumns(track)
                    val artworkUrl = extractArtworkUrl(track)
                    songs.add(
                        Song(
                            id = videoId,
                            title = title,
                            artistName = artistName,
                            albumId = browseId,
                            durationMs = durationMs,
                            artworkUrl = artworkUrl,
                            isLocal = false,
                            streamUrl = videoId
                        )
                    )
                }
            }
            songs
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Browse '$browseId' failed: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    /**
     * Pulls (title, artist, duration) out of a `musicResponsiveListItemRenderer`'s flex columns.
     * Shared shape with [searchMusic]'s per-item parse, kept local to avoid perturbing that path.
     */
    private fun parseTrackColumns(listItem: JsonObject): Triple<String, String, Long> {
        var title = "Unknown Track"
        var artistName = "Unknown Artist"
        var durationMs = 180_000L
        val flexColumns = listItem["flexColumns"]?.jsonArray ?: return Triple(title, artistName, durationMs)
        for (column in flexColumns) {
            val runs = column.jsonObject
                .get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray ?: continue
            val text = runs.joinToString("") {
                it.jsonObject.get("text")?.jsonPrimitive?.content ?: ""
            }.trim()
            when {
                text.isBlank() -> continue
                runs.size == 1 && text.contains(":") -> durationMs = parseDuration(text)
                else -> {
                    val firstRunText = runs.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content ?: ""
                    if (title == "Unknown Track" && firstRunText.isNotBlank() && !firstRunText.contains(":")) {
                        title = firstRunText
                    } else if (artistName == "Unknown Artist" && runs.size > 1) {
                        artistName = text
                    }
                }
            }
        }
        return Triple(title, artistName, durationMs)
    }

    /**
     * Fetches the lyrics YouTube Music holds for [videoId], or `null` when there are none.
     *
     * ## What was wrong before
     *
     * This method previously read `return null`. The parser below it —
     * [extractLyricsFromBrowse] — was complete and correct, but nothing ever called it, so the
     * lyrics tab was dead code and every track fell through to the LRCLIB path. That is the
     * "lyrics never load" and "lyrics don't match the song" report, and no amount of work on the
     * display side would have fixed it.
     *
     * ## The request
     *
     * YouTube Music exposes lyrics only through `browse`, not through `next` or `player`. The
     * lyrics live under a tab whose browse id is derived from the track's video id: the ASCII
     * codepoints are incremented by one and rendered as hex, then prefixed with `MPLYt`.
     *
     * That transform is stable and cheap, but it is not self-documenting — hence this note. A
     * wrong suffix does not produce an error, it produces an empty response, which is
     * indistinguishable from "this track has no lyrics".
     *
     * Returns `null` rather than throwing: a missing lyric is a normal state, not a failure, and
     * the caller has a second source to fall back to.
     */
    suspend fun fetchLyrics(videoId: String): String? {
        if (videoId.isBlank()) return null

        return try {
            val requestBody = buildJsonObject {
                put("context", innerTubeContext)
                put("browseId", JsonPrimitive(lyricsBrowseId(videoId)))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withMusicKey("browse"))
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            extractLyricsFromBrowse(response)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            // Logged at debug: a track without lyrics is the common case, and a warning per
            // track would drown out real failures.
            Log.d(TAG, "Lyrics unavailable for $videoId: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Derives the browse id of a track's lyrics tab from its video id.
     *
     * Each character is shifted up by one codepoint and hex-encoded, and the result is prefixed
     * with `MPLYt`. This mirrors how YouTube Music builds the id internally; the shift is what
     * prevents the plain video id from being usable directly.
     */
    private fun lyricsBrowseId(videoId: String): String {
        val shifted = buildString {
            for (char in videoId) {
                append((char.code + 1).toString(16))
            }
        }
        return "MPLYt$shifted"
    }

    private fun extractLyricsFromBrowse(response: JsonObject): String? {
        val tabs = response["contents"]?.jsonObject
            ?.get("twoColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }

        val content = tabs?.firstOrNull()?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?.firstOrNull()

        val description = content?.get("musicDescriptionShelfRenderer")?.jsonObject
            ?.get("description")?.jsonObject

        val runs = description?.get("runs")?.jsonArray
        if (runs != null && runs.isNotEmpty()) {
            val lyrics = runs.joinToString("") {
                it.jsonObject["text"]?.jsonPrimitive?.content ?: ""
            }.trim()
            if (lyrics.isNotBlank()) return lyrics
        }

        val text = content?.get("musicDescriptionShelfRenderer")?.jsonObject
            ?.get("description")?.jsonObject
            ?.get("simpleText")?.jsonPrimitive?.content
        if (!text.isNullOrBlank()) return text

        val musicShelfRenderer = content?.get("musicShelfRenderer")?.jsonObject
        val shelfRuns = musicShelfRenderer?.get("contents")?.jsonArray
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?.mapNotNull { it["musicResponsiveListItemRenderer"]?.jsonObject }
            ?.mapNotNull { it["flexColumns"]?.jsonArray }
            ?.flatten()
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?.mapNotNull { it["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject }
            ?.mapNotNull { it["text"]?.jsonObject?.get("runs")?.jsonArray }
            ?.mapNotNull { runs ->
                runs.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }.trim()
            }
            ?.filter { it.isNotBlank() }

        if (shelfRuns != null && shelfRuns.isNotEmpty()) {
            return shelfRuns.joinToString("\n")
        }

        return null
    }

    private fun extractVideoId(listItem: JsonObject): String? {
        val flexVideoId = listItem["flexColumns"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
            ?.get("text")?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("navigationEndpoint")?.jsonObject?.get("watchEndpoint")?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.content
        if (!flexVideoId.isNullOrBlank() && flexVideoId.length == 11) return flexVideoId

        val playlistVideoId = listItem["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.content
        if (!playlistVideoId.isNullOrBlank() && playlistVideoId.length == 11) return playlistVideoId

        val doubleTapVideoId = listItem["doubleTapCommand"]?.jsonObject?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.content
        if (!doubleTapVideoId.isNullOrBlank() && doubleTapVideoId.length == 11) return doubleTapVideoId

        val overlayVideoId = listItem["overlay"]?.jsonObject?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
            ?.get("content")?.jsonObject?.get("musicPlayButtonRenderer")?.jsonObject
            ?.get("playNavigationEndpoint")?.jsonObject?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.content
        if (!overlayVideoId.isNullOrBlank() && overlayVideoId.length == 11) return overlayVideoId

        val navVideoId = listItem["navigationEndpoint"]?.jsonObject?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.content
        if (!navVideoId.isNullOrBlank() && navVideoId.length == 11) return navVideoId

        return null
    }

    private fun extractArtworkUrl(item: JsonObject): String {
        val thumbnails = item["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
            ?: item["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
            ?: item["thumbnailRenderer"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray

        val rawUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
        val baseUrl = when {
            rawUrl.isBlank() -> ""
            rawUrl.startsWith("//") -> "https:$rawUrl"
            else -> rawUrl
        }

        if (baseUrl.isBlank()) return ""

        // Upgrade to high-res if it's a YouTube thumbnail
        return when {
            baseUrl.contains("ytimg.com") -> {
                // Extract video ID from thumbnail URL and use maxresdefault
                val videoIdMatch = Regex("""vi/([^/]+)/""").find(baseUrl)
                if (videoIdMatch != null) {
                    val videoId = videoIdMatch.groupValues[1]
                    "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
                } else {
                    baseUrl.replace("default.jpg", "hqdefault.jpg")
                        .replace("mqdefault.jpg", "hqdefault.jpg")
                }
            }
            baseUrl.contains("iTunes") || baseUrl.contains("apple") -> {
                // iTunes: upgrade from 100x100 to 600x600
                baseUrl.replace("100x100bb", "600x600bb")
                    .replace("100x100", "600x600")
            }
            else -> baseUrl
        }
    }

    private fun parseDuration(duration: String): Long {
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> (parts[0].toLongOrNull() ?: 0L) * 60_000 + (parts[1].toLongOrNull() ?: 0L) * 1_000
                3 -> (parts[0].toLongOrNull() ?: 0L) * 3_600_000 + (parts[1].toLongOrNull() ?: 0L) * 60_000 + (parts[2].toLongOrNull() ?: 0L) * 1_000
                else -> 180_000L
            }
        } catch (e: Exception) {
            180_000L
        }
    }
}
