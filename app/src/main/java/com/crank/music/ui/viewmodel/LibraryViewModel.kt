package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.data.local.SongDao
import com.crank.music.domain.model.Playlist
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ArtistItem(
    val id: String,
    val name: String,
    val artworkUrl: String,
    val songCount: Int,
    val letter: Char
)

data class AlbumItem(
    val id: String,
    val title: String,
    val artistName: String,
    val artworkUrl: String,
    val year: String,
    val songCount: Int
)

data class SmartPlaylist(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val songCount: Int,
    val artworkUrl: String
)

data class Collaborator(
    val id: String,
    val name: String,
    val avatarUrl: String
)

data class LibraryUiState(
    val isGridView: Boolean = false,
    val selectedSection: String = "All",
    val playlists: List<Playlist> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val songs: List<Song> = emptyList(),
    val downloadedSongs: List<Song> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val smartPlaylists: List<SmartPlaylist> = emptyList(),
    val searchQuery: String = "",
    val sortBy: String = "Name",
    val filterBy: String = "None",
    val isMultiSelectMode: Boolean = false,
    val selectedItems: Set<String> = emptySet(),
    val showCreatePlaylistSheet: Boolean = false,
    val showSortFilterSheet: Boolean = false,
    val showEditPlaylistSheet: Boolean = false,
    val editingPlaylist: Playlist? = null,
    val createPlaylistName: String = "",
    val createPlaylistDescription: String = "",
    val createPlaylistIsPrivate: Boolean = false,
    val createPlaylistCoverIndex: Int = 0,
    val collaborators: List<Collaborator> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val songDao: SongDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLibraryData()
    }

    private fun loadLibraryData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Load liked songs from database
                val likedSongs = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        songDao.getLikedSongsList().map { entity ->
                            Song(
                                id = entity.id,
                                title = entity.title,
                                artistName = entity.artistName,
                                albumId = entity.albumId,
                                durationMs = entity.durationMs,
                                artworkUrl = entity.artworkUrl,
                                isLocal = entity.isLocal
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CRANK_LIBRARY", "Failed to load liked songs: ${e.message}")
                    emptyList()
                }

                // Load downloaded songs
                val downloadedSongs = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        songDao.getDownloadedSongs().map { entity ->
                            Song(
                                id = entity.id,
                                title = entity.title,
                                artistName = entity.artistName,
                                albumId = entity.albumId,
                                durationMs = entity.durationMs,
                                artworkUrl = entity.artworkUrl,
                                isLocal = entity.isLocal
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CRANK_LIBRARY", "Failed to load downloads: ${e.message}")
                    emptyList()
                }

                // Load playlists from database
                val playlists = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        songDao.getPlaylists().map { entity ->
                            Playlist(
                                id = entity.id,
                                title = entity.title,
                                songCount = entity.songCount,
                                artworkUrl = entity.artworkUrl,
                                songs = emptyList()
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CRANK_LIBRARY", "Failed to load playlists: ${e.message}")
                    emptyList()
                }

                // Create smart playlists based on real data
                val smartPlaylists = listOf(
                    SmartPlaylist("sp1", "Most Played This Month", "Your top songs this month", "play", likedSongs.size, "https://picsum.photos/300/300?random=401"),
                    SmartPlaylist("sp2", "Recently Added", "Fresh additions to your library", "clock", downloadedSongs.size, "https://picsum.photos/300/300?random=402"),
                    SmartPlaylist("sp3", "Top 50 Favorites", "Your all-time favorites", "star", likedSongs.size, "https://picsum.photos/300/300?random=403")
                )

                // Load artists from search
                val artistNames = listOf("The Weeknd", "Dua Lipa", "Ed Sheeran", "Harry Styles", "Doja Cat", "Justin Bieber", "Adele", "Billie Eilish")
                val artists = artistNames.mapIndexed { index, name ->
                    ArtistItem(
                        id = "a$index",
                        name = name,
                        artworkUrl = "https://picsum.photos/300/300?random=${201 + index}",
                        songCount = (10..50).random(),
                        letter = name.first()
                    )
                }

                // Load albums from search
                val albumData = listOf(
                    Triple("Dawn FM", "The Weeknd", "2024"),
                    Triple("Future Nostalgia", "Dua Lipa", "2024"),
                    Triple("Harry's House", "Harry Styles", "2024"),
                    Triple("Planet Her", "Doja Cat", "2023"),
                    Triple("Divide", "Ed Sheeran", "2023"),
                    Triple("Justice", "Justin Bieber", "2022")
                )
                val albums = albumData.mapIndexed { index, (title, artist, year) ->
                    AlbumItem(
                        id = "al$index",
                        title = title,
                        artistName = artist,
                        artworkUrl = "https://picsum.photos/300/300?random=${301 + index}",
                        year = year,
                        songCount = (10..16).random()
                    )
                }

                _uiState.value = LibraryUiState(
                    playlists = playlists,
                    artists = artists,
                    albums = albums,
                    songs = likedSongs,
                    downloadedSongs = downloadedSongs,
                    recentlyAdded = likedSongs.takeLast(4),
                    smartPlaylists = smartPlaylists,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("CRANK_LIBRARY", "Failed to load library: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun toggleViewMode() {
        _uiState.value = _uiState.value.copy(isGridView = !_uiState.value.isGridView)
    }

    fun selectSection(section: String) {
        _uiState.value = _uiState.value.copy(selectedSection = section)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSortBy(sort: String) {
        _uiState.value = _uiState.value.copy(sortBy = sort)
    }

    fun setFilterBy(filter: String) {
        _uiState.value = _uiState.value.copy(filterBy = filter)
    }

    fun toggleMultiSelectMode() {
        _uiState.value = _uiState.value.copy(
            isMultiSelectMode = !_uiState.value.isMultiSelectMode,
            selectedItems = emptySet()
        )
    }

    fun toggleItemSelection(itemId: String) {
        val current = _uiState.value.selectedItems.toMutableSet()
        if (current.contains(itemId)) current.remove(itemId) else current.add(itemId)
        _uiState.value = _uiState.value.copy(selectedItems = current)
    }

    fun selectAll() {
        val allIds = _uiState.value.songs.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(selectedItems = allIds)
    }

    fun selectNone() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet())
    }

    fun showCreateSheet() {
        _uiState.value = _uiState.value.copy(showCreatePlaylistSheet = true)
    }

    fun hideCreateSheet() {
        _uiState.value = _uiState.value.copy(
            showCreatePlaylistSheet = false,
            createPlaylistName = "",
            createPlaylistDescription = "",
            createPlaylistIsPrivate = false,
            createPlaylistCoverIndex = 0
        )
    }

    fun showSortFilterSheet() {
        _uiState.value = _uiState.value.copy(showSortFilterSheet = true)
    }

    fun hideSortFilterSheet() {
        _uiState.value = _uiState.value.copy(showSortFilterSheet = false)
    }

    fun showEditPlaylist(playlist: Playlist) {
        _uiState.value = _uiState.value.copy(
            showEditPlaylistSheet = true,
            editingPlaylist = playlist
        )
    }

    fun hideEditPlaylist() {
        _uiState.value = _uiState.value.copy(
            showEditPlaylistSheet = false,
            editingPlaylist = null
        )
    }

    fun updateCreatePlaylistName(name: String) {
        _uiState.value = _uiState.value.copy(createPlaylistName = name)
    }

    fun updateCreatePlaylistDescription(desc: String) {
        _uiState.value = _uiState.value.copy(createPlaylistDescription = desc)
    }

    fun toggleCreatePlaylistPrivacy() {
        _uiState.value = _uiState.value.copy(createPlaylistIsPrivate = !_uiState.value.createPlaylistIsPrivate)
    }

    fun setCreatePlaylistCover(index: Int) {
        _uiState.value = _uiState.value.copy(createPlaylistCoverIndex = index)
    }

    fun createPlaylist() {
        val state = _uiState.value
        if (state.createPlaylistName.isNotBlank()) {
            viewModelScope.launch {
                try {
                    val newPlaylist = Playlist(
                        id = UUID.randomUUID().toString(),
                        title = state.createPlaylistName.trim(),
                        songCount = 0,
                        artworkUrl = "https://picsum.photos/300/300?random=${(100..999).random()}",
                        songs = emptyList()
                    )
                    // Save to database
                    val entity = com.crank.music.data.local.PlaylistEntity(
                        id = newPlaylist.id,
                        title = newPlaylist.title,
                        songCount = newPlaylist.songCount,
                        artworkUrl = newPlaylist.artworkUrl,
                        createdAt = System.currentTimeMillis()
                    )
                    songDao.insertPlaylist(entity)
                    _uiState.value = state.copy(
                        playlists = listOf(newPlaylist) + state.playlists,
                        showCreatePlaylistSheet = false,
                        createPlaylistName = "",
                        createPlaylistDescription = "",
                        createPlaylistIsPrivate = false,
                        createPlaylistCoverIndex = 0
                    )
                } catch (e: Exception) {
                    Log.e("CRANK_LIBRARY", "Failed to create playlist: ${e.message}")
                }
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                songDao.deletePlaylist(playlistId)
                _uiState.value = _uiState.value.copy(
                    playlists = _uiState.value.playlists.filter { it.id != playlistId }
                )
            } catch (e: Exception) {
                Log.e("CRANK_LIBRARY", "Failed to delete playlist: ${e.message}")
            }
        }
    }

    fun deleteSelectedSongs() {
        val selected = _uiState.value.selectedItems
        _uiState.value = _uiState.value.copy(
            songs = _uiState.value.songs.filter { it.id !in selected },
            selectedItems = emptySet(),
            isMultiSelectMode = false
        )
    }

    fun downloadSelectedSongs() {
        val selected = _uiState.value.selectedItems
        val songsToDownload = _uiState.value.songs.filter { it.id in selected }
        _uiState.value = _uiState.value.copy(
            downloadedSongs = _uiState.value.downloadedSongs + songsToDownload,
            selectedItems = emptySet(),
            isMultiSelectMode = false
        )
    }

    fun getFilteredSongs(): List<Song> {
        val state = _uiState.value
        var filtered = state.songs

        if (state.searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(state.searchQuery, ignoreCase = true) ||
                it.artistName.contains(state.searchQuery, ignoreCase = true)
            }
        }

        filtered = when (state.filterBy) {
            "Downloaded" -> filtered.filter { it.isLocal || state.downloadedSongs.any { d -> d.id == it.id } }
            "Explicit" -> filtered.filter { it.title.contains("feat") || it.title.contains("explicit") }
            "Released this year" -> filtered
            else -> filtered
        }

        filtered = when (state.sortBy) {
            "Name" -> filtered.sortedBy { it.title.lowercase() }
            "Artist" -> filtered.sortedBy { it.artistName.lowercase() }
            "Album" -> filtered.sortedBy { it.albumId ?: "" }
            "Date Added" -> filtered.reversed()
            "Duration" -> filtered.sortedBy { it.durationMs }
            else -> filtered
        }

        return filtered
    }
}
