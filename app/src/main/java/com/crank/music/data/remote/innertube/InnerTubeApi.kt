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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

    private companion object {
        /** Every lyrics-tab browse id begins with this; how the tab is recognised in `next`. */
        private const val LYRICS_BROWSE_PREFIX = "MPLYt"
    }

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
                        var durationMs = 0L

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

                        // The flex-column scan above only finds a duration when it happens to sit
                        // among the title and artist; in a music row it is in `fixedColumns`
                        // instead, so fill it in from there rather than leaving every search result
                        // at the default.
                        if (durationMs == 0L) durationMs = parseRowDuration(listItem)

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

            val browseContents = response["contents"]?.jsonObject ?: return emptyList()

            // YouTube Music serves two different page layouts and the track list sits in a
            // different place in each, so both are collected rather than assuming one.
            //
            // Measured on device: a real playlist/radio browse came back as
            // `twoColumnBrowseResultsRenderer` with the shelf under `secondaryContents`, and the
            // previous single-column-only lookup matched nothing — it returned zero tracks from a
            // 3 MB response full of them, which is indistinguishable from "this album is empty".
            val sections =
                shelfSections(browseContents["singleColumnBrowseResultsRenderer"]?.jsonObject) +
                    shelfSections(browseContents["twoColumnBrowseResultsRenderer"]?.jsonObject)

            val songs = mutableListOf<Song>()
            for (sectionObj in sections) {
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
     * Collects the section lists that can hold a track shelf from one browse-results renderer.
     *
     * Returns every candidate rather than the first, because a page may legitimately carry more
     * than one shelf (an album's tracks plus a "more from this artist" shelf), and dropping the
     * extras silently truncates a real tracklist.
     *
     * Both known locations are covered: `secondaryContents` (the two-column layout) and each
     * tab's `content` (the single-column layout, and some two-column pages).
     */
    private fun shelfSections(renderer: JsonObject?): List<JsonObject> {
        renderer ?: return emptyList()
        val sections = mutableListOf<JsonObject>()

        renderer["secondaryContents"]?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray
            ?.forEach { sections += it.jsonObject }

        renderer["tabs"]?.jsonArray?.forEach { tab ->
            tab.jsonObject["tabRenderer"]?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("sectionListRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?.forEach { sections += it.jsonObject }
        }

        return sections
    }

    /**
     * Reads a row's running time.
     *
     * A music shelf row keeps the duration in `fixedColumns`, not with the title and artist in
     * `flexColumns`. Measured on a real album response: `flexColumns=[Gehra Hua, Arijit Singh, …]`,
     * `fixedColumns=[3:51]`. Reading only flex columns therefore never found a duration, and every
     * track fell through to the default of 180 000 ms — three invented minutes that the UI rendered
     * as "3:00" for every song in the library, and that lyric matching then scored candidates
     * against as though it were real.
     *
     * Returns `0` when the row carries no duration. Inventing a plausible value is worse than
     * admitting it is missing: a fabricated number is silently wrong for every track and cannot be
     * told apart from a real one downstream.
     */
    private fun parseRowDuration(listItem: JsonObject): Long {
        val columns = (listItem["fixedColumns"]?.jsonArray ?: emptyList()) +
            (listItem["flexColumns"]?.jsonArray ?: emptyList())

        for (column in columns) {
            val renderer = column.jsonObject.let {
                it["musicResponsiveListItemFixedColumnRenderer"]
                    ?: it["musicResponsiveListItemFlexColumnRenderer"]
            } ?: continue

            val runs = renderer.jsonObject["text"]?.jsonObject?.get("runs")?.jsonArray ?: continue
            val text = runs.joinToString("") {
                it.jsonObject["text"]?.jsonPrimitive?.content ?: ""
            }.trim()

            // Kept strict: "Intro: Serenade" is a title, not a running time.
            if (looksLikeDuration(text)) return parseDuration(text)
        }
        return 0L
    }

    /**
     * Pulls (title, artist, duration) out of a `musicResponsiveListItemRenderer`'s flex columns.
     * Shared shape with [searchMusic]'s per-item parse, kept local to avoid perturbing that path.
     */
    private fun parseTrackColumns(listItem: JsonObject): Triple<String, String, Long> {
        var title = "Unknown Track"
        var artistName = "Unknown Artist"
        var durationMs = 0L
        val flexColumns = listItem["flexColumns"]?.jsonArray
            ?: return Triple(title, artistName, durationMs)

        // Flatten the non-blank columns first. Skipping blanks matters because a row with no
        // artist still emits an empty column, and indexing into the raw array would then read
        // the album as the artist.
        val texts = ArrayList<String>(flexColumns.size)
        for (column in flexColumns) {
            val runs = column.jsonObject
                .get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject
                ?.get("runs")?.jsonArray ?: continue
            val text = runs.joinToString("") {
                it.jsonObject.get("text")?.jsonPrimitive?.content ?: ""
            }.trim()
            if (text.isNotBlank()) texts += text
        }
        if (texts.isEmpty()) return Triple(title, artistName, durationMs)

        // Position carries the meaning in a music shelf row: title, then artist, then album, with
        // the duration last. Reading by position is what lets a single-run artist column be
        // recognised — the previous `runs.size > 1` test silently dropped every artist whose name
        // was one run, which is most of them, and those rows rendered as "Unknown Artist".
        title = texts.first()
        durationMs = parseRowDuration(listItem)
        artistName = texts.drop(1).firstOrNull { !looksLikeDuration(it) } ?: "Unknown Artist"

        return Triple(title, artistName, durationMs)
    }

    /**
     * True when [text] is a bare timestamp such as `3:45` or `1:02:33`.
     *
     * Kept strict — every part must be digits — because a loose `contains(":")` test mistakes
     * titled tracks like "Intro: Serenade" for a duration and then shifts every later column.
     */
    private fun looksLikeDuration(text: String): Boolean {
        if (!text.contains(':')) return false
        val parts = text.split(':')
        if (parts.size !in 2..3) return false
        return parts.all { part -> part.trim().isNotEmpty() && part.trim().all { it.isDigit() } }
    }

    /**
     * Fetches the lyrics YouTube Music holds for [videoId], or `null` when there are none.
     *
     * ## What was wrong before
     *
     * This method previously read `return null`. The parser below it —
     * Wiring the call up was necessary but not sufficient: the lyrics browse id was also being
     * computed locally from the video id, and that computed id resolves to an empty page. So the
     * method ran, found nothing, and every track still reported "lyrics unavailable". Both halves
     * are fixed here — see [findLyricsBrowseId] and [extractLyricsFromBrowse].
     *
     * ## The request
     *
     * YouTube Music exposes lyrics only through `browse`, not through `next` or `player`. The
     * lyrics live on a `MPLYt…` page whose id is *published* by the watch-next response; it has no
     * derivable relationship to the video id, so it has to be read, not computed.
     *
     * A wrong id does not produce an error, it produces an empty response, which is
     * indistinguishable from "this track has no lyrics" — hence the note.
     *
     * ## Coverage
     *
     * YouTube Music only carries lyrics for part of its catalogue, and for the rest it answers
     * with a `messageRenderer` reading "Lyrics not available" rather than an error. That is an
     * authoritative answer, not a parsing failure: measured on device, 1 of 10 tracks sampled from
     * the app's own feed had lyrics here. So a `null` from this method is normal and the caller
     * must fall back to a second source rather than treat it as a fault.
     *
     * Returns `null` rather than throwing: a missing lyric is a normal state, not a failure, and
     * the caller has a second source to fall back to.
     */
    suspend fun fetchLyrics(videoId: String): String? {
        if (videoId.isBlank()) return null

        return try {
            // The lyrics browse id is published by the watch-next response; it is not derivable.
            //
            // It used to be computed here by shifting each character of the video id, which yields
            // a well-formed `MPLYt…` id that resolves to an empty page. Measured on device: the
            // computed id returned a 1.9 KB response with no lyrics shelf, while the id YouTube
            // Music actually advertises returned 917 characters of real lyrics for the same track.
            // Every track therefore reported "lyrics unavailable" while the lyrics existed.
            val browseId = findLyricsBrowseId(videoId) ?: return null

            val requestBody = buildJsonObject {
                put("context", innerTubeContext)
                put("browseId", JsonPrimitive(browseId))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withMusicKey("browse"))
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            extractLyricsFromBrowse(response)?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Logged at debug: a track without lyrics is the common case, and a warning per
            // track would drown out real failures.
            Log.d(TAG, "Lyrics unavailable for $videoId: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Finds the browse id of a track's lyrics tab.
     *
     * YouTube Music advertises it in the watch-next response as a `browseEndpoint` whose id begins
     * `MPLYt`. Reading it from there is the only reliable way to get it: the id is opaque, with no
     * stable relationship to the video id, so anything computed locally is a guess that looks
     * plausible and silently rots.
     *
     * Returns `null` when the track has no lyrics tab, which is a normal state rather than an
     * error — the caller falls back to a second source.
     */
    private suspend fun findLyricsBrowseId(videoId: String): String? {
        val requestBody = buildJsonObject {
            put("context", innerTubeContext)
            put("videoId", JsonPrimitive(videoId))
        }

        val response: JsonObject = client.post {
            url(innerTubeConfig.withMusicKey("next"))
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        return findFirstString(response, "browseId") { it.startsWith(LYRICS_BROWSE_PREFIX) }
    }

    /**
     * Pulls the lyric text out of a `browse` response.
     *
     * The shelf is located by search rather than by a fixed path. The previous fixed path assumed
     * `contents → twoColumnBrowseResultsRenderer → tabs[0] → tabRenderer → content →
     * sectionListRenderer`, but a real lyrics page carries none of that: it puts the shelf at
     * `contents.sectionListRenderer.contents[0]`, directly under `contents`. So the old lookup
     * found nothing even when handed the correct browse id — which is why fixing the id alone
     * would not have fixed lyrics.
     */
    private fun extractLyricsFromBrowse(response: JsonObject): String? {
        val shelf = findFirstObject(response, "musicDescriptionShelfRenderer") ?: return null
        val description = shelf["description"]?.jsonObject ?: return null

        val runs = description["runs"]?.jsonArray
        if (runs != null && runs.isNotEmpty()) {
            val lyrics = runs.joinToString("") {
                it.jsonObject["text"]?.jsonPrimitive?.content ?: ""
            }.trim()
            if (lyrics.isNotBlank()) return lyrics
        }

        return description["simpleText"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }

    /** Finds the first object stored under [key] anywhere in [root]. */
    private fun findFirstObject(root: JsonElement, key: String): JsonObject? {
        when (root) {
            is JsonObject -> {
                root[key]?.let { candidate ->
                    runCatching { candidate.jsonObject }.getOrNull()?.let { return it }
                }
                for ((_, value) in root) findFirstObject(value, key)?.let { return it }
            }
            is JsonArray -> {
                for (element in root) findFirstObject(element, key)?.let { return it }
            }
            else -> Unit
        }
        return null
    }

    /** Finds the first string stored under [key] anywhere in [root] that satisfies [accept]. */
    private fun findFirstString(
        root: JsonElement,
        key: String,
        accept: (String) -> Boolean,
    ): String? {
        when (root) {
            is JsonObject -> {
                root[key]?.let { candidate ->
                    val text = runCatching { candidate.jsonPrimitive.content }.getOrNull()
                    if (text != null && accept(text)) return text
                }
                for ((_, value) in root) findFirstString(value, key, accept)?.let { return it }
            }
            is JsonArray -> {
                for (element in root) findFirstString(element, key, accept)?.let { return it }
            }
            else -> Unit
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
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
