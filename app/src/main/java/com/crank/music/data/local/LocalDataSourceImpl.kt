package com.crank.music.data.local

import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.LocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalDataSourceImpl @Inject constructor(
    private val songDao: SongDao
) : LocalDataSource {

    override fun getLikedSongs(): Flow<List<Song>> {
        return songDao.getLikedSongs().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun toggleLikeSong(song: Song) {
        val existingSong = songDao.getSongById(song.id)
        if (existingSong != null) {
            val updated = existingSong.copy(isLiked = !existingSong.isLiked)
            songDao.updateSong(updated)
        } else {
            val newEntity = song.toEntity(isLiked = true)
            songDao.insertSong(newEntity)
        }
    }

    override suspend fun getDownloadedSongs(): List<Song> {
        return songDao.getDownloadedSongs().map { it.toDomainModel() }
    }
}
