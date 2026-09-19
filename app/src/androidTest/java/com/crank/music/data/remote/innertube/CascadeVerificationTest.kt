package com.crank.music.data.remote.innertube

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.crank.music.data.remote.SessionProvider
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
import org.junit.Test

/**
 * On-device verification that playback can actually resolve a stream.
 *
 * This is a *verification probe*, not a behavioural unit test: it cannot assert against a recorded
 * YouTube response (those rot), so it drives the real classes against the live service and logs
 * the outcome. The signal is binary: does the cascade return a playable `googlevideo` URL?
 *
 * It deliberately mirrors [com.crank.music.data.remote.StreamResolver.resolveStreamUrl], which is
 * the app's single playback entry point. That method supplies a visitor id from
 * [com.crank.music.data.remote.SessionProvider] before walking the cascade, and the visitor id is
 * not optional: the clients that serve a whole file answer `LOGIN_REQUIRED` with zero formats
 * without it, and it is also what a Proof-of-Origin token is bound to. An earlier revision of this
 * probe passed `visitorData = null` and so verified a code path the app never actually takes —
 * it reported a cascade failure that was an artefact of the probe itself.
 *
 * The graph is constructed by hand from the app's real classes (the same `InnerTubePlayerApi` and
 * `StreamCascadeResolver` the app uses) because the app is a Hilt graph and `hilt-android-testing`
 * is not on the test classpath.
 */
class CascadeVerificationTest {

    @Test
    fun resolvesAStreamForAKnownVideo() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = Json { ignoreUnknownKeys = true }
        val client = HttpClient(Android) {
            install(ContentNegotiation) { json(json) }
        }
        val playerApi = InnerTubePlayerApi(client, json)
        val poToken = PoTokenGenerator(context)
        val resolver = StreamCascadeResolver(playerApi, poToken)

        val videoId = "60ItHLz5WEA" // "Shape of You" by Ed Sheeran — a track the UI surfaces.
        Log.i(TAG, "poToken available=${poToken.isAvailable()}")

        // Step 1: obtain the visitor id exactly the way SessionProvider does.
        val visitorData = runBlocking {
            runCatching {
                val raw = client.get(SW_JS_DATA_URL) {
                    header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                    header("Accept", "*/*")
                }.bodyAsText()
                Log.i(TAG, "sw.js_data bytes=${raw.length}")
                val extracted = SessionProvider.extractVisitorData(raw)
                Log.i(TAG, "visitorData extracted=${extracted != null} len=${extracted?.length}")
                extracted
            }.getOrElse { e ->
                Log.e(TAG, "sw.js_data fetch failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }

        // The one assertion this probe makes.
        //
        // The visitor id is the single input the whole cascade silently depends on: without it
        // every whole-file client answers LOGIN_REQUIRED, no token can be minted, and playback
        // fails in a way that looks like an anti-bot refusal rather than a missing argument. If
        // `sw.js_data` changes shape, this is the regression worth failing loudly on, and it is
        // the part genuinely under our control.
        //
        // The cascade and range results below are *logged, not asserted*: they depend on YouTube
        // accepting this device and IP, so asserting them would make the suite fail for reasons
        // unrelated to a code defect.
        assertNotNull(
            "sw.js_data must yield a visitor id — without it no client serves a whole file",
            visitorData,
        )

        // Step 2: walk the cascade with that visitor id, as StreamResolver does.
        val result = runBlocking {
            try {
                resolver.resolve(
                    videoId = videoId,
                    visitorData = visitorData,
                    dataSyncId = null,
                    isLoggedIn = false,
                )
            } catch (e: Throwable) {
                Log.e(TAG, "resolve FAILED: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }

        if (result != null) {
            val isGooglevideo = result.url.contains("googlevideo.com")
            Log.i(
                TAG,
                "CASCADE_OK googlevideo=$isGooglevideo client=${result.client.clientName} " +
                    "expires=${result.expiresInSeconds} pot=${result.hasPoToken} " +
                    "url=${result.url.take(140)}",
            )

            // Step 3: prove the URL is actually servable.
            //
            // Resolving a URL is not the same as being able to play it. The symptom this whole
            // investigation started from was a 403 on the first byte, and a URL can be returned
            // successfully and still be refused by the CDN. A small range request is the cheapest
            // check that distinguishes "we have a URL" from "the URL works".
            val status = runBlocking {
                runCatching {
                    client.get(result.url) {
                        header("Range", "bytes=0-1023")
                        header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                        header("Accept", "*/*")
                        header("Referer", "https://www.youtube.com/")
                    }.status.value
                }.getOrElse { e ->
                    Log.e(TAG, "range request failed: ${e.javaClass.simpleName}: ${e.message}")
                    -1
                }
            }
            Log.i(TAG, "RANGE_CHECK status=$status (200/206 = playable, 403 = refused)")
        } else {
            Log.e(TAG, "CASCADE_NULL no stream resolved for $videoId")
        }
    }

    private companion object {
        private const val TAG = "CRANK_DEVICETEST"
        private const val SW_JS_DATA_URL = "https://music.youtube.com/sw.js_data"
    }
}
