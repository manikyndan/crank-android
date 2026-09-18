package com.crank.music.data.remote

import android.util.Log
import com.crank.music.data.remote.innertube.InnerTubeConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CRANK_STREAM"

@Serializable
private data class PipedStreamResponse(
    @SerialName("audioStreams") val audioStreams: List<PipedAudioStream> = emptyList()
)

@Serializable
private data class PipedAudioStream(
    @SerialName("url") val url: String? = null,
    @SerialName("bitrate") val bitrate: Long? = null,
    @SerialName("mimeType") val mimeType: String? = null
)

@Serializable
private data class InvidiousResponse(
    @SerialName("adaptiveFormats") val adaptiveFormats: List<InvidiousFormat> = emptyList()
)

@Serializable
private data class InvidiousFormat(
    @SerialName("url") val url: String? = null,
    @SerialName("bitrate") val bitrate: String? = null,
    @SerialName("type") val type: String? = null
)

@Singleton
class YouTubeStreamResolver @Inject constructor(
    private val client: HttpClient,
    private val innerTubeConfig: InnerTubeConfig
) {

    private val streamHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Origin" to "https://www.youtube.com",
        "Referer" to "https://www.youtube.com/"
    )

    @Volatile
    private var cachedVisitorData: String? = null

    suspend fun getSongStreamUrl(videoId: String, songTitle: String = "", artistName: String = ""): StreamData {
        if (videoId.isBlank() && songTitle.isBlank()) {
            throw StreamResolutionException("No video ID or song info")
        }

        if (videoId.startsWith("http://") || videoId.startsWith("https://")) {
            Log.d(TAG, "Already a URL: $videoId")
            return StreamData(url = videoId, headers = streamHeaders)
        }

        Log.d(TAG, "=== Resolving: $videoId | $songTitle - $artistName ===")

        return withContext(Dispatchers.IO) {
            if (cachedVisitorData == null) {
                fetchVisitorData()
            }

            // Strategy 1: InnerTube ANDROID_TESTSUITE (most reliable for audio)
            tryInnerTubeTestSuite(videoId)?.let { url ->
                return@withContext StreamData(url = url, headers = streamHeaders)
            }

            // Strategy 2: InnerTube IOS (Apple client - fewer restrictions)
            tryInnerTubeIOS(videoId)?.let { url ->
                return@withContext StreamData(url = url, headers = streamHeaders)
            }

            // Strategy 3: InnerTube WEB_REMIX (YouTube Music web)
            tryInnerTubeWebRemix(videoId)?.let { url ->
                return@withContext StreamData(url = url, headers = streamHeaders)
            }

            // Strategy 4: InnerTube ANDROID (standard Android client)
            tryInnerTubeAndroid(videoId)?.let { url ->
                return@withContext StreamData(url = url, headers = streamHeaders)
            }

            // Strategy 5: InnerTube WEB (with visitor data)
            tryInnerTubeWeb(videoId)?.let { url ->
                return@withContext StreamData(url = url, headers = streamHeaders)
            }

            // Strategy 6: Piped API (third-party frontend)
            tryPiped(videoId)?.let { url ->
                return@withContext StreamData(url = url, headers = streamHeaders)
            }

            // Strategy 7: Invidious API (another frontend)
            tryInvidious(videoId)?.let { url ->
                return@withContext StreamData(url = url, headers = streamHeaders)
            }

            // Strategy 8: NewPipe Extractor library
            tryNewPipe(videoId)?.let { url ->
                return@withContext StreamData(url = url, headers = streamHeaders)
            }

            // NO iTunes fallback - only full tracks are acceptable
            throw StreamResolutionException("All strategies failed for $videoId ($songTitle)")
        }
    }

    private suspend fun tryInnerTubeTestSuite(videoId: String): String? {
        return try {
            val requestBody = buildJsonObject {
                put("context", buildJsonObject {
                    put("client", buildJsonObject {
                        put("clientName", JsonPrimitive("ANDROID_TESTSUITE"))
                        put("clientVersion", JsonPrimitive("1.9"))
                        put("androidSdkVersion", JsonPrimitive(34))
                        put("hl", JsonPrimitive("en"))
                        put("gl", JsonPrimitive("US"))
                    })
                })
                put("videoId", JsonPrimitive(videoId))
                put("contentCheckOk", JsonPrimitive(true))
                put("racyCheckOk", JsonPrimitive(true))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withKey("player"))
                contentType(ContentType.Application.Json)
                header("User-Agent", "com.google.android.youtube/19.29.37 (Linux; U; Android 14; US) gzip")
                setBody(requestBody)
            }.body()

            extractAudioUrl(response, "TESTSUITE", videoId)
        } catch (e: Exception) {
            Log.d(TAG, "InnerTube TESTSUITE failed for $videoId: ${e.message}")
            null
        }
    }

    private suspend fun tryInnerTubeIOS(videoId: String): String? {
        return try {
            val requestBody = buildJsonObject {
                put("context", buildJsonObject {
                    put("client", buildJsonObject {
                        put("clientName", JsonPrimitive("IOS"))
                        put("clientVersion", JsonPrimitive("19.45.4"))
                        put("deviceMake", JsonPrimitive("Apple"))
                        put("deviceModel", JsonPrimitive("iPhone16,2"))
                        put("hl", JsonPrimitive("en"))
                        put("gl", JsonPrimitive("US"))
                        put("osName", JsonPrimitive("iOS"))
                        put("osVersion", JsonPrimitive("18.1.0.22B83"))
                    })
                })
                put("videoId", JsonPrimitive(videoId))
                put("contentCheckOk", JsonPrimitive(true))
                put("racyCheckOk", JsonPrimitive(true))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withKey("player"))
                contentType(ContentType.Application.Json)
                header("User-Agent", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)")
                setBody(requestBody)
            }.body()

            extractAudioUrl(response, "IOS", videoId)
        } catch (e: Exception) {
            Log.d(TAG, "InnerTube IOS failed for $videoId: ${e.message}")
            null
        }
    }

    private suspend fun tryInnerTubeAndroid(videoId: String): String? {
        return try {
            val requestBody = buildJsonObject {
                put("context", buildJsonObject {
                    put("client", buildJsonObject {
                        put("clientName", JsonPrimitive("ANDROID"))
                        put("clientVersion", JsonPrimitive("19.29.37"))
                        put("androidSdkVersion", JsonPrimitive(34))
                        put("hl", JsonPrimitive("en"))
                        put("gl", JsonPrimitive("US"))
                        put("osName", JsonPrimitive("Android"))
                        put("osVersion", JsonPrimitive("14"))
                        put("platform", JsonPrimitive("MOBILE"))
                    })
                })
                put("videoId", JsonPrimitive(videoId))
                put("contentCheckOk", JsonPrimitive(true))
                put("racyCheckOk", JsonPrimitive(true))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withKey("player"))
                contentType(ContentType.Application.Json)
                header("User-Agent", "com.google.android.youtube/19.29.37 (Linux; U; Android 14; US) gzip")
                setBody(requestBody)
            }.body()

            extractAudioUrl(response, "ANDROID", videoId)
        } catch (e: Exception) {
            Log.d(TAG, "InnerTube ANDROID failed for $videoId: ${e.message}")
            null
        }
    }

    private suspend fun tryInnerTubeWebRemix(videoId: String): String? {
        return try {
            val requestBody = buildJsonObject {
                put("context", buildJsonObject {
                    put("client", buildJsonObject {
                        put("clientName", JsonPrimitive("WEB_REMIX"))
                        put("clientVersion", JsonPrimitive("1.20241111.01.00"))
                        put("hl", JsonPrimitive("en"))
                        put("gl", JsonPrimitive("US"))
                    })
                })
                put("videoId", JsonPrimitive(videoId))
                put("contentCheckOk", JsonPrimitive(true))
                put("racyCheckOk", JsonPrimitive(true))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withMusicKey("player"))
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                setBody(requestBody)
            }.body()

            extractAudioUrl(response, "WEB_REMIX", videoId)
        } catch (e: Exception) {
            Log.d(TAG, "InnerTube WEB_REMIX failed for $videoId: ${e.message}")
            null
        }
    }

    private suspend fun tryInnerTubeWeb(videoId: String): String? {
        return try {
            val visitorData = cachedVisitorData ?: ""

            val requestBody = buildJsonObject {
                put("context", buildJsonObject {
                    put("client", buildJsonObject {
                        put("clientName", JsonPrimitive("WEB"))
                        put("clientVersion", JsonPrimitive("2.20241126.01.00"))
                        put("hl", JsonPrimitive("en"))
                        put("gl", JsonPrimitive("US"))
                        if (visitorData.isNotBlank()) put("visitorData", JsonPrimitive(visitorData))
                    })
                })
                put("videoId", JsonPrimitive(videoId))
                put("contentCheckOk", JsonPrimitive(true))
                put("racyCheckOk", JsonPrimitive(true))
            }

            val response: JsonObject = client.post {
                url(innerTubeConfig.withKey("player"))
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                setBody(requestBody)
            }.body()

            extractAudioUrl(response, "WEB", videoId)
        } catch (e: Exception) {
            Log.d(TAG, "InnerTube WEB failed for $videoId: ${e.message}")
            null
        }
    }

    private suspend fun extractAudioUrl(response: JsonObject, strategy: String, videoId: String): String? {
        val status = response["playabilityStatus"]?.jsonObject
            ?.get("status")?.jsonPrimitive?.content

        Log.d(TAG, "InnerTube $strategy: status=$status for $videoId")

        if (status != "OK") {
            val reason = response["playabilityStatus"]?.jsonObject
                ?.get("reason")?.jsonPrimitive?.content ?: "unknown"
            Log.d(TAG, "InnerTube $strategy: reason=$reason for $videoId")
            return null
        }

        val streamingData = response["streamingData"]?.jsonObject ?: return null

        // Probe the available cipher helpers on every InnerTube response. YouTube
        // rotates which of these it sends, so whichever game is present decides
        // how we can recover a URL.
        val serverAbrStreamingUrl = streamingData["serverAbrStreamingUrl"]
            ?.jsonPrimitive?.content
        val signatureTimestamp = response["playabilityStatus"]?.jsonObject
            ?.get("liveStreamability")?.jsonObject
            ?.get("liveStreamabilityRenderer")?.jsonObject
            ?.get("videoId")?.jsonPrimitive?.content
            ?: response["streamingData"]?.jsonObject
                ?.get("signatureTimestamp")?.jsonPrimitive?.content

        // Try adaptive formats first (separate audio/video - better quality).
        // These are the ones that carry the highest-bitrate audio, so it is worth
        // decoding a cipher here rather than falling through to combined formats.
        val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray
        if (adaptiveFormats != null) {
            val audioFormats = adaptiveFormats.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                .filter { (it["mimeType"]?.jsonPrimitive?.content ?: "").contains("audio/") }
                .sortedByDescending { it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }

            if (audioFormats.isNotEmpty()) {
                val best = audioFormats.first()

                // Case 1: plain URL, nothing to decode.
                val directUrl = best.get("url")?.jsonPrimitive?.content
                if (!directUrl.isNullOrBlank()) {
                    Log.d(TAG, "SUCCESS InnerTube $strategy (adaptive direct) for $videoId")
                    return directUrl
                }

                // Case 2: signatureCipher. This is a URL whose `s` parameter is
                // obfuscated and must be run through the player's decipher function.
                val cipherRaw = best.get("signatureCipher")?.jsonPrimitive?.content
                    ?: best.get("cipher")?.jsonPrimitive?.content
                if (!cipherRaw.isNullOrBlank()) {
                    val decoded = decodeSignatureCipher(cipherRaw, videoId, strategy)
                    if (!decoded.isNullOrBlank()) return decoded
                }

                // Case 3: n-parameter throttling only. Newer responses ship a
                // readable `url` alongside `signatureCipher`; if neither the URL
                // nor the cipher worked we still have the throttling parameter to
                // sort out on whatever URL we did manage to obtain.
                if (!directUrl.isNullOrBlank()) {
                    val nParam = extractQueryParam(directUrl, "n")
                    if (nParam != null) {
                        val unthrottled = deobfuscateNParam(videoId, nParam)
                        if (unthrottled != null) {
                            Log.d(TAG, "SUCCESS InnerTube $strategy (n-param deobfuscated) for $videoId")
                            return unthrottled
                        }
                    }
                }
            }
        }

        // Try combined formats as fallback. These are lower quality but far more
        // often ship a plain URL with no cipher at all.
        val formats = streamingData["formats"]?.jsonArray
        if (formats != null) {
            val combinedFormats = formats.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                .sortedByDescending { it["bitrate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }

            for (fmt in combinedFormats) {
                val directUrl = fmt.get("url")?.jsonPrimitive?.content
                if (!directUrl.isNullOrBlank()) {
                    Log.d(TAG, "SUCCESS InnerTube $strategy (combined direct) for $videoId")
                    return directUrl
                }
                val cipherRaw = fmt.get("signatureCipher")?.jsonPrimitive?.content
                    ?: fmt.get("cipher")?.jsonPrimitive?.content
                if (!cipherRaw.isNullOrBlank()) {
                    val decoded = decodeSignatureCipher(cipherRaw, videoId, strategy)
                    if (!decoded.isNullOrBlank()) {
                        Log.d(TAG, "SUCCESS InnerTube $strategy (combined cipher) for $videoId")
                        return decoded
                    }
                }
            }
        }

        Log.d(TAG, "InnerTube $strategy: no playable URL for $videoId (abr=${serverAbrStreamingUrl != null}, ts=$signatureTimestamp)")
        return null
    }

    /**
     * Decodes a YouTube `signatureCipher` / `cipher` blob into a playable URL.
     *
     * The blob is a form-encoded query string carrying three fields: `s` (the
     * obfuscated signature), `sp` (the query-parameter name it must be written
     * back under, almost always `sig`) and `url` (the base stream URL). We hand
     * `s` to NewPipe's [YoutubeJavaScriptPlayerManager], which downloads the
     * player script and evaluates its decipher routine. That keeps the
     * deobfuscation logic upstream and maintained rather than reimplemented here.
     */
    private suspend fun decodeSignatureCipher(
        cipherRaw: String,
        videoId: String,
        strategy: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val params = cipherRaw.split("&").mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else {
                    val key = pair.substring(0, idx)
                    val value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    key to value
                }
            }.toMap()

            val obfuscatedSignature = params["s"] ?: params["sig"]
            val baseUrl = params["url"]
            val signatureParam = params["sp"] ?: "sig"

            if (obfuscatedSignature.isNullOrBlank() || baseUrl.isNullOrBlank()) {
                Log.d(TAG, "InnerTube $strategy: cipher missing s/url for $videoId")
                return@withContext null
            }

            val deobfuscated = YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                videoId,
                obfuscatedSignature
            ) ?: run {
                Log.d(TAG, "InnerTube $strategy: deobfuscateSignature returned null for $videoId")
                return@withContext null
            }

            Log.d(TAG, "InnerTube $strategy: signature deobfuscated for $videoId")

            // The deobfuscated signature replaces whatever the URL already had —
            // appending would send two competing signature params.
            val sep = if (baseUrl.contains('?')) '&' else '?'
            val urlWithSig = "$baseUrl$sep$signatureParam=" +
                java.net.URLEncoder.encode(deobfuscated, "UTF-8")

            // A decoded signature is usually still accompanied by an `n` throttling
            // parameter. Leaving it un-deobfuscated caps throughput severely.
            val nParam = extractQueryParam(urlWithSig, "n")
            if (nParam != null) {
                val unthrottled = YoutubeJavaScriptPlayerManager
                    .getUrlWithThrottlingParameterDeobfuscated(videoId, urlWithSig)
                if (!unthrottled.isNullOrBlank()) {
                    Log.d(TAG, "InnerTube $strategy: throttling param deobfuscated for $videoId")
                    return@withContext unthrottled
                }
            }

            urlWithSig
        } catch (e: Exception) {
            Log.d(TAG, "InnerTube $strategy: cipher decode failed for $videoId: ${e.message}")
            null
        }
    }

    /** Deobfuscates only the `n` throttling parameter, leaving the rest of the URL intact. */
    private suspend fun deobfuscateNParam(videoId: String, nParam: String): String? =
        withContext(Dispatchers.IO) {
            try {
                YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, nParam)
            } catch (e: Exception) {
                Log.d(TAG, "n-param deobfuscation failed for $videoId: ${e.message}")
                null
            }
        }

    /** Reads a single query parameter out of a URL, or null when absent. */
    private fun extractQueryParam(url: String, name: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isBlank()) return null
        return query.split("&").firstNotNullOfOrNull { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0 && pair.substring(0, idx) == name) {
                pair.substring(idx + 1).takeIf { it.isNotBlank() }
            } else null
        }
    }

    private suspend fun tryPiped(videoId: String): String? {
        val endpoints = listOf(
            "https://pipedapi.kavin.rocks/streams/$videoId",
            "https://pipedapi.leptons.xyz/streams/$videoId",
            "https://api.piped.privacydev.net/streams/$videoId",
            "https://pipedapi.r4fo.com/streams/$videoId",
            "https://pipedapi.adminforge.de/streams/$videoId",
            "https://watchapi.whatever.social/streams/$videoId"
        )
        for (endpoint in endpoints) {
            try {
                val response: PipedStreamResponse = client.get(endpoint).body()
                val url = response.audioStreams
                    .filter { !it.url.isNullOrBlank() }
                    .maxByOrNull { it.bitrate ?: 0L }?.url
                if (url != null) {
                    Log.d(TAG, "SUCCESS Piped for $videoId from $endpoint")
                    return url
                }
            } catch (e: Exception) {
                Log.d(TAG, "Piped failed ($endpoint): ${e.message}")
            }
        }
        return null
    }

    private suspend fun tryInvidious(videoId: String): String? {
        val endpoints = listOf(
            "https://inv.nadeko.net/api/v1/videos/$videoId",
            "https://invidious.nerdvpn.de/api/v1/videos/$videoId",
            "https://inv.tux.im/api/v1/videos/$videoId",
            "https://yewtu.be/api/v1/videos/$videoId",
            "https://vid.puffyan.us/api/v1/videos/$videoId",
            "https://invidious.fdn.fr/api/v1/videos/$videoId"
        )
        for (endpoint in endpoints) {
            try {
                val response: InvidiousResponse = client.get(endpoint).body()
                val url = response.adaptiveFormats
                    .filter { !it.url.isNullOrBlank() && (it.type?.contains("audio/") == true) }
                    .maxByOrNull { it.bitrate?.toLongOrNull() ?: 0L }?.url
                if (url != null) {
                    Log.d(TAG, "SUCCESS Invidious for $videoId from $endpoint")
                    return url
                }
            } catch (e: Exception) {
                Log.d(TAG, "Invidious failed ($endpoint): ${e.message}")
            }
        }
        return null
    }

    private fun tryNewPipe(videoId: String): String? {
        return try {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            val best = extractor.audioStreams
                ?.filter { !it.content.isNullOrBlank() }
                ?.maxByOrNull { it.averageBitrate }
            if (best != null && !best.content.isNullOrBlank()) {
                Log.d(TAG, "SUCCESS NewPipe for $videoId")
                best.content
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "NewPipe failed for $videoId: ${e.message}")
            null
        }
    }

    suspend fun fetchVisitorData() {
        try {
            val page: String = client.get("https://music.youtube.com").body()
            val match = Regex("\"VISITOR_DATA\":\"([^\"]+)\"").find(page)
                ?: Regex("\"visitorData\":\"([^\"]+)\"").find(page)
            cachedVisitorData = match?.groupValues?.get(1)
            Log.d(TAG, "visitorData fetched: ${cachedVisitorData?.take(30)}...")
        } catch (e: Exception) {
            Log.e(TAG, "visitorData fetch failed: ${e.message}")
        }
    }
}

class StreamResolutionException(message: String) : Exception(message)
