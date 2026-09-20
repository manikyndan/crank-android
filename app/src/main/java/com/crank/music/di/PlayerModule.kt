package com.crank.music.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.crank.music.MainActivity
import com.crank.music.service.CrankSessionCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }

    @Provides
    @Singleton
    fun provideHttpDataSourceFactory(): HttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
    }

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        httpDataSourceFactory: HttpDataSource.Factory,
        downloadCache: Cache
    ): ExoPlayer {
        // Read completed downloads from the shared download cache so tapped
        // downloads play offline. The null write sink means playback never
        // writes to the cache — streaming behavior is otherwise unchanged.
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setCacheWriteDataSinkFactory(null)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(mediaSourceFactory)
            // Hold a partial wake lock for as long as the player is playing *or* buffering.
            //
            // This is the fix for playback dying when the screen turns off. ExoPlayer defaults to
            // WAKE_MODE_NONE, so the moment the screen went dark the CPU was free to suspend: the
            // foreground service kept the process alive, but in-flight socket reads (and the DNS
            // lookup of the CDN host behind them) were frozen mid-request and surfaced as
            // "UnknownHostException". WAKE_MODE_NETWORK keeps the CPU up exactly while there is
            // network work to do, and releases it otherwise — it does not pin the device awake
            // while merely paused.
            //
            // Needs android.permission.WAKE_LOCK, declared in the manifest.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    @Provides
    @Singleton
    fun provideMediaSession(
        @ApplicationContext context: Context,
        player: ExoPlayer
    ): MediaSession {
        // Tapping the notification or the lock-screen player opens the app rather than doing
        // nothing. Without a session activity, Media3 posts the notification with no content
        // intent and the panel feels dead when tapped.
        //
        // SINGLE_TOP + CLEAR_TOP so an already-running app is brought forward instead of being
        // recreated — a second instance would fight the first over the single MediaSession.
        val sessionActivity = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return MediaSession.Builder(context, player)
            .setCallback(CrankSessionCallback)
            .setSessionActivity(sessionActivity)
            .build()
    }
}
