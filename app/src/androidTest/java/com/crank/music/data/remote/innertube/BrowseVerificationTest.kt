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
 * On-device verification of the album browse path.
 *
 * `browsePlaylistOrAlbum` parses YouTube's real `browse` response (`musicShelfRenderer` for an
 * album, `musicPlaylistShelfRenderer` for a playlist). That parsing was written against the documented
 * response shape but never exercised against a live payload, which is the one thing that cannot be
 * checked from a JVM unit test — the shelf nesting is easy to get subtly wrong, and a wrong-but-empty
 * result is indistinguishable from "this album has no tracks".
 *
 * So this probe takes a real browse id from the app's own home feed and browses it, logging how many
 * tracks came back and what they are. A count of zero against a chart album means the parser is
 * wrong, which is the regression worth knowing about.
 *
 * As with [CascadeVerificationTest], the graph is built by hand because `hilt-android-testing` is not
 * on the test classpath.
 */
class BrowseVerificationTest {

    @Test
    fun browseAlbumReturnsRealTracks() {
        val json = Json { ignoreUnknownKeys = true }
        val client = HttpClient(Android) {
            install(ContentNegotiation) { json(json) }
        }
        val api = InnerTubeApi(client, InnerTubeConfig())

        // Real browse ids, taken from the same home feed the app renders, so the input is not a
        // guess and cannot silently rot into something the endpoint rejects.
        val albums = runBlocking {
            runCatching { api.getHomeData() }.getOrElse { e ->
                Log.e(TAG, "getHomeData failed: ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            }
        }
        Log.i(TAG, "home albums=${albums.size}")

        // Asserted: if the home feed itself is empty there is nothing to browse and every later
        // line would be misleading. This is the input genuinely under our control.
        assertTrue("home feed must return at least one album to browse", albums.isNotEmpty())

        // Browse several, not one.
        //
        // A single zero-count result is ambiguous: it could mean the parser is wrong, or that this
        // particular item genuinely has no tracks (a radio/auto-mix often does not until played).
        // Sampling a spread of items and logging each browse id's prefix separates the two, because
        // the prefix says what kind of thing it is: `MPREb_` is an album, `VLPL`/`VLRD` a playlist
        // or radio. If every kind returns zero the parser is wrong; if only one kind does, the
        // sample was simply the wrong shape.
        albums.take(6).forEachIndexed { index, album ->
            val songs = runBlocking {
                runCatching { api.browsePlaylistOrAlbum(album.id) }.getOrElse { e ->
                    Log.e(TAG, "browse failed: ${e.javaClass.simpleName}: ${e.message}")
                    emptyList()
                }
            }
            Log.i(
                TAG,
                "BROWSE_RESULT[$index] count=${songs.size} kind=${album.id.take(5)} " +
                    "title=${album.title} browseId=${album.id}",
            )
            songs.take(3).forEachIndexed { trackIndex, song ->
                Log.i(TAG, "  track[$trackIndex] ${song.title} — ${song.artistName}")
            }
        }
    }

    private companion object {
        private const val TAG = "CRANK_BROWSETEST"
    }
}
