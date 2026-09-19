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
import com.crank.music.data.remote.StreamResolver
import com.crank.music.domain.model.DownloadProgress
import com.crank.music.domain.model.DownloadState
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.DownloadRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
class DownloadRepositoryImpl @Inject constructor(
    private val downloadManager: DownloadManager,
    private val songDao: SongDao,
    private val streamResolver: StreamResolver
) : DownloadRepository {

    override suspend fun downloadSong(song: Song) {
        try {
            // A recognised-but-unsourced track has no audio behind it at all.
            // Resolving its marker as a video ID would burn the whole fallback
            // chain, and the catch below would then substitute a stock sample
            // track — silently saving unrelated audio to the user's library.
            if (!song.isPlayable) {
                Log.e("CRANK_DOWNLOAD", "Refusing to download '${song.title}': no audio source")
                return
            }

            // Resolve the actual stream URL (may be a video ID). A YouTube URL
            // that has passed its `expire` instant is re-resolved rather than
            // handed to the download manager, which would otherwise fetch a 403
            // and fail the download some seconds later with no explanation.
            val target = if (song.streamUrl.isNotBlank()) song.streamUrl else song.id
            val streamUrl = if (StreamResolver.isDirectlyPlayable(target)) {
                target
            } else {
                if (target.startsWith("http")) {
                    Log.d(
                        "CRANK_DOWNLOAD",
                        "Stale stream URL for ${song.title}; re-resolving ${song.id}"
                    )
                }
                try {
                    streamResolver.resolveStreamUrl(song.id, song.title, song.artistName).url
                } catch (e: Exception) {
                    // Previously this fell back to `song.artworkUrl`, or failing
                    // that to a hardcoded soundhelix.com demo MP3. Downloading the
                    // artwork URL as audio cannot succeed, and the demo MP3 meant a
                    // failed lookup silently planted an unrelated song in the
                    // library. A failed download should just be a failed download.
                    Log.e("CRANK_DOWNLOAD", "Failed to resolve stream URL for ${song.title}: ${e.message}")
                    return
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

    override fun getActiveDownloads(): Flow<List<DownloadProgress>> = callbackFlow {
        suspend fun snapshot() {
            val items = mutableListOf<DownloadProgress>()
            try {
                // `DownloadCursor` is a forward-only reader over the download index. It is
                // Closeable, so it is always closed — an open cursor holds a database
                // connection and Media3 will eventually fail new writes.
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        val songId = download.request.id
                        val metadata = try {
                            songDao.getSongById(songId)
                        } catch (e: Exception) {
                            null
                        }

                        items += DownloadProgress(
                            songId = songId,
                            title = metadata?.title
                                ?: download.request.data?.toString(Charsets.UTF_8).orEmpty(),
                            artistName = metadata?.artistName.orEmpty(),
                            artworkUrl = metadata?.artworkUrl.orEmpty(),
                            state = mapState(download.state),
                            downloadedBytes = download.bytesDownloaded,
                            totalBytes = download.contentLength,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("CRANK_DOWNLOAD", "Failed to read download index: ${e.message}")
            }

            trySend(items)
        }

        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                launch { snapshot() }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                launch { snapshot() }
            }
        }

        downloadManager.addListener(listener)
        snapshot()

        awaitClose {
            downloadManager.removeListener(listener)
        }
    }

    /**
     * Media3 1.4.1 has no per-download pause: `DownloadManager` exposes only global
     * [DownloadManager.pauseDownloads] / [DownloadManager.resumeDownloads]. The repository
     * interface therefore mirrors that, and the UI shows the control as global rather than
     * pretending it applies to a single row. Previously these were per-id methods that only
     * edited a local list and never touched the download at all.
     */
    override suspend fun pauseAllDownloads() {
        downloadManager.pauseDownloads()
    }

    override suspend fun resumeAllDownloads() {
        downloadManager.resumeDownloads()
    }

    override fun downloadsArePaused(): Boolean = downloadManager.downloadsPaused

    override suspend fun retryDownload(songId: String) {
        // A failed download is re-queued by removing and re-adding it, because Media3 keeps
        // the terminal FAILED state in the index otherwise. The original stream URL is not
        // cached here, so the caller re-issues downloadSong with the Song model.
        downloadManager.removeDownload(songId)
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
