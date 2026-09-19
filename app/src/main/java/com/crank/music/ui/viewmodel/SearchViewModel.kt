package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.data.local.SearchHistoryEntity
import com.crank.music.data.local.SongDao
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val searchQuery: String = "",
    val activeCategory: String = "All",
    val recentSearches: List<String> = emptyList(),
    val filteredSongs: List<Song> = emptyList(),
    val filteredAlbums: List<Album> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val songDao: SongDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var initialQueryApplied = false

    init {
        loadRecentSearches()
    }

    /**
     * Applies a query that arrived as a navigation argument (e.g. tapping an
     * artist name in Explore). Guarded so a recomposition or config change does
     * not clobber whatever the user has since typed, and so we do not fire a
     * network search more than once for the same deep link.
     */
    fun applyInitialQuery(query: String) {
        if (initialQueryApplied || query.isBlank()) return
        initialQueryApplied = true
        _uiState.value = _uiState.value.copy(searchQuery = query)
        addRecentSearch(query)
        performSearch()
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            try {
                songDao.getRecentSearches().collect { entities ->
                    _uiState.value = _uiState.value.copy(
                        recentSearches = entities.map { it.query }
                    )
                }
            } catch (e: Exception) {
                Log.e("CRANK_SEARCH", "Failed to load searches: ${e.message}")
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.value = _uiState.value.copy(searchQuery = newQuery)
        if (newQuery.isNotBlank() && newQuery.length >= 2) {
            addRecentSearch(newQuery)
        }
        performSearch()
    }

    fun onCategoryChanged(category: String) {
        _uiState.value = _uiState.value.copy(activeCategory = category)
        performSearch()
    }

    fun removeRecentSearch(search: String) {
        viewModelScope.launch {
            try {
                songDao.deleteSearchQuery(search)
            } catch (e: Exception) {
                Log.e("CRANK_SEARCH", "Failed to delete search: ${e.message}")
            }
        }
    }

    fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                val entity = SearchHistoryEntity(
                    query = query.trim(),
                    searchedAt = System.currentTimeMillis()
                )
                songDao.insertSearchQuery(entity)
            } catch (e: Exception) {
                Log.e("CRANK_SEARCH", "Failed to save search: ${e.message}")
            }
        }
    }

    private fun performSearch() {
        searchJob?.cancel()
        val query = _uiState.value.searchQuery.trim()
        val category = _uiState.value.activeCategory

        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                filteredSongs = emptyList(),
                filteredAlbums = emptyList(),
                isLoading = false
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(300L)
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val showSongs = category == "All" || category == "Songs" || category == "Artists"
                val showAlbums = category == "All" || category == "Albums"

                val songs = if (showSongs) {
                    musicRepository.search(query)
                } else emptyList()

                val albums = if (showAlbums) {
                    try {
                        musicRepository.getHomeRecommendations().filter {
                            it.title.contains(query, ignoreCase = true) ||
                                    it.artistName.contains(query, ignoreCase = true)
                        }.ifEmpty {
                            // Create album cards from song results if no album matches
                            songs.groupBy { it.artistName }.mapNotNull { (artist, artistSongs) ->
                                if (artistSongs.isNotEmpty() && artistSongs.any { it.title.contains(query, ignoreCase = true) }) {
                                    Album(
                                        id = "album_${artistSongs.first().id}",
                                        title = artistSongs.first().let { "${it.title} - Single" },
                                        artistName = artist,
                                        // No release year is available from the search layer.
                                        // Blank makes the UI omit the badge instead of printing
                                        // a year that has nothing to do with the release.
                                        releaseYear = "",
                                        artworkUrl = artistSongs.first().artworkUrl,
                                        trackCount = artistSongs.size
                                    )
                                } else null
                            }
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else emptyList()

                _uiState.value = _uiState.value.copy(
                    filteredSongs = songs,
                    filteredAlbums = albums,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("CRANK_SEARCH", "Search failed: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
