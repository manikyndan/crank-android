package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Collection
import com.crank.music.domain.model.Playlist
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.ShimmerBox
import com.crank.music.ui.viewmodel.AlbumItem
import com.crank.music.ui.viewmodel.ArtistItem
import com.crank.music.ui.viewmodel.LibrarySection
import com.crank.music.ui.viewmodel.LibraryViewModel
import com.crank.music.ui.viewmodel.SmartPlaylist
import com.crank.music.ui.viewmodel.SmartPlaylistKind
import com.crank.music.ui.viewmodel.looksLikeOpaqueId
import com.crank.music.ui.viewmodel.toDomainModel

/**
 * YouTube Music-style Library.
 *
 * Same public contract as before (identical parameters, so MainScreen needs no
 * changes) and the same data (LibraryViewModel over the local database — no new
 * backend, no invented content). Only the internal layout changed: header with
 * sort, filter chips, pinned History/Liked rows, one vertical list of
 * type-specific rows, per-filter empty states, and a 3-dot options sheet whose
 * actions all resolve through existing navigation and ViewModel calls.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onSmartPlaylistClick: (String) -> Unit = onPlaylistClick,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onBrowseClick: () -> Unit = {}
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val view = LocalView.current
    var optionsTarget by remember { mutableStateOf<LibraryOptionsTarget?>(null) }

    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    val mostPlayed = uiState.smartPlaylistContents[SmartPlaylistKind.MOST_PLAYED.slug].orEmpty()
    val recentSongs = if (mostPlayed.isNotEmpty()) mostPlayed else uiState.recentlyAdded
    val likedCount = uiState.songs.size
    val historyCount = uiState.historyItems.size

    // Refresh on entry so likes/downloads/plays from other screens show up.
    LaunchedEffect(Unit) {
        libraryViewModel.refresh()
    }

    // "Date Added" leaves lists in their stored order, which is newest-first for
    // playlists; only "A to Z" reorders. Artists/albums already arrive sorted.
    val visiblePlaylists = if (uiState.sortBy == "Name") {
        uiState.playlists.sortedBy { it.title.lowercase() }
    } else {
        uiState.playlists
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                YtLibraryHeader(
                    isMultiSelectMode = uiState.isMultiSelectMode,
                    selectedCount = uiState.selectedItems.size,
                    onSortClick = {
                        libraryViewModel.showSortFilterSheet()
                        haptic()
                    },
                    onClearSelection = {
                        libraryViewModel.selectNone()
                        libraryViewModel.toggleMultiSelectMode()
                        haptic()
                    }
                )
            }

            item {
                YtFilterChips(
                    selected = uiState.selectedSection,
                    onSelect = { section ->
                        // Re-tapping the active filter returns to the overview.
                        libraryViewModel.selectSection(
                            if (section == uiState.selectedSection) LibrarySection.ALL else section
                        )
                        haptic()
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (uiState.isLoading && uiState.songs.isEmpty() && uiState.playlists.isEmpty()) {
                item { YtLoadingRows() }
            } else {
                when (uiState.selectedSection) {
                    LibrarySection.ALL -> {
                        item {
                            YtPinnedRow(
                                icon = Icons.Default.History,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                                tileColor = MaterialTheme.colorScheme.surfaceVariant,
                                title = "History",
                                subtitle = "Playlist • ${countSongs(historyCount)}",
                                onClick = {
                                    libraryViewModel.selectSection(LibrarySection.HISTORY)
                                    haptic()
                                }
                            )
                        }
                        item {
                            YtPinnedRow(
                                icon = Icons.Default.ThumbUp,
                                iconTint = MaterialTheme.colorScheme.onPrimary,
                                tileColor = MaterialTheme.colorScheme.primary,
                                title = "Liked Music",
                                subtitle = "Playlist • ${countSongs(likedCount)}",
                                onClick = {
                                    libraryViewModel.selectSection(LibrarySection.SONGS)
                                    haptic()
                                }
                            )
                        }
                        item {
                            YtNewPlaylistRow(onClick = {
                                libraryViewModel.showCreateSheet()
                                haptic()
                            })
                        }
                        if (visiblePlaylists.isNotEmpty()) {
                            item { YtListSubheader("Playlists") }
                            items(visiblePlaylists.take(5), key = { it.id }) { playlist ->
                                YtPlaylistRow(
                                    playlist = playlist,
                                    onClick = {
                                        onPlaylistClick(playlist.id)
                                        haptic()
                                    },
                                    onMenuClick = {
                                        optionsTarget = LibraryOptionsTarget.UserPlaylist(playlist)
                                        haptic()
                                    }
                                )
                            }
                        }
                        val previewSongs = libraryViewModel.getFilteredSongs().take(5)
                        if (previewSongs.isNotEmpty()) {
                            item { YtListSubheader("Songs") }
                            items(previewSongs, key = { it.id }) { song ->
                                YtSongRow(
                                    song = song,
                                    isSelected = song.id in uiState.selectedItems,
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onClick = {
                                        if (uiState.isMultiSelectMode) {
                                            libraryViewModel.toggleItemSelection(song.id)
                                        } else {
                                            onSongSelect(song)
                                        }
                                        haptic()
                                    },
                                    onLongClick = {
                                        if (!uiState.isMultiSelectMode) {
                                            libraryViewModel.toggleMultiSelectMode()
                                            libraryViewModel.toggleItemSelection(song.id)
                                            haptic()
                                        }
                                    },
                                    onToggleSelect = { libraryViewModel.toggleItemSelection(song.id) },
                                    onMenuClick = {
                                        optionsTarget = LibraryOptionsTarget.SongItem(song)
                                        haptic()
                                    }
                                )
                            }
                        }
                        if (visiblePlaylists.isEmpty() && previewSongs.isEmpty()) {
                            item {
                                YtEmptyState(
                                    icon = Icons.Default.MusicNote,
                                    title = "Your library is empty",
                                    message = "Songs you like and playlists you create will show up here",
                                    ctaText = "Browse music",
                                    onCtaClick = onBrowseClick
                                )
                            }
                        }
                    }

                    LibrarySection.RECENTLY_ADDED -> {
                        if (recentSongs.isEmpty()) {
                            item {
                                YtEmptyState(
                                    icon = Icons.Default.History,
                                    title = "Nothing here yet",
                                    message = "Music you play will show up here",
                                    ctaText = "Browse music",
                                    onCtaClick = onBrowseClick
                                )
                            }
                        } else {
                            items(recentSongs, key = { it.id }) { song ->
                                YtSongRow(
                                    song = song,
                                    onClick = {
                                        onSongSelect(song)
                                        haptic()
                                    },
                                    onMenuClick = {
                                        optionsTarget = LibraryOptionsTarget.SongItem(song)
                                        haptic()
                                    }
                                )
                            }
                        }
                    }

                    LibrarySection.PLAYLISTS -> {
                        item {
                            YtNewPlaylistRow(onClick = {
                                libraryViewModel.showCreateSheet()
                                haptic()
                            })
                        }
                        val smartVisible = uiState.smartPlaylists.filter {
                            it.id != SmartPlaylistKind.MOST_PLAYED.slug
                        }
                        items(smartVisible, key = { it.id }) { smart ->
                            YtSmartPlaylistRow(
                                smart = smart,
                                onClick = {
                                    onSmartPlaylistClick(smart.id)
                                    haptic()
                                },
                                onMenuClick = {
                                    optionsTarget = LibraryOptionsTarget.SmartRow(smart)
                                    haptic()
                                }
                            )
                        }
                        if (visiblePlaylists.isEmpty() && smartVisible.isEmpty()) {
                            item {
                                YtEmptyState(
                                    icon = Icons.Default.FolderOpen,
                                    title = "Playlists you create will show up here",
                                    message = "Build your own collections of the music you love",
                                    ctaText = "New playlist",
                                    onCtaClick = {
                                        libraryViewModel.showCreateSheet()
                                        haptic()
                                    }
                                )
                            }
                        } else {
                            items(visiblePlaylists, key = { it.id }) { playlist ->
                                YtPlaylistRow(
                                    playlist = playlist,
                                    onClick = {
                                        onPlaylistClick(playlist.id)
                                        haptic()
                                    },
                                    onMenuClick = {
                                        optionsTarget = LibraryOptionsTarget.UserPlaylist(playlist)
                                        haptic()
                                    }
                                )
                            }
                        }
                    }

                    LibrarySection.SONGS -> {
                        val songs = libraryViewModel.getFilteredSongs()
                        if (songs.isEmpty()) {
                            item {
                                YtEmptyState(
                                    icon = Icons.Default.MusicNote,
                                    title = "Songs you save will show up here",
                                    message = "Tap the like button on any song to keep it in your library",
                                    ctaText = "Browse music",
                                    onCtaClick = onBrowseClick
                                )
                            }
                        } else {
                            items(songs, key = { it.id }) { song ->
                                YtSongRow(
                                    song = song,
                                    isSelected = song.id in uiState.selectedItems,
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onClick = {
                                        if (uiState.isMultiSelectMode) {
                                            libraryViewModel.toggleItemSelection(song.id)
                                        } else {
                                            onSongSelect(song)
                                        }
                                        haptic()
                                    },
                                    onLongClick = {
                                        if (!uiState.isMultiSelectMode) {
                                            libraryViewModel.toggleMultiSelectMode()
                                            libraryViewModel.toggleItemSelection(song.id)
                                            haptic()
                                        }
                                    },
                                    onToggleSelect = { libraryViewModel.toggleItemSelection(song.id) },
                                    onMenuClick = {
                                        optionsTarget = LibraryOptionsTarget.SongItem(song)
                                        haptic()
                                    }
                                )
                            }
                        }
                    }

                    LibrarySection.ALBUMS -> {
                        if (uiState.albums.isEmpty()) {
                            item {
                                YtEmptyState(
                                    icon = Icons.Default.Album,
                                    title = "Albums you save will show up here",
                                    message = "Albums from songs in your library appear automatically",
                                    ctaText = "Browse albums",
                                    onCtaClick = onBrowseClick
                                )
                            }
                        } else {
                            items(uiState.albums, key = { it.id }) { album ->
                                YtAlbumRow(
                                    album = album,
                                    onClick = {
                                        onAlbumClick(album.toDomainModel())
                                        haptic()
                                    },
                                    onMenuClick = {
                                        optionsTarget = LibraryOptionsTarget.AlbumRowItem(album)
                                        haptic()
                                    }
                                )
                            }
                        }
                    }

                    LibrarySection.ARTISTS -> {
                        if (uiState.artists.isEmpty()) {
                            item {
                                YtEmptyState(
                                    icon = Icons.Default.Person,
                                    title = "Artists you follow will show up here",
                                    message = "Artists from songs in your library appear automatically",
                                    ctaText = "Browse artists",
                                    onCtaClick = onBrowseClick
                                )
                            }
                        } else {
                            items(uiState.artists, key = { it.id }) { artist ->
                                YtArtistRow(
                                    artist = artist,
                                    onClick = {
                                        onArtistClick(artist.id)
                                        haptic()
                                    },
                                    onMenuClick = {
                                        optionsTarget = LibraryOptionsTarget.ArtistRowItem(artist)
                                        haptic()
                                    }
                                )
                            }
                        }
                    }

                    LibrarySection.DOWNLOADED -> {
                        if (uiState.downloadedSongs.isEmpty()) {
                            item {
                                YtEmptyState(
                                    icon = Icons.Default.Download,
                                    title = "Music you download will show up here",
                                    message = "Download songs to listen offline",
                                    ctaText = "Browse music",
                                    onCtaClick = onBrowseClick
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = countSongs(uiState.downloadedSongs.size) + " downloaded",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(uiState.downloadedSongs, key = { it.id }) { song ->
                                YtSongRow(
                                    song = song,
                                    onClick = {
                                        onSongSelect(song)
                                        haptic()
                                    },
                                    onMenuClick = {
                                        optionsTarget = LibraryOptionsTarget.SongItem(song)
                                        haptic()
                                    }
                                )
                            }
                        }
                    }

                    LibrarySection.HISTORY -> {
                        if (uiState.historyItems.isEmpty()) {
                            item {
                                YtEmptyState(
                                    icon = Icons.Default.History,
                                    title = "No listening history yet",
                                    message = "Songs you play will show up here",
                                    ctaText = "Browse music",
                                    onCtaClick = onBrowseClick
                                )
                            }
                        } else {
                            // Newest-first already; group consecutive rows by recency.
                            var lastGroup: String? = null
                            uiState.historyItems.forEach { entry ->
                                val group = historyGroup(entry.playedAt)
                                if (group != lastGroup) {
                                    lastGroup = group
                                    item(key = "history-header-$group") {
                                        YtListSubheader(group)
                                    }
                                }
                                item(key = "history-${entry.song.id}-$group") {
                                    YtSongRow(
                                        song = entry.song,
                                        onClick = {
                                            onSongSelect(entry.song)
                                            haptic()
                                        },
                                        onMenuClick = {
                                            optionsTarget =
                                                LibraryOptionsTarget.SongItem(entry.song)
                                            haptic()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isMultiSelectMode && uiState.selectedItems.isNotEmpty()) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                MultiSelectBar(
                    selectedCount = uiState.selectedItems.size,
                    onSelectAll = {
                        libraryViewModel.selectAll()
                        haptic()
                    },
                    onDownload = {
                        libraryViewModel.downloadSelectedSongs()
                        haptic()
                    },
                    onDelete = {
                        libraryViewModel.deleteSelectedSongs()
                        haptic()
                    }
                )
            }
        }

        if (uiState.showCreatePlaylistSheet) {
            CreatePlaylistModal(
                name = uiState.createPlaylistName,
                description = uiState.createPlaylistDescription,
                isPrivate = uiState.createPlaylistIsPrivate,
                coverIndex = uiState.createPlaylistCoverIndex,
                onNameChange = { libraryViewModel.updateCreatePlaylistName(it) },
                onDescriptionChange = { libraryViewModel.updateCreatePlaylistDescription(it) },
                onTogglePrivacy = { libraryViewModel.toggleCreatePlaylistPrivacy() },
                onCoverSelect = { libraryViewModel.setCreatePlaylistCover(it) },
                onCreate = {
                    libraryViewModel.createPlaylist()
                    haptic()
                },
                onDismiss = { libraryViewModel.hideCreateSheet() }
            )
        }

        if (uiState.showSortFilterSheet) {
            YtSortSheet(
                currentSort = uiState.sortBy,
                onSortSelect = {
                    libraryViewModel.setSortBy(it)
                    libraryViewModel.hideSortFilterSheet()
                    haptic()
                },
                onDismiss = { libraryViewModel.hideSortFilterSheet() }
            )
        }

        optionsTarget?.let { target ->
            YtOptionsSheet(
                target = target,
                onPlaySong = {
                    onSongSelect(it)
                    optionsTarget = null
                    haptic()
                },
                onOpenPlaylist = {
                    onPlaylistClick(it)
                    optionsTarget = null
                    haptic()
                },
                onOpenSmart = {
                    onSmartPlaylistClick(it)
                    optionsTarget = null
                    haptic()
                },
                onOpenArtist = {
                    onArtistClick(it)
                    optionsTarget = null
                    haptic()
                },
                onOpenAlbum = {
                    onAlbumClick(it)
                    optionsTarget = null
                    haptic()
                },
                onDeletePlaylist = {
                    libraryViewModel.deletePlaylist(it)
                    optionsTarget = null
                    haptic()
                },
                onDismiss = { optionsTarget = null }
            )
        }
    }
}

/** Filter chips in YT order. Subscriptions has no backend here, so it is omitted
 * rather than shown as a permanently empty tab. */
private val YT_FILTER_ORDER = listOf(
    LibrarySection.RECENTLY_ADDED to "Recent",
    LibrarySection.PLAYLISTS to "Playlists",
    LibrarySection.SONGS to "Songs",
    LibrarySection.ALBUMS to "Albums",
    LibrarySection.ARTISTS to "Artists",
    LibrarySection.DOWNLOADED to "Downloads",
    LibrarySection.HISTORY to "History",
)

/** Sort values are the exact strings LibraryViewModel.getFilteredSongs matches on. */
private val YT_SORT_OPTIONS = listOf(
    "Date Added" to "Recently added",
    "Name" to "A to Z",
    "Artist" to "Artist",
    "Duration" to "Duration",
)

private sealed interface LibraryOptionsTarget {
    data class SongItem(val song: Song) : LibraryOptionsTarget
    data class UserPlaylist(val playlist: Playlist) : LibraryOptionsTarget
    data class SmartRow(val smart: SmartPlaylist) : LibraryOptionsTarget
    data class ArtistRowItem(val artist: ArtistItem) : LibraryOptionsTarget
    data class AlbumRowItem(val album: AlbumItem) : LibraryOptionsTarget
}

private fun countSongs(count: Int): String = if (count == 1) "1 song" else "$count songs"

/** Buckets a play timestamp into Today / Yesterday / Last Week / Older. */
private fun historyGroup(playedAt: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = playedAt }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay) return "Today"
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        yesterday.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    ) return "Yesterday"
    return if (now.timeInMillis - playedAt < 7L * 24 * 60 * 60 * 1000) "Last Week" else "Older"
}

/** Recovers the display album title with the same rule the ViewModel groups on. */
private fun displayAlbumTitle(song: Song): String =
    song.albumId?.takeIf { it.isNotBlank() && !looksLikeOpaqueId(it) }
        ?: song.artistName.ifBlank { "Unknown Album" }

private fun albumForSong(song: Song): Album = Album(
    id = song.albumId.orEmpty(),
    title = displayAlbumTitle(song),
    artistName = song.artistName,
    releaseYear = "",
    artworkUrl = song.artworkUrl,
    trackCount = 0,
)

@Composable
private fun YtLibraryHeader(
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    onSortClick: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (!isMultiSelectMode) {
            IconButton(onClick = onSortClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun YtFilterChips(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(YT_FILTER_ORDER, key = { it.first.slug }) { (section, label) ->
            val isSelected = section == selected
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = tween(200),
                label = "chip_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.background
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(200),
                label = "chip_text"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onSelect(section) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun YtListSubheader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/** Shared 56dp artwork tile: square 8dp radius, or circular for artists. */
@Composable
private fun YtArtwork(
    url: String,
    contentDescription: String?,
    circular: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(56.dp),
        shape = if (circular) CircleShape else RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (url.isNotBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun YtPinnedRow(
    icon: ImageVector,
    iconTint: Color,
    tileColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(8.dp),
            color = tileColor,
            tonalElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun YtNewPlaylistRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "New playlist",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun YtPlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        YtArtwork(
            url = playlist.artworkUrl,
            contentDescription = playlist.title
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Playlist • ${countSongs(playlist.songCount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun YtSmartPlaylistRow(
    smart: SmartPlaylist,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (smart.artworkUrl.isNotBlank()) {
            YtArtwork(url = smart.artworkUrl, contentDescription = smart.title)
        } else {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (smart.icon) {
                            "play" -> Icons.Default.PlayArrow
                            "clock" -> Icons.Default.History
                            "star" -> Icons.Default.ThumbUp
                            else -> Icons.Default.MusicNote
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = smart.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Playlist • ${countSongs(smart.songCount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YtSongRow(
    song: Song,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleSelect: (() -> Unit)? = null,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            val checkScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.8f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "check"
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .scale(checkScale)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                    .then(
                        if (!isSelected) {
                            Modifier.border(
                                1.5.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                RoundedCornerShape(4.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onToggleSelect?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        YtArtwork(url = song.artworkUrl, contentDescription = song.title)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isMultiSelectMode) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun YtAlbumRow(
    album: AlbumItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        YtArtwork(url = album.artworkUrl, contentDescription = album.title)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Album • ${album.artistName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun YtArtistRow(
    artist: ArtistItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        YtArtwork(
            url = artist.artworkUrl,
            contentDescription = artist.name,
            circular = true
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Artist • ${countSongs(artist.songCount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun YtEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    ctaText: String,
    onCtaClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onCtaClick() },
            color = MaterialTheme.colorScheme.onBackground,
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                text = ctaText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun YtLoadingRows(count: Int = 8) {
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(count) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YtSortSheet(
    currentSort: String,
    onSortSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            YT_SORT_OPTIONS.forEach { (value, label) ->
                val isSelected = value == currentSort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSortSelect(value) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YtOptionsSheet(
    target: LibraryOptionsTarget,
    onPlaySong: (Song) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenSmart: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    val headerArtwork: String
    val headerTitle: String
    val headerSubtitle: String
    val actions: List<YtOptionAction>
    when (target) {
        is LibraryOptionsTarget.SongItem -> {
            headerArtwork = target.song.artworkUrl
            headerTitle = target.song.title
            headerSubtitle = target.song.artistName
            actions = listOf(
                YtOptionAction(Icons.Default.PlayArrow, "Play") {
                    onPlaySong(target.song)
                },
                YtOptionAction(Icons.Default.Person, "Go to artist") {
                    onOpenArtist(target.song.artistName)
                },
                YtOptionAction(Icons.Default.Album, "Go to album") {
                    onOpenAlbum(albumForSong(target.song))
                },
            )
        }
        is LibraryOptionsTarget.UserPlaylist -> {
            headerArtwork = target.playlist.artworkUrl
            headerTitle = target.playlist.title
            headerSubtitle = "Playlist • ${countSongs(target.playlist.songCount)}"
            actions = listOf(
                YtOptionAction(Icons.Default.FolderOpen, "Open") {
                    onOpenPlaylist(target.playlist.id)
                },
                YtOptionAction(Icons.Default.Delete, "Delete", isDestructive = true) {
                    onDeletePlaylist(target.playlist.id)
                },
            )
        }
        is LibraryOptionsTarget.SmartRow -> {
            headerArtwork = target.smart.artworkUrl
            headerTitle = target.smart.title
            headerSubtitle = "Playlist • ${countSongs(target.smart.songCount)}"
            actions = listOf(
                YtOptionAction(Icons.Default.FolderOpen, "Open") {
                    onOpenSmart(target.smart.id)
                },
            )
        }
        is LibraryOptionsTarget.ArtistRowItem -> {
            headerArtwork = target.artist.artworkUrl
            headerTitle = target.artist.name
            headerSubtitle = "Artist • ${countSongs(target.artist.songCount)}"
            actions = listOf(
                YtOptionAction(Icons.Default.Person, "Open artist") {
                    onOpenArtist(target.artist.id)
                },
            )
        }
        is LibraryOptionsTarget.AlbumRowItem -> {
            headerArtwork = target.album.artworkUrl
            headerTitle = target.album.title
            headerSubtitle = "Album • ${target.album.artistName}"
            actions = listOf(
                YtOptionAction(Icons.Default.Album, "Open album") {
                    onOpenAlbum(target.album.toDomainModel())
                },
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                YtArtwork(url = headerArtwork, contentDescription = headerTitle)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = headerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { action.onClick() }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = if (action.isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (action.isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private data class YtOptionAction(
    val icon: ImageVector,
    val label: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun MultiSelectBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MultiSelectAction(icon = Icons.Default.Check, label = "All", onClick = onSelectAll)
            MultiSelectAction(icon = Icons.Default.Download, label = "Download", onClick = onDownload)
            MultiSelectAction(icon = Icons.Default.Delete, label = "Delete", onClick = onDelete)
        }
    }
}

@Composable
private fun MultiSelectAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlaylistModal(
    name: String,
    description: String,
    isPrivate: Boolean,
    coverIndex: Int,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTogglePrivacy: () -> Unit,
    onCoverSelect: (Int) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val coverColors = listOf(
        Color(0xFFD4AF37),
        Color(0xFFE53935),
        Color(0xFF43A047),
        Color(0xFF1E88E5),
        Color(0xFF8E24AA),
        Color(0xFFFF8F00),
        Color(0xFF00897B),
        Color(0xFF5C6BC0)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Create Playlist",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Text(
                text = "COVER ART",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                items(coverColors.size) { index ->
                    val isSelected = index == coverIndex
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "scale"
                    )
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(CircleShape)
                            .clickable { onCoverSelect(index) },
                        shape = CircleShape,
                        color = coverColors[index],
                        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground) else null
                    ) {
                        if (isSelected) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("Playlist Name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                placeholder = { Text("Description (optional)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onTogglePrivacy() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Private Playlist",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Only you can see this playlist",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPrivate,
                    onCheckedChange = { onTogglePrivacy() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) onCreate()
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Create Playlist",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
