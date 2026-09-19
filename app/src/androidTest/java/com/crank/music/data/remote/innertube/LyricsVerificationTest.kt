package com.crank.music.data.remote.innertube

import android.util.Log
import com.crank.music.data.remote.lrclib.LrclibLyricsSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * On-device verification that lyrics actually resolve, through the same chain the player uses.
 *
 * ## Why this measures the chain and not one source
 *
 * The user-visible symptom was "lyrics unavailable" for *every* track. That is only possible if
 * both sources fail, so testing one proves nothing about the symptom. The chain is:
 *
 * 1. YouTube Music, asked by track id — cannot mismatch, but only covers part of the catalogue.
 * 2. LRCLIB, asked by title and artist — covers the rest, and is the one that was silently
 *    returning nothing.
 *
 * Measured before the fix: YouTube Music had lyrics for 1 of 10 feed tracks, and LRCLIB returned
 * nothing at all despite having matches for all 10 — because its fractional `duration` field
 * (`229.0`) threw during parsing and the whole response was discarded. The fallback therefore has
 * to be measured separately, not assumed to work because the HTTP call succeeds.
 *
 * ## Why the sample comes from the app's own feed
 *
 * Hand-written youtube.com video ids are misleading here: the lyrics tab belongs to the YouTube
 * Music *song* entity, and a Vevo video id often has no lyrics tab even when the song does. These
 * ids come from the same browse the app renders, so a miss is a real miss.
 *
 * As with the other probes the graph is built by hand, because `hilt-android-testing` is not on the
 * test classpath.
 */
class LyricsVerificationTest {

    @Test
    fun lyricsResolveForRealFeedTracks() {
        val json = Json { ignoreUnknownKeys = true }
        // ContentNegotiation is required: InnerTubeApi reads typed bodies (`body<JsonObject>()`),
        // and without it every call fails to deserialize and returns empty rather than erroring.
        val client = HttpClient(Android) { install(ContentNegotiation) { json(json) } }
        val api = InnerTubeApi(client, InnerTubeConfig())
        val lrclib = LrclibLyricsSource(client)

        val albums = runBlocking { runCatching { api.getHomeData() }.getOrNull() }.orEmpty()
        val songs = albums.firstOrNull()?.let { album ->
            Log.i(TAG, "feed album='${album.title}'")
            runBlocking { runCatching { api.browsePlaylistOrAlbum(album.id) }.getOrNull() }.orEmpty()
        }.orEmpty()

        val sample = songs.take(10)
        Log.i(TAG, "sampling ${sample.size} songs")

        var youtubeHits = 0
        var lrclibHits = 0
        var resolved = 0

        for (song in sample) {
            // Stage 1 — exact match, asked by the id of the track that is actually playing.
            val youtube = runBlocking { runCatching { api.fetchLyrics(song.id) }.getOrNull() }
            if (!youtube.isNullOrBlank()) youtubeHits++

            // Stage 2 — fuzzy fallback, only reached when stage 1 has nothing.
            val fallback = if (youtube.isNullOrBlank()) {
                runBlocking {
                    runCatching { lrclib.findLyrics(song.title, song.artistName, song.durationMs) }
                        .getOrNull()
                }?.let { it.syncedLyrics?.takeIf { s -> s.isNotBlank() } ?: it.plainLyrics }
            } else {
                null
            }
            if (!fallback.isNullOrBlank()) lrclibHits++

            val lyrics = youtube?.takeIf { it.isNotBlank() } ?: fallback
            if (!lyrics.isNullOrBlank()) resolved++ else Log.e(TAG, "  UNRESOLVED '${song.title}'")

            Log.i(
                TAG,
                "  '${song.title}' -> ${lyrics?.length ?: 0} chars " +
                    "(youtube=${youtube?.length ?: 0}, lrclib=${fallback?.length ?: 0})",
            )
        }

        Log.i(TAG, "RESOLVED=$resolved/${sample.size} youtubeOnly=$youtubeHits lrclibCovered=$lrclibHits")

        // The bar is deliberately on the *combined* chain: YouTube Music alone covers ~1 in 10 of
        // these tracks, so requiring it would pass while the user still saw nothing. Equally, a
        // zero here cannot be a network hiccup — it means the fallback is dead again.
        assertTrue(
            "lyrics must resolve for most real feed tracks (got $resolved/${sample.size}) — " +
                "a low number means the fallback is failing and tracks show 'lyrics unavailable'",
            resolved * 10 >= sample.size * 5,
        )

        // Control: a track both sources are known to have, so neither assertion can pass
        // "by accident" on whatever the feed happened to return.
        val control = runBlocking { runCatching { api.fetchLyrics("60ItHLz5WEA") }.getOrNull() }
        Log.i(TAG, "control youtube(Shape of You)=${control?.length ?: 0} chars")
        assertNotNull(
            "YouTube Music must return lyrics for a track that has them — a null here is why " +
                "every track showed 'lyrics unavailable'",
            control,
        )

        val controlLrc = runBlocking {
            runCatching { lrclib.findLyrics("Shape of You", "Ed Sheeran", 263_000L) }.getOrNull()
        }
        Log.i(TAG, "control lrclib(Shape of You)=${controlLrc?.plainLyrics?.length ?: 0} chars")
        assertNotNull(
            "LRCLIB must return a match when it has one — this is the assertion that catches " +
                "the fractional-duration parse discarding every response",
            controlLrc?.plainLyrics ?: controlLrc?.syncedLyrics,
        )
    }

    private companion object {
        private const val TAG = "CRANK_LYRICSVERIFY"
    }
}
