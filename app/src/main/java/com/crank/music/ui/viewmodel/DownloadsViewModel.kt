package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.domain.model.DownloadState
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloadedSongs: List<Song> = emptyList(),
    val isOfflineModeEnabled: Boolean = false
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.getDownloadedSongs().collect { songs ->
                _uiState.value = _uiState.value.copy(downloadedSongs = songs)
            }
        }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isOfflineModeEnabled = enabled)
    }

    fun removeDownload(songId: String) {
        viewModelScope.launch {
            downloadRepository.removeDownload(songId)
        }
    }

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            downloadRepository.downloadSong(song)
        }
    }
}
