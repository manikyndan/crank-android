package com.crank.music.ui.viewmodel

import com.crank.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Liked Songs filter and sort.
 *
 * These two rules decide what the user actually sees in the tracklist, and they are the part of
 * the screen that is hardest to exercise by hand — it needs a device, a database and a real liked
 * set. As a pure function it needs none of them, so every combination is a plain call.
 *
 * The list arrives already ordered by `dateAdded DESC` from the DAO, which is why the fixtures
 * below are written newest-first and why [LikedSort.RECENCY] asserts that order is *preserved*
 * rather than recomputed.
 */
class LikedSongOrderingTest {

    private fun song(
        id: String,
        title: String,
        artist: String,
        albumId: String? = null,
        albumName: String? = null,
        durationMs: Long = 0L,
    ) = Song(
        id = id,
        title = title,
        artistName = artist,
        albumId = albumId,
        albumName = albumName,
        durationMs = durationMs,
        artworkUrl = "",
        isLocal = false,
    )

    // Newest first, as the database returns them.
    private val newestFirst = listOf(
        song("1", "Zebra", "Beta", albumId = "alb-b", durationMs = 300_000L),
        song("2", "apple", "Alpha", albumId = "alb-a", durationMs = 100_000L),
        song("3", "Mango", "Gamma", albumId = "alb-a", durationMs = 200_000L),
    )

    private fun ids(songs: List<Song>) = songs.map { it.id }

    // ── Filtering ───────────────────────────────────────────────────────────────────

    @Test
    fun `an empty query keeps every song`() {
        assertEquals(3, filterAndSortLikedSongs(newestFirst, "", LikedSort.RECENCY).size)
    }

    @Test
    fun `a whitespace-only query is treated as empty`() {
        // Otherwise a stray space would empty the list and look like a bug to the user.
        assertEquals(3, filterAndSortLikedSongs(newestFirst, "   ", LikedSort.RECENCY).size)
    }

    @Test
    fun `the query matches the title case-insensitively`() {
        assertEquals(listOf("1"), ids(filterAndSortLikedSongs(newestFirst, "zeb", LikedSort.RECENCY)))
        // Lowercase query against a capitalised title.
        assertEquals(listOf("3"), ids(filterAndSortLikedSongs(newestFirst, "mang", LikedSort.RECENCY)))
    }

    @Test
    fun `the query matches the artist`() {
        assertEquals(listOf("2"), ids(filterAndSortLikedSongs(newestFirst, "alpha", LikedSort.RECENCY)))
    }

    @Test
    fun `the query matches the album id`() {
        // Album still matches on albumId when no display name is present.
        assertEquals(
            listOf("2", "3"),
            ids(filterAndSortLikedSongs(newestFirst, "alb-a", LikedSort.RECENCY)),
        )
    }

    @Test
    fun `the query matches the album name when present`() {
        val withNames = listOf(
            song("1", "Zebra", "Beta", albumId = "x1", albumName = "Midnight Drive"),
            song("2", "Apple", "Alpha", albumId = "x2", albumName = "After Hours"),
            song("3", "Mango", "Gamma", albumId = "x3", albumName = "After Hours"),
        )
        assertEquals(
            listOf("2", "3"),
            ids(filterAndSortLikedSongs(withNames, "after hours", LikedSort.RECENCY)),
        )
    }

    @Test
    fun `a query matching nothing yields an empty list`() {
        assertTrue(filterAndSortLikedSongs(newestFirst, "zzz", LikedSort.RECENCY).isEmpty())
    }

    @Test
    fun `a song with no album is not matched by an album query`() {
        val noAlbum = listOf(song("9", "Plain", "Artist", albumId = null))
        assertTrue(filterAndSortLikedSongs(noAlbum, "alb", LikedSort.RECENCY).isEmpty())
        // ...but it is still matched by its own title, so the null albumId does not exclude it.
        assertEquals(listOf("9"), ids(filterAndSortLikedSongs(noAlbum, "plain", LikedSort.RECENCY)))
    }

    // ── Sorting ─────────────────────────────────────────────────────────────────────

    @Test
    fun `recency preserves the order the database returned`() {
        // The DAO already ordered by dateAdded DESC. Re-sorting here could only disagree with it.
        assertEquals(
            listOf("1", "2", "3"),
            ids(filterAndSortLikedSongs(newestFirst, "", LikedSort.RECENCY)),
        )
    }

    @Test
    fun `title sorts alphabetically ignoring case`() {
        // "apple" must sort before "Mango" — a case-sensitive sort would put it last.
        assertEquals(
            listOf("2", "3", "1"),
            ids(filterAndSortLikedSongs(newestFirst, "", LikedSort.TITLE)),
        )
    }

    @Test
    fun `artist sorts alphabetically`() {
        assertEquals(
            listOf("2", "1", "3"),
            ids(filterAndSortLikedSongs(newestFirst, "", LikedSort.ARTIST)),
        )
    }

    @Test
    fun `album groups tracks that share an album`() {
        val ordered = ids(filterAndSortLikedSongs(newestFirst, "", LikedSort.ALBUM))
        // alb-a before alb-b, so songs 2 and 3 lead and song 1 follows.
        assertEquals(listOf("2", "3", "1"), ordered)
    }

    @Test
    fun `album sort prefers the display name over the browse id`() {
        // Same albumId bucket, distinct display names: the name wins so the sort reads naturally.
        val mixed = listOf(
            song("1", "Z", "B", albumId = "same", albumName = "Zulu Sessions"),
            song("2", "A", "A", albumId = "same", albumName = "Alpha Wave"),
            song("3", "M", "G", albumId = "same", albumName = "Midnight"),
        )
        assertEquals(
            listOf("2", "3", "1"),
            ids(filterAndSortLikedSongs(mixed, "", LikedSort.ALBUM)),
        )
    }

    @Test
    fun `duration sorts shortest first`() {
        assertEquals(
            listOf("2", "3", "1"),
            ids(filterAndSortLikedSongs(newestFirst, "", LikedSort.DURATION)),
        )
    }

    // ── Composition ─────────────────────────────────────────────────────────────────

    @Test
    fun `the sort applies to the filtered set, not the whole library`() {
        // Filter to album alb-a (songs 2 and 3), then sort by duration: 2 is shorter than 3.
        val result = filterAndSortLikedSongs(newestFirst, "alb-a", LikedSort.DURATION)
        assertEquals(listOf("2", "3"), ids(result))
    }

    @Test
    fun `sorting does not mutate the input list`() {
        // The caller keeps handing back the same `allSongs`; if this sorted in place, the
        // recency ordering would be silently destroyed for every later query.
        val before = ids(newestFirst)
        filterAndSortLikedSongs(newestFirst, "", LikedSort.TITLE)
        assertEquals(before, ids(newestFirst))
    }

    @Test
    fun `every sort is offered with a label`() {
        LikedSort.entries.forEach { sort ->
            assertTrue("$sort has no label", sort.label.isNotBlank())
        }
    }
}
