package com.crank.music.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideDatabaseProvider(
        @ApplicationContext context: Context
    ): DatabaseProvider {
        return StandaloneDatabaseProvider(context)
    }

    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider
    ): Cache {
        val downloadDirectory = File(context.getExternalFilesDir(null) ?: context.filesDir, "crank_downloads")
        return SimpleCache(
            downloadDirectory,
            LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024L),
            databaseProvider
        )
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        cache: Cache
    ): DownloadManager {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
        val executor = Executors.newFixedThreadPool(4)
        return DownloadManager(
            context,
            databaseProvider,
            cache,
            dataSourceFactory,
            executor
        )
    }

    @Provides
    @Singleton
    fun provideDownloadIndex(
        downloadManager: DownloadManager
    ): DownloadIndex {
        return downloadManager.downloadIndex
    }
}
