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
 * It is deliberately minimal — no LRU bookkeeping, just TTL expiry plus a hard entry cap, because a
 * TTL alone bounds *age* and not *size*: this cache is keyed on the raw user query text, so a long
 * search session accumulated one `List<Song>` entry per distinct string ever typed and none of them
 * were ever read again. And it is a pure class (time comes from [clock]) so it can be unit-tested
 * without Android or a wall clock.
 *
 * Nothing here ever fabricates data: it only stores and returns values the caller already produced
 * from a real source.
 */
class TtlCache<V>(
    private val ttlMillis: Long,
    /** Hard cap on stored entries. The oldest entry is dropped when a new one would exceed it. */
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
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

    /**
     * Stores [value] under [key], stamped with the current clock reading.
     *
     * When the cache is full the oldest entry is evicted first. Concurrent puts can briefly race past
     * the cap — the check and the insert are not one atomic step — which is acceptable here: the
     * bound exists to stop unbounded growth over a long session, not to be an exact ceiling.
     */
    fun put(key: String, value: V) {
        if (store.size >= maxEntries && !store.containsKey(key)) {
            store.minByOrNull { it.value.storedAt }?.let { store.remove(it.key) }
        }
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

    /** Number of stored entries. Exposed for tests and for the bound above to be verifiable. */
    val size: Int get() = store.size

    companion object {
        /**
         * Enough for a long search session without holding the whole dictionary.
         *
         * Each entry is a parsed `List<Song>`, so a few hundred is a few megabytes at worst and well
         * within what the home feed and typing-ahead actually reuse.
         */
        const val DEFAULT_MAX_ENTRIES = 256

        /**
         * Canonical form of a user-supplied key.
         *
         * Search terms are cached by their raw text, so `"Beatles"`, `"beatles"` and `" beatles "`
         * were three separate entries holding identical results, and each one consumed part of the
         * budget above. Callers key on this instead of the raw query.
         */
        fun normalizeKey(raw: String): String = raw.trim().lowercase()
    }
}
