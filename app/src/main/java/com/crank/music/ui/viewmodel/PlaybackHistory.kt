package com.crank.music.ui.viewmodel

import com.crank.music.domain.model.Song

/**
 * The user's playback trail, used to back the "Previous" control.
 *
 * ## Why this is its own object
 *
 * The previous implementation kept a `MutableList<Song>` and an `Int` index as two loose fields on
 * the view model and mutated both at every play entry point. The two had to stay consistent by
 * hand, and they did not: `playPrevious` read the index, `playSong` rewrote it, and the forward
 * half of the list was trimmed in yet a third place. A Previous press after skipping forward
 * could therefore land on the *current* track (restarting it) instead of the one before it — the
 * bug this class exists to prevent.
 *
 * Centralising the trail in one object with a small operation set means that invariant is stated
 * once and tested once, instead of re-derived at each call site.
 *
 * ## Relationship to the queue
 *
 * This is deliberately separate from [PlayQueue]. The queue (`upNext`) is the *upcoming* list;
 * this is the *already-played* trail. "Next" draws from the queue, "Previous" draws from here.
 * Keeping them apart is what lets "Previous" reach a track the user skipped past even after the
 * queue has moved on. The two never share state, so they cannot disagree.
 */
class PlaybackHistory {

    private val trail = mutableListOf<Song>()
    private var index: Int = -1

    /** The track currently playing, or null before anything has played. */
    val current: Song? get() = trail.getOrNull(index)

    /** True when pressing Previous would move to an earlier track rather than restart the current. */
    fun canGoBack(): Boolean = trail.size > 1 && index > 0

    /**
     * Records that [song] is now playing.
     *
     * Any trail ahead of the current index is discarded first: pressing Next a few times builds a
     * forward trail, then choosing a different song starts a new branch rather than keeping the old
     * one reachable. Without this trim, Previous could walk back into a branch the user had already
     * abandoned.
     */
    fun record(song: Song) {
        if (index >= 0 && index < trail.size - 1) {
            trail.subList(index + 1, trail.size).clear()
        }
        trail.add(song)
        index = trail.lastIndex
    }

    /**
     * Steps back one track and returns it.
     *
     * Returns `null` when there is nothing earlier — the caller responds by restarting the current
     * track (`seekTo(0)`) rather than by doing nothing, which is the conventional behaviour.
     */
    fun previous(): Song? {
        if (!canGoBack()) return null
        index--
        return trail[index]
    }

    // ── Test-only inspection ───────────────────────────────────────────────────────
    /** The track at a given trail position; out-of-range returns null. */
    fun at(position: Int): Song? = trail.getOrNull(position)

    val size: Int get() = trail.size
    val position: Int get() = index
    fun snapshot(): List<Song> = trail.toList()
}
