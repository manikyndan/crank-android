package com.crank.music.data.remote

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.crank.music.data.remote.innertube.InnerTubeApi
import com.crank.music.data.remote.innertube.InnerTubeConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * On-device proof that track artwork is served at a resolution the player can render sharply.
 *
 * ## What this guards
 *
 * The now-playing artwork is 300 dp. If the image behind it is smaller than that in pixels, it is
 * upscaled and reads as blurry — which is exactly the bug this file exists to prevent. Before the
 * fix, the NewPipe fallback passed `thumbnails.lastOrNull()` through untouched and InnerTube handed
 * out `=w120-h120` in some responses; a 120 px image across ~790 px of screen is unrecoverable.
 *
 * ## Why it measures pixels instead of checking the URL string
 *
 * Two things can only be caught by actually fetching the bytes:
 *
 * 1. `maxresdefault.jpg` does not exist for every video. If it is missing the request fails and the
 *    track ends up with *no* artwork, which is worse than a soft image. A URL that asserts
 *    "=w1080" proves nothing until the server answers with 1080 pixels.
 * 2. The size suffix is a request, not a limit — `googleusercontent` caps at the source
 *    resolution, so asking for 1080 may legitimately return less. Only the decoded size tells us
 *    whether the image is big enough.
 *
 * ## Why it goes through `YouTubeRemoteDataSource`
 *
 * That is the bound `RemoteDataSource`, and it decides between InnerTube and the NewPipe fallback.
 * Testing `InnerTubeApi` alone would have missed the fallback entirely — and the fallback is the
 * path that was shipping low-res URLs.
 */
class ArtworkVerificationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun trackArtworkIsAtLeastAsLargeAsThePlayerRendersIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // The player artwork is 300 dp; anything below this in pixels is being upscaled.
        val renderedPx = (ARTWORK_DP * context.resources.displayMetrics.density).roundToInt()
        Log.i(TAG, "player artwork renders at $ARTWORK_DP dp = $renderedPx px")

        val client = HttpClient(Android) { install(ContentNegotiation) { json(json) } }
        val api = InnerTubeApi(client, InnerTubeConfig())

        // The fallback extractor needs this; without it NewPipe throws and the fallback silently
        // returns nothing, which would make this test pass against an empty sample.
        runCatching { NewPipe.init(NewPipeDownloader()) }
            .onFailure { Log.w(TAG, "NewPipe init failed: ${it.message}") }

        val source = YouTubeRemoteDataSource(api)
        val songs = collectSongs(source, api)

        Log.i(TAG, "sampling ${songs.size} songs")
        assertTrue("no songs to sample — the test proved nothing", songs.size >= 5)

        var resolved = 0
        var sharp = 0
        val failures = mutableListOf<String>()

        for (song in songs) {
            val url = song.artworkUrl
            if (url.isBlank()) {
                failures += "NO URL: ${song.title}"
                continue
            }

            val (w, h) = dimensionsOf(url)
            if (w <= 0) {
                failures += "UNRESOLVABLE ${w}x$h: ${song.title} <- $url"
                continue
            }
            resolved++
            if (w >= renderedPx) sharp++ else failures += "SMALL ${w}x$h < $renderedPx: ${song.title}"

            Log.i(TAG, "  ${w}x$h  ${song.title}  $url")
        }

        Log.i(TAG, "SUMMARY resolved=$resolved sharp=$sharp of ${songs.size} (need >= $renderedPx px)")
        failures.forEach { Log.w(TAG, "  FAIL $it") }

        assertTrue(
            "artwork failed to load for ${songs.size - resolved} of ${songs.size} songs: $failures",
            resolved * 10 >= songs.size * 9
        )
        assertTrue(
            "artwork below render resolution for ${resolved - sharp} of $resolved songs: $failures",
            sharp * 10 >= resolved * 9
        )
    }

    /** Songs from both the InnerTube path and the fallback, so neither can regress alone. */
    private fun collectSongs(
        source: YouTubeRemoteDataSource,
        api: InnerTubeApi
    ): List<com.crank.music.domain.model.Song> {
        val songs = LinkedHashMap<String, com.crank.music.domain.model.Song>()

        for (query in listOf("Arijit Singh", "Shape of You", "Bollywood hits")) {
            val found = runBlocking { runCatching { source.searchMusic(query) }.getOrNull() }.orEmpty()
            Log.i(TAG, "search \"$query\" -> ${found.size} songs")
            found.forEach { songs.putIfAbsent(it.id, it) }
        }

        // Album art is the other half of the problem: it is square googleusercontent artwork and
        // the sizes offered there vary per response.
        val albums = runBlocking { runCatching { source.getHomeData() }.getOrNull() }.orEmpty()
        Log.i(TAG, "home -> ${albums.size} albums")
        for (album in albums.take(3)) {
            val tracks = runBlocking {
                runCatching { api.browsePlaylistOrAlbum(album.id) }.getOrNull()
            }.orEmpty()
            tracks.forEach { songs.putIfAbsent(it.id, it) }
        }

        return songs.values.take(20).toList()
    }

    /** Decoded pixel size, or `0 x 0` when the URL cannot be fetched or is not an image. */
    private fun dimensionsOf(url: String): Pair<Int, Int> {
        val bytes = runCatching { fetch(url) }.getOrNull()
        if (bytes == null || bytes.isEmpty()) return 0 to 0
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth to opts.outHeight
    }

    private fun fetch(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "CrankMusic/1.0")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            return ByteArray(0)
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        return bytes
    }

    private companion object {
        private const val TAG = "CRANK_ART"
        private const val ARTWORK_DP = 300
    }
}
