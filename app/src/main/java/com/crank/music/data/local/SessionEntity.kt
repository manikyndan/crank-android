package com.crank.music.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A long-lived value that belongs to the session rather than to the library.
 *
 * ## Why this exists
 *
 * The stream cascade needs a YouTube *visitor id*, which is a single opaque string that must
 * survive app restarts. The obvious storage choices were a new DataStore dependency or a
 * preferences table.
 *
 * Store it in Room, because the app already owns a database with a real migration chain, and
 * adding a second persistence mechanism for one string would mean two stores to keep consistent,
 * two things to wipe when the user clears data, and a new dependency for no benefit. A generic
 * key/value table also covers the next value of this kind without another migration.
 *
 * Deliberately **not** a `SharedPreferences` file: those are written on the caller's thread,
 * commit synchronously by default, and are a common source of jank. Room gives us a suspend
 * write and a transactional read.
 */
@Entity(tableName = "session_values")
data class SessionEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    /** When this value was last written, so a stale entry can be aged out later. */
    val updatedAt: Long = System.currentTimeMillis(),
)
