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
    private val smartPlaylist: SmartPlaylistKind? = SmartPlaylistKind.fromSlug(playlistId)

    private val kind: DestinationKind = when {
        smartPlaylist != null -> DestinationKind.SMART
        collection != null -> DestinationKind.COLLECTION
        genreName != null -> DestinationKind.GENRE
        else -> DestinationKind.UNKNOWN
    }

    /**
     * The three kinds of thing this destination can open, resolved once.
     *
     * The route carries a single opaque string, and three different subsystems produce values
     * for it: [Collection] slugs from Home/Explore, `genre:<name>` from Explore's genre grid,
     * and [SmartPlaylistKind] slugs from Library. Each is checked in turn so the id that opens
     * a list and the list that renders it come from the same vocabulary.
     *
     * This was the bug the device run caught: Library emitted `smart_most_played`, but only
     * `Collection` and `genre:` were resolved here, so every smart playlist opened as
     * "Unknown Collection". Adding a resolver without adding the matching branch is exactly the
     * mismatch this design is meant to prevent.
     */
    private enum class DestinationKind { SMART, COLLECTION, GENRE, UNKNOWN }

    private val _uiState = MutableStateFlow(
        PlaylistDetailState(
            title = when (kind) {
                DestinationKind.SMART -> smartPlaylist!!.title
                DestinationKind.COLLECTION -> collection!!.title
                DestinationKind.GENRE -> "$genreName Hits"
                DestinationKind.UNKNOWN -> "Unknown Collection"
            },
            subtitle = when (kind) {
                DestinationKind.SMART -> smartPlaylist!!.description
                DestinationKind.COLLECTION -> collection!!.subtitle
                DestinationKind.GENRE -> "Popular $genreName"
                DestinationKind.UNKNOWN -> ""
            },
            isUnknownCollection = kind == DestinationKind.UNKNOWN,
        )
    )
    val uiState: StateFlow<PlaylistDetailState> = _uiState.asStateFlow()

    init {
        loadSongs()
    }

    private fun loadSongs() {
        when (kind) {
            // Smart playlists are backed entirely by the local database, so they never hit
            // the network and are correct offline.
            DestinationKind.SMART -> loadSmartPlaylist()

            // Recently Played is likewise local.
            DestinationKind.COLLECTION -> if (collection == Collection.RECENTLY_PLAYED) {
                loadRecentlyPlayed()
            } else {
                searchAndLoad(collection?.query)
            }

            DestinationKind.GENRE -> searchAndLoad(genreName)

            // An unrecognised slug stops loading rather than searching for a placeholder.
            DestinationKind.UNKNOWN -> _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * Resolves a smart playlist through the shared [SmartPlaylistKind.resolve].
     *
     * Deliberately the same call the Library grid counts with, so the badge and this list are
     * derived from one query rather than two that could drift. Where the count was 0 the list
     * is empty, which is the honest outcome.
     */
    private fun loadSmartPlaylist() {
        viewModelScope.launch {
            try {
                val songs = withContext(Dispatchers.IO) {
                    smartPlaylist?.resolve(songDao).orEmpty()
                }
                _uiState.value = _uiState.value.copy(songs = songs, isLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CRANK_PLAYLIST", "Failed to load smart playlist $playlistId: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun searchAndLoad(query: String?) {
        if (query.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false)
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
                    songDao.getHistoryList(MAX_SONGS).map { it.toSong() }
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
