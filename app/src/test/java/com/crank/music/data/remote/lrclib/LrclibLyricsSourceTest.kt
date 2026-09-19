package com.crank.music.data.remote.lrclib

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the LRCLIB fallback — the source that decides whether a track has lyrics at all.
 *
 * These cover the two ways it used to lose every response:
 *
 * 1. `duration` is a fractional JSON number, and reading it with `jsonPrimitive.long` (`toLong`)
 *    throws. The throw was caught one level up and discarded the *whole* response, lyrics
 *    included, so no track ever got lyrics from this source.
 * 2. Display strings ("Madhubanti Bagchi, Jasmine Sandlas & Shashwat Sachdev") do not match
 *    LRCLIB's single-artist records, so the query has to be rewritten before it is sent.
 */
class LrclibLyricsSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ---------------------------------------------------------------- duration parsing

    @Test
    fun `fractional duration does not throw and is read as milliseconds`() {
        val candidates = source().parse("""[{"trackName":"Shararat","artistName":"Madhubanti Bagchi",
            |"duration":229.0,"plainLyrics":"la la la"}]""".trimMargin())

        assertEquals(1, candidates.size)
        assertEquals(229_000L, candidates.first().result.durationMs)
    }

    @Test
    fun `whole-number duration is read as milliseconds`() {
        val candidates = source().parse("""[{"duration":229,"plainLyrics":"la la la"}]""")

        assertEquals(229_000L, candidates.first().result.durationMs)
    }

    @Test
    fun `null and missing duration yield null rather than throwing`() {
        val withNull = source().parse("""[{"duration":null,"plainLyrics":"x"}]""")
        val without = source().parse("""[{"plainLyrics":"x"}]""")

        assertNull(withNull.first().result.durationMs)
        assertNull(without.first().result.durationMs)
    }

    @Test
    fun `results without lyrics are dropped`() {
        val candidates = source().parse("""[{"trackName":"x","plainLyrics":"","syncedLyrics":null},
            |{"trackName":"y","plainLyrics":null,"syncedLyrics":"[00:01.00] hi"}]""".trimMargin())

        assertEquals(1, candidates.size)
        assertEquals("y", candidates.first().result.trackName)
    }

    @Test
    fun `a non-array response is not an error`() {
        assertTrue(source().parse("""{"error":"nope"}""").isEmpty())
        assertTrue(source().parse("").isEmpty())
    }

    // ---------------------------------------------------------------- query rewriting

    @Test
    fun `parenthetical is stripped from the title`() {
        assertEquals("Barbaad", source().cleanTitle("Barbaad (Movie: Saiyaara)"))
        assertEquals("Ucha Lamba Kad Forever", source().cleanTitle("Ucha Lamba Kad Forever (From \"X\")"))
    }

    @Test
    fun `credit list is tried in full before the primary artist`() {
        val queries = source().searchQueries("Shararat", "Madhubanti Bagchi, Jasmine Sandlas & Shashwat Sachdev")

        assertEquals("Shararat", queries.first().title)
        assertEquals("Madhubanti Bagchi, Jasmine Sandlas & Shashwat Sachdev", queries.first().artist)
        assertEquals("Madhubanti Bagchi", queries[1].artist)
        assertNull("title-only query must remain available as a last resort", queries.last().artist)
    }

    // ---------------------------------------------------------------- candidate scoring

    @Test
    fun `duration separates an original from its remix`() {
        val candidates = source().parse(
            """[
            |{"trackName":"Aaj Ki Raat","artistName":"Madhubanti Bagchi","duration":179.0,"plainLyrics":"a"},
            |{"trackName":"Aaj Ki Raat (Techno Remix)","artistName":"Madhubanti Bagchi","duration":206.0,"plainLyrics":"b"}
            |]""".trimMargin(),
        )

        val best = candidates.maxByOrNull { it.score("Aaj Ki Raat", "Madhubanti Bagchi", 179_000L) }
        assertEquals("Aaj Ki Raat", best?.result?.trackName)
    }

    @Test
    fun `a track whose title does not match is rejected`() {
        val candidate = source().parse(
            """[{"trackName":"Some Other Song","artistName":"Madhubanti Bagchi","plainLyrics":"x"}]""",
        ).first()

        assertEquals(0, candidate.titleScore("Aaj Ki Raat"))
    }

    @Test
    fun `punctuation and case do not hide a title match`() {
        val candidate = source().parse(
            """[{"trackName":"Aankhon Se Tune 2.0","artistName":"Dev Negi","plainLyrics":"x"}]""",
        ).first()

        assertTrue(candidate.titleScore("Aankhon Se Tune 2.0 (From \"Bhai Tera Star Hai\")") > 0)
    }

    @Test
    fun `an original is preferred over a different version of the same song`() {
        val candidates = source().parse(
            """[
            |{"trackName":"Saiyaara Reprise - Female","artistName":"Shreya Ghoshal","plainLyrics":"a"},
            |{"trackName":"Saiyaara","artistName":"Shreya Ghoshal","plainLyrics":"b"}
            |]""".trimMargin(),
        )

        val best = candidates.maxByOrNull { it.score("Saiyaara", "Shreya Ghoshal", 0L) }
        assertEquals("Saiyaara", best?.result?.trackName)
    }

    @Test
    fun `a version is still accepted when it is the only record available`() {
        // A reprise shares its words with the original, so showing it beats showing nothing —
        // the penalty decides between records, it never rejects one.
        val candidate = source().parse(
            """[{"trackName":"Saiyaara Reprise - Female","artistName":"Shreya Ghoshal","plainLyrics":"a"}]""",
        ).first()

        assertTrue(candidate.titleScore("Saiyaara") > 0)
    }

    @Test
    fun `artist match is recognised across a reordered credit list`() {
        val candidate = source().parse(
            """[{"trackName":"Shararat","artistName":"Shashwat Sachdev, Madhubanti Bagchi & Jasmine Sandlas","plainLyrics":"x"}]""",
        ).first()

        val scored = candidate.score("Shararat", "Madhubanti Bagchi, Jasmine Sandlas & Shashwat Sachdev", 0L)
        val unrelated = candidate.score("Shararat", "Someone Else Entirely", 0L)
        assertTrue("credit-list overlap must count: $scored vs $unrelated", scored > unrelated)
    }

    // ---------------------------------------------------------------- end to end over HTTP

    @Test
    fun `first query that matches wins, and the response survives a fractional duration`() {
        val source = source { respond(LRCLIB_RESPONSE, HttpStatusCode.OK) }

        val result = runBlocking { source.findLyrics("Shararat", "Madhubanti Bagchi", 229_000L) }

        assertNotNull("a fractional duration used to discard this response entirely", result)
        assertEquals("Shararat", result?.trackName)
        assertEquals("la la la", result?.plainLyrics)
    }

    @Test
    fun `no candidate means no lyrics rather than a wrong song`() {
        val source = source {
            respond("""[{"trackName":"Unrelated","artistName":"Nobody","plainLyrics":"x"}]""", HttpStatusCode.OK)
        }

        assertNull(runBlocking { source.findLyrics("Shararat", "Madhubanti Bagchi", 229_000L) })
    }

    private fun source(): LrclibLyricsSource = source { respond("", HttpStatusCode.OK) }

    private fun source(handler: io.ktor.client.engine.mock.MockRequestHandler): LrclibLyricsSource {
        val engine = MockEngine(handler)
        val client = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        return LrclibLyricsSource(client)
    }

    private companion object {
        /** Shaped exactly like a real LRCLIB hit, decimal point and all. */
        private const val LRCLIB_RESPONSE = """[
            {"trackName":"Shararat","artistName":"Madhubanti Bagchi",
             "albumName":"Bollywood Fire","duration":229.0,
             "plainLyrics":"la la la","syncedLyrics":"[00:01.00] la la la"}
        ]"""
    }
}
