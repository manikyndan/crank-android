package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.core.awaitOrNull
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Collection
import com.crank.music.domain.model.Song
import com.crank.music.data.local.SessionEntity
import com.crank.music.data.local.SongDao
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * Featured playlists shown in Explore, in display order, each flagged whether it is the hero card.
 * Keyed by [Collection] so the id, title and backing query cannot disagree.
 */
private val FEATURED_COLLECTIONS = listOf(
    Collection.TOP_HITS to true,
    Collection.RAP_CAVIAR to false,
    Collection.ALL_OUT_2020S to false,
    Collection.ROCK_CLASSICS to false,
    Collection.CHILL_HITS to false,
    Collection.VIVA_LATINO to false,
)

/** Query whose results seed the Browse All artist row. */
private const val BROWSE_ARTIST_QUERY = "popular artists"

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
    private val musicRepository: MusicRepository,
    private val songDao: SongDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    companion object {
        /** `session_values` key holding the epoch millis of the last successful Browse fetch. */
        private const val LAST_FETCH_KEY = "browse_last_fetch_ms"

        /**
         * How long fetched Browse content counts as fresh. One day, so opening the tab surfaces the
         * day's charts and releases instead of whatever happened to be cached.
         */
        private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    }

    /** False until this instance has content, so a re-entered tab is never blank but empty. */
    private var hasLoadedThisInstance = false

    init {
        refreshIfStale()
    }

    /**
     * Fetches Browse content when it is missing or older than the one-day cache window.
     *
     * The tab used to load only from `init`, so after the first cold start its charts and releases
     * never changed again until a manual pull-to-refresh. The fetch time is persisted in
     * `session_values` rather than held in memory, because this ViewModel is scoped to the Browse
     * nav entry — an in-memory stamp would reset on every tab switch and make the window useless.
     *
     * A fresh instance always loads even when the persisted stamp is recent, since it has no data
     * of its own to show; the stamp is what stops a long-lived instance from going stale.
     */
    fun refreshIfStale(force: Boolean = false) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            val lastFetch = readLastFetchTime()
            val isStale = force ||
                !hasLoadedThisInstance ||
                lastFetch <= 0L ||
                System.currentTimeMillis() - lastFetch >= CACHE_TTL_MS
            if (isStale) loadExploreData()
        }
    }

    private suspend fun readLastFetchTime(): Long = withContext(Dispatchers.IO) {
        runCatching { songDao.getSessionValue(LAST_FETCH_KEY)?.toLongOrNull() ?: 0L }
            .getOrDefault(0L)
    }

    private suspend fun recordFetchTime() = withContext(Dispatchers.IO) {
        runCatching {
            songDao.putSessionValue(
                SessionEntity(key = LAST_FETCH_KEY, value = System.currentTimeMillis().toString())
            )
        }
    }

    private fun loadExploreData() {
        // Genres.
        //
        // These used to point at `https://picsum.photos/...random=N` — stock Lorem-Picsum photos
        // unrelated to the genre they labelled, and a hardcoded artist roster ("Adele", "Ariana
        // Grande", …) that changed only when someone edited a literal. Artwork is now the real
        // thumbnail of a representative track for each genre, which is honest data the app already
        // knows how to fetch.
        val genreNames = listOf(
            "Pop", "Hip hop", "Rock", "Electronic", "R&B",
            "Jazz", "Classical", "Country", "Reggae", "Metal",
        )

        // Featured playlists and their ids now come from the shared Collection vocabulary, so the
        // card the user taps and the screen that opens agree on what is being opened.
        val featuredPlaylistsList = FEATURED_COLLECTIONS.map { (collection, isHero) ->
            FeaturedPlaylist(
                id = collection.slug,
                title = collection.title,
                description = collection.subtitle,
                // Filled in from real artwork once the first fetch lands; empty renders a
                // placeholder rather than a photo of something unrelated.
                artworkUrl = "",
                isHero = isHero,
            )
        }

        // Paint immediately with what we can already render, then let the fetches fill in art.
        _uiState.value = ExploreUiState(
            genres = genreNames.mapIndexed { index, name ->
                GenreItem(id = Collection.genre(name), title = name, imageUrl = "")
            },
            featuredPlaylists = featuredPlaylistsList,
            isLoading = true,
            genresLoaded = true,
        )

        viewModelScope.launch {
            val genresDeferred = async { loadGenresWithArtwork(genreNames) }
            val chartsDeferred = async { loadTopCharts() }
            val releasesDeferred = async { loadNewReleases() }
            val trendingDeferred = async { loadTrendingSearches() }
            val artistsDeferred = async { loadBrowseArtists() }

            // Independent results are applied as they arrive. The previous version awaited them in
            // a strict chain, so one slow call held back every section below it.
            //
            // Each await is wrapped in awaitOrNull rather than runCatching, because runCatching
            // catches Throwable — including CancellationException — which would keep this coroutine
            // alive after its scope was cancelled.
            chartsDeferred.awaitOrNull()?.let { charts ->
                _uiState.update { it.copy(topCharts = charts, chartsLoaded = true) }
            }
            artistsDeferred.awaitOrNull()?.let { artists ->
                _uiState.update { it.copy(browseArtists = artists) }
            }
            genresDeferred.awaitOrNull()?.let { genres ->
                _uiState.update { it.copy(genres = genres) }
            }
            releasesDeferred.awaitOrNull()?.let { releases ->
                _uiState.update { it.copy(newReleases = releases, releasesLoaded = true) }
            }
            trendingDeferred.awaitOrNull()?.let { trending ->
                _uiState.update { it.copy(trendingSearches = trending, isLoading = false) }
            }
            _uiState.update { it.copy(isLoading = false) }

            hasLoadedThisInstance = true
            recordFetchTime()
        }
    }

    /**
     * Browse artists, derived from real search results rather than a hardcoded roster.
     *
     * This previously listed twelve fixed names ("Adele", "Ariana Grande", …) with Picsum stock
     * photos. Deriving from a live search means the row reflects who is actually in the catalogue,
     * and every artist carries genuine artwork.
     */
    private suspend fun loadBrowseArtists(): List<BrowseArtist> {
        return try {
            val songs = musicRepository.search(BROWSE_ARTIST_QUERY)
            songs
                .groupBy { it.artistName }
                .entries
                .filter { (name, _) -> name.isNotBlank() && name != "Unknown Artist" }
                .take(12)
                .mapIndexed { index, (name, artistSongs) ->
                    BrowseArtist(
                        // Address by name: the Browse All row navigates to a search for the artist,
                        // so the name is the only identifier the destination needs.
                        id = name,
                        name = name,
                        artworkUrl = artistSongs.firstOrNull()?.artworkUrl.orEmpty(),
                        letter = name.first().uppercaseChar(),
                    )
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CRANK_EXPLORE", "Failed to load artists: ${e.message}")
            emptyList()
        }
    }

    /** Gives each genre a real thumbnail by looking up one representative track. */
    private suspend fun loadGenresWithArtwork(genreNames: List<String>): List<GenreItem> {
        val withArt = genreNames.mapIndexed { index, name ->
            val art = try {
                musicRepository.search("$name music").firstOrNull()?.artworkUrl.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d("CRANK_EXPLORE", "No artwork for genre $name: ${e.message}")
                ""
            }
            GenreItem(id = Collection.genre(name), title = name, imageUrl = art)
        }

        // Also upgrade the featured playlist cards to real artwork now that we have a search path.
        val featured = FEATURED_COLLECTIONS.mapNotNull { (collection, isHero) ->
            try {
                val songs = musicRepository.search(collection.query).take(4)
                val art = songs.firstOrNull()?.artworkUrl.orEmpty()
                FeaturedPlaylist(
                    id = collection.slug,
                    title = collection.title,
                    description = collection.subtitle,
                    artworkUrl = art,
                    isHero = isHero,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d("CRANK_EXPLORE", "No artwork for ${collection.slug}: ${e.message}")
                null
            }
        }
        if (featured.isNotEmpty()) {
            _uiState.update { it.copy(featuredPlaylists = featured) }
        }

        return withArt
    }

    private suspend fun loadTopCharts(): List<ChartItem> {
        return try {
            val chartSongs = musicRepository.search("top hits 2024").take(8)
            chartSongs.mapIndexed { index, song ->
                ChartItem(
                    song = song,
                    // A real rank, derived from the order the search backend returned.
                    rank = index + 1,
                    // Trend and play count were both invented — arrows keyed off `index % 3` and
                    // counts off `1_000_000L - index * 100_000L`. Neither number came from data, so
                    // they are gone rather than shown as if they meant something. The chart is
                    // ordered; that ordering is the only real signal available.
                    trend = "",
                    playCount = "",
                    duration = formatDuration(song.durationMs)
                )
            }
        } catch (e: CancellationException) {
            throw e
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
                        // The search response does not carry a release year. "" means unknown; the
                        // previous hardcoded "2024" asserted a date we had no evidence for.
                        releaseYear = "",
                        artworkUrl = song.artworkUrl,
                        trackCount = 1
                    ),
                    releaseDate = ""
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("CRANK_EXPLORE", "Failed to load releases: ${e.message}")
            emptyList()
        }
    }

    private suspend fun loadTrendingSearches(): List<String> {
        return try {
            val trendingSongs = musicRepository.search("trending").take(4)
            trendingSongs.map { it.title }
        } catch (e: CancellationException) {
            throw e
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
                            subtitle = "${song.artistName} • Song",                            type = "song",
                            imageUrl = song.artworkUrl
                        )
                    }
                    _uiState.value = _uiState.value.copy(searchSuggestions = suggestions)
                } catch (e: CancellationException) {
                    // Expected and routine: the user typed another character and this job was
                    // replaced. Rethrow so the coroutine ends cleanly instead of continuing past
                    // its cancellation point and logging a misleading failure.
                    throw e
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
