package com.crank.music.data.remote.potoken

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the expensive BotGuard machinery and hands out Proof-of-Origin tokens.
 *
 * Adapted from the Echo Music project (GPL-3.0).
 *
 * ## Why this caches at all
 *
 * Spinning up a [PoTokenWebView] means constructing a WebView, loading the BotGuard page, and
 * evaluating its interpreter — around 2-5 seconds on a healthy device. Doing that per track
 * would make every skip feel broken. Instead the instance is kept alive, its long-lived
 * `integrityToken` is reused, and only the cheap per-video token derivation is repeated.
 *
 * ## The session token is minted before any player token
 *
 * BotGuard requires the session-scoped token to be produced *first* on a fresh minter. Minting
 * a player token before it fails. See [getWebClientPoToken].
 */
class PoTokenGenerator(private val appContext: Context) {

    /**
     * Whether the system has a usable WebView at all.
     *
     * `CookieManager.getInstance()` is used as the probe because it throws on a device with no
     * WebView installed, which is a real configuration on some Android builds.
     */
    private val webViewSupported: Boolean =
        runCatching { android.webkit.CookieManager.getInstance() }.isSuccess

    /** Latched once a broken WebView is detected; see [BadWebViewException]. */
    private var webViewBadImpl = false

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenSessionBoundPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    /**
     * Scope for background warm-up only.
     *
     * A dedicated scope rather than `GlobalScope`: warm-up outlives any single caller, but it
     * should still be owned by something and must not leak across an application teardown.
     * `SupervisorJob` so one failed warm-up does not cancel the scope for later attempts.
     */
    private val warmUpScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Latched when a mint has already been abandoned once, so we stop retrying.
     *
     * A WebView that could not mint inside the deadline will not mint on the next track either.
     * Without this, every subsequent play would pay the full timeout before falling back.
     */
    private val mintAbandoned = AtomicBoolean(false)

    /** True when a bot-guard asset is present, so callers can warn before the first play attempt. */
    fun isAvailable(): Boolean = webViewSupported && !webViewBadImpl

    /**
     * Warms the WebView in the background so the first real playback does not pay the cold-start
     * cost. Safe to call repeatedly; does nothing once warmed.
     *
     * Bounded, and it does not latch [mintAbandoned] on failure: a warm-up that times out is a
     * missed optimisation, not proof that minting is broken, and a real play should still get its
     * own attempt.
     *
     * On `Dispatchers.Main` because `getNewPoTokenGenerator` needs the main thread. This is a
     * `launch`, not a blocked caller, so it cannot deadlock the way [awaitMint] once did.
     */
    fun initialize() {
        if (!isAvailable()) return
        warmUpScope.launch {
            runCatching {
                withTimeoutOrNull(POTOKEN_TIMEOUT_MS) {
                    webPoTokenGenLock.withLock {
                        if (webPoTokenGenerator == null) {
                            webPoTokenSessionId = "init-" + System.currentTimeMillis()
                            webPoTokenGenerator =
                                PoTokenWebView.getNewPoTokenGenerator(appContext)
                            webPoTokenSessionBoundPot =
                                webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
                        }
                    }
                }
            }.onFailure { e ->
                Log.d(TAG, "PoToken warm-up skipped: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /**
     * Returns a fresh token pair for a playback resolve, or `null` when no token can be made.
     *
     * ## Returning null is a normal outcome, not an error
     *
     * `null` means "no Proof-of-Origin token is available". Callers must treat that as a signal
     * to skip the token-gated clients and go straight to the rest of the cascade, **not** as a
     * reason to fail the playback. A missing or broken WebView degrades the quality of the
     * client chain; it must not make the app unplayable.
     *
     * ## Binding
     *
     * Both returned tokens are **session-bound**, which is correct specifically because
     * [YouTubeClients.MAIN_CLIENT] is `WEB_REMIX`; yt-dlp special-cases that client so its
     * player context is session-bound too. This is stated rather than assumed — if the main
     * client changes, this function must change with it. See [PoTokenResult].
     *
     * @param sessionId the binding target: the account's data-sync id when signed in, else the
     *   visitor data id. **Passing an empty string is accepted and produces a token bound to
     *   nothing, which can never validate** — so an empty value is rejected here explicitly
     *   rather than being allowed to mint a useless token.
     */
    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        Log.i(TAG, "mint request: video=$videoId available=${isAvailable()} abandoned=${mintAbandoned.get()} session=${sessionId.length}ch")
        if (!isAvailable()) return null
        if (mintAbandoned.get()) return null
        if (sessionId.isEmpty()) {
            // A token bound to "" is always invalid. Failing here keeps the cause attached to
            // the decision instead of surfacing later as an unexplained 403 on a stream URL.
            return null
        }

        return awaitMint(videoId, sessionId)
    }

    /**
     * Runs the mint and gives up on it after [POTOKEN_TIMEOUT_MS].
     *
     * ## Why this is not just `runBlocking { withTimeout { ... } }`
     *
     * It was, and that shape has two separate failure modes — both observed on a device, so both
     * are documented here rather than rediscovered.
     *
     * **`withTimeout` alone cannot bound blocking work.** It cancels only at a suspension point.
     * The mint body constructs a WebView and drives a JS interpreter, so if it stalls there the
     * deadline elapses while the code keeps running and the caller never resumes. The 8s cap was
     * advisory, not enforced: a run showed `resolve start:` followed by 90+ seconds of silence
     * with no phase line for the token step.
     *
     * **`runBlocking` here is a deadlock, not a fix.** The caller reaches this point on the main
     * thread (`viewModelScope.launch` uses `Main.immediate`), and the mint itself needs the main
     * thread — `PoTokenWebView.getNewPoTokenGenerator` does `withContext(Dispatchers.Main)` because
     * WebView construction is main-thread-only. `runBlocking` blocks Main while Main is required
     * *by* the work being awaited, so the mint can never start. The device killed the app with
     * `Application Not Responding: com.crank.music`.
     *
     * So the mint is awaited as a plain suspend call. Nothing blocks a thread, every `withContext`
     * inside it is a real suspension point that the timeout can act on, and the deadline is
     * enforced by the caller. WebView construction hops to Main by itself.
     *
     * `null` is an ordinary outcome meaning "no token": callers degrade to the non-token clients
     * in the cascade rather than failing the resolve.
     */
    private suspend fun awaitMint(videoId: String, sessionId: String): PoTokenResult? {
        Log.i(TAG, "awaitMint: deadline ${POTOKEN_TIMEOUT_MS}ms")
        val started = android.os.SystemClock.elapsedRealtime()

        val result = runCatching {
            withTimeoutOrNull(POTOKEN_TIMEOUT_MS) {
                getWebClientPoToken(videoId, sessionId, forceRecreate = false)
            }
        }.getOrElse { e ->
            when (e) {
                // A broken WebView implementation will never work, no matter how long we wait.
                // Latch it so [isAvailable] starts returning false and callers stop paying for it.
                is BadWebViewException -> {
                    webViewBadImpl = true
                    Log.w(TAG, "WebView cannot run BotGuard; disabling token minting")
                    null
                }
                // A genuine bug in the BotGuard path, not a timeout. Log it and degrade rather
                // than letting it fail the whole resolve — a token is an optimisation here, not a
                // prerequisite, because the non-token clients in the cascade can still serve audio.
                else -> {
                    Log.w(TAG, "PoToken mint failed: ${e.javaClass.simpleName}: ${e.message}")
                    null
                }
            }
        }

        if (result == null) {
            // Timed out (or failed). Drop the cached minter and stop retrying: a WebView that
            // cannot mint within the deadline will not mint on the next track either, and
            // retrying per-track would pay the full timeout on every play.
            mintAbandoned.set(true)
            runCatching {
                webPoTokenGenLock.withLock {
                    runCatching { withContext(Dispatchers.Main) { webPoTokenGenerator?.close() } }
                    webPoTokenGenerator = null
                    webPoTokenSessionBoundPot = null
                    webPoTokenSessionId = null
                }
            }
            Log.w(TAG, "PoToken unavailable after ${android.os.SystemClock.elapsedRealtime() - started}ms; continuing without it")
        } else {
            Log.i(TAG, "mint ok in ${android.os.SystemClock.elapsedRealtime() - started}ms")
        }

        return result
    }

    private suspend fun getWebClientPoToken(
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean,
    ): PoTokenResult {
        val (poTokenGenerator, sessionBoundPot) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate ||
                        webPoTokenGenerator == null ||
                        webPoTokenGenerator!!.isExpired ||
                        webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    webPoTokenSessionId = sessionId

                    withContext(Dispatchers.Main) { webPoTokenGenerator?.close() }

                    webPoTokenGenerator = PoTokenWebView.getNewPoTokenGenerator(appContext)

                    // The session-bound token must be minted exactly once on a fresh minter,
                    // before any player-bound token is requested. Reversing this order fails.
                    webPoTokenSessionBoundPot =
                        webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
                }

                webPoTokenGenerator!! to webPoTokenSessionBoundPot!!
            }

        // Minting a player-context token is what proves the client is live. Its *result* is not
        // used for the stream URL (see below), but the call must still happen, and a failure
        // here means the minter is stale — typically the app was backgrounded and the WebView
        // content was discarded — so the instance is rebuilt exactly once and retried.
        try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (forceRecreate) {
                // Already rebuilt once; there is nothing further to try.
                throw throwable
            }
            return getWebClientPoToken(videoId, sessionId, forceRecreate = true)
        }

        // Both fields receive the SESSION-bound token, deliberately.
        //
        // - streamingDataPoToken is appended to the googlevideo URL as `pot=` and must be
        //   session-bound.
        // - playerRequestPoToken normally wants a video-id-bound token, but yt-dlp
        //   special-cases WEB_REMIX (our MAIN_CLIENT) so that its player context is
        //   session-bound as well.
        //
        // This coupling is intentional and load-bearing: swapping the two would fix the stream
        // URL and break the /player request. If MAIN_CLIENT ever moves off WEB_REMIX, the
        // playerRequestPoToken must become a separate video-bound token.
        return PoTokenResult(
            playerRequestPoToken = sessionBoundPot,
            streamingDataPoToken = sessionBoundPot,
        )
    }

    private companion object {
        const val TAG = "CRANK_POTOKEN"

        /**
         * Healthy cold start is ~2-5s. 8s leaves slack for a slow device without making the
         * user wait before the non-token clients take over.
         */
        const val POTOKEN_TIMEOUT_MS = 8_000L
    }
}
