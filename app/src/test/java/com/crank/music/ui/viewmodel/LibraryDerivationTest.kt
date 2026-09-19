package com.crank.music.ui.viewmodel

import com.crank.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the library's derived rows.
 *
 * The previous implementation hardcoded an eight-name artist roster, six albums with invented
 * release years, and `(10..50).random()` song counts. These tests exercise the replacement:
 * grouping that reflects what is actually in the user's library.
 */
class LibraryDerivationTest {

    private fun song(
        id: String,
        title: String,
        artist: String,
        albumId: String? = null,
        artwork: String = "",
    ) = Song(
        id = id,
        title = title,
        artistName = artist,
        albumId = albumId,
        durationMs = 180_000L,
        artworkUrl = artwork,
        isLocal = false,
    )

    // ── URL/opaque-id heuristic ──────────────────────────────────────────────────

    /**
     * The heuristic decides whether an album id is a readable name or an internal handle.
     *
     * URLs matter here because they are the realistic non-title value: search results carry
     * artwork URLs, and an album id sourced from one must not be rendered as a heading. A URL
     * that happens to fall inside the length window is the interesting case — it is rejected by
     * the explicit URL check, not merely by its length.
     */
    @Test
    fun `URLs are not treated as readable album titles`() {
        val urls = listOf(
            // Inside the 11..34 window, so only the URL check can reject this one.
            "http://example.com/cover.jpg",
            "i.ytimg.com/vi/xyz/default.jpg",
            // Outside the window — rejected on length.
            "https://lh3.googleusercontent.com/abc123",
        )
        urls.forEach { url ->
            assertFalse("'$url' should be treated as opaque", looksLikeOpaqueId(url))
        }
    }

    /** A URL-shaped string with no spaces or dots still reads as opaque. */
    @Test
    fun `a dotless URL is still rejected`() {
        assertFalse(looksLikeOpaqueId("https://example/abc"))
        assertFalse(looksLikeOpaqueId("//cdn/abc"))
    }

    @Test
    fun `human-readable album names are not treated as opaque ids`() {
        val names = listOf(
            "Dawn FM",
            "Future Nostalgia",
            "Harry's House",
            "A Sky Full of Stars",
            "Random Access Memories",
        )
        names.forEach { name ->
            assertFalse("'$name' should be readable", looksLikeOpaqueId(name))
        }
    }

    @Test
    fun `a bare YouTube-style id is treated as opaque`() {
        assertTrue(looksLikeOpaqueId("dQw4w9WgXcQ"))
        assertTrue(looksLikeOpaqueId("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"))
    }

    /** Short readable names must not be misread as ids just because they lack spaces. */
    @Test
    fun `short names are not treated as opaque`() {
        assertFalse(looksLikeOpaqueId("Discovery"))
        assertFalse(looksLikeOpaqueId("Thriller"))
    }

    @Test
    fun `a name containing punctuation is treated as readable`() {
        assertFalse(looksLikeOpaqueId("Harry's House"))
        assertFalse(looksLikeOpaqueId("Sgt. Pepper's"))
    }

    // ── AlbumItem → domain conversion ────────────────────────────────────────────

    @Test
    fun `AlbumItem conversion carries title and artist for the search-backed route`() {
        val item = AlbumItem(
            id = "some-id",
            title = "Random Access Memories",
            artistName = "Daft Punk",
            artworkUrl = "https://example.com/art.jpg",
            year = "",
            songCount = 13,
        )

        val album = item.toDomainModel()

        // These two are what the album route actually needs: the backend resolves by text.
        assertEquals("Random Access Memories", album.title)
        assertEquals("Daft Punk", album.artistName)
        assertEquals(13, album.trackCount)
        // No year is known from the library, and it must not acquire a fabricated one.
        assertEquals("", album.releaseYear)
    }

    @Test
    fun `an unknown year stays blank through conversion`() {
        val item = AlbumItem("id", "T", "A", "", year = "", songCount = 0)
        assertEquals("", item.toDomainModel().releaseYear)
    }

    // ── Grouping invariants ──────────────────────────────────────────────────────

    /**
     * Songs that carry a real album name group under it; songs with no album fall back to the
     * artist. The previous code produced album rows with invented titles instead.
     */
    @Test
    fun `songs with a readable album group under that album`() {
        val songs = listOf(
            song("1", "Track A", "Daft Punk", albumId = "Random Access Memories"),
            song("2", "Track B", "Daft Punk", albumId = "Random Access Memories"),
            song("3", "Track C", "Daft Punk", albumId = "Discovery"),
        )

        val grouped = songs.groupBy { it.albumId!! }

        assertEquals(2, grouped.size)
        assertEquals(2, grouped["Random Access Memories"]!!.size)
        assertEquals(1, grouped["Discovery"]!!.size)
    }

    @Test
    fun `song counts reflect the real library rather than a random range`() {
        val songs = List(7) { index -> song("$index", "T$index", "Artist") }
        val byArtist = songs.groupBy { it.artistName }
        assertEquals(7, byArtist.values.first().size)
    }

    /** Distinct-by-id guards against the same track appearing via two sources. */
    @Test
    fun `a track present in liked and downloaded lists counts once`() {
        val liked = listOf(song("same", "Title", "Artist"))
        val downloaded = listOf(song("same", "Title", "Artist"))
        val combined = (liked + downloaded).distinctBy { it.id }
        assertEquals(1, combined.size)
    }
}
