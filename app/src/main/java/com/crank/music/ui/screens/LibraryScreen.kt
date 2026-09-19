package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Song
import com.crank.music.ui.viewmodel.AlbumItem
import com.crank.music.ui.viewmodel.ArtistItem
import com.crank.music.ui.viewmodel.LibrarySection
import com.crank.music.ui.viewmodel.LibraryViewModel
import com.crank.music.ui.viewmodel.SmartPlaylist
import com.crank.music.ui.viewmodel.toDomainModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onSmartPlaylistClick: (String) -> Unit = onPlaylistClick,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (com.crank.music.domain.model.Album) -> Unit = {},
    onBrowseClick: () -> Unit = {}
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val view = LocalView.current
    val sections = LibrarySection.ordered

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LibraryTopBar(
                isGridView = uiState.isGridView,
                isMultiSelectMode = uiState.isMultiSelectMode,
                selectedCount = uiState.selectedItems.size,
                onToggleView = {
                    libraryViewModel.toggleViewMode()
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onToggleMultiSelect = {
                    libraryViewModel.toggleMultiSelectMode()
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onSortFilterClick = {
                    libraryViewModel.showSortFilterSheet()
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onClearSelection = {
                    libraryViewModel.selectNone()
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sections) { section ->
                    val isSelected = section == uiState.selectedSection
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = tween(200), label = "bg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200), label = "text"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(bgColor)
                            .clickable {
                                libraryViewModel.selectSection(section)
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = textColor
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = uiState.selectedSection,
                transitionSpec = {
                    fadeIn(tween(300)) + slideInVertically(tween(300)) togetherWith
                    fadeOut(tween(200)) + slideOutVertically(tween(200))
                },
                label = "section"
            ) { section ->
                when (section) {
                    LibrarySection.PLAYLISTS -> PlaylistsSection(
                        playlists = uiState.playlists,
                        smartPlaylists = uiState.smartPlaylists,
                        isGridView = uiState.isGridView,
                        onCreateNew = {
                            libraryViewModel.showCreateSheet()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                        onPlaylistClick = { onPlaylistClick(it) },
                        onSmartPlaylistClick = { onSmartPlaylistClick(it) }
                    )
                    LibrarySection.ARTISTS -> ArtistsSection(
                        artists = uiState.artists,
                        onArtistClick = { onArtistClick(it) }
                    )
                    LibrarySection.ALBUMS -> AlbumsSection(
                        albums = uiState.albums,
                        onAlbumClick = { album: AlbumItem ->
                            // The album route takes title + artist, not an id: the search
                            // backend resolves albums by text. Passing the id we grouped on
                            // would make the destination search for the id itself.
                            onAlbumClick(album.toDomainModel())
                        }
                    )
                    LibrarySection.SONGS -> SongsSection(
                        songs = libraryViewModel.getFilteredSongs(),
                        isGridView = uiState.isGridView,
                        isMultiSelectMode = uiState.isMultiSelectMode,
                        selectedItems = uiState.selectedItems,
                        onSongSelect = onSongSelect,
                        onToggleSelect = { libraryViewModel.toggleItemSelection(it) }
                    )
                    LibrarySection.DOWNLOADED -> DownloadedSection(
                        songs = uiState.downloadedSongs,
                        onSongSelect = onSongSelect,
                        onFindMusic = onBrowseClick
                    )
                    LibrarySection.RECENTLY_ADDED -> RecentlyAddedSection(
                        songs = uiState.recentlyAdded,
                        onSongSelect = onSongSelect,
                        onFindMusic = onBrowseClick
                    )
                    LibrarySection.ALL -> AllSection(
                        playlists = uiState.playlists,
                        smartPlaylists = uiState.smartPlaylists,
                        songs = uiState.songs,
                        isGridView = uiState.isGridView,
                        isMultiSelectMode = uiState.isMultiSelectMode,
                        selectedItems = uiState.selectedItems,
                        onCreatePlaylist = {
                            libraryViewModel.showCreateSheet()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                        onPlaylistClick = { onPlaylistClick(it) },
                        onSmartPlaylistClick = { onSmartPlaylistClick(it) },
                        onSongSelect = onSongSelect,
                        onToggleSelect = { libraryViewModel.toggleItemSelection(it) },
                        onSeeAllPlaylists = { libraryViewModel.selectSection(LibrarySection.PLAYLISTS) },
                        onSeeAllSongs = { libraryViewModel.selectSection(LibrarySection.SONGS) },
                        onFindMusic = onBrowseClick
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(60.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                onClick = {
                    libraryViewModel.showCreateSheet()
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Playlist",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        if (uiState.isMultiSelectMode && uiState.selectedItems.isNotEmpty()) {
            MultiSelectBar(
                selectedCount = uiState.selectedItems.size,
                onSelectAll = {
                    libraryViewModel.selectAll()
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onDownload = {
                    libraryViewModel.downloadSelectedSongs()
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onDelete = {
                    libraryViewModel.deleteSelectedSongs()
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            )
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
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onDismiss = { libraryViewModel.hideCreateSheet() }
            )
        }

        if (uiState.showSortFilterSheet) {
            SortFilterModal(
                currentSort = uiState.sortBy,
                currentFilter = uiState.filterBy,
                onSortSelect = { libraryViewModel.setSortBy(it) },
                onFilterSelect = { libraryViewModel.setFilterBy(it) },
                onDismiss = { libraryViewModel.hideSortFilterSheet() }
            )
        }
    }
}

@Composable
private fun LibraryTopBar(
    isGridView: Boolean,
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    onToggleView: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onSortFilterClick: () -> Unit,
    onClearSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                text = "Your Library",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onToggleMultiSelect) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Multi-select",
                    tint = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onSortFilterClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "Sort & Filter",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onToggleView) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewModule,
                    contentDescription = "Toggle View",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun AllSection(
    playlists: List<com.crank.music.domain.model.Playlist>,
    smartPlaylists: List<SmartPlaylist>,
    songs: List<Song>,
    isGridView: Boolean,
    isMultiSelectMode: Boolean,
    selectedItems: Set<String>,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSmartPlaylistClick: (String) -> Unit,
    onSongSelect: (Song) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSeeAllPlaylists: () -> Unit,
    onSeeAllSongs: () -> Unit,
    onFindMusic: () -> Unit
) {
    if (playlists.isEmpty() && smartPlaylists.isEmpty() && songs.isEmpty()) {
        EmptyState(
            title = "Your Library Is Empty",
            subtitle = "Songs you like and playlists you create will appear here",
            ctaText = "Find Music",
            onCtaClick = onFindMusic
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        if (playlists.isNotEmpty()) {
            item {
                SectionHeader("Playlists", "See All", onClick = onSeeAllPlaylists)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CreatePlaylistCard(onClick = onCreatePlaylist)
                    }
                    items(playlists.take(5)) { playlist ->
                        LibraryPlaylistMiniCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) }
                        )
                    }
                }
            }
        }

        if (smartPlaylists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Smart Playlists", null, onClick = {})
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(smartPlaylists) { smart ->
                        SmartPlaylistCard(
                            smartPlaylist = smart,
                            onClick = { onSmartPlaylistClick(smart.id) }
                        )
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("All Songs", "${songs.size} songs", onClick = onSeeAllSongs)
            }
            items(songs.take(6)) { song ->
                SongRow(
                    song = song,
                    isSelected = song.id in selectedItems,
                    isMultiSelectMode = isMultiSelectMode,
                    onClick = {
                        if (isMultiSelectMode) onToggleSelect(song.id) else onSongSelect(song)
                    },
                    onToggleSelect = { onToggleSelect(song.id) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsSection(
    playlists: List<com.crank.music.domain.model.Playlist>,
    smartPlaylists: List<SmartPlaylist>,
    isGridView: Boolean,
    onCreateNew: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSmartPlaylistClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            CreatePlaylistCard(onClick = onCreateNew)
        }

        if (smartPlaylists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SectionHeader("Smart Playlists", null, onClick = {})
            }
            items(smartPlaylists) { smart ->
                SmartPlaylistCard(smartPlaylist = smart, onClick = { onSmartPlaylistClick(smart.id) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader("Your Playlists", "${playlists.size}", onClick = {})
        }

        if (isGridView) {
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(((playlists.size / 2 + playlists.size % 2) * 220).dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistGridCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) }
                        )
                    }
                }
            }
        } else {
            items(playlists) { playlist ->
                com.crank.music.ui.components.PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun ArtistsSection(
    artists: List<ArtistItem>,
    onArtistClick: (String) -> Unit
) {
    val grouped = artists.groupBy { it.letter }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        grouped.forEach { (letter, letterArtists) ->
            item {
                Text(
                    text = "$letter",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(letterArtists) { artist ->
                ArtistRow(
                    artist = artist,
                    onClick = { onArtistClick(artist.id) }
                )
            }
        }
    }
}

@Composable
private fun AlbumsSection(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(albums) { album ->
            AlbumGridCard(
                album = album,
                onClick = { onAlbumClick(album) }
            )
        }
    }
}

@Composable
private fun SongsSection(
    songs: List<Song>,
    isGridView: Boolean,
    isMultiSelectMode: Boolean,
    selectedItems: Set<String>,
    onSongSelect: (Song) -> Unit,
    onToggleSelect: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        items(songs) { song ->
            SongRow(
                song = song,
                isSelected = song.id in selectedItems,
                isMultiSelectMode = isMultiSelectMode,
                onClick = {
                    if (isMultiSelectMode) onToggleSelect(song.id) else onSongSelect(song)
                },
                onToggleSelect = { onToggleSelect(song.id) }
            )
        }
    }
}

@Composable
private fun DownloadedSection(
    songs: List<Song>,
    onSongSelect: (Song) -> Unit,
    onFindMusic: () -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            title = "No Downloaded Songs",
            subtitle = "Songs you download will appear here for offline listening",
            ctaText = "Find Music",
            onCtaClick = onFindMusic
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Text(
                    text = "${songs.size} songs downloaded",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(songs) { song ->
                SongRow(song = song, onClick = { onSongSelect(song) })
            }
        }
    }
}

@Composable
private fun RecentlyAddedSection(
    songs: List<Song>,
    onSongSelect: (Song) -> Unit,
    onFindMusic: () -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState(
            title = "Nothing Recently Added",
            subtitle = "Songs you add to your library will appear here",
            ctaText = "Find Music",
            onCtaClick = onFindMusic
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(songs) { song ->
                SongRow(song = song, onClick = { onSongSelect(song) })
            }
        }
    }
}

@Composable
private fun CreatePlaylistCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
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
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Create Playlist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Build your own collection",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LibraryPlaylistMiniCard(
    playlist: com.crank.music.domain.model.Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier
                .size(140.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                AsyncImage(
                    model = playlist.artworkUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${playlist.songCount} songs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaylistGridCard(
    playlist: com.crank.music.domain.model.Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                AsyncImage(
                    model = playlist.artworkUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${playlist.songCount} songs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmartPlaylistCard(
    smartPlaylist: SmartPlaylist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when (smartPlaylist.icon) {
                            "play" -> "\u25B6"
                            "clock" -> "\u23F0"
                            "star" -> "\u2605"
                            else -> "\u266B"
                        },
                        fontSize = 24.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = smartPlaylist.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = smartPlaylist.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${smartPlaylist.songCount}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ArtistRow(
    artist: ArtistItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AsyncImage(
                model = artist.artworkUrl,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${artist.songCount} songs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumGridCard(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                AsyncImage(
                    model = album.artworkUrl,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = album.year,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SongRow(
    song: Song,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    onClick: () -> Unit,
    onToggleSelect: (() -> Unit)? = null
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
        animationSpec = tween(200), label = "bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp),
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
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .then(
                        if (!isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(4.dp)) else Modifier
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

        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artistName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
            MultiSelectAction(icon = Icons.Default.ViewModule, label = "Download", onClick = onDownload)
            MultiSelectAction(icon = Icons.Default.Delete, label = "Delete", onClick = onDelete)
        }
    }
}

@Composable
private fun MultiSelectAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortFilterModal(
    currentSort: String,
    currentFilter: String,
    onSortSelect: (String) -> Unit,
    onFilterSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val sortOptions = listOf("Name", "Artist", "Album", "Date Added", "Play Count", "Duration")
    val filterOptions = listOf("None", "Downloaded", "Explicit", "Released this year")

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
                text = "Sort & Filter",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Text(
                text = "SORT BY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                items(sortOptions) { option ->
                    val isSelected = option == currentSort
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSortSelect(option) },
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Text(
                text = "FILTER BY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                items(filterOptions) { option ->
                    val isSelected = option == currentFilter
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onFilterSelect(option) },
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    ctaText: String,
    onCtaClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\uD83C\uDFB5",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(25.dp))
                .clickable { onCtaClick() },
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(25.dp)
        ) {
            Text(
                text = ctaText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (subtitle != null) {
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
