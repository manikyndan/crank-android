package com.crank.music.data.remote

import com.crank.music.domain.model.Album
import com.crank.music.domain.model.AlbumWithKind
import com.crank.music.domain.model.Song

/**
 * The read surface the app uses for catalogue data.
 *
 * Implemented by [YouTubeRemoteDataSource], which is what `RepositoryModule` binds.
 *
 * An earlier iTunes-backed implementation (`RemoteDataSourceImpl`, plus its `ITunes*` DTOs) lived
 * here but was never bound to anything — genuinely dead code that also swallowed
 * `CancellationException` and used `printStackTrace`, the exact anti-patterns
 * `core/CoroutineDiscipline.kt` exists to prevent. It has been removed rather than left to rot.
 */
interface RemoteDataSource {
    suspend fun searchMusic(query: String): List<Song>
    suspend fun searchAlbums(query: String): List<Album>
    suspend fun searchAlbumsWithKind(query: String): List<AlbumWithKind>
    suspend fun getHomeData(): List<Album>
}
