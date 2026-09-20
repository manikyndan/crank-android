package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.data.local.SongDao
import com.crank.music.data.local.toEntity
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Artist
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val topSongs: List<Song> = emptyList(),
    /** Full albums, in backend order. Singles/EPs live in [singlesEps]. */
    val albums: List<Album> = emptyList(),
    val singlesEps: List<Album> = emptyList(),
    /** First release card, if the backend returned any. */
    val latestRelease: Album? = null,
    /** True once every top song is liked in the library. */
    val isSavedToLibrary: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * Artist detail.
 *
 * Two corrections from the previous implementation:
 *
 * 1. The route argument is an artist *name* (percent-encoded), not an opaque id. The old code
 *    read `artistId ?: "artist_1"` and then called `search(artistId)` — searching for the
 *    literal string "artist_1" whenever the route was missing, which is the same
 *    search-for-the-id mistake that broke album detail. The name is also what the search
 *    backend actually needs, since it resolves by free text.
 *
 * 2. The discography was two fabricated albums ("<Artist> - Greatest Hits", 2024) with
 *    invented track counts. Albums are now derived from the songs that came back, so the
 *    row shows releases the artist actually has. When search returns nothing, the section
 *    is empty rather than populated with fiction.
 */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository,
    private val songDao: SongDao,
) : ViewModel() {

    private val artistName: String = savedStateHandle.get<String>("artistId").orEmpty()

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        loadArtistDetails()
    }

    private fun loadArtistDetails() {
        viewModelScope.launch {
            if (artistName.isBlank()) {
                // No name in the route means there is nothing to look up. Fail visibly
                // (empty screen) instead of searching for a placeholder string.
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Songs and releases are independent backend calls; running them
                // together costs the slower of the two, not their sum.
                val songsDeferred = async { musicRepository.search(artistName) }
                val releasesDeferred = async { musicRepository.searchAlbumsWithKind(artistName) }
                val results = songsDeferred.await()
                val releases = releasesDeferred.await()

                // Everything on this screen is derived from real responses, so the
                // header, the track list and the discography all describe the same artist.
                val displayName = results.firstOrNull()?.artistName?.takeIf { it.isNotBlank() }
                    ?: releases.firstOrNull()?.album?.artistName?.takeIf { it.isNotBlank() }
                    ?: artistName

                val bannerArt = results.firstNotNullOfOrNull { song ->
                    song.artworkUrl.takeIf { it.isNotBlank() }
                } ?: releases.firstOrNull()?.album?.artworkUrl.orEmpty()

                val artist = Artist(
                    id = artistName,
                    name = displayName,
                    imageUrl = bannerArt,
                    // The search API does not expose a listener count. Zero means "unknown"
                    // and the UI omits the line, rather than showing a confident 1,250,000.
                    followerCount = 0L,
                )

                // The kind travels with each card from the parse layer: "Single"
                // and "EP" cards form their own shelf, everything else (full
                // albums plus fallback cards that declare no kind) is an album.
                // Dedupe by browse id; order is the backend's relevance order.
                val seenIds = LinkedHashSet<String>()
                val albums = mutableListOf<Album>()
                val singlesEps = mutableListOf<Album>()
                for ((album, kind) in releases) {
                    if (!seenIds.add(album.id)) continue
                    if (kind == "Single" || kind == "EP") singlesEps.add(album) else albums.add(album)
                }

                _uiState.value = ArtistDetailUiState(
                    artist = artist,
                    topSongs = results,
                    albums = albums,
                    singlesEps = singlesEps,
                    latestRelease = releases.firstOrNull()?.album,
                    isLoading = false,
                )
                refreshSavedState(results)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * True once every top song is liked in the library. Read from the liked
     * table rather than a toggle flag, so the checkmark survives restarts and
     * can never claim songs are saved when they are not.
     */
    private fun refreshSavedState(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch {
            try {
                val likedIds = withContext(Dispatchers.IO) {
                    songDao.getLikedSongsList().map { it.id }.toSet()
                }
                _uiState.value = _uiState.value.copy(
                    isSavedToLibrary = songs.all { it.id in likedIds }
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.e("CRANK_ARTIST", "Failed to check library state: ${e.message}")
            }
        }
    }

    /**
     * Saves the artist's top songs to the library as liked songs, reusing the
     * same mapping the library itself reads back. One-way and additive:
     * nothing is ever unliked or deleted here.
     */
    fun saveToLibrary() {
        val songs = _uiState.value.topSongs
        if (songs.isEmpty() || _uiState.value.isSavedToLibrary) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    songDao.insertSongs(songs.map { it.toEntity(isLiked = true) })
                }
                _uiState.value = _uiState.value.copy(isSavedToLibrary = true)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.e("CRANK_ARTIST", "Failed to save artist songs: ${e.message}")
            }
        }
    }

    /** Saves a single track to the library (per-row "Save" action). */
    fun saveTrack(song: Song) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    songDao.insertSongs(listOf(song.toEntity(isLiked = true)))
                }
                refreshSavedState(_uiState.value.topSongs)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.e("CRANK_ARTIST", "Failed to save track '${song.title}': ${e.message}")
            }
        }
    }
}
