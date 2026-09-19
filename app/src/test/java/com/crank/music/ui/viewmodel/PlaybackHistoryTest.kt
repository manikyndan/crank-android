package com.crank.music.ui.viewmodel

import com.crank.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the playback trail that backs "Previous".
 *
 * The trail was previously two loose fields on the view model — a list and an index — mutated at
 * three different call sites, so a Previous press could land on the *current* track and restart it
 * instead of stepping back. Encapsulating it in [PlaybackHistory] makes that invariant testable.
 */
class PlaybackHistoryTest {

    private fun song(id: String) = Song(
        id = id,
        title = "Title $id",
        artistName = "Artist",
        albumId = null,
        durationMs = 180_000L,
        artworkUrl = "",
        isLocal = false,
    )

    private val a = song("a")
    private val b = song("b")
    private val c = song("c")

    @Test
    fun `nothing recorded means no current track`() {
        val history = PlaybackHistory()
        assertNull(history.current)
        assertFalse(history.canGoBack())
        assertNull(history.previous())
    }

    @Test
    fun `recording the first track makes it current but nothing to go back to`() {
        val history = PlaybackHistory()
        history.record(a)

        assertEquals(a, history.current)
        assertFalse(history.canGoBack())
        // At the start of the trail, Previous restarts the current track (caller does seekTo(0)).
        assertNull(history.previous())
    }

    @Test
    fun `previous steps back through recorded tracks in order`() {
        val history = PlaybackHistory()
        listOf(a, b, c).forEach { history.record(it) }

        assertEquals(c, history.current)
        assertTrue(history.canGoBack())

        assertEquals(b, history.previous())
        assertEquals(a, history.previous())
    }

    @Test
    fun `previous at the start of the trail restarts rather than going negative`() {
        val history = PlaybackHistory()
        history.record(a)
        history.record(b)
        // current is 'b' at index 1; one Previous reaches 'a'.
        assertEquals(a, history.previous())
        assertEquals(a, history.current)
        // Now at the start. Another Previous must not reach a phantom track.
        assertNull(history.previous())
        assertEquals(a, history.current)
    }

    @Test
    fun `playing a new track while mid-trail branches forward`() {
        // Play a, b, c, go back to b, then play a again. The old 'c' must be unreachable from the
        // new branch, otherwise Previous could jump forward into abandoned history.
        val history = PlaybackHistory()
        listOf(a, b, c).forEach { history.record(it) }

        assertEquals(b, history.previous()) // back from c to b
        assertEquals(b, history.current)

        history.record(a) // start a new branch from b
        assertEquals(a, history.current)
        // The earlier 'c' is gone from the trail.
        assertEquals(listOf("a", "b", "a"), history.snapshot().map { it.id })
    }

    @Test
    fun `regressing the trail leaves the abandoned forward part unreachable`() {
        val history = PlaybackHistory()
        listOf(a, b, c, a).forEach { history.record(it) }

        // [a0, b1, c2, a3], index 3
        assertEquals(c, history.previous()) // -> c (index 2)
        assertEquals(b, history.previous()) // -> b (index 1)
        // index is 1; recording 'a' must drop index 2 and 3
        history.record(a)

        assertEquals(listOf("a", "b", "a"), history.snapshot().map { it.id })
        // And going back from the new 'a' reaches 'b', not the dropped 'c'.
        assertEquals(b, history.previous())
    }

    @Test
    fun `size and position track the trail`() {
        val history = PlaybackHistory()
        listOf(a, b, c).forEach { history.record(it) }

        assertEquals(3, history.size)
        assertEquals(2, history.position)
        history.previous()
        assertEquals(1, history.position)
    }

    @Test
    fun `replaying the same track is recorded twice not deduped`() {
        // The trail is a history of plays, not a set of unique songs; replaying must appear so the
        // back button can return to it.
        val history = PlaybackHistory()
        listOf(a, a, a).forEach { history.record(it) }

        assertEquals(3, history.size)
        assertEquals(a, history.previous()) // index 1
        assertEquals(a, history.previous()) // index 0
        // No earlier track remains.
        assertNull(history.previous())
    }
}
