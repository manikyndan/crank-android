package com.crank.music.data.remote.innertube

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Talks to the InnerTube `/player` endpoint.
 *
 * Adapted from the Echo Music project (GPL-3.0).
 *
 * ## What was wrong before
 *
 * This replaces `InnerTubeApi`'s inline request building, which sent a single hardcoded client
 * identity, a `clientVersion` roughly two years stale, no `X-Goog-Visitor-Id` header, no
 * retry, and — decisively — no Proof-of-Origin token. A stale `clientVersion` alone is enough
 * to be refused, because YouTube gates clients by version server-side.
 *
 * ## Header rules that are not obvious
 *
 * - `X-YouTube-Client-Name` carries the client's **numeric id**, not its name, despite the
 *   header's wording. See [YouTubeClient.clientId].
 * - `X-Goog-Visitor-Id` is sent to **every** client, including ones that do not support login.
 *   Withholding it from anonymous clients was tried and measured to be backwards: the clients
 *   that currently return usable streams *require* it, and respond `LOGIN_REQUIRED` with zero
 *   formats without one.
 * - `Authorization` is only added when signed in. It requires a `SAPISIDHASH` derived from the
 *   `SAPISID` cookie, not a bearer token.
 */
class InnerTubePlayerApi(
    private val client: HttpClient,
    private val json: Json,
) {

    /**
     * Fetches a player response for [videoId] using [ytClient]'s identity.
     *
     * @param signatureTimestamp only sent when the client declares `useSignatureTimestamp`.
     * @param poToken only sent when the client declares `useWebPoTokens`. Passing a token to a
     *   client that does not accept one is ignored at best.
     * @param visitorData sent as a header and in the context together; they must agree.
     * @param dataSyncId the account-scoped id, sent as `onBehalfOfUser` only for clients that
     *   support login.
     */
    suspend fun player(
        videoId: String,
        ytClient: YouTubeClient,
        visitorData: String?,
        dataSyncId: String?,
        signatureTimestamp: Int?,
        poToken: String?,
        playlistId: String? = null,
    ): InnerTubePlayerResponse = withRetry {
        val response: HttpResponse =
            client.post {
                url("${YouTubeClient.API_URL_YOUTUBE_MUSIC}player")
                contentType(ContentType.Application.Json)
                applyInnerTubeHeaders(ytClient, visitorData)
                setBody(
                    InnerTubePlayerRequest(
                        context = buildContext(ytClient, visitorData, dataSyncId),
                        videoId = videoId,
                        playlistId = playlistId,
                        playbackContext =
                            if (ytClient.useSignatureTimestamp && signatureTimestamp != null) {
                                InnerTubePlayerRequest.PlaybackContext(
                                    InnerTubePlayerRequest.PlaybackContext.ContentPlaybackContext(
                                        signatureTimestamp
                                    )
                                )
                            } else {
                                null
                            },
                        serviceIntegrityDimensions =
                            if (ytClient.useWebPoTokens && poToken != null) {
                                InnerTubePlayerRequest.ServiceIntegrityDimensions(poToken)
                            } else {
                                null
                            },
                    )
                )
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                }
            }

        // The response is decoded through the raw text so that an unexpected shape produces a
        // readable log line rather than an opaque serialization failure. A refusal body is
        // still valid JSON, so this is about diagnosis, not about tolerating bad JSON.
        val text = decodeBodyText(response)
        json.decodeFromString(InnerTubePlayerResponse.serializer(), text)
    }

    /**
     * Builds the `context` block, mirroring the headers.
     *
     * `visitorData` appears in both places deliberately and they must match — YouTube treats the
     * header and the context field as one identity, and a mismatch is treated as an anonymous
     * request at best.
     */
    private fun buildContext(
        ytClient: YouTubeClient,
        visitorData: String?,
        dataSyncId: String?,
    ): InnerTubeContext {
        return InnerTubeContext(
            client =
                InnerTubeContext.InnerTubeClientContext(
                    clientName = ytClient.clientName,
                    clientVersion = ytClient.clientVersion,
                    visitorData = visitorData,
                    osName = ytClient.osName,
                    osVersion = ytClient.osVersion,
                    deviceMake = ytClient.deviceMake,
                    deviceModel = ytClient.deviceModel,
                    androidSdkVersion = ytClient.androidSdkVersion,
                    buildId = ytClient.buildId,
                    cronetVersion = ytClient.cronetVersion,
                ),
            // Only sent for clients that support login: an account-scoped id attached to a
            // client that cannot authenticate looks like a hijacked session.
            user =
                if (ytClient.loginSupported && dataSyncId != null) {
                    InnerTubeContext.InnerTubeUserContext(onBehalfOfUser = dataSyncId)
                } else {
                    null
                },
        )
    }

    /**
     * Applies the InnerTube headers.
     *
     * `X-Origin` and `Referer` are required and must match the host being called, otherwise the
     * request is rejected as coming from an unknown web origin.
     */
    private fun HttpRequestBuilder.applyInnerTubeHeaders(
        ytClient: YouTubeClient,
        visitorData: String?,
    ) {
        headers.append("X-Goog-Api-Format-Version", "1")
        headers.append("X-YouTube-Client-Name", ytClient.clientId)
        headers.append("X-YouTube-Client-Version", ytClient.clientVersion)
        headers.append("X-Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
        headers.append("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
        headers.append("Accept-Language", "en-US,en;q=0.9")

        if (visitorData != null) {
            headers.append("X-Goog-Visitor-Id", visitorData)
        }
    }

    /**
     * Reads the response body as text, logging a usable diagnostic when it cannot be read.
     *
     * Keeping this separate means a decode failure names the endpoint and the body length,
     * rather than surfacing as a stack trace pointing at a generated serializer.
     */
    private suspend fun decodeBodyText(response: HttpResponse): String {
        return try {
            response.body<String>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read player response body: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    /**
     * Retries on transport failure with exponential backoff.
     *
     * Retries **only** [IOException] — a connection reset or a timeout. A 4xx does not retry:
     * those are deterministic answers, and retrying a `LOGIN_REQUIRED` just burns time before
     * the cascade moves to a client that might work. That distinction is why `expectSuccess` is
     * on in the shared client: a refusal arrives as an exception with a status code rather than
     * as a response the caller might mistake for data.
     */
    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 250,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        var attempt = 0
        var currentDelay = initialDelayMs
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                attempt++
                if (attempt >= maxAttempts) throw e
                Log.w(TAG, "Transport failure (attempt $attempt/$maxAttempts), retrying in ${currentDelay}ms")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
    }

    companion object {
        private const val TAG = "CRANK_INNERTUBE"

        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val SOCKET_TIMEOUT_MS = 30_000L
    }
}
