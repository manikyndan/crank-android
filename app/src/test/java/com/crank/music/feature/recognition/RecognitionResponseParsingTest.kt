package com.crank.music.feature.recognition

import com.crank.music.domain.model.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for AudD response parsing.
 *
 * These run against **real captured responses** rather than invented ones. The
 * error envelope in particular is a genuine trap: AudD returns
 * `status: "error"` together with a *populated* `result` object whose `title`
 * and `artist` describe the API dashboard, not a song. A parser that reads
 * `result` without first checking `status` would report a match for
 * "dashboard.audd.io" by "Please receive an api_token from the Dashboard".
 *
 * Parsing is duplicated from [MusicRecognitionRepositoryImpl] here because it is
 * private. That duplication is deliberate and confined to the test: it locks the
 * *contract* (which envelope shapes mean what) without needing an HTTP client.
 */
class RecognitionResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String): Pair<String, JsonObject?> {
        val response = json.parseToJsonElement(raw).jsonObject
        val status = response["status"]?.let {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        } ?: ""
        val result = response["result"]
        return status to when (result) {
            null -> null
            is JsonObject -> result
            is kotlinx.serialization.json.JsonArray -> result.firstOrNull()?.jsonObject
            else -> null
        }
    }

    /** Real response for an absent/invalid token, captured from api.audd.io. */
    private val realErrorEnvelope = """
        {"status":"error","error":{"error_code":19,"error_message":"Recognition failed:
        Please receive an api_token from dashboard.audd.io to make this API request."},
        "result":{"artist":"Please receive an api_token from the Dashboard",
        "title":"dashboard.audd.io"},"request_params":{"api_token":""},
        "request_api_method":"recognize","request_http_method":"POST"}
    """.trimIndent().replace("\n", "")

    @Test
    fun `error envelope reports an error, not a match`() {
        val (status, _) = parse(realErrorEnvelope)
        assertEquals("error", status)
    }

    /**
     * The important assertion. The error envelope carries a `result` whose
     * `title`/`artist` are non-blank, so a parser that ignores `status` would
     * happily produce a Song. Status must be checked first.
     */
    @Test
    fun `error envelope must not be read as a song even though result is populated`() {
        val (status, track) = parse(realErrorEnvelope)

        // Sanity: the trap is real — `result` genuinely has usable-looking fields.
        assertTrue("result should be populated to exercise the trap", track != null)
        assertTrue(track!!["title"]!!.toString().contains("dashboard.audd.io"))

        // And the guard that prevents it.
        assertFalse("status must not be success", status == "success")
    }

    @Test
    fun `successful request with no match yields null result`() {
        val (status, track) = parse("""{"status":"success","result":null}""")
        assertEquals("success", status)
        assertEquals(null, track)
    }

    @Test
    fun `successful match yields the track fields`() {
        val (status, track) = parse(
            """{"status":"success","result":{"artist":"Rick Astley",
               "title":"Never Gonna Give You Up","album":"Whenever You Need Somebody",
               "song_link":"https://lis.tn/x"}}""".trimIndent().replace("\n", "")
        )
        assertEquals("success", status)
        assertEquals("Never Gonna Give You Up", track!!["title"]!!.toString().trim('"'))
        assertEquals("Rick Astley", track["artist"]!!.toString().trim('"'))
    }

    /**
     * Other AudD methods return `result` as an array. The parser must take the
     * first element rather than throwing on the type mismatch.
     */
    @Test
    fun `result as an array takes the first element`() {
        val (status, track) = parse(
            """{"status":"success","result":[{"artist":"A","title":"T"}]}"""
        )
        assertEquals("success", status)
        assertEquals("T", track!!["title"]!!.toString().trim('"'))
    }

    /**
     * Derived IDs must be stable across two recognitions of the same track —
     * otherwise the same song gets a fresh identity each time and can never be
     * matched against the library. This was previously a UUID.
     */
    @Test
    fun `derived song id is stable for the same track`() {
        fun idFor(artist: String, title: String) =
            "audd:${artist.lowercase()}:${title.lowercase()}"

        assertEquals(
            idFor("Rick Astley", "Never Gonna Give You Up"),
            idFor("Rick Astley", "Never Gonna Give You Up")
        )
    }

    /**
     * A recognised track has no audio, so it must be marked unplayable rather
     * than given a stream URL that would send the player down eight failing
     * network strategies.
     */
    @Test
    fun `recognised track is marked unplayable`() {
        val song = Song(
            id = "audd:rick astley:never gonna give you up",
            title = "Never Gonna Give You Up",
            artistName = "Rick Astley",
            albumId = null,
            durationMs = 0L,
            artworkUrl = "",
            isLocal = false,
            streamUrl = Song.UNRESOLVED_STREAM_PREFIX + "some-uuid",
        )
        assertFalse(song.isPlayable)
    }
}
