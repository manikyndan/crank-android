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
}
