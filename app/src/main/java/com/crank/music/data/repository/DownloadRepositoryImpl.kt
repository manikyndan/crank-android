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
import kotlinx.coroutines.delay
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

            // An explicit per-song tap is a direct request to transfer this song, so it also
            // clears a global pause. Without this, tapping Download while downloads were paused
            // queued the song and appeared to do nothing at all. Resuming is the honest reading
            // of the gesture: the user just asked for this download by name.
            downloadManager.resumeDownloads()

            // Metadata only for now: isLocal flips to true in markComplete(),
            // so the flag means "bytes fully on disk" rather than "requested".
            //
            // The existing row's `isLiked` and `dateAdded` are carried forward deliberately.
            // `SongDao.insertSong` is an `@Insert(onConflict = REPLACE)`, so this write replaces
            // the whole row — and `Song.toEntity` defaults `isLiked` to false. Enqueueing a
            // download with a bare `toEntity()` therefore silently un-liked the song: it vanished
            // from Liked Songs the moment the user downloaded it. `dateAdded` matters for the same
            // reason, because that is the column the liked query orders by, so resetting it also
            // silently reordered the user's library.
            val existing = try {
                songDao.getSongById(song.id)
            } catch (e: Exception) {
                null
            }
            songDao.insertSong(
                song.toEntity(
                    isLiked = existing?.isLiked == true,
                    dateAdded = existing?.dateAdded ?: System.currentTimeMillis(),
                ).copy(isLocal = false)
            )
        } catch (e: Exception) {
            Log.e("CRANK_DOWNLOAD", "Download failed for ${song.title}: ${e.message}", e)
        }
    }

    /**
     * Removes a download: the transfer, its bytes, and the library's "downloaded" flag.
     *
     * ## Why the library row is not simply deleted any more
     *
     * The `songs` row is the single record that also carries `isLiked` and `dateAdded` — see the
     * note in [downloadSong], which deliberately preserves both when a download is enqueued.
     * Deleting the row here therefore deleted the user's *like* along with the download: removing
     * a download silently un-liked the track, with nothing to explain why it left Liked Songs.
     *
     * So a row that exists for a liked song is kept and only has its downloaded flag cleared — it
     * disappears from Offline/Downloads (both filter on `isLocal`) while staying in the library.
     * A row that exists purely because of the download (not liked) is deleted outright, so no
     * orphan metadata is left behind.
     */
    override suspend fun removeDownload(songId: String) {
        // Purge the transfer first: this is what actually frees the bytes on disk and evicts the
        // entry from Media3's download index.
        downloadManager.removeDownload(songId)

        val existing = try {
            songDao.getSongById(songId)
        } catch (e: Exception) {
            Log.e("CRANK_DOWNLOAD", "Could not read row for $songId: ${e.message}")
            null
        } ?: return

        if (existing.isLiked) {
            songDao.updateSong(existing.copy(isLocal = false))
        } else {
            songDao.deleteSongById(songId)
        }
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
                    if (finalException != null) {
                        Log.e(
                            "CRANK_DOWNLOAD",
                            "finalException for $songId state=${download.state}: " +
                                "${finalException.javaClass.simpleName}: ${finalException.message}"
                        )
                    }
                    if (download.state == Download.STATE_COMPLETED) {
                        launch { markComplete(songId) }
                    }
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
        if (currentDownload?.state == Download.STATE_COMPLETED) {
            launch { markComplete(songId) }
        }
        trySend(if (currentDownload != null) mapState(currentDownload.state) else DownloadState.IDLE)

        awaitClose {
            downloadManager.removeListener(listener)
        }
    }

    override suspend fun getDownloadSnapshot(songId: String): DownloadProgress? {
        return try {
            val download = downloadManager.downloadIndex.getDownload(songId)
                ?: return null
            Log.d(
                "CRANK_DOWNLOAD",
                "snapshot $songId state=${download.state} " +
                    "bytes=${download.bytesDownloaded}/${download.contentLength} " +
                    "stopReason=${download.stopReason}"
            )
            if (download.state == Download.STATE_COMPLETED) {
                markComplete(songId)
            }
            toProgress(download)
        } catch (e: Exception) {
            Log.e("CRANK_DOWNLOAD", "Failed to read snapshot for $songId: ${e.message}")
            null
        }
    }

    /**
     * Records that a download's bytes are fully on disk.
     *
     * The enqueue path deliberately leaves `isLocal` false, so every reader
     * of that flag (Library tab, player tick) sees completion, not intent.
     */
    private suspend fun markComplete(songId: String) {
        try {
            val existing = songDao.getSongById(songId) ?: return
            if (!existing.isLocal) {
                songDao.updateSong(existing.copy(isLocal = true))
            }
        } catch (e: Exception) {
            Log.e("CRANK_DOWNLOAD", "Failed to mark $songId complete: ${e.message}")
        }
    }

    /**
     * Builds the domain view of a Media3 [Download], resolving the library row for its title
     * and artist.
     *
     * The three readers of the download index — the one-shot snapshot, the live progress stream
     * and the active-downloads list — all describe the same transfer, so they share this one
     * mapping rather than each growing their own copy. A field added here (as `isTransferring`
     * was) therefore reaches all three at once instead of silently reaching only one.
     */
    private suspend fun toProgress(download: Download): DownloadProgress {
        val songId = download.request.id
        val metadata = try {
            songDao.getSongById(songId)
        } catch (e: Exception) {
            null
        }
        return DownloadProgress(
            songId = songId,
            title = metadata?.title
                ?: download.request.data?.toString(Charsets.UTF_8).orEmpty(),
            artistName = metadata?.artistName.orEmpty(),
            artworkUrl = metadata?.artworkUrl.orEmpty(),
            state = mapState(download.state),
            downloadedBytes = download.bytesDownloaded,
            totalBytes = download.contentLength,
            // Media3's own STATE_DOWNLOADING, kept separate from the mapped state so a
            // queued entry is not mistaken for one that is actively moving bytes.
            isTransferring = download.state == Download.STATE_DOWNLOADING,
        )
    }

    override fun getDownloadProgress(songId: String): Flow<DownloadProgress?> = callbackFlow {
        // Re-reads the index rather than trusting the listener's `Download` argument, because
        // `downloadIndex.getDownload` is the same source the snapshot path uses — so the ring
        // and the state can never disagree about how far along the transfer is.
        suspend fun emitCurrent() {
            val download = try {
                downloadManager.downloadIndex.getDownload(songId)
            } catch (e: Exception) {
                Log.e("CRANK_DOWNLOAD", "Failed to read progress for $songId: ${e.message}")
                null
            }
            trySend(download?.let { toProgress(it) })
        }

        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                // Filtered to this song: the manager notifies for every transfer, and a
                // screen showing one song has no use for the others' ticks.
                if (download.request.id == songId) launch { emitCurrent() }
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                if (download.request.id == songId) launch { emitCurrent() }
            }
        }

        downloadManager.addListener(listener)
        emitCurrent()

        // The listener is not enough on its own, and this is the part that is easy to get wrong.
        //
        // Media3's `DownloadManager` does NOT notify on byte progress — it notifies when a
        // download's *state* changes. Measured on device: sixteen seconds of an actively
        // transferring download produced zero callbacks. Relying on the listener alone therefore
        // emitted once at subscribe time (usually 0 bytes, since a fresh transfer has none yet)
        // and then went silent until the completion state change, so the ring sat at 0% for the
        // whole download and jumped to full at the end.
        //
        // Polling the index is what actually drives the animation. At this interval the reads are
        // one indexed row lookup, and the UI interpolates between them, so the result is a
        // continuous sweep rather than the visible steps the raw values would give.
        launch {
            while (true) {
                delay(PROGRESS_POLL_MS)
                emitCurrent()
            }
        }

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
                        items += toProgress(cursor.download)
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

        // The listener alone is not enough, for the same reason documented on
        // [getDownloadProgress]: Media3's `DownloadManager` notifies on a download's *state*
        // changing, not on byte progress. Without this poll the list refreshed only when a
        // transfer started or finished, so every progress bar here jumped straight from empty to
        // full and the per-item percentages never moved.
        launch {
            while (true) {
                delay(PROGRESS_POLL_MS)
                snapshot()
            }
        }

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

    private companion object {
        /**
         * How often the progress stream re-reads the download index.
         *
         * Fast enough that the UI's interpolation between samples reads as continuous motion,
         * slow enough that the cost is negligible: each pass is one indexed row lookup by primary
         * key plus one cached metadata read. Only one song is ever observed at a time.
         */
        const val PROGRESS_POLL_MS = 500L
    }
}
