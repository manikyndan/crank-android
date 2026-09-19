package com.crank.music.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ═══════════════════════════════════════════════════════════════
// CRANK DATABASE MIGRATIONS
//
// Replaces the previous `fallbackToDestructiveMigration(true)`, which
// silently dropped every table on any schema change — taking playlists,
// likes and playback history with it.
//
// Each migration lists its columns explicitly rather than using
// `SELECT *`, so the copy stays correct even if the column order in the
// entity is changed later. `INSERT OR REPLACE` keeps a duplicate row from
// aborting an upgrade halfway through.
// ═══════════════════════════════════════════════════════════════

/**
 * v1 → v2
 *
 * Adds `playback_position`, a single-row table holding the last played track
 * plus its queue snapshot. Introduced so playback could resume across launches.
 *
 * Purely additive — no existing table is touched, so all user data survives.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playback_position` (
                `id` INTEGER NOT NULL,
                `songId` TEXT NOT NULL,
                `songTitle` TEXT NOT NULL,
                `songArtist` TEXT NOT NULL,
                `songArtwork` TEXT NOT NULL,
                `songAlbumId` TEXT,
                `songDurationMs` INTEGER NOT NULL,
                `songStreamUrl` TEXT NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `queueJson` TEXT NOT NULL,
                `repeatMode` INTEGER NOT NULL,
                `shuffleEnabled` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v2 → v3
 *
 * Adds `streamUrl` to `songs`, `queue_items` and `playback_history`.
 *
 * This is the column that lets a downloaded track be played back from its
 * resolved media URL rather than re-resolved from a video id on every play.
 * Existing rows default to an empty string, which the resolver already treats
 * as "not yet resolved" — so the older fallback path still works for them.
 *
 * Also adds `isLiked` and `dateAdded` to `songs`, so the library can show a
 * liked-songs list ordered by when each track was saved.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `streamUrl` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `isLiked` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `dateAdded` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `queue_items` ADD COLUMN `streamUrl` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `playback_history` ADD COLUMN `streamUrl` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v3 → v4
 *
 * Introduces the playlist tables. `playlists` stores the collection itself and
 * `playlist_song_cross_ref` is the join table, keyed on (playlistId, songId)
 * so a track can only appear once per playlist.
 *
 * Purely additive. Both tables start empty; there is nothing to backfill since
 * no playlist concept existed before this version.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlists` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `songCount` INTEGER NOT NULL,
                `artworkUrl` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playlist_song_cross_ref` (
                `playlistId` TEXT NOT NULL,
                `songId` TEXT NOT NULL,
                `songOrder` INTEGER NOT NULL,
                PRIMARY KEY(`playlistId`, `songId`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v4 → v5
 *
 * Adds `session_values`, a key/value table for session-scoped strings.
 *
 * First user is the YouTube visitor id, which the stream cascade needs and which must survive
 * restarts. Stored here rather than in a new DataStore dependency so the app keeps exactly one
 * persistence mechanism and one thing for "clear app data" to remove.
 *
 * Purely additive; no existing table is touched.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `session_values` (
                `key` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`key`)
            )
            """.trimIndent()
        )
    }
}

/** Every migration the database understands, in ascending order. */
val CRANK_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5
)
