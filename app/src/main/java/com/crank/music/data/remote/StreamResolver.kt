package com.crank.music.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

data class StreamData(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

@Singleton
class StreamResolver @Inject constructor(
    private val youtubeStreamResolver: YouTubeStreamResolver
) {
    suspend fun init() {
        try {
            youtubeStreamResolver.fetchVisitorData()
        } catch (e: Exception) {
            Log.e("CRANK_STREAM", "Failed to pre-fetch visitorData: ${e.message}")
        }
    }

    suspend fun resolveStreamUrl(videoId: String, songTitle: String = "", artistName: String = ""): StreamData {
        Log.d("CRANK_STREAM", "Resolving: $videoId ($songTitle - $artistName)")
        return youtubeStreamResolver.getSongStreamUrl(videoId, songTitle, artistName)
    }

    companion object {
        /** Hosts that serve YouTube's signed, expiring stream URLs. */
        private val YOUTUBE_MEDIA_HOSTS = listOf(
            "googlevideo.com",
            "youtube.com",
            "ytimg.com"
        )

        /**
         * True when [url] carries its own expiry and that moment has passed.
         *
         * YouTube hands out CDN URLs with an `expire=<unixSeconds>` parameter, and
         * a URL past that instant is answered with HTTP 403 — permanently, no
         * matter how many times it is retried. Detecting it up front turns an
         * opaque player error into a re-resolve.
         *
         * URLs with no `expire` (third-party frontends, plain file URLs) are
         * treated as usable; we have no grounds to reject them here.
         */
        fun isExpired(url: String, nowSeconds: Long = System.currentTimeMillis() / 1000L): Boolean {
            if (!url.startsWith("http")) return false

            val expiry = extractQueryParam(url, "expire")?.toLongOrNull() ?: return false

            // A little slack so a URL that is about to lapse mid-handshake is
            // treated as already gone rather than starting a request it will lose.
            return expiry <= nowSeconds + 30L
        }

        /**
         * True when [url] points at a source whose signed URLs rot and therefore
         * cannot be trusted across app restarts.
         *
         * Only YouTube media is in scope. A URL pointing anywhere else — a
         * podcast feed, a local file, a third-party mirror known to serve stable
         * links — is left alone, because re-resolving those would be wrong.
         */
        fun isRottingUrl(url: String): Boolean {
            if (!url.startsWith("http")) return false
            val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
            return YOUTUBE_MEDIA_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        /**
         * True when [streamUrl] can be handed to ExoPlayer as-is.
         *
         * A blank value, a non-URL identifier (a bare video ID needs resolving),
         * or a YouTube URL that has expired all mean "resolve this again".
         *
         * [nowSeconds] exists so callers — and tests — can supply their own
         * clock. Without it this was untestable, and the expiry comparison
         * silently ran against wall-clock time even in cases built to exercise
         * a different instant.
         */
        fun isDirectlyPlayable(
            streamUrl: String,
            nowSeconds: Long = System.currentTimeMillis() / 1000L
        ): Boolean {
            if (!streamUrl.startsWith("http")) return false
            if (!isRottingUrl(streamUrl)) return true
            return !isExpired(streamUrl, nowSeconds)
        }

        private fun extractQueryParam(url: String, name: String): String? {
            val query = url.substringAfter('?', "").substringBefore('#')
            if (query.isBlank()) return null
            return query.split("&").firstNotNullOfOrNull { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0 && pair.substring(0, idx) == name) pair.substring(idx + 1) else null
            }
        }
    }
}
