package com.crank.music.data.remote

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.crank.music.data.remote.innertube.InnerTubePlayerApi
import com.crank.music.data.remote.innertube.StreamCascadeResolver
import com.crank.music.data.remote.innertube.YouTubeClient
import com.crank.music.data.remote.potoken.PoTokenGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * On-device proof that a resolved stream actually plays.
 *
 * ## The gap this closes
 *
 * `CascadeVerificationTest` proves a `googlevideo` URL comes back and answers a range request.
 * That is necessary but not sufficient: the user-facing question is whether ExoPlayer renders
 * audio from it. Those are separated by everything in `PlayerModule` — the data-source factory,
 * the user agent, redirect handling, the timeouts, and the audio attributes — none of which is
 * exercised by fetching bytes over HTTP. A misconfigured data source still returns a URL and
 * still passes a range check, then fails inside the player.
 *
 * So this drives the real `StreamResolver` and a real `ExoPlayer` built exactly the way the app
 * builds it, and waits for the media clock to actually move.
 *
 * ## Why a real player rather than a stub
 *
 * Substituting a fake player would verify that we call `play()`, not that audio plays. The whole
 * value here is that ExoPlayer itself accepts the stream: it has to reach `STATE_READY`, which
 * means it opened the source, parsed the container, and selected a decoder.
 */
class PlaybackVerificationTest {

    @OptIn(UnstableApi::class)
    @Test
    fun aResolvedStreamPlaysInExoPlayer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val json = Json { ignoreUnknownKeys = true }
        val client = HttpClient(Android) { install(ContentNegotiation) { json(json) } }

        // --- Resolve through the app's real entry point --------------------
        //
        // `VisitorDataProvider` is an interface precisely so this is possible without a database.
        // The visitor id is still fetched the real way: without it the whole-file clients answer
        // LOGIN_REQUIRED and the cascade silently falls back to a ~1 MiB preview, which *would*
        // play — for about a minute — and pass a naive "is it playing" check.
        val visitorProvider = object : VisitorDataProvider {
            override suspend fun visitorData(): String? = fetchVisitorData(client)
            override suspend fun dataSyncId(): String? = null
        }
        val poToken = PoTokenGenerator(context)
        val resolver = StreamResolver(
            cascadeResolver = StreamCascadeResolver(InnerTubePlayerApi(client, json), poToken),
            poTokenGenerator = poToken,
            visitorDataProvider = visitorProvider,
        )

        val stream = runBlocking {
            runCatching { resolver.resolveStreamUrl(VIDEO_ID) }
                .onFailure { Log.e(TAG, "resolve failed: ${it.javaClass.simpleName}: ${it.message}") }
                .getOrNull()
        }
        assertNotNull("no stream resolved for $VIDEO_ID — playback cannot be verified", stream)
        stream!!
        Log.i(
            TAG,
            "resolved client=${stream.sourceClient} expires=${stream.expiresInSeconds} " +
                "headers=${stream.headers} url=${stream.url.take(110)}"
        )

        // --- Build the player the way PlayerModule does ---------------------
        //
        // Mirroring matters: a probe with default settings would miss a regression in exactly the
        // configuration the app ships (missing user agent, no cross-protocol redirects, short
        // timeouts) because those only fail against the real CDN.
        lateinit var player: ExoPlayer
        val ready = CountDownLatch(1)
        var error: PlaybackException? = null

        instrumentation.runOnMainSync {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(30_000)

            player = ExoPlayer.Builder(context)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
                .build()

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.i(TAG, "state=$playbackState (${stateName(playbackState)})")
                    if (playbackState == Player.STATE_READY) ready.countDown()
                }

                override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
                    error = playbackError
                    if (playbackError != null) {
                        Log.e(TAG, "player error: ${playbackError.errorCodeName}: ${playbackError.message}")
                        ready.countDown()
                    }
                }
            })

            player.setMediaItem(MediaItem.fromUri(stream.url))
            player.prepare()
            player.playWhenReady = true
        }

        try {
            val reachedReady = ready.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            assertTrue(
                "player never reached STATE_READY within ${READY_TIMEOUT_MS}ms" +
                    (error?.let { " (error: ${it.errorCodeName}: ${it.message})" } ?: ""),
                reachedReady
            )
            error?.let { fail("player error: ${it.errorCodeName}: ${it.message}") }

            // --- Proof of playback: the media clock must move ---------------
            //
            // Reaching READY means the source opened and a decoder was selected. Advancing
            // position means the renderer is actually running. Together they distinguish
            // "ExoPlayer accepted this" from "we called play()".
            val before = readPosition(player)
            Thread.sleep(ADVANCE_WINDOW_MS)
            val after = readPosition(player)
            val audioFormat = readAudioFormat(player)

            Log.i(TAG, "position ${before}ms -> ${after}ms over ${ADVANCE_WINDOW_MS}ms")
            Log.i(TAG, "audioFormat=$audioFormat playing=${readIsPlaying(player)}")

            assertTrue(
                "player reached READY but position did not advance ($before -> $after); " +
                    "audio is not rendering",
                after > before
            )
            assertNotNull("no audio format selected — nothing was decoded", audioFormat)
        } finally {
            instrumentation.runOnMainSync { player.release() }
        }
    }

    private fun readPosition(player: ExoPlayer): Long {
        var position = 0L
        InstrumentationRegistry.getInstrumentation().runOnMainSync { position = player.currentPosition }
        return position
    }

    private fun readIsPlaying(player: ExoPlayer): Boolean {
        var playing = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { playing = player.isPlaying }
        return playing
    }

    private fun readAudioFormat(player: ExoPlayer): String? {
        var format: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            format = player.audioFormat?.let { "${it.sampleRate}Hz ${it.channelCount}ch ${it.sampleMimeType}" }
        }
        return format
    }

    private suspend fun fetchVisitorData(client: HttpClient): String? = runCatching {
        val raw = client.get(SW_JS_DATA_URL) {
            header("User-Agent", YouTubeClient.USER_AGENT_WEB)
            header("Accept", "*/*")
        }.bodyAsText()
        val extracted = SessionProvider.extractVisitorData(raw)
        Log.i(TAG, "visitorData=${if (extracted != null) "len=${extracted.length}" else "NULL"}")
        extracted
    }.getOrElse {
        Log.e(TAG, "visitor fetch failed: ${it.javaClass.simpleName}: ${it.message}")
        null
    }

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "?"
    }

    private companion object {
        private const val TAG = "CRANK_PLAYVERIFY"
        private const val SW_JS_DATA_URL = "https://music.youtube.com/sw.js_data"

        /** "Shape of You" — a track the UI surfaces, so the probe exercises a realistic id. */
        private const val VIDEO_ID = "60ItHLz5WEA"

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private const val READY_TIMEOUT_MS = 30_000L
        private const val ADVANCE_WINDOW_MS = 4_000L
    }
}
