package com.crank.music.feature.recognition

import android.util.Log
import com.crank.music.BuildConfig
import com.crank.music.domain.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a recognition attempt.
 *
 * A sealed result rather than a nullable [Song], because "no match" and "we could
 * not ask" are different situations that deserve different messages. Collapsing
 * both into `null` previously made a dead upstream service look like silence in
 * the room, so users kept retrying a call that could never succeed.
 */
sealed interface RecognitionResult {
    data class Match(val song: Song) : RecognitionResult
    data object NoMatch : RecognitionResult

    /** No token configured — a build/configuration problem, not a user problem. */
    data object NotConfigured : RecognitionResult

    /** The service was reachable but rejected the request (bad token, quota, etc.). */
    data class ServiceError(val statusCode: Int) : RecognitionResult

    /** Network unreachable, timed out, or the response could not be parsed. */
    data class NetworkError(val detail: String) : RecognitionResult
}

interface MusicRecognitionRepository {
    suspend fun recognizeSong(audioBytes: ByteArray): RecognitionResult
}

@Singleton
class MusicRecognitionRepositoryImpl @Inject constructor(
    private val client: HttpClient
) : MusicRecognitionRepository {

    private val apiToken: String = runCatching { BuildConfig.AUDD_API_TOKEN }.getOrDefault("")

    override suspend fun recognizeSong(audioBytes: ByteArray): RecognitionResult {
        if (audioBytes.isEmpty()) return RecognitionResult.NoMatch
        if (apiToken.isBlank()) return RecognitionResult.NotConfigured

        return try {
            val response: JsonObject = client.submitFormWithBinaryData(
                formData = formData {
                    append("api_token", apiToken)
                    // AudD expects the raw audio under `file`. The recorded clip is
                    // headerless PCM, so we declare it as such; AudD sniffs the
                    // container itself and handles headerless PCM fine.
                    append(
                        key = "file",
                        value = audioBytes,
                        headers = Headers.build {
                            append(HttpHeaders.ContentType, "audio/L16")
                            append(HttpHeaders.ContentDisposition, "filename=\"crank-sample.pcm\"")
                        }
                    )
                }
            ) {
                // AudD's standard recognition endpoint. There is no `/v1/recognize`
                // path — the API is served from the bare host root.
                url(AUDD_ENDPOINT)
            }.body()
            parseResponse(response)
        } catch (e: io.ktor.client.plugins.ResponseException) {
            // Reached the service; it said no. `expectSuccess` in NetworkModule
            // guarantees this is only raised for a genuine non-2xx status.
            Log.e(TAG, "AudD rejected the request: HTTP ${e.response.status.value}", e)
            RecognitionResult.ServiceError(e.response.status.value)
        } catch (e: Exception) {
            Log.e(TAG, "Recognition request failed: ${e.message}", e)
            RecognitionResult.NetworkError(e.message ?: "Unknown error")
        }
    }

    private fun parseResponse(response: JsonObject): RecognitionResult {
        when (response["status"]?.jsonPrimitive?.content) {
            "success" -> Unit
            "error" -> {
                val code = response["error"]?.jsonObject
                    ?.get("error_code")?.jsonPrimitive?.content?.toIntOrNull()
                return RecognitionResult.ServiceError(code ?: -1)
            }
            else -> return RecognitionResult.NetworkError("Unrecognized response envelope")
        }

        // On a successful request with no match, `result` is null (or an empty
        // array on other AudD methods). Both shapes must be tolerated.
        val result = response["result"]
        val track = when (result) {
            null -> null
            is JsonObject -> result
            is JsonArray -> result.firstOrNull()?.jsonObject
            else -> null
        } ?: return RecognitionResult.NoMatch

        val title = track["title"]?.jsonPrimitive?.contentOrNullSafe()
            ?: return RecognitionResult.NoMatch

        return RecognitionResult.Match(
            Song(
                // AudD returns no stable per-track ID, so derive one from the fields
                // we do get. Using a UUID here (as the previous implementation did)
                // would create a new "song" identity on every single recognition,
                // so the same track could never be recognised twice in a row and
                // would never match anything already in the library.
                id = "audd:${(track["artist"]?.jsonPrimitive?.contentOrNullSafe() ?: "unknown")
                    .lowercase()}:${title.lowercase()}",
                title = title,
                artistName = track["artist"]?.jsonPrimitive?.contentOrNullSafe() ?: "Unknown Artist",
                albumId = track["album"]?.jsonPrimitive?.contentOrNullSafe(),
                // AudD does not return duration. 180s was previously fabricated here
                // as if it were real data; 0 is an honest "unknown" that the UI and
                // the player already tolerate (they resolve real duration on load).
                durationMs = 0L,
                artworkUrl = track["song_link"]?.jsonPrimitive?.contentOrNullSafe() ?: "",
                isLocal = false,
                streamUrl = Song.UNRESOLVED_STREAM_PREFIX + UUID.randomUUID(),
            )
        )
    }

    /**
     * `jsonPrimitive` throws if the value is a JSON object/array, and a null value
     * arrives as `JsonNull` whose `.content` is the literal string "null". Both
     * would otherwise leak junk ("null") or crash on an unexpected field shape.
     */
    private fun kotlinx.serialization.json.JsonElement.contentOrNullSafe(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "CRANK_RECOGNITION"
        const val AUDD_ENDPOINT = "https://api.audd.io/"
    }
}
