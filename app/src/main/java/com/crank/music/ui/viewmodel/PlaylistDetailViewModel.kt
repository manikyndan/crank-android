package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.data.local.SongDao
import com.crank.music.domain.model.Collection
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PlaylistDetailState(
    val title: String = "",
    val subtitle: String = "",
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    /** Set when the route named a collection this build does not know how to open. */
    val isUnknownCollection: Boolean = false,
)

/**
 * Backs the playlist/collection destination.
 *
 * Rewritten to resolve its argument through [Collection] instead of a private id vocabulary.
 * The previous version matched a hand-written set of ids (`daily_mix_1`, `made_for_you_1`, …)
 * against ids that the emitters never sent (`dm1`, `dw`, `fp1`, …), so every collection fell
 * through to `search("playlist")` and rendered the same unrelated results under the title
 * "Playlist". Resolution now lives in one place ([Collection.fromSlug]) and the title comes from
 * the collection itself rather than from a route parameter that was never passed.
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val songDao: SongDao,
) : ViewModel() {

    private val playlistId: String = savedStateHandle.get<String>("playlistId").orEmpty()

    private val collection: Collection? = Collection.fromSlug(playlistId)
    private val genreName: String? = Collection.genreOf(playlistId)

    private val _uiState = MutableStateFlow(
        PlaylistDetailState(
            title = collection?.title ?: genreName?.let { "$it Hits" } ?: "Unknown Collection",
            subtitle = collection?.subtitle ?: genreName?.let { "Popular $it" } ?: "",
            isUnknownCollection = collection == null && genreName == null,
        )
    )
    val uiState: StateFlow<PlaylistDetailState> = _uiState.asStateFlow()

    init {
        loadSongs()
    }

    private fun loadSongs() {
        val query = collection?.query ?: genreName
        if (query.isNullOrBlank()) {
            // Nothing to fetch — either an unknown slug or a collection backed by local data
            // (Recently Played), which is not a search. Stop spinning.
            if (collection == Collection.RECENTLY_PLAYED) {
                loadRecentlyPlayed()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val songs = musicRepository.search(query).take(MAX_SONGS)
                _uiState.value = _uiState.value.copy(songs = songs, isLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CRANK_PLAYLIST", "Failed to load ${playlistId}: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun loadRecentlyPlayed() {
        viewModelScope.launch {
            try {
                val songs = withContext(Dispatchers.IO) {
                    songDao.getHistoryList(MAX_SONGS).map { entity ->
                        Song(
                            id = entity.songId,
                            title = entity.title,
                            artistName = entity.artistName,
                            albumId = entity.albumId,
                            durationMs = entity.durationMs,
                            artworkUrl = entity.artworkUrl,
                            isLocal = false,
                            streamUrl = entity.streamUrl,
                        )
                    }
                }
                _uiState.value = _uiState.value.copy(songs = songs, isLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CRANK_PLAYLIST", "Failed to load history: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private companion object {
        const val MAX_SONGS = 30
    }
}
