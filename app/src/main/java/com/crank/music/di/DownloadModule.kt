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
import androidx.media3.exoplayer.scheduler.Requirements
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
        // Same browser UA as the player's data source: googlevideo rejects
        // the fetch with a 403 otherwise, so every download sat in a retry
        // loop with zero bytes forever and nothing ever completed.
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val executor = Executors.newFixedThreadPool(4)
        return DownloadManager(
            context,
            databaseProvider,
            cache,
            dataSourceFactory,
            executor
        ).apply {
            // Media3 only runs downloads when these are met, and the default
            // demands an unmetered network — on mobile data every download sat
            // queued with zero bytes forever while streaming worked fine.
            // A download here is always an explicit per-song user tap, so any
            // connected network is the honest requirement.
            requirements = Requirements(Requirements.NETWORK)

            // DownloadManager is constructed *paused*. Until it is resumed it accepts
            // addDownload() calls, writes each one to the index as STATE_QUEUED, and never
            // starts a transfer — so every download sat at "Downloading" with zero bytes
            // forever and no song ever reached the downloaded state. Nothing in the app called
            // resumeDownloads(), so the manager stayed paused for the whole process lifetime.
            //
            // Resuming here restores the default the rest of the app assumes. The user-facing
            // pause control on the Offline screen still works: it pauses this same instance at
            // runtime, and an explicit per-song tap resumes it again.
            resumeDownloads()
        }
    }

    @Provides
    @Singleton
    fun provideDownloadIndex(
        downloadManager: DownloadManager
    ): DownloadIndex {
        return downloadManager.downloadIndex
    }
}
