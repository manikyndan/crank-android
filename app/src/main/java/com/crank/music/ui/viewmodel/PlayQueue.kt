package com.crank.music.ui.viewmodel

import com.crank.music.domain.model.Song

/**
 * The playback queue.
 *
 * ## Why this is a class and not two loose lists
 *
 * The queue was previously represented by three fields that were mutated independently:
 * `_queue` (the upcoming songs), `originalQueue` (the unshuffled order), and `shuffledIndices`
 * (indexes into `originalQueue`). Every queue operation reimplemented the relationship between
 * them, and each one could get it wrong on its own:
 *
 * - Turning shuffle off rebuilt the queue from `originalQueue.drop(currentIdx + 1)`, which
 *   **silently discards** anything the user had explicitly added with "add to queue" — those
 *   songs are in `_queue` but never in `originalQueue`.
 * - `saveQueueToRoom()` persisted only the upcoming songs, so a restart restored a queue with no
 *   knowledge of `originalQueue`; repeat-all then wrapped to a partial list.
 * - `playSongWithContext` replaced `originalQueue` wholesale, so playing a single song from an
 *   album reset the repeat-all context to that album — usually right, but it also meant the
 *   "up next" list and the repeat source could disagree.
 *
 * Making it one object with a small set of operations means those invariants are stated once and
 * tested once, instead of being re-derived at each call site.
 *
 * ## The model
 *
 * - [upNext] — what plays after the current track, in order. This is what the queue screen shows.
 * - [context] — the list the current track was started from, used as the repeat-all source and
 *   as the basis for reshuffling. Null when there is no meaningful context (a single track).
 * - [manualAdditions] — songs the user queued explicitly. Tracked separately because they must
 *   survive a shuffle toggle: they were never part of [context], so rebuilding from context
 *   would drop them.
 *
 * Ordering rule: `upNext` is always `[shuffled remainder of context] + [manualAdditions]`. That
 * single sentence is the invariant the whole class exists to enforce.
 */
data class PlayQueue(
    val upNext: List<Song> = emptyList(),
    val context: List<Song>? = null,
    val manualAdditions: List<Song> = emptyList(),
) {

    val isEmpty: Boolean get() = upNext.isEmpty()

    /** The next song to play, or `null` when the queue is exhausted. */
    fun peekNext(): Song? = upNext.firstOrNull()

    /** Drops the first song, returning the remaining queue. */
    fun dropFirst(): PlayQueue = copy(upNext = upNext.drop(1))

    /** Appends a song that the user queued explicitly. */
    fun addManual(song: Song): PlayQueue =
        copy(
            upNext = upNext + song,
            manualAdditions = manualAdditions + song,
        )

    /** Removes the song at [index] from the upcoming list. */
    fun removeAt(index: Int): PlayQueue {
        if (index !in upNext.indices) return this
        val removed = upNext[index]
        val remaining = upNext.toMutableList().apply { removeAt(index) }

        // A manual addition must also leave `manualAdditions`, or the next shuffle toggle would
        // re-add a song the user deliberately deleted.
        val manualIndex = manualAdditions.indexOfFirst { it.id == removed.id }
        val manual =
            if (manualIndex >= 0) {
                manualAdditions.toMutableList().apply { removeAt(manualIndex) }
            } else {
                manualAdditions
            }

        return copy(upNext = remaining, manualAdditions = manual)
    }

    /**
     * Moves the song at [fromIndex] to [toIndex] within the upcoming list.
     *
     * Deliberately does **not** touch [manualAdditions]: reordering is a display concern, and a
     * song queued by hand stays queued by hand wherever it ends up.
     */
    fun move(fromIndex: Int, toIndex: Int): PlayQueue {
        if (fromIndex !in upNext.indices || toIndex !in upNext.indices) return this
        val working = upNext.toMutableList()
        working.add(toIndex, working.removeAt(fromIndex))
        return copy(upNext = working)
    }

    /** Empties the queue, including any pending manual additions. */
    fun cleared(): PlayQueue = PlayQueue()

    /**
     * Builds a queue for a track started from [contextList], honouring [shuffle].
     *
     * The current song is excluded from `upNext`, and is preserved even when it does not appear
     * in [contextList] — playing a search result that is not in the list it was started from is
     * normal, and dropping the context entirely in that case would disable repeat-all.
     *
     * @param currentSong the track now playing.
     * @param contextList the list it was started from.
     * @param shuffle whether to randomise the upcoming order.
     * @param random source of randomness, injectable so ordering is reproducible in tests.
     */
    fun fromContext(
        currentSong: Song,
        contextList: List<Song>,
        shuffle: Boolean,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): PlayQueue {
        // De-duplicate by id: the same track can legitimately arrive twice from a search or an
        // album, and duplicates make "remove this one" ambiguous.
        val deduped = contextList.distinctBy { it.id }
        val others = deduped.filter { it.id != currentSong.id }

        val ordered =
            if (shuffle) {
                val rest = others.toMutableList().apply { shuffle(random) }

                // When the playing track came from this context, that context still contains it,
                // so `others` already excludes it and no split is needed. When it did not, the
                // whole list is upcoming.
                rest
            } else {
                val index = deduped.indexOfFirst { it.id == currentSong.id }
                if (index >= 0) others else deduped
            }

        return PlayQueue(upNext = ordered, context = deduped, manualAdditions = emptyList())
    }

    /**
     * Restores a queue whose upcoming songs were persisted but whose context was not.
     *
     * `upNext` is the source of truth here; [context] is unknown across a restart, so repeat-all
     * falls back to the restored list. That is a deliberate trade: reconstructing a context we
     * did not save would invent an order the user never saw.
     */
    fun restored(upNext: List<Song>): PlayQueue =
        PlayQueue(upNext = upNext, context = upNext.ifEmpty { null }, manualAdditions = emptyList())

    /**
     * Rebuilds the upcoming order for the current [shuffle] setting.
     *
     * This is the operation that was previously spread across `toggleShuffle` and
     * `buildShuffledQueue`, and the one that lost manual additions. Manual additions are
     * re-appended after the context remainder, which is where the user expects songs they queued
     * to land.
     */
    fun reshuffled(
        currentSong: Song,
        shuffle: Boolean,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): PlayQueue {
        val source = context
        if (source.isNullOrEmpty()) {
            // No context to reorder. Preserve whatever is upcoming rather than clearing it —
            // the user is looking at that list.
            return this
        }

        val others = source.filter { it.id != currentSong.id }
        val ordered = if (shuffle) others.toMutableList().apply { shuffle(random) } else others

        // Manual additions follow the context remainder and are de-duplicated against it, so a
        // song that is both in the album and explicitly queued does not appear twice.
        val orderedIds = ordered.map { it.id }.toSet()
        val manual = manualAdditions.filterNot { it.id in orderedIds }

        return copy(upNext = ordered + manual)
    }

    /**
     * Wraps back to the start of [context] for repeat-all.
     *
     * Returns `null` when there is nothing to repeat, so the caller can stop playback rather than
     * loop silently.
     */
    fun forRepeatAll(
        currentSong: Song,
        shuffle: Boolean,
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): PlayQueue? {
        val source = context?.takeIf { it.isNotEmpty() } ?: return null
        return reshuffled(currentSong, shuffle, random)
    }

    companion object {
        /**
         * Upper bound on the persisted queue.
         *
         * The snapshot is written every five seconds, so an unbounded queue would mean writing an
         * unbounded string to SQLite on a timer. A few thousand tracks is far beyond any real
         * listening session.
         */
        const val MAX_PERSISTED_SIZE = 2000

        /**
         * The songs to persist in the playback snapshot.
         *
         * Only `upNext`: [context] is reconstructible from the upcoming list plus the current
         * track for our purposes, and persisting both would double the write on every save.
         */
        fun persistableSongs(queue: PlayQueue): List<Song> =
            queue.upNext.take(MAX_PERSISTED_SIZE)
    }
}
