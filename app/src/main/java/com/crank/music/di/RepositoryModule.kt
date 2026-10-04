package com.crank.music.di

import com.crank.music.data.local.LocalDataSourceImpl
import com.crank.music.data.remote.RemoteDataSource
import com.crank.music.data.remote.RemoteSourceRouter
import com.crank.music.data.repository.DownloadRepositoryImpl
import com.crank.music.data.repository.MusicRepositoryImpl
import com.crank.music.domain.repository.DownloadRepository
import com.crank.music.domain.repository.LocalDataSource
import com.crank.music.domain.repository.MusicRepository
import com.crank.music.feature.recognition.MusicRecognitionRepository
import com.crank.music.feature.recognition.MusicRecognitionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(
        musicRepositoryImpl: MusicRepositoryImpl
    ): MusicRepository

    @Binds
    @Singleton
    abstract fun bindLocalDataSource(
        localDataSourceImpl: LocalDataSourceImpl
    ): LocalDataSource

    @Binds
    @Singleton
    abstract fun bindRemoteDataSource(
        router: RemoteSourceRouter
    ): RemoteDataSource

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        downloadRepositoryImpl: DownloadRepositoryImpl
    ): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindMusicRecognitionRepository(
        musicRecognitionRepositoryImpl: MusicRecognitionRepositoryImpl
    ): MusicRecognitionRepository
}
