package com.crank.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Id namespacing between the two sources.
 *
 * The property that matters is that a Gaana id can never equal a YouTube id, because both land in
 * Room's `songs.id` primary key and `insertSong` is `@Insert(onConflict = REPLACE)`. A collision
 * would silently delete a user's like and reset its `dateAdded` — see f8dae61 / a5b7fd8.
 */
class ContentSourceTest {

    @Test
    fun `a bare id is youtube`() {
        assertEquals(ContentSource.YOUTUBE, ContentSource.of("dQw4w9WgXcQ"))
    }

    @Test
    fun `a namespaced id is gaana`() {
        assertEquals(ContentSource.GAANA, ContentSource.of("gaana:sanam-re"))
    }

    /** Recognised tracks carry a marker that must not be mistaken for a Gaana id. */
    @Test
    fun `the unresolved recognition marker is youtube`() {
        assertEquals(ContentSource.YOUTUBE, ContentSource.of("audd:unresolved:Foo:Bar"))
    }

    @Test
    fun `a gaana id never collides with a youtube video id`() {
        val videoId = "sanam-re"
        assertNotEquals(videoId, ContentSource.gaanaTrackId(videoId))
        assertFalse(ContentSource.of(videoId).idPrefix.isNotEmpty())
    }

    @Test
    fun `a gaana track id round trips to its seokey`() {
        assertEquals("sanam-re", ContentSource.gaanaTrackSeokeyOrNull(ContentSource.gaanaTrackId("sanam-re")))
    }

    @Test
    fun `a gaana album id round trips to its seokey`() {
        assertEquals("sanam-re", ContentSource.gaanaAlbumSeokeyOrNull(ContentSource.gaanaAlbumId("sanam-re")))
    }

    /**
     * Album ids share the `gaana:` prefix but address a collection. Handing one to the track
     * endpoint would 404, so it must not be accepted as a track id.
     */
    @Test
    fun `an album id is not a track id`() {
        assertNull(ContentSource.gaanaTrackSeokeyOrNull(ContentSource.gaanaAlbumId("sanam-re")))
    }

    @Test
    fun `a track id is not an album id`() {
        assertNull(ContentSource.gaanaAlbumSeokeyOrNull(ContentSource.gaanaTrackId("sanam-re")))
    }

    @Test
    fun `youtube ids are not gaana seokeys`() {
        assertNull(ContentSource.gaanaTrackSeokeyOrNull("dQw4w9WgXcQ"))
        assertNull(ContentSource.gaanaAlbumSeokeyOrNull("dQw4w9WgXcQ"))
    }

    /** A bare prefix with nothing behind it is not a usable id. */
    @Test
    fun `the prefix alone is not an id`() {
        assertNull(ContentSource.gaanaTrackSeokeyOrNull("gaana:"))
        assertNull(ContentSource.gaanaAlbumSeokeyOrNull("gaana:album:"))
    }

    /** Every source except YouTube must namespace, or collisions become possible again. */
    @Test
    fun `every non-youtube source carries a prefix`() {
        ContentSource.entries
            .filter { it != ContentSource.YOUTUBE }
            .forEach { assertTrue("${it.name} must namespace its ids", it.idPrefix.isNotEmpty()) }
    }
}
