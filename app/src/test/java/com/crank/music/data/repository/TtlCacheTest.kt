package com.crank.music.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TtlCache is a pure class (time injected via [TtlCache.clock]) so these tests use a manual clock
 * to move time forward deterministically — no real sleeping, no Android.
 */
class TtlCacheTest {

    private var now = 0L
    private fun cache(ttl: Long) = TtlCache<Int>(ttlMillis = ttl, clock = { now })

    @Test
    fun `miss when empty`() {
        val cache = cache(ttl = 1000)
        assertNull(cache.get("missing"))
    }

    @Test
    fun `fresh entry is returned`() {
        val cache = cache(ttl = 1000)
        cache.put("k", 42)
        assertEquals(42, cache.get("k"))
        assertTrue(cache.isFresh("k"))
    }

    @Test
    fun `entry expires after ttl`() {
        val cache = cache(ttl = 1000)
        cache.put("k", 42)
        now += 1000 // exactly at the boundary — still fresh
        assertEquals(42, cache.get("k"))
        now += 1 // just past TTL
        assertNull(cache.get("k"))
        assertFalse(cache.isFresh("k"))
    }

    @Test
    fun `stale entry served only within grace window`() {
        val cache = cache(ttl = 1000) // grace window = 4000ms
        cache.put("k", 7)

        now += 1500 // past TTL, inside grace
        assertEquals(7, cache.get("k", allowStale = true))
        assertNull("fresh-only get must not return a stale entry", cache.get("k"))

        now += 3000 // 1500 + 3000 = 4500ms, beyond the 4000ms grace
        assertNull(cache.get("k", allowStale = true))
    }

    @Test
    fun `invalidate removes an entry`() {
        val cache = cache(ttl = 1000)
        cache.put("k", 1)
        cache.invalidate("k")
        assertNull(cache.get("k"))
    }

    @Test
    fun `clear empties the cache`() {
        val cache = cache(ttl = 1000)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    @Test
    fun `distinct keys are independent`() {
        val cache = cache(ttl = 1000)
        cache.put("a", 1)
        cache.put("b", 2)
        now += 2000 // both expired
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }

    // ── Entry bound ──────────────────────────────────────────────────────────────

    @Test
    fun `cache does not grow past its entry cap`() {
        // A TTL bounds age, not size. Without the cap, one entry per distinct search string ever
        // typed accumulated for the life of the process.
        val cache = TtlCache<Int>(ttlMillis = 60_000, maxEntries = 10, clock = { now })

        repeat(100) { index ->
            cache.put("key$index", index)
            now += 1
        }

        assertEquals(10, cache.size)
    }

    @Test
    fun `overflow evicts oldest entry, keeping recent ones`() {
        val cache = TtlCache<Int>(ttlMillis = 60_000, maxEntries = 3, clock = { now })

        cache.put("oldest", 1)
        now += 10
        cache.put("middle", 2)
        now += 10
        cache.put("newest", 3)

        // This put must evict "oldest" — the least recently stored — and nothing else.
        now += 10
        cache.put("fresh", 4)

        assertEquals(3, cache.size)
        assertNull("the oldest entry should have been evicted", cache.get("oldest"))
        assertEquals(2, cache.get("middle"))
        assertEquals(3, cache.get("newest"))
        assertEquals(4, cache.get("fresh"))
    }

    @Test
    fun `rewriting an existing key does not evict anything`() {
        // The guard is `size >= maxEntries && !containsKey(key)`: refreshing a key that is already
        // present must not push a different entry out.
        val cache = TtlCache<Int>(ttlMillis = 60_000, maxEntries = 2, clock = { now })

        cache.put("a", 1)
        now += 10
        cache.put("b", 2)
        now += 10

        cache.put("a", 99) // update in place, cache is already full

        assertEquals(2, cache.size)
        assertEquals(99, cache.get("a"))
        assertEquals(2, cache.get("b"))
    }

    @Test
    fun `expired entries are removed on read, so they do not count against the cap`() {
        val cache = TtlCache<Int>(ttlMillis = 100, maxEntries = 2, clock = { now })

        cache.put("a", 1)
        now += 1_000 // "a" is far past its TTL and its stale grace window

        assertNull(cache.get("a"))
        assertEquals("the dead entry should have been dropped", 0, cache.size)
    }

    // ── Key normalisation ────────────────────────────────────────────────────────

    @Test
    fun `normalizeKey lowercases and trims`() {
        assertEquals("beatles", TtlCache.normalizeKey("  Beatles  "))
        assertEquals("the beatles", TtlCache.normalizeKey("The Beatles"))
        assertEquals("", TtlCache.normalizeKey("   "))
    }

    @Test
    fun `differently cased spellings of one query share a single entry`() {
        // Before normalisation these were three separate List<Song> entries for one user intent.
        val cache = TtlCache<Int>(ttlMillis = 60_000, maxEntries = 8, clock = { now })

        listOf("Beatles", "beatles", "  BEATLES ").forEach { raw ->
            cache.put(TtlCache.normalizeKey(raw), 1)
        }

        assertEquals(1, cache.size)
    }

    @Test
    fun `normalizeKey leaves internal punctuation alone`() {
        // Only case and surrounding whitespace are canonicalised; "ac/dc" and "ac dc" are genuinely
        // different searches and must not be folded together.
        assertEquals("ac/dc", TtlCache.normalizeKey(" AC/DC "))
        assertEquals("don't stop", TtlCache.normalizeKey("Don't Stop"))
    }
}
