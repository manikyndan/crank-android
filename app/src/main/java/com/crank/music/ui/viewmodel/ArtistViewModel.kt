package com.crank.music.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.core.rethrowIfCancellation
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
    private val musicRepository: MusicRepository
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
                val results = musicRepository.search(artistName)

                // Everything on this screen is derived from the search results, so the
                // header, the track list and the discography all describe the same artist.
                val displayName = results.firstOrNull()?.artistName?.takeIf { it.isNotBlank() }
                    ?: artistName

                val artist = Artist(
                    id = artistName,
                    name = displayName,
                    imageUrl = results.firstNotNullOfOrNull { song ->
                        song.artworkUrl.takeIf { it.isNotBlank() }
                    }.orEmpty(),
                    // The search API does not expose a listener count. Zero means "unknown"
                    // and the UI omits the line, rather than showing a confident 1,250,000.
                    followerCount = 0L,
                )

                _uiState.value = ArtistDetailUiState(
                    artist = artist,
                    topSongs = results,
                    albums = deriveAlbums(results),
                    isLoading = false,
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Groups the artist's search results into album rows.
     *
     * The search response carries a human-readable album title per track, so that is used as
     * the row title. Tracks with no album attribution are skipped rather than being collected
     * into a catch-all pseudo-album, which would invent a release that does not exist.
     */
    private fun deriveAlbums(songs: List<Song>): List<Album> =
        songs
            .filter { !it.albumId.isNullOrBlank() }
            .groupBy { it.albumId!! }
            .map { (albumKey, albumSongs) ->
                val lead = albumSongs.first()
                Album(
                    // The album route resolves by text, so this id is only a list key.
                    id = albumKey,
                    title = albumKey,
                    artistName = lead.artistName,
                    // No release year is returned by the search layer; blank means the UI
                    // omits the badge instead of showing a made-up "2024".
                    releaseYear = "",
                    artworkUrl = albumSongs.firstNotNullOfOrNull { song ->
                        song.artworkUrl.takeIf { it.isNotBlank() }
                    }.orEmpty(),
                    trackCount = albumSongs.size,
                )
            }
            .sortedBy { it.title.lowercase() }
}
