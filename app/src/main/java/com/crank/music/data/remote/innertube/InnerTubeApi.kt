package com.crank.music.data.remote.innertube

import android.util.Log
import com.crank.music.data.remote.ArtworkUrl
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

        /** The result type search must keep; the others are not playable as tracks. */
        private const val SONG_CATEGORY = "Song"

        /** YouTube addresses albums/singles/EPs with browse ids starting `MPRE`. */
        private const val ALBUM_BROWSE_PREFIX = "MPRE"

        /** Renderers that can hold `musicResponsiveListItemRenderer` rows. See [listRowsIn]. */
        private val ROW_CONTAINERS = listOf(
            "musicShelfRenderer",
            "musicCardShelfRenderer",
            "itemSectionRenderer"
        )

        /** Result types a search row can declare in its second column. */
        private val RESULT_CATEGORIES = setOf(
            "Song", "Video", "Episode", "Album", "Artist", "Playlist", "Podcast", "Profile"
        )
    }

    private val innerTubeContext = buildJsonObject {
        put("client", buildJsonObject {
            put("clientName", JsonPrimitive(YouTubeClients.WEB_REMIX.clientName))
            // Read from the client table rather than pinned here. This context declares itself
            // as WEB_REMIX, so a version hardcoded below it drifts from the one [YouTubeClients]
            // actually sends, and browse/search degrade in ways that read as network faults: a
            // shelf comes back empty while the HTTP call still reports 200. It sat at
            // 1.20231212.00.00 while the client table was two and a half years ahead.
            put("clientVersion", JsonPrimitive(YouTubeClients.WEB_REMIX.clientVersion))
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
                    for (listItem in listRowsIn(section.jsonObject)) {
                        val videoId = extractVideoId(listItem) ?: continue

                        // Search is unfiltered: a query for a track also returns videos, episodes
                        // and playlists. Only songs are playable, so the rest are dropped. A row
                        // that declares no type at all is kept, because album-style rows omit it.
                        val category = rowCategory(listItem)
                        if (category != null && category != SONG_CATEGORY) continue

                        // Shared with album browse rather than parsed again here: the inline
                        // version this replaced required the artist column to have more than one
                        // run, which silently discarded most artists.
                        val (title, artistName, durationMs) = parseTrackColumns(listItem)
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

    /**
     * Album results for a free-text query, e.g. "So Close to What" -> the Tate
     * McRae album card rather than just its songs.
     *
     * ## Why a separate method instead of widening [searchMusic]
     *
     * That method deliberately keeps only `Song` rows because its callers play
     * whatever comes back. Mixing album cards into it would hand an unplayable
     * browse id to the player. Albums live in `musicTwoRowItemRenderer` cards
     * (same shape as the home feed), so they get their own parse with their own
     * filter: only `MPRE*` browse ids are albums/singles/EPs — `VL*` and `UC*`
     * cards are playlists and artists, not albums.
     *
     * The response order is the relevance order YouTube returned; callers must
     * not re-sort it.
     */
    suspend fun searchAlbums(query: String): List<Album> {
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

            val seen = LinkedHashSet<String>()
            val albums = mutableListOf<Album>()

            for (tab in contents) {
                val sectionList = tab.jsonObject
                    .get("tabRenderer")?.jsonObject
                    ?.get("content")?.jsonObject
                    ?.get("sectionListRenderer")?.jsonObject
                    ?.get("contents")?.jsonArray
                    ?: continue

                for (section in sectionList) {
                    for (card in twoRowItemsIn(section.jsonObject)) {
                        val browseId = card
                            .get("navigationEndpoint")?.jsonObject
                            ?.get("browseEndpoint")?.jsonObject
                            ?.get("browseId")?.jsonPrimitive?.content
                            ?: continue
                        if (!browseId.startsWith(ALBUM_BROWSE_PREFIX)) continue
                        if (!seen.add(browseId)) continue

                        val title = card
                            .get("title")?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content ?: continue

                        val subtitle = card
                            .get("subtitle")?.jsonObject
                            ?.get("runs")?.jsonArray
                            ?.joinToString("") {
                                it.jsonObject.get("text")?.jsonPrimitive?.content ?: ""
                            } ?: ""

                        val (artistName, year) = parseSearchAlbumSubtitle(subtitle)
                        val artworkUrl = extractArtworkUrl(card)

                        albums.add(
                            Album(
                                id = browseId,
                                title = title,
                                artistName = artistName,
                                releaseYear = year,
                                artworkUrl = artworkUrl,
                                // Search cards carry no track count; 0 renders as unknown,
                                // which is honest — the detail screen counts the real list.
                                trackCount = 0
                            )
                        )
                    }
                }
            }
            albums
        } catch (e: Exception) {
            Log.e("CRANK_INTEGRATION", "InnerTube album search failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Every `musicTwoRowItemRenderer` card in [section], across the same
     * containers [listRowsIn] covers — search mixes song rows and album cards
     * in the same shelves, so both collectors run over the same sections.
     */
    private fun twoRowItemsIn(section: JsonObject): List<JsonObject> {
        val cards = mutableListOf<JsonObject>()
        for (key in ROW_CONTAINERS) {
            val contents = section[key]?.jsonObject?.get("contents")?.jsonArray ?: continue
            for (item in contents) {
                item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject?.let { cards += it }
            }
        }
        return cards
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

                    val (artistName, year) = parseAlbumSubtitle(subtitle)

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

        val texts = columnTexts(listItem)
        if (texts.isEmpty()) return Triple(title, artistName, durationMs)

        // Position carries the meaning in a music shelf row: title, then artist, then album, with
        // the duration last. Reading by position is what lets a single-run artist column be
        // recognised — the previous `runs.size > 1` test silently dropped every artist whose name
        // was one run, which is most of them, and those rows rendered as "Unknown Artist".
        title = texts.first()
        durationMs = parseRowDuration(listItem)
        artistName = texts.drop(1)
            .map { cleanArtistText(it) }
            .firstOrNull { !looksLikeDuration(it) && it.isNotBlank() }
            ?: "Unknown Artist"

        return Triple(title, artistName, durationMs)
    }

    /**
     * The non-blank text of each flex column, left to right.
     *
     * Skipping blanks matters because a row with no artist still emits an empty column, and
     * indexing into the raw array would then read the album as the artist.
     */
    private fun columnTexts(listItem: JsonObject): List<String> {
        val flexColumns = listItem["flexColumns"]?.jsonArray ?: return emptyList()
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
        return texts
    }

    /**
     * The result type a search row declares, e.g. `Song`, `Video`, `Episode`, or `null` when the
     * row does not declare one.
     *
     * Search is unfiltered, so the type has to be read from the row itself — it is the first
     * `•`-separated segment of the second column (`Song • Ed Sheeran`). Album rows do not carry
     * one, which is why a missing type is treated as "unknown", not as "song".
     */
    private fun rowCategory(listItem: JsonObject): String? {
        val second = columnTexts(listItem).drop(1).firstOrNull() ?: return null
        return second.substringBefore("•").trim().takeIf { it in RESULT_CATEGORIES }
    }

    /**
     * Strips the search-result decorations off an artist column.
     *
     * Two things get appended in search rows: the result type (`Song • Ed Sheeran`) and, on some
     * rows, view counts and a duration (`Ed Sheeran • 4.2B views`). Splitting on the spaced
     * separator rather than a bare `•` keeps artist names that legitimately contain one intact.
     */
    private fun cleanArtistText(text: String): String {
        val head = text.substringBefore(" • ").trim()
        return if (head in RESULT_CATEGORIES) text.substringAfter(" • ").substringBefore(" • ").trim() else head
    }

    /**
     * Every `musicResponsiveListItemRenderer` in [section].
     *
     * Three different renderers hold result rows and which one appears depends on the endpoint:
     * album browse uses `musicShelfRenderer`, while search uses `musicCardShelfRenderer` for the
     * top-result block and `itemSectionRenderer` for the rest — and **no** `musicShelfRenderer` at
     * all. Accepting only the last one is why search returned zero results from an 800 KB response.
     */
    private fun listRowsIn(section: JsonObject): List<JsonObject> {
        val rows = mutableListOf<JsonObject>()
        for (key in ROW_CONTAINERS) {
            val contents = section[key]?.jsonObject?.get("contents")?.jsonArray ?: continue
            for (item in contents) {
                item.jsonObject["musicResponsiveListItemRenderer"]?.jsonObject?.let { rows += it }
            }
        }
        return rows
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

    /**
     * The artwork URL for [item], at the highest resolution the source will serve.
     *
     * ## Why the offered URL is not used as-is
     *
     * YouTube Music returns whatever size it happens to need for the surface it is rendering, not
     * the best one available. Measured on device, the same album art is served at `=w120-h120` in
     * one response and `=w544-h544` in another, and both were previously passed straight to the UI —
     * a 120 px image stretched across a 300 dp player artwork is exactly the "blurry song image" the
     * user sees. [ArtworkUrl] has the reasoning for each host.
     */
    private fun extractArtworkUrl(item: JsonObject): String {
        val thumbnails = item["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
            ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
            ?: item["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
            ?: item["thumbnailRenderer"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray

        // Widest offered, rather than last: the array is usually ascending but is not guaranteed
        // to be, and "last" silently degrades to the smallest if it ever ships the other way round.
        return ArtworkUrl.bestOf(
            thumbnails?.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.content }.orEmpty()
        )
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

/**
 * Reads (artist, releaseYear) out of a home-feed album card's subtitle.
 *
 * The subtitle arrives as the card's `runs` joined into one string: "Ed Sheeran • 2017", or
 * just "Ed Sheeran", or "Ed Sheeran • Divide". Splitting on the bullet and taking the first
 * and last fields — which is what this replaced — fails in two separate ways:
 *
 * 1. **`split` with no match returns the whole string**, not an empty list. So
 *    `"Ed Sheeran".split("•")` is `["Ed Sheeran"]` and `lastOrNull()` hands back the artist.
 *    Every album whose subtitle had no bullet got its release year set to the artist's name.
 * 2. **The trailing field is not always a year.** "Artist • Deluxe" is a real shape, and the
 *    old code stored "Deluxe" as a release year.
 *
 * The year is therefore taken only from a field that is four digits, and is blank otherwise.
 * A missing date must render as nothing: a default year is indistinguishable from a real one,
 * which is the same rule that removed the invented three-minute durations elsewhere.
 *
 * Extracted from `getHomeData` and `internal` rather than `private` so the tests in this
 * package can pin both cases above — see `AlbumSubtitleParsingTest`.
 */
internal fun parseAlbumSubtitle(subtitle: String): Pair<String, String> {
    val fields = subtitle.split("•").map { it.trim() }.filter { it.isNotEmpty() }
    val artist = fields.firstOrNull() ?: "Unknown Artist"
    val year = fields.drop(1)
        .firstOrNull { it.length == 4 && it.all { c -> c.isDigit() } }
        .orEmpty()
    return artist to year
}

/**
 * Reads (artist, releaseYear) out of a *search-result* album card's subtitle.
 *
 * Unlike the home feed ("Ed Sheeran • 2017"), search cards lead with the item
 * type: "Album • Tate McRae • 2025" or "Single • Artist • 2024". The shared
 * [parseAlbumSubtitle] would take that leading token as the artist and show
 * every album as "by Album". The type token is therefore dropped first; the
 * year rule (four digits only) is the same honest rule as there.
 */
internal fun parseSearchAlbumSubtitle(subtitle: String): Pair<String, String> {
    val typeTokens = setOf("Album", "Single", "EP", "Playlist", "Artist")
    val fields = subtitle.split("•").map { it.trim() }.filter { it.isNotEmpty() }
    val withoutType = if (fields.firstOrNull() in typeTokens) fields.drop(1) else fields
    val artist = withoutType.firstOrNull() ?: "Unknown Artist"
    val year = withoutType.drop(1)
        .firstOrNull { it.length == 4 && it.all { c -> c.isDigit() } }
        .orEmpty()
    return artist to year
}
