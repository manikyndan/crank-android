package com.crank.music.data.remote.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses a real, captured YouTube Music search response.
 *
 * ## What this guards
 *
 * Search returned **zero results from an 818 KB response**. The parser accepted only
 * `musicShelfRenderer`, but a search response puts its rows in `musicCardShelfRenderer` (the
 * top-result block) and `itemSectionRenderer` (everything else) and has no `musicShelfRenderer` at
 * all, so every section hit `continue`. Nothing errored — the response was fine, the parser was
 * looking in the wrong place, and the app silently fell back to the NewPipe extractor for every
 * query.
 *
 * ## Why the fixture is a real response
 *
 * A hand-written fixture would have reproduced the shape the parser already expected and passed.
 * `search_response.json` is a captured response for "Shape of You", trimmed to the first 12
 * sections, so it contains the container types and result categories that actually occur.
 */
class SearchMusicParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val response: ByteArray by lazy {
        requireNotNull(javaClass.classLoader?.getResourceAsStream("search_response.json")) {
            "search_response.json missing from test resources"
        }.use { it.readBytes() }
    }

    private fun apiReturning(body: ByteArray): InnerTubeApi {
        val engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        return InnerTubeApi(client, InnerTubeConfig())
    }

    @Test
    fun parsesSongsOutOfARealSearchResponse() {
        val songs = runBlocking { apiReturning(response).searchMusic("Shape of You") }

        // Zero here is the whole bug. The response below is full of rows; a parser that finds none
        // is looking in the wrong place, not receiving empty data.
        assertTrue("expected results from a real search response, got none", songs.isNotEmpty())
        assertEquals("row count changed — re-check the fixture is still representative", 8, songs.size)
    }

    @Test
    fun everyResultIsIdentifiableAndLabelled() {
        val songs = runBlocking { apiReturning(response).searchMusic("Shape of You") }

        for (song in songs) {
            assertEquals("id must be a YouTube video id: ${song.title}", 11, song.id.length)
            assertTrue("title left as placeholder: $song", song.title != "Unknown Track")
            assertTrue("artist left as placeholder: $song", song.artistName != "Unknown Artist")
            assertTrue(
                "artist still carries search decorations ('Song • …' / '… • 4.2B views'): " +
                    "'${song.artistName}'",
                !song.artistName.contains("•")
            )
        }
    }

    @Test
    fun dropsResultsThatAreNotSongs() {
        val songs = runBlocking { apiReturning(response).searchMusic("Shape of You") }
        val titles = songs.map { it.title }

        // Search is unfiltered: these are declared "Video" in the response. Keeping them would put
        // unplayable rows in the queue and show a lyric video where a track belongs.
        for (video in listOf(
            "Ed Sheeran - Shape of You (Lyrics)",
            "Ed Sheeran – Shape of You (Lyrics)"
        )) {
            assertTrue("a Video row was kept as a track: $video", video !in titles)
        }
    }

    @Test
    fun readsArtistFromTheSingleRunColumn() {
        val songs = runBlocking { apiReturning(response).searchMusic("Shape of You") }

        // The parser this replaced required the artist column to have more than one run, which
        // dropped most artists. These rows each have a one-run artist.
        //
        // Grouped rather than keyed by title: several results are legitimately called "Shape of
        // You" by different artists, so a map would keep only one and the assert would be about
        // map ordering rather than about parsing.
        val byTitle = songs.groupBy { it.title }
        assertTrue(
            "'Ed Sheeran' missing from ${byTitle["Shape of You"].orEmpty().map { it.artistName }}",
            "Ed Sheeran" in byTitle["Shape of You"].orEmpty().map { it.artistName }
        )
        assertEquals(
            "Ed Sheeran & Diljit Dosanjh",
            byTitle["Shape of You x Naina"]?.singleOrNull()?.artistName
        )
    }

    @Test
    fun artworkIsUpgradedOnSearchResultsToo() {
        val songs = runBlocking { apiReturning(response).searchMusic("Shape of You") }

        val withArt = songs.count { it.artworkUrl.isNotBlank() }
        assertTrue("no search result has artwork", withArt > 0)
        for (song in songs.filter { it.artworkUrl.isNotBlank() }) {
            // Search rows carry ytimg thumbnails; the offered hqdefault is 480x360, far below the
            // ~790 px the player renders at.
            assertTrue(
                "search artwork not upgraded: ${song.artworkUrl}",
                song.artworkUrl.contains("maxresdefault") || song.artworkUrl.contains("=w1080")
            )
        }
    }

    @Test
    fun anUnrecognisedShapeYieldsNothingRatherThanGarbage() {
        val songs = runBlocking {
            apiReturning("""{"contents":{}}""".toByteArray()).searchMusic("Shape of You")
        }
        assertTrue(songs.isEmpty())
    }
}
