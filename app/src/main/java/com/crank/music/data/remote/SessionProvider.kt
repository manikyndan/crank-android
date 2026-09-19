package com.crank.music.data.remote

import android.util.Log
import com.crank.music.data.local.SessionEntity
import com.crank.music.data.local.SongDao
import com.crank.music.data.remote.innertube.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Supplies the visitor id the stream cascade depends on.
 *
 * ## Why this is not optional
 *
 * The clients that can serve a **whole** audio file — visionOS and Android VR 1.65.10 — answer
 * `LOGIN_REQUIRED` with zero formats when no visitor id is present. Without one, the cascade
 * falls through to the iOS clients, which serve only about a 1 MiB preview: playback that dies
 * roughly 60-90 seconds in. So this id is not a nicety, it is what makes playback work.
 *
 * ## Where the value comes from
 *
 * `https://music.youtube.com/sw.js_data`, which returns a service-worker payload. The response
 * has two traps:
 *
 * 1. It begins with the XSSI guard `)]}'` followed by a blank line. The body is **not** valid
 *    JSON until that prefix is stripped, and a naive `Json.parseToJsonElement` on the raw body
 *    fails with an unhelpful "unexpected token" error at offset 0.
 * 2. The visitor id is a long base64-ish string identifiable by its `Cg` prefix (`Cgt`/`Cgs`),
 *    nested at `data[0][2]`. It is found by scanning rather than by a fixed offset because the
 *    array's contents vary between responses — the position of the id within it is not stable.
 *
 * Parsing the payload structurally rather than with a regex over the raw text is deliberate: a
 * regex would match a `Cg`-prefixed substring appearing anywhere in the document, including in
 * unrelated fields, and would silently produce a wrong-but-plausible token.
 *
 * ## Storage
 *
 * Persisted in the app's Room database (`session_values`), not DataStore and not
 * `SharedPreferences`. The app already owns exactly one database with a real migration chain;
 * adding a second persistence mechanism for a single string would mean two stores to keep
 * consistent and two things for "clear app data" to remove.
 */
@Singleton
class SessionProvider
@Inject
constructor(
    private val songDao: SongDao,
    private val client: HttpClient,
    private val json: Json,
) : VisitorDataProvider {

    private val lock = Mutex()

    /**
     * Cached in memory for the process lifetime.
     *
     * The visitor id is long-lived, so refetching per resolve would add a round trip to every
     * play and risk a rate limit for no benefit.
     */
    @Volatile
    private var cachedVisitorData: String? = null

    /**
     * Returns the visitor id, fetching and persisting it on first use.
     *
     * Returns `null` only if the fetch genuinely fails, which is a degraded-but-functioning
     * state rather than an error: the cascade will skip the clients that need it and report
     * `LOGIN_REQUIRED` per client, so the cause stays visible.
     */
    override suspend fun visitorData(): String? {
        cachedVisitorData?.let { return it }

        return lock.withLock {
            // Re-check inside the lock: a concurrent caller may have completed the fetch while
            // this one waited.
            cachedVisitorData?.let { return@withLock it }

            // Prefer a previously persisted value. It survives restarts and avoids refetching
            // on every cold start.
            persistedVisitorData()?.let { stored ->
                cachedVisitorData = stored
                return@withLock stored
            }

            fetchVisitorData()?.also { fetched ->
                cachedVisitorData = fetched
                persist(fetched)
            }
        }
    }

    /**
     * The account-scoped data-sync id, or `null` when signed out.
     *
     * Never fabricated. A made-up value would bind Proof-of-Origin tokens to a non-existent
     * session and enable the login-gated clients against nothing, producing 403s that look
     * exactly like an anti-bot refusal.
     */
    override suspend fun dataSyncId(): String? = null

    /** Fetches and stores a fresh visitor id, discarding any cached value. */
    suspend fun refreshVisitorData(): String? {
        return lock.withLock {
            val fetched = fetchVisitorData()
            cachedVisitorData = fetched
            if (fetched != null) persist(fetched) else clearPersisted()
            fetched
        }
    }

    private suspend fun fetchVisitorData(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val raw =
                client.get(SW_JS_DATA_URL) {
                    // A browser user agent is required; the endpoint rejects a default client UA
                    // and the resulting failure looks like an outage rather than a header issue.
                    header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                    header("Accept", "*/*")
                }.bodyAsText()

            extractVisitorData(raw).also { extracted ->
                if (extracted == null) {
                    // Logged here rather than inside `extractVisitorData`: the parser is pure so
                    // it stays testable on the JVM, and `Log` is only safe in Android code.
                    Log.w(
                        TAG,
                        "sw.js_data returned no visitor id " +
                            "(${raw.length} bytes): ${consumeParseError() ?: "no Cg-prefixed field"}",
                    )
                }
            }
        }.getOrElse { e ->
            Log.w(TAG, "visitorData fetch failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private suspend fun persistedVisitorData(): String? = withContext(Dispatchers.IO) {
        runCatching { songDao.getSessionValue(VISITOR_DATA_KEY) }
            .getOrElse { e ->
                // A read failure must not be fatal: the caller falls through to a network fetch,
                // so the worst case is one extra request rather than degraded playback.
                Log.w(TAG, "Failed to read persisted visitorData: ${e.message}")
                null
            }
    }

    private suspend fun persist(value: String) = withContext(Dispatchers.IO) {
        runCatching {
            songDao.putSessionValue(SessionEntity(key = VISITOR_DATA_KEY, value = value))
        }.onFailure { Log.w(TAG, "Failed to persist visitorData: ${it.message}") }
    }

    private suspend fun clearPersisted() = withContext(Dispatchers.IO) {
        runCatching { songDao.deleteSessionValue(VISITOR_DATA_KEY) }
    }

    companion object {
        private const val TAG = "CRANK_SESSION"

        private const val SW_JS_DATA_URL = "https://music.youtube.com/sw.js_data"

        /** Storage key for the visitor id in `session_values`. */
        private const val VISITOR_DATA_KEY = "visitor_data"

        private const val XSSI_GUARD = ")]}'"

        /**
         * Visitor ids are protobuf-encoded and begin `Cg` followed by a length-ish byte. Matching
         * the wider prefix avoids latching onto a short `Cg`-prefixed field that happens to
         * appear earlier in the payload.
         */
        private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")

        /**
         * Extracts the visitor id from the raw `sw.js_data` response.
         *
         * Exposed as a pure function so it can be unit-tested against the real payload shape
         * without a network call — the XSSI prefix and the nesting are both easy to get wrong,
         * and both fail with messages that do not point at the cause.
         *
         * Deliberately **no logging in here**. `android.util.Log` is not available in a local JVM
         * unit test and throws `Method ... not mocked`, so a log call inside the failure path
         * would make every "returns null" test fail for a reason unrelated to what it asserts.
         * Callers that want a record of a parse failure use [lastParseError].
         */
        fun extractVisitorData(rawBody: String): String? {
            // Strip the XSSI guard. Some responses include a leading BOM as well, so the search
            // is for the guard anywhere near the start rather than a fixed offset.
            val guardIndex = rawBody.indexOf(XSSI_GUARD)
            val body =
                if (guardIndex >= 0) {
                    rawBody.substring(guardIndex + XSSI_GUARD.length).trimStart()
                } else {
                    rawBody.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                }

            return runCatching {
                val root = Json.parseToJsonElement(body).jsonArray

                // The payload is [[ "yt.sw.adr", null, [ ...fields... ] ]].
                // Locate the field array by shape rather than by a hardcoded path, because the
                // leading entries vary between responses.
                val candidates =
                    root.firstOrNull()?.jsonArray
                        ?.getOrNull(2)?.jsonArray
                        ?: return@runCatching null

                candidates
                    .firstNotNullOfOrNull { element ->
                        val text =
                            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
                        text?.takeIf { VISITOR_DATA_REGEX.containsMatchIn(it) }
                    }
            }.getOrElse { e ->
                parseError = "${e.javaClass.simpleName}: ${e.message}"
                null
            }
        }

        /**
         * The last parse failure, or `null` if the most recent parse succeeded.
         *
         * Held on the companion because [extractVisitorData] is pure and therefore cannot log.
         * `@Volatile` because parsing happens on an IO dispatcher while the read may not.
         */
        @Volatile
        private var parseError: String? = null

        /** Returns the most recent parse failure and clears it. */
        fun consumeParseError(): String? {
            val error = parseError
            parseError = null
            return error
        }
    }
}
