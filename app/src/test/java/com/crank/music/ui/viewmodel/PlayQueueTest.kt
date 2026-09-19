package com.crank.music.ui.viewmodel

import com.crank.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests for the queue model.
 *
 * Each test here corresponds to a specific way the previous three-field representation went
 * wrong. They are written against the operations rather than the fields on purpose — the point
 * of [PlayQueue] is that callers cannot reach in and mutate half of an invariant.
 */
class PlayQueueTest {

    private fun song(id: String) = Song(
        id = id,
        title = "Title $id",
        artistName = "Artist $id",
        albumId = null,
        durationMs = 180_000L,
        artworkUrl = "",
        isLocal = false,
    )

    private val a = song("a")
    private val b = song("b")
    private val c = song("c")
    private val d = song("d")

    private val album = listOf(a, b, c, d)

    /** A fixed seed makes shuffle ordering deterministic, so failures are reproducible. */
    private val seeded = Random(1234)

    @Test
    fun `fromContext excludes the current song from up next`() {
        // Note the expected order includes `a`. Starting on `b` does not discard the songs that
        // came before it — they stay queued so Previous can reach them, which is what every
        // music player does and what the old "drop(index + 1)" implementation got wrong.
        val queue = PlayQueue().fromContext(b, album, shuffle = false)
        assertEquals(listOf("a", "c", "d"), queue.upNext.map { it.id })
        assertTrue("current song must not be in up next", queue.upNext.none { it.id == "b" })
    }

    @Test
    fun `fromContext keeps every other song`() {
        val queue = PlayQueue().fromContext(a, album, shuffle = false)
        assertEquals(4 - 1, queue.upNext.size)
    }

    @Test
    fun `fromContext handles a song absent from the context`() {
        // Playing a search result that is not in the list it was started from is normal. The
        // context must survive so repeat-all still works.
        val stranger = song("zzz")
        val queue = PlayQueue().fromContext(stranger, album, shuffle = false)

        assertEquals(listOf("a", "b", "c", "d"), queue.upNext.map { it.id })
        assertEquals(album, queue.context)
    }

    @Test
    fun `fromContext records the context for repeat all`() {
        val queue = PlayQueue().fromContext(b, album, shuffle = false)
        assertEquals(album, queue.context)
    }

    @Test
    fun `fromContext de-duplicates repeated ids`() {
        val withDupe = listOf(a, b, b, c)
        val queue = PlayQueue().fromContext(a, withDupe, shuffle = false)
        assertEquals(listOf("b", "c"), queue.upNext.map { it.id })
    }

    @Test
    fun `shuffled context contains exactly the other songs`() {
        val queue = PlayQueue().fromContext(a, album, shuffle = true, random = Random(7))
        assertEquals(setOf("b", "c", "d"), queue.upNext.map { it.id }.toSet())
    }

    @Test
    fun `dropFirst advances the queue`() {
        val queue = PlayQueue().fromContext(a, album, shuffle = false)
        assertEquals(listOf("c", "d"), queue.dropFirst().upNext.map { it.id })
    }

    @Test
    fun `peekNext returns the head and null when empty`() {
        val queue = PlayQueue().fromContext(a, album, shuffle = false)
        assertEquals("b", queue.peekNext()?.id)
        assertNull(PlayQueue().peekNext())
    }

    @Test
    fun `manual additions survive a shuffle toggle`() {
        // The regression this guards: turning shuffle off rebuilt the queue from the context
        // alone, so a song added with "add to queue" vanished without explanation.
        val queued = song("manual")
        val queue = PlayQueue().fromContext(b, album, shuffle = false).addManual(queued)

        val reshuffled = queue.reshuffled(b, shuffle = true, random = seeded)

        assertTrue(
            "manually queued song must survive shuffling",
            reshuffled.upNext.any { it.id == "manual" },
        )
    }

    @Test
    fun `manual additions survive turning shuffle back off`() {
        val queued = song("manual")
        val queue = PlayQueue().fromContext(b, album, shuffle = true, random = seeded)
            .addManual(queued)

        val unshuffled = queue.reshuffled(b, shuffle = false)

        assertTrue(
            "manually queued song must survive un-shuffling",
            unshuffled.upNext.any { it.id == "manual" },
        )
    }

    @Test
    fun `manual additions are not duplicated when also in the context`() {
        // "Add to queue" on a song already in the album must not create two entries, or the
        // queue screen shows it twice and removing one leaves the other.
        val queue = PlayQueue().fromContext(a, album, shuffle = false).addManual(c)
        val reshuffled = queue.reshuffled(a, shuffle = false)

        assertEquals(1, reshuffled.upNext.count { it.id == "c" })
    }

    @Test
    fun `removing a manual addition also forgets it`() {
        // Otherwise the next shuffle toggle resurrects a song the user deleted.
        val queued = song("manual")
        val queue = PlayQueue().fromContext(b, album, shuffle = false).addManual(queued)
        val index = queue.upNext.indexOfFirst { it.id == "manual" }

        val afterRemove = queue.removeAt(index)
        val afterReshuffle = afterRemove.reshuffled(b, shuffle = true, random = seeded)

        assertTrue(afterReshuffle.upNext.none { it.id == "manual" })
    }

    @Test
    fun `removing an out of range index is a no-op`() {
        val queue = PlayQueue().fromContext(a, album, shuffle = false)
        assertEquals(queue, queue.removeAt(99))
        assertEquals(queue, queue.removeAt(-1))
    }

    @Test
    fun `move reorders without changing membership`() {
        val queue = PlayQueue().fromContext(a, album, shuffle = false)
        assertEquals(listOf("b", "c", "d"), queue.upNext.map { it.id })

        val moved = queue.move(0, 1)

        assertEquals(listOf("c", "b", "d"), moved.upNext.map { it.id })
        assertEquals(queue.upNext.map { it.id }.toSet(), moved.upNext.map { it.id }.toSet())
    }

    @Test
    fun `move keeps manual additions tracked`() {
        // Reordering is a display concern; a hand-queued song stays hand-queued wherever it sits.
        val queued = song("manual")
        val queue = PlayQueue().fromContext(a, album, shuffle = false).addManual(queued)
        val moved = queue.move(0, 1)

        assertEquals(1, moved.manualAdditions.size)
    }

    @Test
    fun `move with an out of range index is a no-op`() {
        val queue = PlayQueue().fromContext(a, album, shuffle = false)
        assertEquals(queue, queue.move(0, 99))
    }

    @Test
    fun `forRepeatAll rebuilds from context rather than the drained queue`() {
        // The bug: repeat-all restored `_queue.value = originalQueue`, which excluded the track
        // that was playing, so the first wrap replayed the *second* song onwards.
        val queue = PlayQueue().fromContext(c, album, shuffle = false)
        val drained = PlayQueue(upNext = emptyList(), context = queue.context)

        val wrapped = drained.forRepeatAll(c, shuffle = false)

        assertEquals(setOf("a", "b", "d"), wrapped?.upNext?.map { it.id }?.toSet())
    }

    @Test
    fun `forRepeatAll returns null without a context`() {
        // A single track played with no context must stop, not loop silently.
        assertNull(PlayQueue().forRepeatAll(a, shuffle = false))
    }

    @Test
    fun `forRepeatAll returns null for an empty context`() {
        val queue = PlayQueue(upNext = listOf(a), context = emptyList())
        assertNull(queue.forRepeatAll(a, shuffle = false))
    }

    @Test
    fun `reshuffled preserves the upcoming list when there is no context`() {
        // No context to reorder from. Clearing the queue here would look like data loss.
        val queue = PlayQueue(upNext = listOf(a, b), context = null)
        assertEquals(listOf("a", "b"), queue.reshuffled(a, shuffle = true).upNext.map { it.id })
    }

    @Test
    fun `restored rebuilds a queue from persisted songs`() {
        val queue = PlayQueue().restored(listOf(a, b))
        assertEquals(listOf("a", "b"), queue.upNext.map { it.id })
    }

    @Test
    fun `restored uses the persisted list as the repeat source`() {
        // Context is not persisted, so repeat-all falls back to the restored list rather than
        // inventing an order the user never saw.
        val queue = PlayQueue().restored(listOf(a, b))
        assertEquals(listOf(a, b), queue.context)
    }

    @Test
    fun `restored with no songs has no context`() {
        assertNull(PlayQueue().restored(emptyList()).context)
    }

    @Test
    fun `cleared empties everything including manual additions`() {
        val queue = PlayQueue().fromContext(a, album, shuffle = false).addManual(song("m"))
        val cleared = queue.cleared()

        assertTrue(cleared.upNext.isEmpty())
        assertTrue(cleared.manualAdditions.isEmpty())
        assertNull(cleared.context)
    }

    @Test
    fun `persistable songs are capped`() {
        val huge = (1..(PlayQueue.MAX_PERSISTED_SIZE + 50)).map { song("s$it") }
        val queue = PlayQueue(upNext = huge)

        assertEquals(PlayQueue.MAX_PERSISTED_SIZE, PlayQueue.persistableSongs(queue).size)
    }

    @Test
    fun `shuffling the same seed reproduces the same order`() {
        // Guards against a shuffle implementation that ignores its Random, which would make the
        // ordering tests above pass by accident.
        val first = PlayQueue().fromContext(a, album, shuffle = true, random = Random(99))
        val second = PlayQueue().fromContext(a, album, shuffle = true, random = Random(99))

        assertEquals(first.upNext.map { it.id }, second.upNext.map { it.id })
    }
}
