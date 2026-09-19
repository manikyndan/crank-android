package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val isResolved: Boolean = false,
)

/**
 * Backs the album destination.
 *
 * The album is addressed by a **search query plus its display name**, both supplied by the route,
 * because the app's stream/search layer identifies tracks by free-text query — an album id from
 * the search results is not something the backend can be asked about directly.
 *
 * The previous version fed the raw route id straight into `musicRepository.search(albumId)`, so a
 * request for album `1234567` searched for the literal text "1234567" and then labelled whatever
 * came back with a hardcoded year and a fabricated "CRANK Featured Album" fallback title.
 */
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val albumTitle: String = savedStateHandle.get<String>("albumTitle").orEmpty()
    private val albumArtist: String = savedStateHandle.get<String>("albumArtist").orEmpty()

    private val _uiState = MutableStateFlow(
        AlbumDetailUiState(
            album = Album(
                id = albumTitle,
                title = albumTitle,
                artistName = albumArtist.ifBlank { "Various Artists" },
                // "" means unknown. The search response carries no release year, and the previous
                // hardcoded "2024" was a guess presented as a fact.
                releaseYear = "",
                artworkUrl = "",
                trackCount = 0,
            ).takeIf { albumTitle.isNotBlank() }
        )
    )
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        loadAlbumDetails()
    }

    private fun loadAlbumDetails() {
        // Nothing to search for. Stop rather than searching for an empty string.
        if (albumTitle.isBlank() && albumArtist.isBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false, isResolved = true)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val query = listOf(albumTitle, albumArtist)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val results = musicRepository.search(query)

                val artwork = results.firstOrNull()?.artworkUrl.orEmpty()
                _uiState.value = AlbumDetailUiState(
                    album = Album(
                        id = albumTitle,
                        title = albumTitle,
                        artistName = albumArtist.ifBlank {
                            results.firstOrNull()?.artistName ?: "Various Artists"
                        },
                        releaseYear = "",
                        artworkUrl = artwork,
                        trackCount = results.size,
                    ),
                    songs = results,
                    isLoading = false,
                    isResolved = true,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CRANK_ALBUM", "Failed to load '$albumTitle': ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false, isResolved = true)
            }
        }
    }
}
