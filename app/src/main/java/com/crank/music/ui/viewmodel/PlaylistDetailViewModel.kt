package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailState(
    val playlistTitle: String = "",
    val playlistSubtitle: String = "",
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val playlistId: String = savedStateHandle.get<String>("playlistId") ?: ""
    private val playlistTitle: String = savedStateHandle.get<String>("playlistTitle") ?: "Playlist"

    private val _uiState = MutableStateFlow(PlaylistDetailState(playlistTitle = playlistTitle))
    val uiState: StateFlow<PlaylistDetailState> = _uiState.asStateFlow()

    init {
        loadPlaylistSongs()
    }

    private fun loadPlaylistSongs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val query = when (playlistId) {
                    "daily_mix_1" -> "today top hits 2024"
                    "daily_mix_2" -> "chill vibes playlist"
                    "daily_mix_3" -> "workout energy music"
                    "daily_mix_4" -> "focus concentrate music"
                    "daily_mix_5" -> "party dance hits"
                    "made_for_you_1" -> "your top songs"
                    "made_for_you_2" -> "discover weekly mix"
                    "made_for_you_3" -> "release radar"
                    "made_for_you_4" -> "daily mix pop"
                    "made_for_you_5" -> "daily mix hip hop"
                    else -> playlistTitle.lowercase()
                }

                val songs = musicRepository.search(query).take(20)

                val subtitle = when {
                    playlistId.startsWith("daily_mix") -> "Made for you based on your listening"
                    playlistId.startsWith("made_for_you") -> "A playlist made just for you"
                    else -> "Playlist"
                }

                _uiState.value = PlaylistDetailState(
                    playlistTitle = playlistTitle,
                    playlistSubtitle = subtitle,
                    songs = songs,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("CRANK_PLAYLIST", "Failed to load playlist: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
