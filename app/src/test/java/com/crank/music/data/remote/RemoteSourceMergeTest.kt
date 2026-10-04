package com.crank.music.data.remote

import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge policy behind [RemoteSourceRouter]: YouTube first, Gaana appended, never replacing.
 *
 * The invariant that matters most is the first test — if adding a source can change or drop what
 * the primary source returned, then configuring a Gaana server would silently alter the results of
 * users who never asked for it.
 */
class RemoteSourceMergeTest {

    private fun song(id: String, title: String, artist: String) = Song(
        id = id,
        title = title,
        artistName = artist,
        albumId = null,
        durationMs = 0L,
        artworkUrl = "",
        isLocal = false,
    )

    private fun album(id: String, title: String, artist: String) = Album(
        id = id,
        title = title,
        artistName = artist,
        releaseYear = "",
        artworkUrl = "",
        trackCount = 0,
    )

    // --- appendDistinct: the primary half is inviolable -------------------

    @Test
    fun `primary results keep their order and are never dropped`() {
        val youtube = listOf(song("a", "Alpha", "X"), song("b", "Beta", "Y"))
        assertEquals(youtube, appendDistinct(youtube, emptyList(), ::matchesSameTrack))
    }

    @Test
    fun `secondary results are appended after the primary`() {
        val merged = appendDistinct(
            primary = listOf(song("a", "Alpha", "X")),
            secondary = listOf(song("gaana:b", "Beta", "Y")),
            matches = ::matchesSameTrack,
        )
        assertEquals(listOf("a", "gaana:b"), merged.map { it.id })
    }

    @Test
    fun `an empty secondary leaves the primary list untouched`() {
        val youtube = listOf(song("a", "Alpha", "X"))
        assertEquals(youtube, appendDistinct(youtube, emptyList<Song>(), ::matchesSameTrack))
    }

    // --- matching across sources ------------------------------------------

    @Test
    fun `a track both sources know is not shown twice`() {
        val merged = appendDistinct(
            primary = listOf(song("a", "Sanam Re", "Arijit Singh")),
            secondary = listOf(song("gaana:sanam-re", "Sanam Re", "Arijit Singh")),
            matches = ::matchesSameTrack,
        )
        assertEquals(listOf("a"), merged.map { it.id })
    }

    /**
     * The case a key-based de-duplication could not express, and the one that failed when identity
     * was the *leading* artist of each side: Gaana lists every contributor while YouTube names the
     * lead, so it is the shared artist that must match, not the first one.
     */
    @Test
    fun `differing credit lists match on a shared artist`() {
        val merged = appendDistinct(
            primary = listOf(song("a", "Sanam Re", "Arijit Singh")),
            secondary = listOf(song("gaana:sanam-re", "Sanam Re", "Mithoon, Arijit Singh")),
            matches = ::matchesSameTrack,
        )
        assertEquals(listOf("a"), merged.map { it.id })
    }

    @Test
    fun `credit order does not affect the match`() {
        val merged = appendDistinct(
            primary = listOf(song("a", "Sanam Re", "Arijit Singh, Mithoon")),
            secondary = listOf(song("gaana:x", "Sanam Re", "Mithoon, Arijit Singh")),
            matches = ::matchesSameTrack,
        )
        assertEquals(listOf("a"), merged.map { it.id })
    }

    @Test
    fun `differing punctuation and case still match`() {
        val merged = appendDistinct(
            primary = listOf(song("a", "Sanam Re", "Arijit Singh")),
            secondary = listOf(song("gaana:x", "sanam re!", "ARIJIT SINGH")),
            matches = ::matchesSameTrack,
        )
        assertEquals(listOf("a"), merged.map { it.id })
    }

    @Test
    fun `a song by a different artist with the same title is kept`() {
        val merged = appendDistinct(
            primary = listOf(song("a", "Hello", "Adele")),
            secondary = listOf(song("gaana:b", "Hello", "Oasis")),
            matches = ::matchesSameTrack,
        )
        assertEquals(listOf("a", "gaana:b"), merged.map { it.id })
    }

    @Test
    fun `two genuinely different songs by one artist both survive`() {
        val merged = appendDistinct(
            primary = listOf(song("a", "One", "Artist")),
            secondary = listOf(song("gaana:b", "Two", "Artist")),
            matches = ::matchesSameTrack,
        )
        assertEquals(listOf("a", "gaana:b"), merged.map { it.id })
    }

    @Test
    fun `duplicates within the secondary are collapsed`() {
        val merged = appendDistinct(
            primary = emptyList(),
            secondary = listOf(
                song("gaana:x", "Same", "Artist"),
                song("gaana:y", "Same", "Artist"),
            ),
            matches = ::matchesSameTrack,
        )
        assertEquals(listOf("gaana:x"), merged.map { it.id })
    }

    // --- helpers ----------------------------------------------------------

    @Test
    fun `normalisation strips case, spacing and punctuation`() {
        assertEquals("sanamre", normalizeForMatch("Sanam Re!"))
        assertEquals("sanamre", normalizeForMatch("  SANAM RE "))
    }

    @Test
    fun `a shared artist token is what two credit lists compare on`() {
        assertTrue(
            matchesSameTrack(
                song("a", "Sanam Re", "Arijit Singh"),
                song("b", "Sanam Re", "Mithoon, Arijit Singh"),
            ),
        )
        assertFalse(
            matchesSameTrack(
                song("a", "Sanam Re", "Arijit Singh"),
                song("b", "Sanam Re", "Kishore Kumar"),
            ),
        )
    }

    @Test
    fun `album identity merges on title and a shared artist`() {
        val merged = appendDistinct(
            primary = listOf(album("a", "Sanam Re", "Mithoon")),
            secondary = listOf(album("gaana:sanam-re", "Sanam Re", "Mithoon, Arijit Singh")),
            matches = ::matchesSameAlbum,
        )
        assertEquals(listOf("a"), merged.map { it.id })
    }

    @Test
    fun `albums with the same title by different artists are both kept`() {
        val merged = appendDistinct(
            primary = listOf(album("a", "Greatest Hits", "Artist One")),
            secondary = listOf(album("gaana:b", "Greatest Hits", "Artist Two")),
            matches = ::matchesSameAlbum,
        )
        assertEquals(listOf("a", "gaana:b"), merged.map { it.id })
    }
}

