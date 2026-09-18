package com.crank.music.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Artist
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val topSongs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val artistId: String = savedStateHandle.get<String>("artistId") ?: "artist_1"

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        loadArtistDetails()
    }

    private fun loadArtistDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val searchResults = musicRepository.search(artistId)
                val artist = Artist(
                    id = artistId,
                    name = searchResults.firstOrNull()?.artistName ?: "CRANK Featured Artist",
                    imageUrl = searchResults.firstOrNull()?.artworkUrl.orEmpty(),
                    followerCount = 1250000L
                )

                _uiState.value = ArtistDetailUiState(
                    artist = artist,
                    topSongs = searchResults,
                    albums = listOf(
                        Album("art_a1", "${artist.name} - Greatest Hits", artist.name, "2024", artist.imageUrl, 12),
                        Album("art_a2", "${artist.name} - Live Essentials", artist.name, "2023", artist.imageUrl, 10)
                    ),
                    isLoading = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
