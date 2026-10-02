package com.crank.music.data.remote.potoken

import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import com.crank.music.data.remote.innertube.YouTubeClient
import java.util.Collections
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Mints BotGuard Proof-of-Origin tokens using an offscreen [WebView].
 *
 * Adapted from the Echo Music project (GPL-3.0).
 *
 * ## How the handshake works
 *
 * YouTube's anti-bot system will not accept a `/player` request without proof that a real
 * JavaScript environment evaluated its challenge program. This class is that environment:
 *
 * 1. Load the local `po_token.html` asset, which contains the BotGuard JS runner.
 * 2. Request a challenge from `/api/jnn/v1/Create` and hand it to the page.
 * 3. The page runs BotGuard and returns a `botguardResponse` string.
 * 4. Exchange that at `/api/jnn/v1/GenerateIT` for a long-lived `integrityToken`.
 * 5. The page builds a minter from it; each `obtainPoToken(identifier)` call then yields a
 *    token bound to that identifier.
 *
 * ## Why the WebView is offline
 *
 * [WebView.getSettings] has `blockNetworkLoads = true` and the page is loaded with
 * `loadDataWithBaseURL("https://www.youtube.com", ...)`. The base URL is present so the page
 * has a YouTube origin for its own origin checks, but the WebView itself is forbidden from
 * making network requests — **all** network traffic goes through this class's OkHttp client
 * instead. That split is deliberate: it keeps the WebView's cookie jar and cache from being
 * touched, and it means a hostile page cannot exfiltrate anything.
 *
 * ## Lifecycle
 *
 * Instances are expensive (a WebView plus a BotGuard evaluation, ~2-5s cold). They are owned
 * and cached by [PoTokenGenerator], never created per-request, and destroyed on
 * [close]. A token minted here expires; [isExpired] reports that using the margin YouTube
 * reports minus a safety window.
 */
class PoTokenWebView
private constructor(
    context: Context,
    /** Used exactly once, during initialisation, to hand the ready instance back. */
    private val continuation: Continuation<PoTokenWebView>,
) {
    private val webView = WebView(context)
    private val scope = MainScope()

    /**
     * In-flight `obtainPoToken` calls, keyed by identifier.
     *
     * A map rather than a single slot because several tokens can legitimately be requested
     * concurrently (a metadata fetch and a playback resolve may overlap). Synchronised because
     * the JS interface callbacks arrive on the WebView's thread, not the caller's.
     */
    private val poTokenContinuations =
        Collections.synchronizedMap(ArrayMap<String, Continuation<String>>())

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        onInitializationErrorCloseAndCancel(t)
    }

    /**
     * When the current integrity token stops being usable, in epoch milliseconds.
     *
     * `0L` (the default, i.e. before any token has been minted) reads as "already expired", which
     * is the honest answer: there is no usable token yet, so [PoTokenGenerator] rebuilds the
     * minter instead of using a token it does not have.
     *
     * An epoch-millis `Long` rather than a `lateinit Instant`: `java.time.Instant` is API 26 while
     * `minSdk` is 24 and core library desugaring is not enabled, so the previous form was a
     * guaranteed `NoClassDefFoundError` on Android 7.x. It was also `lateinit`, so a minter that
     * had not yet received its integrity token would throw `UninitializedPropertyAccessException`
     * from inside [PoTokenGenerator]'s lock rather than reporting "expired".
     */
    private var expirationAtMs: Long = 0L

    init {
        val webViewSettings = webView.settings
        webViewSettings.javaScriptEnabled = true
        webViewSettings.userAgentString = USER_AGENT
        // The page never needs the network: this class performs every HTTP call itself.
        // Blocking loads also prevents the WebView from leaking requests or cookies.
        webViewSettings.blockNetworkLoads = true

        // Bridge so the JS side can hand results back asynchronously.
        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    val msg = m.message()
                    when (m.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> logError("JS: $msg")
                        ConsoleMessage.MessageLevel.WARNING -> logWarn("JS: $msg")
                        else -> logDebug("JS: $msg")
                    }

                    // An "Uncaught" error means the interpreter itself failed to evaluate,
                    // which is characteristic of a broken OEM WebView rather than a bad
                    // challenge. Latch it so we stop paying for doomed attempts.
                    if (msg.contains("Uncaught")) {
                        val fmt = "\"$msg\", source: ${m.sourceId()} (${m.lineNumber()})"
                        val exception = BadWebViewException(fmt)
                        logError("This WebView implementation is broken: $fmt")

                        onInitializationErrorCloseAndCancel(exception)
                        popAllPoTokenContinuations().forEach { (_, cont) ->
                            cont.resumeWithException(exception)
                        }
                    }
                    return super.onConsoleMessage(m)
                }
            }
    }

    // region Initialization

    /**
     * Loads `po_token.html` and kicks off the BotGuard handshake.
     *
     * The call to `downloadAndRunBotguard()` is appended after the page's first `</script>`,
     * which is how the runner is triggered once its own definitions exist.
     */
    private fun loadHtmlAndObtainBotguard() {
        scope.launch(exceptionHandler) {
            val html =
                withContext(Dispatchers.IO) {
                    val asset = webView.context.assets
                    val stream =
                        runCatching { asset.open(PO_TOKEN_ASSET) }.getOrNull()
                            ?: throw PoTokenException(missingAssetMessage())
                    stream.bufferedReader().use { it.readText() }
                }

            val data =
                html.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>")
            webView.loadDataWithBaseURL(
                YouTubeClient.ORIGIN_YOUTUBE_MUSIC,
                data,
                "text/html",
                "utf-8",
                null,
            )
        }
    }

    /** Called by the page once loaded. Requests the BotGuard challenge. */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        makeBotguardServiceRequest(
            "$BOTGUARD_API/Create",
            "[ \"$REQUEST_KEY\" ]",
        ) { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            webView.evaluateJavascript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    /** Reported by the page when the interpreter throws. Fatal for this instance. */
    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(error))
    }

    /** Called with the BotGuard execution output. Exchanges it for an integrity token. */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        makeBotguardServiceRequest(
            "$BOTGUARD_API/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { responseBody ->
            try {
                val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)

                // Leave 10 minutes of margin: the reported lifetime is optimistic, and a token
                // that expires mid-request produces a 403 that looks like an anti-bot refusal.
                expirationAtMs =
                    System.currentTimeMillis() + expirationTimeInSeconds * 1000L - 10 * 60 * 1000L

                webView.evaluateJavascript(
                    """try {
                        this.integrityToken = $integrityToken
                        createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                            $JS_INTERFACE.onMinterCreated()
                        }).catch(function(error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\n" + (error.stack || ''))
                        })
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    }""",
                    null
                )
            } catch (e: Exception) {
                onInitializationErrorCloseAndCancel(
                    PoTokenException("parseIntegrityTokenData failed: ${e.message}")
                )
            }
        }
    }

    /** Called once the minter exists, which means this instance is usable. */
    @JavascriptInterface
    fun onMinterCreated() {
        continuation.resume(this)
    }

    // endregion

    // region Obtaining tokens

    /**
     * Mints a token bound to [identifier].
     *
     * The identifier is the binding target: pass the session id for a session-bound token, or a
     * video id for a video-bound one. See [PoTokenResult] — mixing these up yields a
     * well-formed token that YouTube rejects.
     */
    suspend fun generatePoToken(identifier: String): String {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                addPoTokenEmitter(identifier, cont)
                webView.evaluateJavascript(
                    """try {
                        identifier = "$identifier"
                        u8Identifier = ${stringToU8(identifier)}
                        obtainPoToken(u8Identifier).then(function(poTokenU8) {
                            poTokenU8String = poTokenU8.join(",")
                            $JS_INTERFACE.onObtainPoTokenResult(identifier, poTokenU8String)
                        }).catch(function(error) {
                            $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + (error.stack || ''))
                        })
                    } catch (error) {
                        $JS_INTERFACE.onObtainPoTokenError(identifier, error + "\n" + error.stack)
                    }""",
                    null
                )
            }
        }
    }

    /** Reported by the page when `obtainPoToken` fails. */
    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        popPoTokenContinuation(identifier)?.resumeWithException(buildExceptionForJsError(error))
    }

    /** Reported by the page with the raw token bytes, as comma-separated decimals. */
    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val poToken =
            try {
                u8ToBase64(poTokenU8)
            } catch (t: Throwable) {
                popPoTokenContinuation(identifier)?.resumeWithException(t)
                return
            }

        popPoTokenContinuation(identifier)?.resume(poToken)
    }

    /** True once the integrity token's usable lifetime has elapsed. */
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expirationAtMs

    // endregion

    // region Emitter bookkeeping

    private fun addPoTokenEmitter(identifier: String, continuation: Continuation<String>) {
        poTokenContinuations[identifier] = continuation
    }

    private fun popPoTokenContinuation(identifier: String): Continuation<String>? {
        return poTokenContinuations.remove(identifier)
    }

    private fun popAllPoTokenContinuations(): Map<String, Continuation<String>> {
        val result = poTokenContinuations.toMap()
        poTokenContinuations.clear()
        return result
    }

    // endregion

    // region Transport

    /**
     * POSTs an RPC-style body to one of the BotGuard endpoints.
     *
     * The headers are not decorative: `Content-Type: application/json+protobuf` and the
     * `x-user-agent: grpc-web-javascript/0.1` value are what the endpoint expects from the web
     * client, and `x-goog-api-key` is the public browser key that endpoint requires. Without
     * them the request is rejected before reaching the challenge logic.
     */
    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit,
    ) {
        scope.launch(exceptionHandler) {
            val request =
                okhttp3.Request.Builder()
                    .post(data.toRequestBody())
                    .headers(
                        mapOf(
                                "User-Agent" to USER_AGENT,
                                "Accept" to "application/json",
                                "Content-Type" to "application/json+protobuf",
                                "x-goog-api-key" to GOOGLE_API_KEY,
                                "x-user-agent" to "grpc-web-javascript/0.1",
                            )
                            .toHeaders()
                    )
                    .url(url)
                    .build()

            val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
            val httpCode = response.code
            if (httpCode != 200) {
                onInitializationErrorCloseAndCancel(
                    PoTokenException("BotGuard request to $url failed with HTTP $httpCode")
                )
            } else {
                val body = withContext(Dispatchers.IO) { response.body?.string().orEmpty() }
                handleResponseBody(body)
            }
        }
    }

    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        close()
        continuation.resumeWithException(error)
    }

    /** Tears down the WebView. Safe to call more than once. */
    @MainThread
    fun close() {
        scope.cancel()
        runCatching {
            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    // endregion

    companion object {
        private const val TAG = "CRANK_POTOKEN"

        const val PO_TOKEN_ASSET = "po_token.html"

        private const val BOTGUARD_API = "https://www.youtube.com/api/jnn/v1"
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "PoTokenWebView"

        private val httpClient = OkHttpClient.Builder().build()

        /**
         * Explains the one prerequisite that cannot be satisfied from source control.
         *
         * `po_token.html` is deliberately not committed by upstream (both CONTRIBUTING.md and
         * SECURITY.md list it as sensitive), so a fresh checkout will not have it. Failing with
         * this message is far more useful than a generic file-not-found, because the fix is not
         * discoverable from the stack trace.
         */
        fun missingAssetMessage(): String =
            "BotGuard asset '$PO_TOKEN_ASSET' is missing from app/src/main/assets/. " +
                "Playback cannot mint Proof-of-Origin tokens without it, so YouTube will " +
                "reject every stream with HTTP 403. This file is deliberately not committed " +
                "(it is listed as sensitive upstream); supply it locally to enable playback."

        /** True when [PO_TOKEN_ASSET] is present, so the app can warn early rather than at play time. */
        fun isAssetPresent(context: Context): Boolean =
            runCatching { context.assets.open(PO_TOKEN_ASSET).close() }.isSuccess

        /**
         * Creates and initialises an instance, returning only once the minter is ready.
         *
         * Suspends on the main thread because [WebView] must be constructed there.
         */
        suspend fun getNewPoTokenGenerator(context: Context): PoTokenWebView {
            return withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val potWv = PoTokenWebView(context, cont)
                    potWv.loadHtmlAndObtainBotguard()
                }
            }
        }

        private fun logDebug(msg: String) = android.util.Log.d(TAG, msg)
        private fun logWarn(msg: String) = android.util.Log.w(TAG, msg)
        private fun logError(msg: String) = android.util.Log.e(TAG, msg)
    }
}

/** Classifies a JS-side error string into the right exception type. */
private fun buildExceptionForJsError(error: String): Exception =
    if (error.contains("Uncaught")) BadWebViewException(error) else PoTokenException(error)
