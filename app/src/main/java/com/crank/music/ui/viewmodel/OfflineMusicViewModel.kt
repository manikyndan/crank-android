package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val progress: Float,
    val speed: String,
    val status: DownloadStatus,
    val fileSize: String = ""
)

enum class DownloadStatus { DOWNLOADING, PAUSED, QUEUED, FAILED, COMPLETED }

data class OfflineUiState(
    val totalStorage: Long = 64_000_000_000L,
    val usedStorage: Long = 18_400_000_000L,
    val playlists: List<OfflineItem> = listOf(
        OfflineItem("1", "Chill Vibes", "Playlist • 24 songs", fileSize = "482 MB", downloadDate = "Jan 20, 2025"),
        OfflineItem("2", "Workout Mix", "Playlist • 18 songs", fileSize = "356 MB", downloadDate = "Jan 18, 2025"),
        OfflineItem("3", "Road Trip", "Playlist • 32 songs", fileSize = "612 MB", downloadDate = "Jan 15, 2025")
    ),
    val albums: List<OfflineItem> = listOf(
        OfflineItem("4", "Midnight Rain", "Taylor Swift • Album", fileSize = "724 MB", downloadDate = "Jan 22, 2025"),
        OfflineItem("5", "After Hours", "The Weeknd • Album", fileSize = "518 MB", downloadDate = "Jan 19, 2025")
    ),
    val artists: List<OfflineItem> = listOf(
        OfflineItem("6", "The Weeknd", "Artist • 156 songs", fileSize = "3.2 GB", downloadDate = "Jan 10, 2025"),
        OfflineItem("7", "Dua Lipa", "Artist • 89 songs", fileSize = "1.8 GB", downloadDate = "Jan 8, 2025")
    ),
    val songs: List<OfflineItem> = listOf(
        OfflineItem("8", "Blinding Lights", "The Weeknd", fileSize = "8.2 MB", downloadDate = "Jan 22, 2025"),
        OfflineItem("9", "Levitating", "Dua Lipa", fileSize = "7.8 MB", downloadDate = "Jan 22, 2025"),
        OfflineItem("10", "Anti-Hero", "Taylor Swift", fileSize = "9.1 MB", downloadDate = "Jan 21, 2025"),
        OfflineItem("11", "As It Was", "Harry Styles", fileSize = "7.4 MB", downloadDate = "Jan 20, 2025"),
        OfflineItem("12", "Stay", "The Kid LAROI, Justin Bieber", fileSize = "8.6 MB", downloadDate = "Jan 19, 2025")
    ),
    val autoDownloadOnWifi: Boolean = true,
    val autoDownloadLiked: Boolean = true,
    val autoDownloadArtistReleases: Boolean = false,
    val autoDownloadDailyMix: Boolean = false,
    val downloadQuality: DownloadQuality = DownloadQuality.HIGH,
    val storageLimit: StorageLimit = StorageLimit.GB_10,
    val activeDownloads: List<DownloadTask> = listOf(
        DownloadTask("1", "Starboy", "The Weeknd", 0.65f, "2.4 MB/s", DownloadStatus.DOWNLOADING, "8.5 MB"),
        DownloadTask("2", "Save Your Tears", "The Weeknd", 0.32f, "1.8 MB/s", DownloadStatus.DOWNLOADING, "7.9 MB"),
        DownloadTask("3", "Don't Start Now", "Dua Lipa", 0.0f, "—", DownloadStatus.QUEUED, "8.1 MB"),
        DownloadTask("4", "Watermelon Sugar", "Harry Styles", 0.0f, "—", DownloadStatus.FAILED, "7.6 MB")
    ),
    val expandedSection: String? = null,
    val swipedItemId: String? = null
)

@HiltViewModel
class OfflineMusicViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineUiState())
    val uiState: StateFlow<OfflineUiState> = _uiState.asStateFlow()

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

    fun removeItem(id: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            playlists = state.playlists.filter { it.id != id },
            albums = state.albums.filter { it.id != id },
            artists = state.artists.filter { it.id != id },
            songs = state.songs.filter { it.id != id },
            swipedItemId = null
        )
    }

    fun pauseDownload(id: String) {
        _uiState.value = _uiState.value.copy(
            activeDownloads = _uiState.value.activeDownloads.map {
                if (it.id == id) it.copy(status = DownloadStatus.PAUSED) else it
            }
        )
    }

    fun resumeDownload(id: String) {
        _uiState.value = _uiState.value.copy(
            activeDownloads = _uiState.value.activeDownloads.map {
                if (it.id == id) it.copy(status = DownloadStatus.DOWNLOADING) else it
            }
        )
    }

    fun cancelDownload(id: String) {
        _uiState.value = _uiState.value.copy(
            activeDownloads = _uiState.value.activeDownloads.filter { it.id != id }
        )
    }

    fun retryDownload(id: String) {
        _uiState.value = _uiState.value.copy(
            activeDownloads = _uiState.value.activeDownloads.map {
                if (it.id == id) it.copy(status = DownloadStatus.QUEUED, progress = 0f, speed = "—") else it
            }
        )
    }
}
