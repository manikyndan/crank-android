package com.crank.music.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.core.rethrowIfCancellation
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class DownloadQuality(val label: String, val sizeMultiplier: Float) {
    LOW("Low (96 kbps)", 0.5f),
    MEDIUM("Medium (160 kbps)", 1.0f),
    HIGH("High (320 kbps)", 2.0f),
    LOSSLESS("Lossless (FLAC)", 5.0f)
}

enum class StorageLimit(val label: String, val bytes: Long) {
    GB_1("1 GB", 1_073_741_824L),
    GB_5("5 GB", 5_368_709_120L),
    GB_10("10 GB", 10_737_418_240L),
    UNLIMITED("Unlimited", Long.MAX_VALUE)
}

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
    val autoDownloadOnWifi: Boolean = false,
    val autoDownloadLiked: Boolean = false,
    val autoDownloadArtistReleases: Boolean = false,
    val autoDownloadDailyMix: Boolean = false,
    val downloadQuality: DownloadQuality = DownloadQuality.HIGH,
    val storageLimit: StorageLimit = StorageLimit.GB_10,
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
class OfflineMusicViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val songDao: SongDao,
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
                downloadRepository.getDownloadedSongs().collect { songs ->
                    _uiState.value = _uiState.value.copy(
                        songs = songs.map { it.toOfflineItem() },
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Real storage figures.
     *
     * `totalBytes` is the volume the app's files live on and `usedBytes` is what the app's own
     * directory actually occupies — measured by walking it, not guessed.
     */
    private fun loadStorageUsage() {
        viewModelScope.launch {
            val (total, used) = withContext(Dispatchers.IO) {
                val filesDir = context.filesDir
                val totalBytes = filesDir.totalSpace
                val usedBytes = filesDir.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
                totalBytes to usedBytes
            }
            _uiState.value = _uiState.value.copy(totalStorage = total, usedStorage = used)
        }
    }

    private fun Song.toOfflineItem(): OfflineItem {
        val file = resolveLocalFile(id)
        return OfflineItem(
            id = id,
            title = title,
            subtitle = artistName,
            artworkUrl = artworkUrl,
            fileSize = file?.let { formatBytes(it.length()) } ?: "—",
            downloadDate = file?.let { formatDate(it.lastModified()) } ?: "",
            type = "song",
        )
    }

    /**
     * Finds the file backing a download.
     *
     * Media3 stores downloads under its own directory using the download id, so the id is
     * searched for directly; if that misses, nothing is reported rather than a guessed size.
     */
    private fun resolveLocalFile(songId: String): File? {
        val root = File(context.filesDir, "downloads")
        if (!root.exists()) return null
        return root.walkTopDown().firstOrNull { it.isFile && it.name.contains(songId) }
    }

    fun toggleAutoDownloadOnWifi(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoDownloadOnWifi = enabled)
    }

    fun toggleAutoDownloadLiked(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoDownloadLiked = enabled)
    }

    fun toggleAutoDownloadArtistReleases(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoDownloadArtistReleases = enabled)
    }

    fun toggleAutoDownloadDailyMix(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoDownloadDailyMix = enabled)
    }

    fun setDownloadQuality(quality: DownloadQuality) {
        _uiState.value = _uiState.value.copy(downloadQuality = quality)
    }

    fun setStorageLimit(limit: StorageLimit) {
        _uiState.value = _uiState.value.copy(storageLimit = limit)
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
     * Clears a failed entry from the index so it can be requested again.
     *
     * Media3 holds a terminal FAILED state, so the row must be evicted first. The song is
     * re-requested from the local table, which is the only place the metadata survives.
     */
    fun retryDownload(id: String) {
        viewModelScope.launch {
            try {
                val song = withContext(Dispatchers.IO) { songDao.getSongById(id) } ?: return@launch
                downloadRepository.retryDownload(id)
                downloadRepository.downloadSong(song.toDomainModel())
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
