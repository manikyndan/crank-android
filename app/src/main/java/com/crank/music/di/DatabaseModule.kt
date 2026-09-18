package com.crank.music.di

import android.content.Context
import com.crank.music.data.local.CrankDatabase
import com.crank.music.data.local.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCrankDatabase(
        @ApplicationContext context: Context
    ): CrankDatabase {
        return CrankDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideSongDao(database: CrankDatabase): SongDao {
        return database.songDao()
    }
}
