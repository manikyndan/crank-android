package com.crank.music.ui.viewmodel

import com.crank.music.domain.model.Song

/**
 * Orderings offered by the sort sheet.
 *
 * [RECENCY] is deliberately the DAO's own order rather than a re-sort in memory: the liked query
 * is already `ORDER BY dateAdded DESC`, and `dateAdded` lives on the Room entity, not on the
 * [Song] domain model. Re-sorting here would need a field the domain object does not carry, so
 * the natural order *is* recency — and re-sorting would only risk disagreeing with it.
 */
enum class LikedSort(val label: String) {
    RECENCY("Recency"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
}

/**
 * Applies the Liked Songs search filter and sort to [songs].
 *
 * A pure function so the two rules that actually shape what the user sees can be tested directly,
 * rather than only through the screen. The screen is the hard part to exercise — it needs a
 * device, a database and a real liked set — while this needs none of them.
 *
 * The filter matches title, artist and album. Album is matched against `albumName` when the
 * source provided one, and falls back to `albumId` (a browse id) for tracks that have no display
 * name — so a typed album *name* matches where known, and the id still matches for the rest.
 *
 * Ordering is stable and case-insensitive for the text sorts, so two songs by the same artist
 * keep their relative recency instead of shuffling on every recomposition.
 */
internal fun filterAndSortLikedSongs(
    songs: List<Song>,
    query: String,
    sort: LikedSort,
): List<Song> {
    val trimmed = query.trim()

    val filtered = if (trimmed.isEmpty()) {
        songs
    } else {
        songs.filter { song ->
            song.title.contains(trimmed, ignoreCase = true) ||
                song.artistName.contains(trimmed, ignoreCase = true) ||
                song.albumName?.contains(trimmed, ignoreCase = true) == true ||
                song.albumId?.contains(trimmed, ignoreCase = true) == true
        }
    }

    return when (sort) {
        // Already most-recently-added first, straight from the database. See [LikedSort].
        LikedSort.RECENCY -> filtered
        LikedSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
        LikedSort.ARTIST -> filtered.sortedBy { it.artistName.lowercase() }
        // Sort by the album display name when present, falling back to albumId so tracks that
        // only have a browse id still group together instead of scattering.
        LikedSort.ALBUM -> filtered.sortedBy {
            (it.albumName ?: it.albumId).orEmpty().lowercase()
        }
        LikedSort.DURATION -> filtered.sortedBy { it.durationMs }
    }
}
