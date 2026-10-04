package com.crank.music.feature.ytmusic

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.data.local.SongDao
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Collection
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NEW additive feature: YouTube Music-style home feed.
 *
 * Does not touch [com.crank.music.ui.viewmodel.HomeViewModel] or any existing
 * screen. It only *reads* from the same sources ([MusicRepository], [SongDao])
 * and exposes a YT Music section layout.
 *
 * Every section degrades independently: one failing query never blanks the page.
 */

data class YtMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrls: List<String>,
    val songs: List<Song> = emptyList(),
)

data class YtArtistRec(
    val id: String,
    val name: String,
    val imageUrl: String,
    val reason: String,
)

data class YtMoodCategory(
    val id: String,
    val label: String,
    val query: String,
    val collectionSlug: String,
)

data class YtRecap(
    val topSongs: List<Song>,
    val topArtist: String,
    val totalMinutes: Double,
    val historyCount: Int,
)

data class YtTrendingEntry(
    val song: Song,
    val rank: Int,
)

data class YtMusicHomeUiState(
    val isLoading: Boolean = true,
    val quickPicks: List<Song> = emptyList(),
    val mixes: List<YtMix> = emptyList(),
    val recommendedAlbums: List<Album> = emptyList(),
    val similarArtists: List<YtArtistRec> = emptyList(),
    val libraryPicks: List<Song> = emptyList(),
    val newReleases: List<Song> = emptyList(),
    val recommendedPlaylists: List<YtMix> = emptyList(),
    val continueListening: List<Song> = emptyList(),
    /** Genuine saved progress per song id (0..1). Absent = unknown, never invented. */
    val resumeProgress: Map<String, Float> = emptyMap(),
    val trending: List<YtTrendingEntry> = emptyList(),
    val moods: List<YtMoodCategory> = emptyList(),
    val recap: YtRecap? = null,
    val errorMessage: String? = null,
)

private val MIX_SPECS = listOf(
    Triple(Collection.DAILY_MIX_1, "My Mix 1", "Pop & Charts"),
    Triple(Collection.DAILY_MIX_2, "My Mix 2", "Chill & Easy"),
    Triple(Collection.DAILY_MIX_3, "Discovery Mix", "Energy & Movement"),
    Triple(Collection.DISCOVER_WEEKLY, "Discover Mix", "Fresh picks for you"),
    Triple(Collection.TIME_CAPSULE, "Time Capsule", "Nostalgic favorites"),
    Triple(Collection.ON_REPEAT, "On Repeat Mix", "Songs on repeat"),
)

private val PLAYLIST_SPECS = listOf(
    Collection.TOP_HITS,
    Collection.RAP_CAVIAR,
    Collection.ALL_OUT_2020S,
    Collection.ROCK_CLASSICS,
    Collection.CHILL_HITS,
    Collection.VIVA_LATINO,
)

private val STATIC_MOODS = listOf(
    YtMoodCategory("workout", "Workout", "workout music", Collection.DAILY_MIX_3.slug),
    YtMoodCategory("chill", "Chill", "chill hits", Collection.CHILL_HITS.slug),
    YtMoodCategory("focus", "Focus", "focus music", Collection.DAILY_MIX_2.slug),
    YtMoodCategory("party", "Party", "party hits", Collection.TOP_HITS.slug),
    YtMoodCategory("commute", "Commute", "commute songs", Collection.ON_REPEAT.slug),
    YtMoodCategory("rock", "Rock", "rock classics", Collection.ROCK_CLASSICS.slug),
    YtMoodCategory("latin", "Latin", "latin hits", Collection.VIVA_LATINO.slug),
    YtMoodCategory("throwback", "Throwback", "throwback hits", Collection.REPEAT_REWIND.slug),
)

@HiltViewModel
class YtMusicHomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val songDao: SongDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YtMusicHomeUiState(moods = STATIC_MOODS))
    val uiState: StateFlow<YtMusicHomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                // Local reads stay on IO; network reads fan out concurrently.
                val historyDeferred = async(Dispatchers.IO) {
                    runCatching { songDao.getHistoryList(30) }.getOrDefault(emptyList())
                }
                val likedDeferred = async(Dispatchers.IO) {
                    runCatching { songDao.getLikedSongsList() }.getOrDefault(emptyList())
                }
                val statsDeferred = async(Dispatchers.IO) {
                    Triple(
                        runCatching { songDao.getTopArtist() }.getOrNull(),
                        // Derived from listened time, not nominal duration: `durationMs` counted a
                        // full song even when it was skipped.
                        runCatching { songDao.getTotalListenedMs() }.getOrDefault(0L) / 60_000.0,
                        runCatching { songDao.getTotalPlayCount() }.getOrDefault(0),
                    )
                }
                // The single restorable playback position: the only genuine
                // per-track progress the app persists. Shown on the matching
                // continue-listening card; nothing is invented for other tracks.
                val resumeDeferred = async(Dispatchers.IO) {
                    runCatching {
                        val saved = songDao.getPlaybackState() ?: return@runCatching emptyMap<String, Float>()
                        if (saved.songDurationMs <= 0L || saved.positionMs <= 0L) return@runCatching emptyMap<String, Float>()
                        mapOf(saved.songId to (saved.positionMs.toFloat() / saved.songDurationMs.toFloat()).coerceIn(0f, 1f))
                    }.getOrDefault(emptyMap())
                }
                val albumsDeferred = async { runCatching { musicRepository.getHomeRecommendations() }.getOrDefault(emptyList()) }
                val trendingDeferred = async { runCatching { musicRepository.search("top hits") }.getOrDefault(emptyList()) }
                val newReleasesDeferred = async { runCatching { musicRepository.search("new releases") }.getOrDefault(emptyList()) }

                val history = historyDeferred.await()
                val liked = likedDeferred.await()
                val (topArtist, totalMinutes, historyCount) = statsDeferred.await()

                val historySongs = history.map { e ->
                    Song(
                        id = e.songId,
                        title = e.title,
                        artistName = e.artistName,
                        albumId = e.albumId,
                        durationMs = e.durationMs,
                        artworkUrl = e.artworkUrl,
                        isLocal = false,
                        streamUrl = e.streamUrl,
                    )
                }

                // Quick picks: most recent history, fallback to trending when empty.
                val trending = trendingDeferred.await()
                val quickPicks = if (historySongs.isNotEmpty()) historySongs.take(12) else trending.take(12)

                // Continue listening: next slice of history (offset so it differs from quick picks).
                val continueListening = if (historySongs.size > 6) {
                    historySongs.drop(6).take(10)
                } else {
                    emptyList()
                }

                // Mixes: one search per Collection query, artwork collage = first 4 covers.
                val mixes = MIX_SPECS.map { (collection, title, subtitle) ->
                    async {
                        val songs = runCatching { musicRepository.search(collection.query) }
                            .getOrDefault(emptyList()).take(8)
                        YtMix(
                            id = collection.slug,
                            title = title,
                            subtitle = subtitle,
                            artworkUrls = songs.map { it.artworkUrl }.filter { it.isNotBlank() }.take(4),
                            songs = songs,
                        )
                    }
                }.map { it.await() }.filter { it.songs.isNotEmpty() }

                // Recommended playlists: same treatment for Explore featured collections.
                val recommendedPlaylists = PLAYLIST_SPECS.map { collection ->
                    async {
                        val songs = runCatching { musicRepository.search(collection.query) }
                            .getOrDefault(emptyList()).take(6)
                        YtMix(
                            id = collection.slug,
                            title = collection.title,
                            subtitle = collection.subtitle,
                            artworkUrls = songs.map { it.artworkUrl }.filter { it.isNotBlank() }.take(4),
                            songs = songs,
                        )
                    }
                }.map { it.await() }.filter { it.songs.isNotEmpty() }

                // Similar artists: top artists from history -> search each for a cover image.
                val topArtistNames = historySongs
                    .groupingBy { it.artistName }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second }
                    .take(8)
                    .map { it.first }
                    .filter { it.isNotBlank() && it != "Unknown Artist" }
                val similarArtists = topArtistNames.map { name ->
                    async {
                        val match = runCatching { musicRepository.search(name) }
                            .getOrDefault(emptyList())
                            .firstOrNull { it.artworkUrl.isNotBlank() }
                        YtArtistRec(
                            id = name,
                            name = name,
                            imageUrl = match?.artworkUrl.orEmpty(),
                            reason = "Similar to $name",
                        )
                    }
                }.map { it.await() }.filter { it.imageUrl.isNotBlank() }

                // Library picks: liked songs mapped back to domain Song.
                val libraryPicks = withContext(Dispatchers.IO) {
                    liked.take(12).map { e ->
                        Song(
                            id = e.id,
                            title = e.title,
                            artistName = e.artistName,
                            albumId = e.albumId,
                            durationMs = e.durationMs,
                            artworkUrl = e.artworkUrl,
                            isLocal = e.isLocal,
                            streamUrl = e.streamUrl,
                        )
                    }
                }

                val newReleases = newReleasesDeferred.await().take(12)
                val recommendedAlbums = albumsDeferred.await().take(12)
                val resumeProgress = resumeDeferred.await()

                // Trending: chart order is the backend's result order. No
                // up/down indicators — the backend never reports movement.
                val trendingEntries = trending.take(10).mapIndexed { index, song ->
                    YtTrendingEntry(song = song, rank = index + 1)
                }

                val recap = if (historySongs.isNotEmpty()) {
                    YtRecap(
                        topSongs = historySongs.take(5),
                        topArtist = topArtist ?: topArtistNames.firstOrNull().orEmpty(),
                        totalMinutes = totalMinutes,
                        historyCount = historyCount,
                    )
                } else {
                    null
                }

                _uiState.value = YtMusicHomeUiState(
                    isLoading = false,
                    quickPicks = quickPicks,
                    mixes = mixes,
                    recommendedAlbums = recommendedAlbums,
                    similarArtists = similarArtists,
                    libraryPicks = libraryPicks,
                    newReleases = newReleases,
                    recommendedPlaylists = recommendedPlaylists,
                    continueListening = continueListening,
                    resumeProgress = resumeProgress,
                    trending = trendingEntries,
                    moods = STATIC_MOODS,
                    recap = recap,
                    errorMessage = null,
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.e("CRANK_YT_HOME", "Failed to load YT home: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Couldn't load everything. Pull to retry.",
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
