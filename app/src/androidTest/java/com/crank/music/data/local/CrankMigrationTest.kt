package com.crank.music.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies every [Migration] against the exported schema snapshots in
 * `app/schemas`. These run on a device/emulator because Room's migration
 * machinery needs a real SQLite implementation.
 *
 * The point of these tests is not just "does the SQL execute" — it is that user
 * data written under the old schema is still readable afterwards. The previous
 * `fallbackToDestructiveMigration(true)` would have passed a naive
 * "does it open" check while silently deleting every row, so each test below
 * seeds data first and asserts it survives.
 */
@RunWith(AndroidJUnit4::class)
class CrankMigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CrankDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesSongsAndAddsPlaybackPosition() {
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO `songs`
                    (`id`, `title`, `artistName`, `albumId`, `durationMs`,
                     `artworkUrl`, `isLocal`)
                VALUES
                    ('song-1', 'Midnight City', 'M83', 'album-1', 243000,
                     'https://example.test/art.jpg', 1)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2)

        // The pre-existing song must still be there.
        db.query("SELECT `title` FROM `songs` WHERE `id` = 'song-1'").use { cursor ->
            assertTrue("song row survived migration", cursor.moveToFirst())
            assertEquals("Midnight City", cursor.getString(0))
        }

        // And the new table must exist and be usable.
        db.execSQL(
            """
            INSERT INTO `playback_position`
                (`id`, `songId`, `songTitle`, `songArtist`, `songArtwork`,
                 `songAlbumId`, `songDurationMs`, `songStreamUrl`, `positionMs`,
                 `queueJson`, `repeatMode`, `shuffleEnabled`)
            VALUES
                (1, 'song-1', 'Midnight City', 'M83', 'https://example.test/art.jpg',
                 'album-1', 243000, 'https://example.test/stream', 42000, '', 0, 0)
            """.trimIndent()
        )
        db.query("SELECT COUNT(*) FROM `playback_position`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun migrate2To3_addsStreamUrlAndLikeColumnsWithSafeDefaults() {
        helper.createDatabase(testDb, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO `songs`
                    (`id`, `title`, `artistName`, `albumId`, `durationMs`,
                     `artworkUrl`, `isLocal`)
                VALUES
                    ('song-2', 'Genesis', 'Grimes', NULL, 195000,
                     'https://example.test/genesis.jpg', 0)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_2_3)

        // Existing row survives, with the new columns populated by their defaults
        // rather than left null (which would crash the non-null Kotlin fields).
        db.query(
            "SELECT `title`, `streamUrl`, `isLiked`, `dateAdded` FROM `songs` WHERE `id` = 'song-2'"
        ).use { cursor ->
            assertTrue("song row survived migration", cursor.moveToFirst())
            assertEquals("Genesis", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(0L, cursor.getLong(3))
        }

        // The new NOT NULL column must accept a real value and be readable back.
        db.execSQL("UPDATE `songs` SET `streamUrl` = 'https://example.test/s', `isLiked` = 1 WHERE `id` = 'song-2'")
        db.query("SELECT `streamUrl`, `isLiked` FROM `songs` WHERE `id` = 'song-2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("https://example.test/s", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
    }

    @Test
    fun migrate3To4_createsPlaylistTablesWithoutTouchingExistingData() {
        helper.createDatabase(testDb, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO `songs`
                    (`id`, `title`, `artistName`, `albumId`, `durationMs`,
                     `artworkUrl`, `isLocal`, `streamUrl`, `isLiked`, `dateAdded`)
                VALUES
                    ('song-3', 'Oblivion', 'Grimes', NULL, 245000,
                     'https://example.test/oblivion.jpg', 0, '', 1, 1700000000000)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 4, true, MIGRATION_3_4)

        db.query("SELECT `title`, `isLiked` FROM `songs` WHERE `id` = 'song-3'").use { cursor ->
            assertTrue("liked song survived migration", cursor.moveToFirst())
            assertEquals("Oblivion", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }

        // Playlist tables are usable and the composite primary key works.
        db.execSQL(
            "INSERT INTO `playlists` (`id`, `title`, `songCount`, `artworkUrl`, `createdAt`) " +
                "VALUES ('pl-1', 'Late Night', 1, 'https://example.test/pl.jpg', 1700000000000)"
        )
        db.execSQL(
            "INSERT INTO `playlist_song_cross_ref` (`playlistId`, `songId`, `songOrder`) " +
                "VALUES ('pl-1', 'song-3', 0)"
        )
        db.query("SELECT COUNT(*) FROM `playlist_song_cross_ref` WHERE `playlistId` = 'pl-1'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
    }

    @Test
    fun migrateAll_chainFrom1To4_preservesEverything() {
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO `songs`
                    (`id`, `title`, `artistName`, `albumId`, `durationMs`,
                     `artworkUrl`, `isLocal`)
                VALUES
                    ('song-legacy', 'Legacy Track', 'Legacy Artist', NULL, 180000,
                     'https://example.test/legacy.jpg', 1)
                """.trimIndent()
            )
        }

        // Run the whole chain at once, the way a user skipping three releases would.
        val db = helper.runMigrationsAndValidate(
            testDb, 4, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
        )

        db.query("SELECT `title`, `isLocal` FROM `songs` WHERE `id` = 'song-legacy'").use { cursor ->
            assertTrue("legacy download survived full chain", cursor.moveToFirst())
            assertEquals("Legacy Track", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
    }

    @Test
    fun openHelper_migratesSeededDatabaseEndToEnd() {
        // Exercise the real production path: an on-disk db at v1, opened through
        // Room with the same migration array the app ships.
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO `songs`
                    (`id`, `title`, `artistName`, `albumId`, `durationMs`,
                     `artworkUrl`, `isLocal`)
                VALUES
                    ('song-e2e', 'End To End', 'Tester', NULL, 100000,
                     'https://example.test/e2e.jpg', 0)
                """.trimIndent()
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, CrankDatabase::class.java, testDb)
            .addMigrations(*CRANK_MIGRATIONS)
            .build()

        try {
            // Touching the dao forces the open + migration to actually run.
            // getSongById is suspend, so bridge from the synchronous test method.
            val song = runBlocking { db.songDao().getSongById("song-e2e") }
            assertTrue("song reachable through production DAO", song != null)
            assertEquals("End To End", song!!.title)
        } finally {
            db.close()
        }
    }
}
