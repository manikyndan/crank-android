package com.crank.music.ui.viewmodel

import android.util.Log
import com.crank.music.data.local.HistoryEntity
import com.crank.music.data.local.SettingsStore
import com.crank.music.data.local.SongDao
import com.crank.music.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the listening-history rows and the clock that measures how long each song was actually heard.
 *
 * Extracted from `PlayerViewModel`, which owns enough already. Everything here was previously three
 * pieces of the same class that only ever talked to each other: the row a play writes, the privacy
 * gate that can silence it, and the accumulator that turns playback ticks into "time listened".
 * Colocating them is what makes the three invariants they share hold — see the methods — and a
 * class with one job is a lot easier to check for them than a 1,750-line ViewModel.
 *
 * ## Invariants
 *
 * 1. **A play is counted exactly once.** The insert-then-bump order in [recordPlay] is load-bearing:
 *    [SongDao.bumpHistory] reports rows updated, so a zero means first play and a fresh row is
 *    inserted with `playCount = 1`. Bumping first cannot double-count a first play, which an
 *    unconditional insert-then-increment would.
 * 2. **Time is measured, not inferred.** The accumulator is fed by playback ticks, not the track's
 *    nominal duration, so a song skipped after ten seconds contributes ten seconds to "Total
 *    Listening" — the previous duration-based number overstated listening by however much was
 *    skipped.
 * 3. **History-off means nothing is written**, including the clock. When the user disables history,
 *    [recordPlay] stops attributing time to any song rather than silently crediting whichever song
 *    happened to be current.
 */
class HistoryRecorder(
    private val songDao: SongDao,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope
) {
    /**
     * The song whose listening clock is currently running, and how long it has run for.
     *
     * Reset by [recordPlay] whenever a new track starts. Exposed as a pair only so the accumulator
     * and its owner cannot drift apart; nothing outside this class should hold one without the other.
     */
    private var listenedSongId: String? = null
    private var listenedAccumulatedMs: Long = 0L

    /**
     * Records a play of [song] and starts its listening clock.
     *
     * Called when playback of a track actually begins. The previous track's accumulated time is
     * banked first — doing it here, rather than on track end, is what makes a track the user
     * skipped still contribute the time they did hear.
     */
    fun recordPlay(song: Song) {
        scope.launch {
            try {
                flushListeningTime()

                if (!isListeningHistoryEnabled()) {
                    // Stop attributing time to any song while history is off, instead of silently
                    // crediting whichever song happened to be current.
                    listenedSongId = null
                    return@launch
                }

                val now = System.currentTimeMillis()
                val updated = songDao.bumpHistory(song.id, now)
                if (updated == 0) {
                    songDao.insertHistoryItem(
                        HistoryEntity(
                            songId = song.id,
                            title = song.title,
                            artistName = song.artistName,
                            albumId = song.albumId,
                            durationMs = song.durationMs,
                            artworkUrl = song.artworkUrl,
                            streamUrl = song.streamUrl,
                            playedAt = now,
                            playCount = 1,
                            listenedMs = 0L
                        )
                    )
                }

                // Now that this song has a row, its listening clock can accumulate into it.
                listenedSongId = song.id
                listenedAccumulatedMs = 0L
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record history: ${e.message}")
            }
        }
    }

    /**
     * Adds one playback tick's worth of time to the current song's accumulator.
     *
     * The caller (the progress ticker) is the only place that knows whether the player is actually
     * playing, so the decision to credit time stays there; this just holds the running total.
     */
    fun accumulateTick(tickMs: Long) {
        if (listenedSongId != null) listenedAccumulatedMs += tickMs
    }

    /**
     * Adds the listening time accumulated for the current song to its history row.
     *
     * Only a non-trivial delta is written, so a song glimpsed for a moment does not create a
     * statistics row of a few hundred milliseconds. Failures are swallowed with a log: losing a few
     * seconds of statistics must not surface as a playback error.
     */
    suspend fun flushListeningTime() {
        val songId = listenedSongId ?: return
        val delta = listenedAccumulatedMs
        listenedAccumulatedMs = 0L
        if (delta < MIN_LISTENED_FLUSH_MS) return

        runCatching { songDao.addListenedMs(songId, delta) }
            .onFailure { Log.w(TAG, "Failed to persist listened time: ${it.message}") }
    }

    /**
     * Persists whatever the clock has accumulated but not yet flushed, on a scope that outlives
     * the ViewModel.
     *
     * This exists for `onCleared`, which cannot use the injected [scope] — it is already cancelled
     * by then. It goes on an application-lifetime scope, the only context that outlives the caller.
     */
    fun flushListeningTimeOnProcessScope() {
        val pendingSongId = listenedSongId
        val pendingDelta = listenedAccumulatedMs
        listenedSongId = null
        listenedAccumulatedMs = 0L
        if (pendingSongId != null && pendingDelta >= MIN_LISTENED_FLUSH_MS) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { songDao.addListenedMs(pendingSongId, pendingDelta) }
            }
        }
    }

    private suspend fun isListeningHistoryEnabled(): Boolean =
        settingsStore.getBoolean(SettingsStore.PRIVACY_LISTENING_HISTORY, true)

    companion object {
        private const val TAG = "CRANK_PLAYER"

        /**
         * Smallest listening delta worth persisting.
         *
         * Below this the write is pure overhead — a track glimpsed for a moment should not create a
         * statistics row of a few hundred milliseconds.
         */
        private const val MIN_LISTENED_FLUSH_MS = 1_000L
    }
}
