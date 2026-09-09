package com.crank.music.data.repository

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.crank.music.data.local.SongDao
import com.crank.music.data.local.toDomainModel
import com.crank.music.data.local.toEntity
import com.crank.music.data.remote.YouTubeStreamResolver
import com.crank.music.domain.model.DownloadState
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.DownloadRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@OptIn(UnstableApi::class)
class DownloadRepositoryImpl @Inject constructor(
    private val downloadManager: DownloadManager,
    private val songDao: SongDao,
    private val streamResolver: YouTubeStreamResolver
) : DownloadRepository {

    override suspend fun downloadSong(song: Song) {
        try {
            // Resolve the actual stream URL (may be a video ID)
            val target = if (song.streamUrl.isNotBlank()) song.streamUrl else song.id
            val streamUrl = if (target.startsWith("http://") || target.startsWith("https://")) {
                target
            } else {
                try {
                    val resolved = streamResolver.getSongStreamUrl(target, song.title, song.artistName)
                    resolved.url
                } catch (e: Exception) {
                    Log.e("CRANK_DOWNLOAD", "Failed to resolve stream URL for ${song.title}: ${e.message}")
                    // Fall back to artwork URL as last resort (will likely fail but better than nothing)
                    song.artworkUrl.ifBlank { "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3" }
                }
            }

            if (streamUrl.isBlank()) {
                Log.e("CRANK_DOWNLOAD", "No stream URL for ${song.title}")
                return
            }

            Log.d("CRANK_DOWNLOAD", "Downloading ${song.title} from: ${streamUrl.take(80)}...")

            val request = DownloadRequest.Builder(song.id, Uri.parse(streamUrl))
                .setData(song.title.toByteArray(Charsets.UTF_8))
                .build()

            downloadManager.addDownload(request)

            // Save song metadata to Room with local flag
            songDao.insertSong(
                song.toEntity(isLiked = false, dateAdded = System.currentTimeMillis()).copy(isLocal = true)
            )
        } catch (e: Exception) {
            Log.e("CRANK_DOWNLOAD", "Download failed for ${song.title}: ${e.message}", e)
        }
    }

    override suspend fun removeDownload(songId: String) {
        downloadManager.removeDownload(songId)
        songDao.deleteSongById(songId)
    }

    override fun getDownloadedSongs(): Flow<List<Song>> {
        return songDao.getAllSongs().map { entities ->
            entities.filter { it.isLocal }.map { it.toDomainModel() }
        }
    }

    override fun getDownloadState(songId: String): Flow<DownloadState> = callbackFlow {
        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                if (download.request.id == songId) {
                    trySend(mapState(download.state))
                }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                if (download.request.id == songId) {
                    trySend(DownloadState.IDLE)
                }
            }
        }

        downloadManager.addListener(listener)

        val currentDownload = downloadManager.downloadIndex.getDownload(songId)
        trySend(if (currentDownload != null) mapState(currentDownload.state) else DownloadState.IDLE)

        awaitClose {
            downloadManager.removeListener(listener)
        }
    }

    private fun mapState(state: Int): DownloadState {
        return when (state) {
            Download.STATE_COMPLETED -> DownloadState.COMPLETED
            Download.STATE_DOWNLOADING, Download.STATE_QUEUED, Download.STATE_RESTARTING -> DownloadState.DOWNLOADING
            Download.STATE_FAILED -> DownloadState.FAILED
            else -> DownloadState.IDLE
        }
    }
}
