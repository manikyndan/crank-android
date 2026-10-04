package com.crank.music.ui.viewmodel

import android.util.Log
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fetches and owns the lyrics for whatever track is playing.
 *
 * ## Why this is its own class
 *
 * Roughly 140 lines of `PlayerViewModel` were about lyrics: two network sources, a parse, an ordering
 * rule, and a race guard. None of it needed the queue, the downloads or the sleep timer — its only
 * inputs are the repository and "which song is on screen". Lyrics also have a distinct failure
 * profile (two sources that can disagree, a fuzzy search that can match the wrong recording), which is
 * much easier to reason about when it is not interleaved with transport control.
 *
 * ## The race this guard exists for
 *
 * Lyrics arrive over the network, so a fetch is slow enough to lose a race. Skipping a track while the
 * previous one is still fetching used to let the *older* response land last and replace the new
 * track's lyrics — the visible symptom being lyrics belonging to the previous song. [load] discards a
 * response whose song id is no longer the one on screen.
 *
 * The check compares ids rather than using a request token, because the question that matters is
 * simply "is this still the track on screen" — a late response for the *same* song is still correct.
 *
 * @param scope lifetime for in-flight fetches; in production `viewModelScope`, so a fetch cannot
 *   outlive the player.
 * @param currentSongId reads the id of the track currently displayed, for the stale-response guard.
 */
class LyricsSession(
    private val scope: CoroutineScope,
    private val musicRepository: MusicRepository,
    private val currentSongId: () -> String?
) {

    private val _state = MutableStateFlow<LyricsState>(LyricsState.Loading)
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    /**
     * Fetches lyrics for [song] and publishes them, unless the user has moved on first.
     *
     * The state goes to [LyricsState.Loading] synchronously so the screen does not keep showing the
     * previous track's lyrics while the new request is in flight.
     */
    fun load(song: Song) {
        _state.value = LyricsState.Loading
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchFor(song) }

                // Discard anything that arrived after the user moved on.
                if (!lyricsAreForCurrentTrack(song.id, currentSongId())) {
                    Log.d(
                        "CRANK_LYRICS",
                        "Discarding lyrics for '${song.title}': no longer the current track"
                    )
                    return@launch
                }

                if (result != null) {
                    // Which branch produced these lyrics decides whether the screen may highlight a
                    // line as "now singing". Logged because the two render very differently and the
                    // difference is otherwise invisible without a debugger.
                    Log.d(
                        "CRANK_LYRICS",
                        "Lyrics for '${song.title}': timing=${result.timing}, lines=${result.lines.size}"
                    )
                }

                _state.value = if (result != null) {
                    LyricsState.Success(result.lines, result.timing)
                } else {
                    LyricsState.Unavailable
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.e("CRANK_PLAYER", "Lyrics fetch failed: ${e.message}")
                if (currentSongId() == song.id) {
                    _state.value = LyricsState.Unavailable
                }
            }
        }
    }

    /**
     * Fetches lyrics for [song], trying the exact-match source before the fuzzy one.
     *
     * ## Why the order matters
     *
     * YouTube Music is asked by *track id*, so whatever comes back is for the song actually playing —
     * it cannot mismatch. LRCLIB is asked by *title and artist*, which covers tracks YouTube Music has
     * no lyrics for, but the search is fuzzy and can match a different recording: a live version, a
     * cover, or a same-titled song. That is how lyrics for the wrong track reach the screen. LRCLIB
     * results are scored on title, artist and duration and a non-matching title is rejected outright —
     * see `LrclibLyricsSource`.
     *
     * So the exact source is tried first and the fuzzy one only as a fallback. The previous order was
     * forced: the YouTube Music path was a stub returning `null`, so every track went to LRCLIB and the
     * mismatch was guaranteed rather than occasional.
     *
     * Parsing is delegated to [LyricsParser], which handles the LRC shapes an earlier inline regex
     * silently dropped.
     */
    private suspend fun fetchFor(song: Song): ParsedLyrics? {
        var fromExactSource: ParsedLyrics? = null

        // Exact source: needs a video id. A recognised-only track (and any track whose id is not a
        // YouTube id) has none, and asking would produce a browse id that resolves to nothing.
        if (song.id.isNotBlank() && song.isPlayable && !song.id.startsWith("http")) {
            val raw = try {
                musicRepository.getLyricsByVideoId(song.id)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.d("CRANK_LYRICS", "YouTube Music lyrics unavailable for '${song.title}'")
                null
            }

            if (!raw.isNullOrBlank()) {
                // YouTube Music returns timestamped LRC when it has it, and plain text otherwise.
                // The parser handles both, so there is no separate plain-text branch here — that
                // duplication was how the two paths could disagree about timing.
                val parsed = LyricsParser.parse(raw, song.durationMs)
                if (!parsed.isEmpty) {
                    // Real timings beat estimated ones, so a timed result wins outright.
                    if (parsed.timing == LyricsTiming.SYNCED) return parsed

                    // Plain text, though, is not the end of the search. Returning here on it is what
                    // left the karaoke view unreachable for most tracks: YouTube Music very often has
                    // the words without the timings, while LRCLIB frequently has the same words WITH
                    // them. Hold this as a fallback and ask the other source.
                    fromExactSource = parsed
                }
            }
        }

        val fromFuzzy = fetchFromLrclib(song.title, song.artistName, song.durationMs)
        return when {
            fromFuzzy == null -> fromExactSource
            // A timed match from the fuzzy source is still better than untimed text from the exact
            // one — the whole point of the karaoke view is that it knows when each line is.
            fromFuzzy.timing == LyricsTiming.SYNCED -> fromFuzzy
            // Both untimed: prefer the exact source, whose title/artist match is not a guess.
            fromExactSource != null -> fromExactSource
            else -> fromFuzzy
        }
    }

    private suspend fun fetchFromLrclib(
        title: String,
        artist: String,
        durationMs: Long
    ): ParsedLyrics? {
        return try {
            // durationMs was already threaded here but unused; passing it is what lets the match
            // reject a remix whose lyrics are paced differently from the track playing.
            val response = musicRepository.searchLyrics(title, artist, durationMs) ?: return null

            // Synced lyrics first: real timings beat estimated ones.
            val synced = response.syncedLyrics
            if (!synced.isNullOrBlank()) {
                val parsed = LyricsParser.parse(synced, durationMs)
                if (!parsed.isEmpty) return parsed
            }

            val plain = response.plainLyrics
            if (!plain.isNullOrBlank()) {
                val parsed = LyricsParser.parse(plain, durationMs)
                if (!parsed.isEmpty) return parsed
            }

            null
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Log.e("CRANK_LYRICS", "LRCLIB failed for '$title': ${e.message}")
            null
        }
    }
}

/**
 * Whether a lyric response for [requestedSongId] is still worth showing.
 *
 * Pulled out of the fetch above so it can be unit-tested without a network, a player or a repository —
 * it is pure, and it is the rule that prevents the "lyrics for the previous song" bug.
 */


internal fun lyricsAreForCurrentTrack(requestedSongId: String, currentSongId: String?): Boolean =
    requestedSongId == currentSongId
