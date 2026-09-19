package com.crank.music.data.remote.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two defects found in the home-feed album card parse.
 *
 * ## What was wrong
 *
 * The card subtitle was split on `•` and read as `firstOrNull()` for the artist and
 * `lastOrNull() ?: "2024"` for the year. That is wrong twice:
 *
 * 1. **`split` on a delimiter that is absent returns the whole string**, so a subtitle with no
 *    bullet produced the artist as *both* the artist and the release year.
 * 2. **The trailing field is not always a year**, and when it isn't, whatever it is ("Divide",
 *    "Deluxe", "16 songs") was stored as the release year.
 *
 * And the `"2024"` fallback put a real-looking date on any album that had none, which is
 * indistinguishable from a date YouTube Music actually supplied — the same fabrication the
 * invented three-minute durations were removed for.
 *
 * These are assertions about a pure function, so no fixture or device is needed. The first test
 * pins the `split` semantics the fix depends on, so the guard can never quietly become dead code.
 */
class AlbumSubtitleParsingTest {

    @Test
    fun splitOnAMissingDelimiterReturnsTheWholeString() {
        // The premise the fix rests on. If this ever stops holding, parseAlbumSubtitle's
        // "only after a bullet" rule is dead code and the bug it prevents returns unnoticed.
        assertEquals(listOf("Ed Sheeran"), "Ed Sheeran".split("•"))
    }

    @Test
    fun subtitleWithNoBulletYieldsNoYearRatherThanTheArtist() {
        val (artist, year) = parseAlbumSubtitle("Ed Sheeran")

        assertEquals("Ed Sheeran", artist)

        // The regression: `lastOrNull()` on a one-element list returns the artist, so this
        // album's release year was "Ed Sheeran".
        assertEquals("", year)
    }

    @Test
    fun readsArtistAndYearFromABulletedSubtitle() {
        assertEquals("Ed Sheeran" to "2017", parseAlbumSubtitle("Ed Sheeran • 2017"))
    }

    @Test
    fun trailingFieldThatIsNotAYearIsNotStoredAsOne() {
        // "Artist • Divide" is a real shape; the old code took the last field unconditionally.
        assertEquals("Ed Sheeran" to "", parseAlbumSubtitle("Ed Sheeran • Divide"))
    }

    @Test
    fun findsTheYearWhenItIsNotTheLastField() {
        assertEquals("Ed Sheeran" to "2017", parseAlbumSubtitle("Ed Sheeran • 2017 • 16 songs"))
    }

    @Test
    fun onlyAFourDigitFieldCountsAsAYear() {
        assertEquals("", parseAlbumSubtitle("Ed Sheeran • 201").second)
        assertEquals("", parseAlbumSubtitle("Ed Sheeran • 20175").second)
    }

    @Test
    fun blankSubtitleFallsBackWithoutInventingAYear() {
        val (artist, year) = parseAlbumSubtitle("")

        assertEquals("Unknown Artist", artist)
        assertEquals("", year)
    }

    @Test
    fun trimsPaddingAroundBulletFields() {
        assertEquals("Ed Sheeran" to "2017", parseAlbumSubtitle("  Ed Sheeran  •  2017  "))
    }

    /**
     * The browse/search context used to hardcode `clientVersion` `1.20231212.00.00` while
     * [YouTubeClients.WEB_REMIX] carried a current one, so the pinned value drifted years behind
     * the identity it claimed to be. This asserts the version that actually goes out on the
     * wire is the one in the client table — not merely that some string is present.
     */
    @Test
    fun browseRequestsDeclareTheCurrentWebRemixClientVersion() {
        val sent = mutableListOf<String>()
        val engine = MockEngine { request ->
            val body = request.body
            sent += when (body) {
                is ByteArrayContent -> String(body.bytes())
                is TextContent -> body.text
                else -> ""
            }
            respond(
                "{}",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val api = InnerTubeApi(
            HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } },
            InnerTubeConfig()
        )

        runBlocking { api.getHomeData() }

        assertTrue("no request body captured — the mock engine shape changed", sent.isNotEmpty())

        val context = Json.parseToJsonElement(sent.first()).jsonObject["context"]
            ?.jsonObject?.get("client")?.jsonObject
        assertEquals("WEB_REMIX", context?.get("clientName")?.jsonPrimitive?.content)

        // A stale version is answered with 200 and an empty shelf, so nothing surfaces as an
        // error — the only way to catch the drift is to compare against the client table here.
        assertEquals(
            "context clientVersion drifted from YouTubeClients.WEB_REMIX",
            YouTubeClients.WEB_REMIX.clientVersion,
            context?.get("clientVersion")?.jsonPrimitive?.content
        )
    }
}
