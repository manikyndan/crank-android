package com.crank.music.ui.viewmodel

import com.crank.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the queue snapshot encoding used by playback restore.
 *
 * These matter more than they look. The snapshot is written every five seconds and read once, at
 * app start — so a defect here does not show up while using the app, it shows up the next time
 * it launches, as a queue with tracks missing. That is a hard bug to attribute, and an easy one
 * to prevent.
 */
class QueueSnapshotTest {

    private fun song(id: String) = Song(
        id = id,
        title = "Title $id",
        artistName = "Artist",
        albumId = null,
        durationMs = 180_000L,
        artworkUrl = "",
        isLocal = false,
    )

    @Test
    fun `round trips a plain queue`() {
        val ids = listOf("abc123", "def456", "ghi789")
        val encoded = PlayerViewModel.encodeQueueIds(ids.map { song(it) })
        assertEquals(ids, PlayerViewModel.decodeQueueIds(encoded))
    }

    @Test
    fun `round trips ids containing commas`() {
        // The regression this guards: a comma-joined snapshot splits these into five ids rather
        // than three, and the two extra fragments resolve to no song.
        val ids = listOf("artist,track", "a,b,c", "x,y")
        val encoded = PlayerViewModel.encodeQueueIds(ids.map { song(it) })
        assertEquals(ids, PlayerViewModel.decodeQueueIds(encoded))
    }

    @Test
    fun `round trips an empty queue`() {
        assertEquals(emptyList<String>(), PlayerViewModel.decodeQueueIds(PlayerViewModel.encodeQueueIds(emptyList())))
    }

    @Test
    fun `reads a legacy comma-joined snapshot`() {
        // A snapshot written before the separator changed must still restore, or the upgrade
        // loses whatever the user had queued.
        assertEquals(
            listOf("abc123", "def456"),
            PlayerViewModel.decodeQueueIds("abc123,def456"),
        )
    }

    @Test
    fun `blank snapshot decodes to an empty queue`() {
        assertEquals(emptyList<String>(), PlayerViewModel.decodeQueueIds(""))
        assertEquals(emptyList<String>(), PlayerViewModel.decodeQueueIds("   "))
    }

    @Test
    fun `ignores empty segments`() {
        assertEquals(listOf("a", "b"), PlayerViewModel.decodeQueueIds("a\n\nb"))
    }

    @Test
    fun `prefers the newline separator when both are present`() {
        // A new-format snapshot whose ids contain commas must not be split on the comma. This is
        // the case that makes the "which separator" choice load-bearing rather than cosmetic.
        val ids = listOf("artist,track", "other,id")
        val encoded = PlayerViewModel.encodeQueueIds(ids.map { song(it) })

        assertTrue("expected a newline separator", encoded.contains('\n'))
        assertEquals(ids, PlayerViewModel.decodeQueueIds(encoded))
    }
}
