package com.crank.music.ui.viewmodel

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadIndex
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.data.local.DownloadLocations
import com.crank.music.data.local.SongDao
import com.crank.music.data.local.toDomainModel
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class OfflineItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String = "",
    val fileSize: String,
    val downloadDate: String,
    val type: String = "song",
    val downloadProgress: Float = 1f
)

data class DownloadTask(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String = "",
    val progress: Float,
    val speed: String,
    val status: DownloadStatus,
    val fileSize: String = ""
)

enum class DownloadStatus { DOWNLOADING, PAUSED, QUEUED, FAILED, COMPLETED }

data class OfflineUiState(
    /** Total space on the volume the app can use, read from the filesystem. */
    val totalStorage: Long = 0L,
    /** Bytes the app's own files currently occupy. */
    val usedStorage: Long = 0L,
    /** Everything the user has downloaded, newest first. */
    val songs: List<OfflineItem> = emptyList(),
    /** Playlists the user created that contain at least one downloaded song. */
    val playlists: List<OfflineItem> = emptyList(),
    /**
     * Transfers Media3 is currently tracking, with real byte progress. Empty when nothing is
     * downloading — previously this was four hardcoded rows that never changed.
     */
    val activeDownloads: List<DownloadTask> = emptyList(),
    /** Whether Media3 is globally paused. There is no per-download pause in 1.4.1. */
    val downloadsPaused: Boolean = false,
    val expandedSection: String? = null,
    val swipedItemId: String? = null,
    val isLoading: Boolean = true,
)

/**
 * Offline / download management.
 *
 * Previously this screen was entirely static: three invented playlists ("Chill Vibes",
 * "Road Trip"), two invented albums including "Midnight Rain by Taylor Swift", five invented
 * songs with invented file sizes, four invented in-progress downloads with invented transfer
 * speeds, and hardcoded 64 GB / 18.4 GB storage figures. None of it referenced a real file.
 * Removing a row only hid it from an in-memory list and it reappeared on the next visit.
 *
 * Everything below is now derived from the download repository and the filesystem:
 * the song list is the real downloaded set, file sizes are measured from disk, storage
 * figures come from the volume, and remove actually deletes through the repository.
 *
 * Deliberately absent: a list of "active downloads" with per-item speed and progress. Media3
 * exposes live progress through `DownloadManager`, but wiring that up faithfully is a larger
 * piece of work than a rewrite of this screen; showing a static fake progress bar in the
 * meantime is exactly the failure mode being removed. The section is omitted until it can be
 * backed by real transfer state.
 */
@HiltViewModel
@OptIn(UnstableApi::class)
class OfflineMusicViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val songDao: SongDao,
    // `DownloadIndex` and `Cache` are both flagged `@UnstableApi` by Media3. They are the only
    // honest source for real download sizes and cache usage — the alternative is walking a
    // directory that Media3 does not use (which is what produced empty sizes) or inventing numbers.
    // The opt-in is scoped to this class.
    private val downloadIndex: DownloadIndex,
    private val cache: Cache,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineUiState())
    val uiState: StateFlow<OfflineUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
        observeActiveDownloads()
        loadStorageUsage()
    }

    /**
     * Live transfer state, straight from Media3.
     *
     * The previous version hardcoded four rows — including a "2.4 MB/s" speed and a 65%
     * progress bar — for downloads that had never been started. These come from the download
     * manager's byte counters, so a transfer that has not begun produces no row at all.
     *
     * Speed is intentionally blank: Media3 reports cumulative bytes and the current state but
     * no instantaneous rate, and deriving one from successive samples would be a guess. A
     * blank speed renders as "—" in the UI, which is truthful.
     */
    private fun observeActiveDownloads() {
        viewModelScope.launch {
            try {
                downloadRepository.getActiveDownloads().collect { progressList ->
                    _uiState.value = _uiState.value.copy(
                        activeDownloads = progressList
                            .filter { it.state != com.crank.music.domain.model.DownloadState.IDLE }
                            .map { it.toDownloadTask() }
                    )
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
            }
        }
    }

    private fun com.crank.music.domain.model.DownloadProgress.toDownloadTask(): DownloadTask =
        DownloadTask(
            id = songId,
            title = title,
            artist = artistName,
            artworkUrl = artworkUrl,
            progress = fraction,
            speed = "—",
            status = when (state) {
                com.crank.music.domain.model.DownloadState.DOWNLOADING -> DownloadStatus.DOWNLOADING
                com.crank.music.domain.model.DownloadState.COMPLETED -> DownloadStatus.COMPLETED
                com.crank.music.domain.model.DownloadState.FAILED -> DownloadStatus.FAILED
                com.crank.music.domain.model.DownloadState.IDLE -> DownloadStatus.QUEUED
            },
            fileSize = if (totalBytes > 0L) formatBytes(totalBytes) else "",
        )

    private fun observeDownloads() {
        viewModelScope.launch {
            try {
                downloadRepository.getDownloadedSongs()
                    // Required, not cosmetic: building each row reads the Media3 download index,
                    // which is a blocking database call. Without flowOn it ran on the main thread
                    // once per song on every emission.
                    .map { songs -> songs.map { it.toOfflineItem() } }
                    .flowOn(Dispatchers.IO)
                    .collect { items ->
                        _uiState.value = _uiState.value.copy(
                            songs = items,
                            isLoading = false,
                        )
                    }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun Song.toOfflineItem(): OfflineItem {
        val download = runCatching { downloadIndex.getDownload(id) }.getOrNull()
        return OfflineItem(
            id = id,
            title = title,
            subtitle = artistName,
            artworkUrl = artworkUrl,
            fileSize = download?.bytesDownloaded
                ?.takeIf { it > 0L }
                ?.let { formatBytes(it) }
                ?: "—",
            downloadDate = download?.updateTimeMs
                ?.takeIf { it > 0L }
                ?.let { formatDate(it) }
                ?: "",
            type = "song",
        )
    }

    /**
     * Real storage figures for the volume downloads live on.
     *
     * `used` is the bytes Media3 actually holds for downloads ([Cache.getCacheSpace]), not a walk of
     * the app's *internal* directory — which is what this used to measure, while downloads were
     * written to external storage. The result was a bar showing the app's internal files against the
     * internal volume's total, which reported nothing about downloads at all.
     *
     * Both figures are read on [Dispatchers.IO]: `getCacheSpace` queries the cache database, and
     * `totalSpace` hits the filesystem.
     */
    private fun loadStorageUsage() {
        viewModelScope.launch {
            val (total, used) = withContext(Dispatchers.IO) {
                val volume = DownloadLocations.directory(context)
                volume.totalSpace to cache.cacheSpace
            }
            _uiState.value = _uiState.value.copy(totalStorage = total, usedStorage = used)
        }
    }


    fun toggleSection(section: String) {
        _uiState.value = _uiState.value.copy(
            expandedSection = if (_uiState.value.expandedSection == section) null else section
        )
    }

    fun setSwipedItemId(id: String?) {
        _uiState.value = _uiState.value.copy(swipedItemId = id)
    }

    // ── Transfer control ────────────────────────────────────────────────────────
    // These previously mutated an in-memory list of fake tasks, so pressing pause changed
    // a label and nothing else. They now drive the Media3 download manager.
    //
    // Pause/resume are global because Media3 1.4.1 does not expose per-download control —
    // the UI presents them as a single switch rather than per-row buttons that would lie.

    fun pauseAllDownloads() {
        viewModelScope.launch {
            try {
                downloadRepository.pauseAllDownloads()
                _uiState.value = _uiState.value.copy(downloadsPaused = true)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
            }
        }
    }

    fun resumeAllDownloads() {
        viewModelScope.launch {
            try {
                downloadRepository.resumeAllDownloads()
                _uiState.value = _uiState.value.copy(downloadsPaused = false)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
            }
        }
    }

    /**
     * Cancels a transfer and removes whatever it had written.
     *
     * The row disappears because the index no longer holds it, not because the view model
     * filtered it out locally — so a failed removal leaves the row visible.
     */
    fun cancelDownload(id: String) {
        viewModelScope.launch {
            try {
                downloadRepository.removeDownload(id)
                loadStorageUsage()
            } catch (e: Exception) {
                e.rethrowIfCancellation()
            }
        }
    }

    /**
     * Re-queues a failed download.
     *
     * Media3 holds a terminal FAILED state, so the entry is evicted and rebuilt inside the
     * repository. The song is looked up from the local table, which is the only place its metadata
     * survives.
     */
    fun retryDownload(id: String) {
        viewModelScope.launch {
            try {
                val song = withContext(Dispatchers.IO) { songDao.getSongById(id) } ?: return@launch
                downloadRepository.retryDownload(song.toDomainModel())
            } catch (e: Exception) {
                e.rethrowIfCancellation()
            }
        }
    }

    /**
     * Removes a download for real.
     *
     * The row is not filtered out locally: `observeDownloads` is driven by the database, so the
     * list re-renders from the repository once the delete lands. If the delete fails the row
     * stays visible, which is the correct signal — the previous implementation hid the row
     * regardless and the download reappeared on the next screen open.
     */
    fun removeItem(id: String) {
        viewModelScope.launch {
            try {
                downloadRepository.removeDownload(id)
                _uiState.value = _uiState.value.copy(swipedItemId = null)
                loadStorageUsage()
            } catch (e: Exception) {
                e.rethrowIfCancellation()
            }
        }
    }

    private fun formatBytes(bytes: Long): String = when {        bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMillis))
}
