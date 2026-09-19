package com.crank.music.data.remote.innertube

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * On-device verification that track metadata is read, not invented.
 *
 * ## Why durations are asserted
 *
 * Every track in the app used to report 180 000 ms. The cause was not a missing field: the running
 * time sits in a row's `fixedColumns` (`[3:51]`) while the parser only read `flexColumns`
 * (`[Gehra Hua, Arijit Singh, …]`), so no duration was ever found and the hard-coded default was
 * used for 100% of tracks. Because the default is a *plausible* number rather than an obvious
 * placeholder, nothing looked broken — the library simply showed "3:00" against every song.
 *
 * A fabricated value is worse than a missing one precisely because it cannot be detected by
 * inspection, so this asserts the parsed duration is real rather than merely present: non-zero, and
 * within a bound no real track exceeds.
 *
 * As with the other probes the graph is built by hand, because `hilt-android-testing` is not on the
 * test classpath.
 */
class TrackMetadataVerificationTest {

    @Test
    fun tracksCarryRealDurationsAndNames() {
        val json = Json { ignoreUnknownKeys = true }
        val client = HttpClient(Android) { install(ContentNegotiation) { json(json) } }
        val api = InnerTubeApi(client, InnerTubeConfig())

        val albums = runBlocking { runCatching { api.getHomeData() }.getOrNull() }.orEmpty()
        val album = albums.firstOrNull()
        val songs = album?.let {
            Log.i(TAG, "feed album='${it.title}'")
            runBlocking { runCatching { api.browsePlaylistOrAlbum(it.id) }.getOrNull() }.orEmpty()
        }.orEmpty()

        assertTrue("feed must return tracks to verify", songs.isNotEmpty())

        val sample = songs.take(10)
        var realDurations = 0

        for (song in sample) {
            val minutes = song.durationMs / 60_000
            val seconds = (song.durationMs / 1000) % 60
            Log.i(TAG, "  '${song.title}' | ${song.artistName} | $minutes:${String.format("%02d", seconds)}")

            // Bounds are deliberately wide: the point is to catch a constant placeholder (or a
            // zero), not to police the catalogue. A real track is seconds to ~20 minutes.
            if (song.durationMs > 0L && song.durationMs <= MAX_PLAUSIBLE_DURATION_MS) realDurations++

            assertTrue(
                "'${song.title}' should have a title, not a placeholder",
                song.title != "Unknown Track",
            )
            assertTrue(
                "'${song.title}' should have an artist, not a placeholder",
                song.artistName != "Unknown Artist",
            )
        }

        Log.i(TAG, "REAL_DURATIONS=$realDurations/${sample.size}")
        assertTrue(
            "durations must be read from the response (got $realDurations/${sample.size} real) — " +
                "a constant value here means the parser is falling back to a default and the " +
                "library shows the same made-up length for every track",
            realDurations == sample.size,
        )

        // A single shared value is the signature of a default, even when that value looks plausible.
        val distinct = sample.map { it.durationMs }.distinct().size
        Log.i(TAG, "DISTINCT_DURATIONS=$distinct of ${sample.size}")
        assertTrue(
            "ten different tracks must not all share one duration (got $distinct distinct) — " +
                "that is the shape of a hard-coded fallback, not of real metadata",
            distinct > 1,
        )
    }

    private companion object {
        private const val TAG = "CRANK_TRACKMETA"
        private const val MAX_PLAUSIBLE_DURATION_MS = 20 * 60 * 1000L
    }
}
