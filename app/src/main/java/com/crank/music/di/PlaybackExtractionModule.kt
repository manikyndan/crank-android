package com.crank.music.di

import android.content.Context
import com.crank.music.data.local.SongDao
import com.crank.music.data.remote.SessionProvider
import com.crank.music.data.remote.VisitorDataProvider
import com.crank.music.data.remote.innertube.InnerTubePlayerApi
import com.crank.music.data.remote.innertube.StreamCascadeResolver
import com.crank.music.data.remote.potoken.PoTokenGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Wiring for the playback extraction layer.
 *
 * ## Why PoTokenGenerator is application-scoped and not request-scoped
 *
 * It owns a live WebView plus a BotGuard integrity token. Both are expensive — several seconds
 * of cold start — and both are *stateful across calls*: the session-bound token must be minted
 * once on a fresh minter before any player token. Creating this per resolve would pay the cost
 * every track and break that ordering.
 *
 * It holds an application context on purpose, because it must outlive any screen. That is safe:
 * the WebView is explicitly created and destroyed by this class, and it never holds an Activity.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackExtractionModule {

    @Provides
    @Singleton
    fun providePoTokenGenerator(
        @ApplicationContext context: Context,
    ): PoTokenGenerator = PoTokenGenerator(context)

    @Provides
    @Singleton
    fun provideInnerTubePlayerApi(
        client: HttpClient,
        json: Json,
    ): InnerTubePlayerApi = InnerTubePlayerApi(client, json)

    @Provides
    @Singleton
    fun provideStreamCascadeResolver(
        playerApi: InnerTubePlayerApi,
        poTokenGenerator: PoTokenGenerator,
    ): StreamCascadeResolver = StreamCascadeResolver(playerApi, poTokenGenerator)

    /**
     * The visitor-data source, backed by `SessionProvider`.
     *
     * This is load-bearing for playback quality, not a detail: the clients that can serve a
     * whole audio file require a visitor id. Without one the cascade degrades to the
     * preview-length iOS clients, and playback dies around 60-90 seconds in.
     */
    @Provides
    @Singleton
    fun provideSessionProvider(
        songDao: SongDao,
        client: HttpClient,
        json: Json,
    ): SessionProvider = SessionProvider(songDao, client, json)

    @Provides
    @Singleton
    fun provideVisitorDataProvider(sessionProvider: SessionProvider): VisitorDataProvider =
        sessionProvider
}
