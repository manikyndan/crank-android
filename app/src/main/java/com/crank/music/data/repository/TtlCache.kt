package com.crank.music.data.repository

import java.util.concurrent.ConcurrentHashMap

/**
 * A tiny, thread-safe in-memory cache with per-entry time-to-live.
 *
 * Why this exists: transient network failures were blanking the home feed and re-hitting the
 * network for the same repeated query (the home feed on every tab entry, the same search term
 * while the user is still typing). Two small, honest behaviours fix that:
 *  - a *fresh* entry is served without touching the network; and
 *  - a *stale* entry (just past TTL) is served rather than an empty screen when the network is
 *    down, within a bounded grace window.
 *
 * It is deliberately minimal — no LRU/eviction beyond TTL expiry, because the key space here is
 * small and bounded (one key per distinct search term, one key for the home feed). And it is a
 * pure class (time comes from [clock]) so it can be unit-tested without Android or a wall clock.
 *
 * Nothing here ever fabricates data: it only stores and returns values the caller already produced
 * from a real source.
 */
class TtlCache<V>(
    private val ttlMillis: Long,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private data class Entry<V>(val value: V, val storedAt: Long)

    // Stale entries may be served for up to this multiple of the TTL before being dropped entirely.
    private val staleGraceFactor = 4L

    private val store = ConcurrentHashMap<String, Entry<V>>()

    /**
     * Returns the cached value when it is still within TTL, or — if [allowStale] is true — when it
     * is within the stale grace window. Expired-beyond-grace entries are removed and `null` is
     * returned.
     */
    fun get(key: String, allowStale: Boolean = false): V? {
        val entry = store[key] ?: return null
        val age = clock() - entry.storedAt
        return when {
            age <= ttlMillis -> entry.value
            allowStale && age <= ttlMillis * staleGraceFactor -> entry.value
            else -> {
                store.remove(key)
                null
            }
        }
    }

    /** Stores [value] under [key], stamped with the current clock reading. */
    fun put(key: String, value: V) {
        store[key] = Entry(value, storedAt = clock())
    }

    /** True when [key] holds a value that has not yet expired (used for cheap "is cached?" checks). */
    fun isFresh(key: String): Boolean = get(key) != null

    /** Drops a single entry (e.g. to force a refresh on the next call). */
    fun invalidate(key: String) {
        store.remove(key)
    }

    /** Drops every entry. */
    fun clear() {
        store.clear()
    }
}
