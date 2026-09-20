package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.data.local.SongDao
import com.crank.music.data.local.toEntity
import com.crank.music.domain.model.Playlist
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * Converts a library album row into the domain model.
 *
 * The navigation layer routes on title + artist rather than an id, because the search backend
 * resolves albums by free text — so these two strings are the ones the destination needs.
 */
fun AlbumItem.toDomainModel(): com.crank.music.domain.model.Album =
    com.crank.music.domain.model.Album(
        id = id,
        title = title,
        artistName = artistName,
        releaseYear = year,
        artworkUrl = artworkUrl,
        trackCount = songCount,
    )

/**
 * True for strings that are internal handles rather than human-readable names — YouTube
 * video and playlist ids such as `dQw4w9WgXcQ`, and URLs.
 *
 * The library stores an album *id* per song but only ever receives a display album *title* from
 * search results. This decides which ids are worth rendering as a title and which should fall
 * back to the artist name. Top-level and `internal` so the test suite exercises this exact
 * function rather than a copy that could drift.
 *
 * URLs are rejected explicitly: `https://lh3.googleusercontent.com/abc` has no spaces and is
 * within the length range, so the shape test alone would accept it and the album grid would
 * show a raw image URL to the user.
 */
internal fun looksLikeOpaqueId(value: String): Boolean {
    if (value.length !in 11..34) return false
    if (value.any { it == ' ' }) return false
    if (value.contains("://") || value.contains("//") || value.contains('.')) return false
    return value.matches(Regex("^[A-Za-z0-9_-]+$"))
}

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

/**
 * The library's own filter tabs.
 *
 * Same contract idea as [com.crank.music.domain.model.Collection]: the label the user
 * taps and the value the view model matches on travel together, so a tab can never
 * render a heading that no branch handles. Previously these were bare strings in
 * [LibraryScreen] and a separate bare-string `when` here — the classic drift that
 * produced the "tap a card, get 'Playlist'" bug elsewhere in the app.
 *
 * [ALL] is the fallback: `fromSlug` maps anything unrecognised (and null) to it, so an
 * unknown section degrades to the full library rather than to an empty screen.
 */
enum class LibrarySection(val slug: String, val label: String) {
    ALL("all", "All"),
    PLAYLISTS("playlists", "Playlists"),
    ARTISTS("artists", "Artists"),
    ALBUMS("albums", "Albums"),
    SONGS("songs", "Songs"),
    DOWNLOADED("downloaded", "Downloaded"),
    RECENTLY_ADDED("recently_added", "Recently Added"),
    HISTORY("history", "History");

    companion object {
        /** Ordered exactly as the tab row renders them. */
        val ordered: List<LibrarySection> = listOf(
            ALL, PLAYLISTS, ARTISTS, ALBUMS, SONGS, DOWNLOADED, RECENTLY_ADDED, HISTORY
        )

        fun fromSlug(slug: String?): LibrarySection =
            entries.firstOrNull { it.slug == slug } ?: ALL

        /** Tolerates the label too, so a caller holding "Songs" still resolves. */
        fun fromLabelOrSlug(value: String?): LibrarySection =
            entries.firstOrNull { it.slug == value || it.label == value } ?: ALL
    }
}

/**
 * The three derived collections at the top of the library.
 *
 * These are computed from what the user actually has — they are not search queries and
 * they are not a fixed enum in the same sense as [com.crank.music.domain.model.Collection],
 * because their contents come from the local database. The [slug] is the stable wire value
 * carried in the nav route; [resolve] is the single place that decides what each one means,
 * so the count shown on the card and the list it opens are guaranteed to be the same set.
 */
enum class SmartPlaylistKind(val slug: String, val title: String, val description: String, val icon: String) {
    MOST_PLAYED("smart_most_played", "Most Played This Month", "Your most-played songs", "play"),
    RECENTLY_ADDED("smart_recently_added", "Recently Added", "Fresh additions to your library", "clock"),
    TOP_FAVORITES("smart_top_favorites", "Top 50 Favorites", "Your all-time favorites", "star");

    /**
     * Resolves this collection against the local database.
     *
     * Shared by the Library grid and the detail destination it opens. Keeping the query in one
     * place is what stops the card and its list from disagreeing — the failure that shipped in
     * every earlier version of this screen.
     *
     * "Most Played" is backed by playback history, which is the only listening signal the app
     * records. With no history it is empty, and the card correctly reads 0 rather than
     * borrowing the liked-songs count to look populated.
     */
    suspend fun resolve(songDao: SongDao): List<Song> = when (this) {
        MOST_PLAYED -> songDao.getHistoryList(HISTORY_LIMIT).map { it.toSong() }
        RECENTLY_ADDED -> songDao.getLikedSongsList().take(RECENTLY_ADDED_LIMIT).map { it.toSong() }
        TOP_FAVORITES -> songDao.getLikedSongsList().take(TOP_FAVORITES_LIMIT).map { it.toSong() }
    }.distinctBy { it.id }

    companion object {
        fun fromSlug(slug: String?): SmartPlaylistKind? =
            slug?.let { wanted -> entries.firstOrNull { it.slug == wanted } }

        /** Rows on the "Recently Added" shelf, shared with the library grid. */
        const val RECENTLY_ADDED_LIMIT = 4

        /** "Top 50 Favorites" is capped at 50 by its own name. */
        const val TOP_FAVORITES_LIMIT = 50

        /** How much playback history feeds the "Most Played" collection. */
        const val HISTORY_LIMIT = 30
    }
}

/** Maps a local row to the domain model, so the shape is defined once. */
internal fun com.crank.music.data.local.LocalSongEntity.toSong(): Song = Song(
    id = id,
    title = title,
    artistName = artistName,
    albumId = albumId,
    albumName = albumName,
    isExplicit = isExplicit,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    isLocal = isLocal,
)

/** History rows carry the stream URL, which the local song table does not. */
internal fun com.crank.music.data.local.HistoryEntity.toSong(): Song = Song(
    id = songId,
    title = title,
    artistName = artistName,
    albumId = albumId,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
    isLocal = false,
    streamUrl = streamUrl,
)

/** One history row with its real play timestamp, for date grouping. */
data class HistoryItem(
    val song: Song,
    val playedAt: Long,
)

data class LibraryUiState(
    val isGridView: Boolean = false,
    val selectedSection: LibrarySection = LibrarySection.ALL,
    val playlists: List<Playlist> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val songs: List<Song> = emptyList(),
    val downloadedSongs: List<Song> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val historyItems: List<HistoryItem> = emptyList(),
    val smartPlaylists: List<SmartPlaylist> = emptyList(),
    /**
     * Contents of each smart playlist, keyed by slug. Resolved once during load and read
     * back by [LibraryViewModel.resolveSmartPlaylist] so the count badge on the card and
     * the rows on the detail screen can never disagree.
     */
    val smartPlaylistContents: Map<String, List<Song>> = emptyMap(),
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
                // Every read below is a local database call. Each is guarded so one failing
                // table degrades just that section instead of blanking the whole library —
                // but CancellationException is always rethrown, so a dismissed screen stops
                // work immediately instead of running to completion against a dead scope.
                val likedSongs = loadSongs("liked songs") { songDao.getLikedSongsList() }
                val downloadedSongs = loadSongs("downloads") { songDao.getDownloadedSongs() }
                val historyItems = try {
                    withContext(Dispatchers.IO) {
                        songDao.getHistoryList(100).map { entity ->
                            HistoryItem(song = entity.toSong(), playedAt = entity.playedAt)
                        }
                    }
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    Log.e("CRANK_LIBRARY", "Failed to load history: ${e.message}")
                    emptyList()
                }

                val playlists = try {
                    withContext(Dispatchers.IO) {
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
                    e.rethrowIfCancellation()
                    Log.e("CRANK_LIBRARY", "Failed to load playlists: ${e.message}")
                    emptyList()
                }

                // ── Derived collections ───────────────────────────────────────────────
                // All three are resolved through SmartPlaylistKind.resolve, which is the same
                // call the detail destination makes. The card count and the opened list
                // therefore cannot disagree — the failure that shipped in every earlier
                // version of this screen. Nothing here is invented: no stock photos, no fixed
                // artist roster, no random counts. A library with 4 liked songs shows 4.

                val contents: Map<String, List<Song>> = withContext(Dispatchers.IO) {
                    SmartPlaylistKind.entries.associate { kind ->
                        kind.slug to kind.resolve(songDao)
                    }
                }

                val smartPlaylists = SmartPlaylistKind.entries.map { kind ->
                    val songs = contents[kind.slug].orEmpty()
                    SmartPlaylist(
                        id = kind.slug,
                        title = kind.title,
                        description = kind.description,
                        icon = kind.icon,
                        songCount = songs.size,
                        // Artwork comes from the first track the collection actually holds.
                        // Empty when the collection is empty, which renders as a blank tile
                        // rather than a stock photo standing in for content that isn't there.
                        artworkUrl = songs.firstOrNull()?.artworkUrl.orEmpty(),
                    )
                }

                // Most played doubles as the recency signal for artist/album grouping, and
                // recently-added is part of the liked set, so both are already in hand.
                val mostPlayed = contents[SmartPlaylistKind.MOST_PLAYED.slug].orEmpty()

                // Artists and albums are derived by grouping the real library, so tapping
                // one always lands on something that exists in the user's own collection.
                val artists = buildArtists(likedSongs, downloadedSongs, mostPlayed)
                val albums = buildAlbums(likedSongs, downloadedSongs, mostPlayed)

                _uiState.value = LibraryUiState(
                    playlists = playlists,
                    artists = artists,
                    albums = albums,
                    songs = likedSongs,
                    downloadedSongs = downloadedSongs,
                    recentlyAdded = likedSongs.take(SmartPlaylistKind.RECENTLY_ADDED_LIMIT),
                    historyItems = historyItems,
                    smartPlaylists = smartPlaylists,
                    smartPlaylistContents = contents,
                    isLoading = false
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.e("CRANK_LIBRARY", "Failed to load library: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun loadSongs(
        label: String,
        query: suspend () -> List<com.crank.music.data.local.LocalSongEntity>,
    ): List<Song> = try {
        withContext(Dispatchers.IO) { query().map { it.toSong() } }
    } catch (e: Exception) {
        e.rethrowIfCancellation();
        Log.e("CRANK_LIBRARY", "Failed to load $label: ${e.message}")
        emptyList()
    }

    /**
     * Groups the real library into artist rows.
     *
     * Built from liked + downloaded + played songs, de-duplicated by artist name, with the
     * song count reflecting how many of that artist's tracks the user actually holds. Rows
     * with no artwork keep an empty URL so the UI shows its placeholder instead of a
     * stand-in photo.
     */
    private fun buildArtists(vararg sources: List<Song>): List<ArtistItem> =
        sources.asSequence()
            .flatten()
            .filter { it.artistName.isNotBlank() }
            .groupBy { it.artistName }
            .map { (name, songs) ->
                ArtistItem(
                    id = name,
                    name = name,
                    artworkUrl = songs.firstNotNullOfOrNull { it.artworkUrl.takeIf(String::isNotBlank) }.orEmpty(),
                    songCount = songs.distinctBy { it.id }.size,
                    letter = name.first().uppercaseChar(),
                )
            }
            .sortedBy { it.name.lowercase() }

    /**
     * Groups the real library into album rows.
     *
     * Keyed on the album title we can recover: songs carry an album *id*, but only search
     * results carry a human-readable album title. Where the id is a readable string it is
     * used; where it is opaque the track is attributed to the artist instead, so the album
     * grid never shows an empty title.
     */
    private fun buildAlbums(vararg sources: List<Song>): List<AlbumItem> =
        sources.asSequence()
            .flatten()
            .groupBy { song ->
                song.albumId?.takeIf { it.isNotBlank() && !looksLikeOpaqueId(it) }
                    ?: song.artistName.ifBlank { "Unknown Album" }
            }
            .map { (key, songs) ->
                val lead = songs.first()
                AlbumItem(
                    // `id` is unused for navigation — the album route carries title + artist
                    // because the search backend resolves albums by text, never by id.
                    id = key,
                    title = key,
                    artistName = lead.artistName,
                    artworkUrl = songs.firstNotNullOfOrNull { it.artworkUrl.takeIf(String::isNotBlank) }.orEmpty(),
                    // No release year is recorded anywhere in the library, so the badge is
                    // left blank rather than showing a made-up one.
                    year = "",
                    songCount = songs.distinctBy { it.id }.size,
                )
            }
            .sortedBy { it.title.lowercase() }

    /**
     * The rows behind a smart playlist card, resolved from the map built at load time.
     *
     * Returns an empty list for an unknown slug rather than guessing a default, matching the
     * `Collection.fromSlug` contract — an unrecognised destination shows nothing, which is
     * visible and fixable, instead of a wrong list, which is silent.
     */
    fun resolveSmartPlaylist(slug: String): List<Song> =
        _uiState.value.smartPlaylistContents[slug].orEmpty()

    /**
     * Reloads the library, e.g. when navigating back after listening, liking
     * or downloading. Local-database reads only, so this is cheap.
     */
    fun refresh() {
        loadLibraryData()
    }

    fun toggleViewMode() {
        _uiState.value = _uiState.value.copy(isGridView = !_uiState.value.isGridView)
    }

    /** Accepts a slug or a label and always lands on a real section — never a dead branch. */
    fun selectSection(section: String) {
        selectSection(LibrarySection.fromLabelOrSlug(section))
    }

    fun selectSection(section: LibrarySection) {
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
        val trimmed = state.createPlaylistName.trim()
        // Guarded here as well as in the dialog: the sheet is one caller, and a duplicate title
        // reaching the database would be silently unrecoverable from the UI.
        val isDuplicate = state.playlists.any { it.title.equals(trimmed, ignoreCase = true) }
        if (trimmed.isNotBlank() && !isDuplicate) {
            viewModelScope.launch {
                try {
                    val newPlaylist = Playlist(
                        id = UUID.randomUUID().toString(),
                        title = state.createPlaylistName.trim(),
                        songCount = 0,
                        // A brand-new playlist has no songs, so it has no artwork. It previously
                        // took a random stock photo — which meant two different playlists could
                        // show unrelated images and neither reflected its contents. Artwork is
                        // now derived from the first song as tracks are added.
                        artworkUrl = "",
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
                    e.rethrowIfCancellation()
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
                e.rethrowIfCancellation()
                Log.e("CRANK_LIBRARY", "Failed to delete playlist: ${e.message}")
            }
        }
    }

    /**
     * Adds [song] to a user playlist, creating the membership row and
     * refreshing the card's count/artwork. The song row itself is persisted
     * (preserving an existing liked flag) so the detail screen can resolve it.
     */
    fun addSongToPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (songDao.getSongById(song.id) == null) {
                        songDao.insertSong(song.toEntity())
                    }
                    val order = songDao.getSongIdsForPlaylist(playlistId).size
                    songDao.insertPlaylistSong(
                        com.crank.music.data.local.PlaylistSongCrossRef(
                            playlistId = playlistId,
                            songId = song.id,
                            songOrder = order
                        )
                    )
                    val entity = songDao.getPlaylistById(playlistId)
                    if (entity != null) {
                        val ids = songDao.getSongIdsForPlaylist(playlistId)
                        songDao.insertPlaylist(
                            entity.copy(
                                songCount = ids.size,
                                artworkUrl = entity.artworkUrl.ifBlank { song.artworkUrl }
                            )
                        )
                    }
                }
                refreshPlaylists()
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.e("CRANK_LIBRARY", "Failed to add song to playlist: ${e.message}")
            }
        }
    }

    private suspend fun refreshPlaylists() {
        try {
            val playlists = withContext(Dispatchers.IO) {
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
            _uiState.value = _uiState.value.copy(playlists = playlists)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Log.e("CRANK_LIBRARY", "Failed to refresh playlists: ${e.message}")
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
