package com.crank.music.data.remote.lrclib

import android.util.Log
import com.crank.music.domain.repository.LyricsSearchResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Second-source lyrics: LRCLIB, asked by title and artist because it has no notion of a YouTube
 * track id.
 *
 * ## Why this is not just "search and take the first hit"
 *
 * LRCLIB is a fuzzy text search, so the first hit is frequently the wrong recording: a remix, a
 * cover, or a same-titled song by a different artist. Measured on device against the app's own
 * feed, taking the first hit returned "Aaj Ki Raat (Techno Remix)" for `Aaj Ki Raat` and a 306 s
 * recording for a 198 s track. So results are scored and the closest match wins — see
 * [Candidate.score].
 *
 * ## Why the query is rewritten before it is sent
 *
 * YouTube Music publishes artist and title strings that are meant for display, not for lookup: the
 * artist is the full credit list ("Madhubanti Bagchi, Jasmine Sandlas & Shashwat Sachdev") and the
 * title carries a parenthetical ("Ucha Lamba Kad Forever (From "Welcome To The Jungle")"). LRCLIB
 * stores one artist per field and a bare title, so querying with the display strings returns
 * nothing for those tracks — measured: 6 of 10 feed tracks matched as-is, 10 of 10 after
 * rewriting. [searchQueries] tries the most specific form first and relaxes only on a miss, so the
 * artist constraint is kept whenever it can be.
 */
class LrclibLyricsSource(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Best lyrics LRCLIB has for [title] / [artist], or `null` if it has nothing that plausibly
     * matches. [durationMs] is the length of the track actually playing; it is the strongest
     * signal for telling a remix from the original and may be `0` when unknown.
     */
    suspend fun findLyrics(title: String, artist: String, durationMs: Long): LyricsSearchResult? {
        if (title.isBlank()) return null

        for (query in searchQueries(title, artist)) {
            val candidates = runCatching { search(query) }
                .onFailure {
                    Log.w(TAG, "LRCLIB query '${query.title}' / '${query.artist}' failed: ${it.message}")
                }
                .getOrNull()
                .orEmpty()

            // Requiring the title to match is what keeps a different song off the screen. Without
            // it the fuzzy search happily returns a same-artist track whose lyrics belong to
            // something else entirely.
            val best = candidates
                .filter { it.titleScore(title) > 0 }
                .maxByOrNull { it.score(title, artist, durationMs) }
                ?: continue

            Log.d(
                TAG,
                "LRCLIB matched '${best.result.trackName}' / '${best.result.artistName}' " +
                    "for '$title' / '$artist'",
            )
            return best.result
        }

        Log.d(TAG, "LRCLIB has no match for '$title' / '$artist'")
        return null
    }

    private suspend fun search(query: Query): List<Candidate> {
        val text = client.get(SEARCH_URL) {
            header("User-Agent", USER_AGENT)
            parameter("track_name", query.title)
            if (query.artist != null) parameter("artist_name", query.artist)
        }.bodyAsText()

        return parse(text)
    }

    /**
     * Query forms, most specific first.
     *
     * The cleaned title with the full credit list comes first because it is the only form that
     * still constrains the artist while LRCLIB's own records also list several artists. The
     * remaining forms each drop one constraint, so a track that only exists under a shortened
     * credit still resolves.
     */
    internal fun searchQueries(title: String, artist: String): List<Query> {
        val cleanTitle = cleanTitle(title)
        val primaryArtist = artist.substringBefore(",").trim()
        val fullArtist = artist.takeIf { it.isNotBlank() }
        val primaryOrNull = primaryArtist.takeIf { it.isNotBlank() }

        val queries = LinkedHashSet<Query>()
        queries += Query(cleanTitle, fullArtist)
        queries += Query(cleanTitle, primaryOrNull)
        queries += Query(cleanTitle, null)
        if (cleanTitle != title) queries += Query(title, primaryOrNull)
        return queries.toList()
    }

    /** Drops the display-only suffix: `Barbaad (Movie: Saiyaara)` -> `Barbaad`. */
    internal fun cleanTitle(title: String): String =
        title.substringBefore("(").substringBefore("[").trim().takeIf { it.isNotBlank() } ?: title

    internal fun parse(text: String): List<Candidate> {
        if (text.isBlank()) return emptyList()
        val array = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return emptyList()
        return array.mapNotNull { (it as? JsonObject)?.let(::candidate) }
    }

    private fun candidate(obj: JsonObject): Candidate? {
        val plain = obj.stringOrNull("plainLyrics")
        val synced = obj.stringOrNull("syncedLyrics")
        if (plain.isNullOrBlank() && synced.isNullOrBlank()) return null

        return Candidate(
            LyricsSearchResult(
                trackName = obj.stringOrNull("trackName"),
                artistName = obj.stringOrNull("artistName"),
                albumName = obj.stringOrNull("albumName"),
                durationMs = obj.durationMillisOrNull("duration"),
                plainLyrics = plain,
                syncedLyrics = synced,
            ),
        )
    }

    internal data class Query(val title: String, val artist: String?)

    internal data class Candidate(val result: LyricsSearchResult) {

        fun titleScore(title: String): Int {
            val wanted = normalize(title)
            val actual = normalize(result.trackName ?: "")
            if (wanted.isBlank() || actual.isBlank()) return 0
            return when {
                actual == wanted -> 4
                actual.contains(wanted) || wanted.contains(actual) -> 3
                else -> 0
            }
        }

        /**
         * How well this record matches the track that is playing.
         *
         * Duration is weighted heaviest because it is the only signal that separates versions of
         * the same song: a title and artist can be identical across an original and its remix, but
         * their lengths are not. A record more than a quarter different in length is penalised
         * rather than merely un-rewarded, since that is the shape of a different recording.
         */
        fun score(title: String, artist: String, durationMs: Long): Int {
            val artistScore = if (matchesArtist(artist)) 2 else 0
            return titleScore(title) + artistScore + durationScore(durationMs) + versionPenalty(title)
        }

        /**
         * Penalises a record that is a different *version* of the song — reprise, remix, cover,
         * live take — unless the track being played is itself that version.
         *
         * A reprise shares its words with the original, so it is far better than showing nothing,
         * and it is never rejected outright for this reason. The penalty only decides which record
         * wins when several exist, which is what stopped `Saiyaara` from resolving to
         * `Saiyaara Reprise - Female` while a plain-titled record was available.
         */
        private fun versionPenalty(title: String): Int {
            val wanted = normalize(title)
            val actual = normalize(result.trackName ?: "")
            if (actual.isBlank()) return 0
            val hasExtraMarker = VERSION_MARKERS.any { actual.contains(it) && !wanted.contains(it) }
            return if (hasExtraMarker) -2 else 0
        }

        private fun matchesArtist(artist: String): Boolean {
            val actual = normalize(result.artistName ?: "")
            if (actual.isBlank()) return false
            return artistTokens(artist).any { actual.contains(it) }
        }

        private fun durationScore(durationMs: Long): Int {
            val actual = result.durationMs ?: return 0
            if (durationMs <= 0L || actual <= 0L) return 0
            val delta = kotlin.math.abs(actual - durationMs)
            if (delta <= 3_000L) return 3
            if (delta <= 8_000L) return 2
            val ratio = delta.toDouble() / durationMs.toDouble()
            if (ratio <= 0.20) return 1
            return if (ratio > 0.25) -3 else 0
        }

        private companion object {
            /** Words that mark a record as a version of a song rather than the song itself. */
            val VERSION_MARKERS = listOf(
                "remix", "reprise", "cover", "version", "live", "mashup", "instrumental",
                "karaoke", "slowed", "reverb", "nightcore", "acoustic", "unplugged",
                "extended", "dance mix", "club mix", "radio edit", "sped up", "lofi", "lo fi",
            )
        }
    }

    internal companion object {
        private const val TAG = "CRANK_LRCLIB"
        private const val SEARCH_URL = "https://lrclib.net/api/search"
        private const val USER_AGENT = "CrankMusic/1.0"

        /** Lowercase, punctuation-free, single-spaced — the form titles are compared in. */
        internal fun normalize(text: String): String =
            text.lowercase()
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        /** Significant words of a credit list; short tokens like "ip" match far too much. */
        internal fun artistTokens(artist: String): List<String> =
            normalize(artist).split(' ').filter { it.length >= 3 }

        /**
         * Reads a nullable string field.
         *
         * A direct `jsonPrimitive.content` is not safe here: the field may be absent, may be
         * `null`, or may not be a string at all, and any of those threw and discarded the whole
         * response — including the lyrics that were in it.
         */
        private fun JsonObject.stringOrNull(key: String): String? {
            val value = get(key) ?: return null
            if (value is JsonNull) return null
            return runCatching { (value as? JsonPrimitive)?.content }.getOrNull()
        }

        /**
         * Reads LRCLIB's `duration` (seconds) as milliseconds.
         *
         * LRCLIB serialises it as a JSON number that is always fractional in practice
         * (`"duration": 229.0`). Reading it with `jsonPrimitive.long` — which is
         * `content.toLong()` — throws `NumberFormatException` on that decimal point. Because the
         * throw happened inside the caller's catch-all, *every* LRCLIB response was discarded and
         * the fallback returned nothing at all, which is why no track ever showed lyrics. Parsing
         * as a double and converting keeps both `229` and `229.0` working.
         */
        private fun JsonObject.durationMillisOrNull(key: String): Long? {
            val value = get(key) ?: return null
            if (value is JsonNull) return null
            val seconds = runCatching { (value as? JsonPrimitive)?.content?.toDouble() }.getOrNull()
                ?: return null
            if (seconds <= 0.0) return null
            return (seconds * 1000).toLong()
        }
    }
}
