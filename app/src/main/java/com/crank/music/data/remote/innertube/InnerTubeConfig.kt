package com.crank.music.data.remote.innertube

import com.crank.music.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central definition of the InnerTube clients CRANK talks to.
 *
 * ## Why this class exists
 *
 * The four InnerTube keys previously inlined across `YouTubeStreamResolver` and
 * `InnerTubeApi` were Google's own *public* web/mobile client keys — the same
 * values shipped in youtube.com's HTML. They are not user secrets and not
 * credentials to a privileged API.
 *
 * Two things were nevertheless wrong with hardcoding them:
 *
 *  1. **They get rotated.** Two of the four had already been replaced by Google,
 *     which silently degraded playback to the slower third-party mirrors. A key
 *     that no longer works is worse than no key, because the failure is a 400
 *     buried in a log rather than an obvious error.
 *  2. **There was no single source of truth.** The same key appeared in three
 *     places, so any rotation meant editing several files and hoping none were
 *     missed — which is exactly what happened here.
 *
 * ## Why the `key` parameter is now omitted entirely
 *
 * The `?key=` query parameter on `youtubei/v1` is legacy. Current YouTube web and
 * mobile clients send no API key for public content; the client identity in the
 * request body's `context.client` block is what actually matters. Every request
 * below now relies on that instead, which removes a whole class of 400 responses
 * caused by stale keys.
 *
 * If a key is ever genuinely required again, it can be supplied per-build via
 * `INNERTUBE_API_KEY` in `local.properties` (see `app/build.gradle.kts`) rather
 * than being committed to source.
 */
@Singleton
class InnerTubeConfig @Inject constructor() {

    /**
     * Optional override, injected at build time and empty by default.
     *
     * Deliberately defaulted to empty rather than to a hardcoded fallback: if a
     * key is needed and none was configured, we want the request to go out
     * key-less (which currently works) rather than to silently use a value that
     * may have been rotated months ago.
     */
    val apiKey: String = runCatching { BuildConfig.INNERTUBE_API_KEY }.getOrDefault("")

    /** Base URL for the standard YouTube client. */
    val youtubeBaseUrl: String = "https://www.youtube.com/youtubei/v1"

    /** Base URL for the YouTube Music (WEB_REMIX) client. */
    val musicBaseUrl: String = "https://music.youtube.com/youtubei/v1"

    /**
     * Appends the legacy `key` parameter only when one was explicitly configured.
     * Returns the bare path otherwise, which is the correct modern form.
     */
    fun withKey(path: String): String =
        if (apiKey.isBlank()) "$youtubeBaseUrl/$path" else "$youtubeBaseUrl/$path?key=$apiKey"

    /** As [withKey], but for the YouTube Music host. */
    fun withMusicKey(path: String): String =
        if (apiKey.isBlank()) "$musicBaseUrl/$path" else "$musicBaseUrl/$path?key=$apiKey"
}
