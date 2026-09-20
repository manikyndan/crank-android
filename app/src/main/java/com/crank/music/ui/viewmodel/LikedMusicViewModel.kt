package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.data.local.PlaylistEntity
import com.crank.music.data.local.PlaylistSongCrossRef
import com.crank.music.data.local.SongDao
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.DownloadRepository
import com.crank.music.domain.repository.LocalDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

data class LikedMusicUiState(
    /** Every liked song, in the order the database returned them. */
    val allSongs: List<Song> = emptyList(),
    /**
     * What the tracklist actually renders: [allSongs] with the search filter and sort applied.
     *
     * Computed once when an input changes rather than in a property getter, because a getter
     * would re-filter and re-sort the whole list on every recomposition of the screen.
     */
    val visibleSongs: List<Song> = emptyList(),
    val query: String = "",
    val sort: LikedSort = LikedSort.RECENCY,
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = true,
    val playlists: List<PlaylistEntity> = emptyList(),
    val isDownloadingAll: Boolean = false,
)

/**
 * Backs the Liked Songs screen.
 *
 * Reads the real liked set from [LocalDataSource] — the same source the Library and the player's
 * heart button write to — so liking a song anywhere shows up here, and removing one here removes
 * it everywhere. Nothing on this screen invents data.
 */
@HiltViewModel
class LikedMusicViewModel @Inject constructor(
    private val localDataSource: LocalDataSource,
    private val downloadRepository: DownloadRepository,
    private val songDao: SongDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LikedMusicUiState())
    val uiState: StateFlow<LikedMusicUiState> = _uiState.asStateFlow()

    /** The in-flight bulk download, kept so a second tap can stop it. See [toggleDownloadAll]. */
    private var downloadAllJob: Job? = null

    init {
        observeLikedSongs()
        loadPlaylists()
    }

    /**
     * The liked set, live. Because this is a Room `Flow`, a removal writes to the database and
     * the list updates from the same emission — the UI never keeps its own copy to get stale.
     */
    private fun observeLikedSongs() {
        viewModelScope.launch {
            localDataSource.getLikedSongs()
                .catch { emit(emptyList()) }
                .collect { songs ->
                    _uiState.value = recompute(
                        _uiState.value.copy(allSongs = songs, isLoading = false)
                    )
                }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            val playlists = runCatching { songDao.getPlaylists() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(playlists = playlists)
        }
    }

    // ── Search ──────────────────────────────────────────────────────────────────────

    fun setSearchActive(active: Boolean) {
        // Clearing the query on exit matters: leaving it set would keep the list filtered after
        // the field is gone, so the screen would look like it had lost songs.
        _uiState.value = recompute(
            _uiState.value.copy(
                isSearchActive = active,
                query = if (active) _uiState.value.query else "",
            )
        )
    }

    fun onQueryChange(query: String) {
        _uiState.value = recompute(_uiState.value.copy(query = query))
    }

    // ── Sort ────────────────────────────────────────────────────────────────────────

    fun setSort(sort: LikedSort) {
        _uiState.value = recompute(_uiState.value.copy(sort = sort))
    }

    // ── Removal ─────────────────────────────────────────────────────────────────────

    /**
     * Removes [song] from Liked Songs.
     *
     * Uses the existing `toggleLikeSong` rather than adding a dedicated un-like to the data
     * layer, because every song on this screen came from `WHERE isLiked = 1` — so for a row the
     * user can actually see, the toggle can only ever clear the flag. The row disappears on the
     * same database emission, which is what makes a double-removal impossible.
     */
    fun removeFromLiked(song: Song) {
        viewModelScope.launch {
            runCatching { localDataSource.toggleLikeSong(song) }
        }
    }

    // ── Bulk download ───────────────────────────────────────────────────────────────

    /**
     * Starts queueing every liked song for offline playback, or stops an in-flight run.
     *
     * A toggle because the brief calls the control one, and because being unable to stop this is
     * worse than not starting it: the loop resolves a stream and enqueues a transfer per song, so
     * on a large liked library it runs for a long time and cannot be undone by waiting.
     *
     * Sequential rather than parallel: the download manager already serialises transfers, so
     * firing them all at once would only build a queue of coroutines waiting on the same lock.
     * Already-downloaded songs are skipped so their flags are not reset.
     */
    fun toggleDownloadAll() {
        if (downloadAllJob?.isActive == true) {
            downloadAllJob?.cancel()
            downloadAllJob = null
            _uiState.value = _uiState.value.copy(isDownloadingAll = false)
            return
        }

        downloadAllJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloadingAll = true)
            try {
                _uiState.value.allSongs
                    .filterNot { it.isLocal }
                    .forEach { song ->
                        // `ensureActive` rather than a plain loop: a cancel between two songs has
                        // to stop the walk, and runCatching below would otherwise swallow the
                        // cancellation and keep going.
                        coroutineContext.ensureActive()
                        runCatching { downloadRepository.downloadSong(song) }
                    }
            } finally {
                _uiState.value = _uiState.value.copy(isDownloadingAll = false)
            }
        }
    }

    // ── Playlists ───────────────────────────────────────────────────────────────────

    fun addToPlaylist(song: Song, playlistId: String) {
        viewModelScope.launch {
            runCatching {
                songDao.insertPlaylistSong(
                    PlaylistSongCrossRef(playlistId = playlistId, songId = song.id)
                )
            }
        }
    }

    // ── Derivation ──────────────────────────────────────────────────────────────────

    /**
     * Re-applies the filter and the sort.
     *
     * The rules themselves live in [filterAndSortLikedSongs], where they are unit-tested; this
     * only decides when to run them.
     */
    private fun recompute(state: LikedMusicUiState): LikedMusicUiState =
        state.copy(
            visibleSongs = filterAndSortLikedSongs(
                songs = state.allSongs,
                query = state.query,
                sort = state.sort,
            )
        )
}
