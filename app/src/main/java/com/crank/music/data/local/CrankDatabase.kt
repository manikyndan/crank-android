package com.crank.music.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocalSongEntity::class,
        QueueItemEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        HistoryEntity::class,
        SearchHistoryEntity::class,
        PlaybackPositionEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class CrankDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        const val DATABASE_NAME = "crank_database"

        @Volatile
        private var INSTANCE: CrankDatabase? = null

        fun getInstance(context: Context): CrankDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CrankDatabase::class.java,
                    DATABASE_NAME
                )
                    // Explicit migrations replace the previous destructive
                    // fallback, which wiped playlists, likes and history on any
                    // schema change. Do NOT reintroduce fallbackToDestructiveMigration
                    // here — add a Migration instead.
                    .addMigrations(*CRANK_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
