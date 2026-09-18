package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenreItem(
    val id: String,
    val title: String,
    val imageUrl: String
)

data class ChartItem(
    val song: Song,
    val rank: Int,
    val trend: String,
    val playCount: String,
    val duration: String
)

data class NewReleaseItem(
    val album: Album,
    val releaseDate: String,
    val isNew: Boolean = true
)

data class FeaturedPlaylist(
    val id: String,
    val title: String,
    val description: String,
    val artworkUrl: String,
    val isHero: Boolean = false
)

data class BrowseArtist(
    val id: String,
    val name: String,
    val artworkUrl: String,
    val letter: Char
)

data class SearchSuggestion(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String,
    val imageUrl: String = ""
)

data class ExploreUiState(
    val genres: List<GenreItem> = emptyList(),
    val topCharts: List<ChartItem> = emptyList(),
    val newReleases: List<NewReleaseItem> = emptyList(),
    val featuredPlaylists: List<FeaturedPlaylist> = emptyList(),
    val browseArtists: List<BrowseArtist> = emptyList(),
    val searchQuery: String = "",
    val isSearchExpanded: Boolean = false,
    val searchSuggestions: List<SearchSuggestion> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val trendingSearches: List<String> = emptyList(),
    val selectedChartFilter: String = "Songs",
    val selectedChartRegion: String = "Global",
    val isLoading: Boolean = false,
    val genresLoaded: Boolean = false,
    val chartsLoaded: Boolean = false,
    val releasesLoaded: Boolean = false
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadExploreData()
    }

    private fun loadExploreData() {
        // Genres are static — load immediately
        val genreQueries = listOf(
            "pop", "hip hop", "rock", "electronic", "r&b",
            "jazz", "classical", "country", "reggae", "metal"
        )
        val genresList = genreQueries.mapIndexed { index, genre ->
            GenreItem(
                id = "g$index",
                title = genre.replaceFirstChar { it.uppercase() },
                imageUrl = "https://picsum.photos/300/200?random=${21 + index}"
            )
        }

        // Featured playlists are static — load immediately
        val featuredPlaylistsList = listOf(
            FeaturedPlaylist("fp1", "Today's Top Hits", "The biggest songs right now", "https://picsum.photos/600/400?random=51", true),
            FeaturedPlaylist("fp2", "RapCaviar", "New music from top artists", "https://picsum.photos/300/300?random=52"),
            FeaturedPlaylist("fp3", "All Out 2020s", "The biggest songs of the 2020s", "https://picsum.photos/300/300?random=53"),
            FeaturedPlaylist("fp4", "Rock Classics", "Rock legends & iconic songs", "https://picsum.photos/300/300?random=54"),
            FeaturedPlaylist("fp5", "Chill Hits", "Kick back to the best chill hits", "https://picsum.photos/300/300?random=55"),
            FeaturedPlaylist("fp6", "Viva Latino", "The biggest Latin hits", "https://picsum.photos/300/300?random=56")
        )

        // Browse artists are static — load immediately
        val artistNames = listOf("Adele", "Ariana Grande", "Billie Eilish", "Bruno Mars", "Dua Lipa", "Ed Sheeran", "Harry Styles", "Justin Bieber", "Lady Gaga", "The Weeknd", "Taylor Swift", "Drake")
        val browseArtistsList = artistNames.mapIndexed { index, name ->
            BrowseArtist(
                id = "a$index",
                name = name,
                artworkUrl = "https://picsum.photos/300/300?random=${61 + index}",
                letter = name.first()
            )
        }

        // Show static content immediately
        _uiState.value = ExploreUiState(
            genres = genresList,
            featuredPlaylists = featuredPlaylistsList,
            browseArtists = browseArtistsList,
            isLoading = true,
            genresLoaded = true
        )

        // Load API data in parallel
        viewModelScope.launch {
            val chartsDeferred = async { loadTopCharts() }
            val releasesDeferred = async { loadNewReleases() }
            val trendingDeferred = async { loadTrendingSearches() }

            // Wait for charts first (most important)
            val charts = chartsDeferred.await()
            _uiState.update { it.copy(topCharts = charts, chartsLoaded = true) }

            // Then releases
            val releases = releasesDeferred.await()
            _uiState.update { it.copy(newReleases = releases, releasesLoaded = true) }

            // Then trending
            val trending = trendingDeferred.await()
            _uiState.update { it.copy(trendingSearches = trending, isLoading = false) }
        }
    }

    private suspend fun loadTopCharts(): List<ChartItem> {
        return try {
            val chartSongs = musicRepository.search("top hits 2024").take(8)
            chartSongs.mapIndexed { index, song ->
                ChartItem(
                    song = song,
                    rank = index + 1,
                    trend = if (index % 3 == 0) "up" else if (index % 5 == 0) "down" else "same",
                    playCount = String.format("%,d", 1000000L - index * 100000L),
                    duration = formatDuration(song.durationMs)
                )
            }
        } catch (e: Exception) {
            Log.e("CRANK_EXPLORE", "Failed to load charts: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadNewReleases(): List<NewReleaseItem> {
        return try {
            val newReleaseSongs = musicRepository.search("new releases").take(6)
            newReleaseSongs.map { song ->
                NewReleaseItem(
                    album = Album(
                        id = song.albumId ?: song.id,
                        title = song.title,
                        artistName = song.artistName,
                        releaseYear = "2024",
                        artworkUrl = song.artworkUrl,
                        trackCount = 1
                    ),
                    releaseDate = "2024"
                )
            }
        } catch (e: Exception) {
            Log.e("CRANK_EXPLORE", "Failed to load releases: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadTrendingSearches(): List<String> {
        return try {
            val trendingSongs = musicRepository.search("trending").take(4)
            trendingSongs.map { it.title }
        } catch (e: Exception) {
            Log.e("CRANK_EXPLORE", "Failed to load trending: ${e.message}")
            emptyList()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                delay(300) // Debounce
                try {
                    val results = musicRepository.search(query).take(6)
                    val suggestions = results.map { song ->
                        SearchSuggestion(
                            id = song.id,
                            title = song.title,
                            subtitle = "${song.artistName} • Song",
                            type = "song",
                            imageUrl = song.artworkUrl
                        )
                    }
                    _uiState.value = _uiState.value.copy(searchSuggestions = suggestions)
                } catch (e: Exception) {
                    Log.e("CRANK_EXPLORE", "Search failed: ${e.message}")
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(searchSuggestions = emptyList())
        }
    }

    fun expandSearch() {
        _uiState.value = _uiState.value.copy(isSearchExpanded = true)
    }

    fun collapseSearch() {
        _uiState.value = _uiState.value.copy(isSearchExpanded = false, searchQuery = "", searchSuggestions = emptyList())
    }

    fun addRecentSearch(query: String) {
        val current = _uiState.value.recentSearches.toMutableList()
        current.remove(query)
        current.add(0, query)
        _uiState.value = _uiState.value.copy(recentSearches = current.take(10))
    }

    fun removeRecentSearch(query: String) {
        _uiState.value = _uiState.value.copy(
            recentSearches = _uiState.value.recentSearches.filter { it != query }
        )
    }

    fun clearRecentSearches() {
        _uiState.value = _uiState.value.copy(recentSearches = emptyList())
    }

    fun setChartFilter(filter: String) {
        _uiState.value = _uiState.value.copy(selectedChartFilter = filter)
    }

    fun setChartRegion(region: String) {
        _uiState.value = _uiState.value.copy(selectedChartRegion = region)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }
}
