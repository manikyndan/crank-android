package com.crank.music.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val albumId: String = savedStateHandle.get<String>("albumId") ?: "album_1"

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadAlbumDetails()
    }

    private fun loadAlbumDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val searchResults = musicRepository.search(albumId)
                val album = Album(
                    id = albumId,
                    title = searchResults.firstOrNull()?.title ?: "CRANK Featured Album",
                    artistName = searchResults.firstOrNull()?.artistName ?: "Various Artists",
                    releaseYear = "2024",
                    artworkUrl = searchResults.firstOrNull()?.artworkUrl.orEmpty(),
                    trackCount = searchResults.size.coerceAtLeast(1)
                )

                _uiState.value = AlbumDetailUiState(
                    album = album,
                    songs = searchResults,
                    isLoading = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
