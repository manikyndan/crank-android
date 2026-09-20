package com.crank.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.AlbumCard
import com.crank.music.ui.components.CategoryTabs
import com.crank.music.ui.components.EmptyState
import com.crank.music.ui.components.SearchBar
import com.crank.music.ui.components.SectionHeader
import com.crank.music.ui.components.SongRow
import com.crank.music.ui.components.SongRowSkeleton
import com.crank.music.ui.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = hiltViewModel(),
    initialQuery: String = "",
    currentSongId: String? = null,
    isPlaying: Boolean = false,
    onSongSelect: (Song) -> Unit = {},
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onAlbumClick: (Album) -> Unit = {}
) {
    val uiState by searchViewModel.uiState.collectAsState()
    val categories = listOf("All", "Songs", "Artists", "Albums")

    LaunchedEffect(initialQuery) {
        searchViewModel.applyInitialQuery(initialQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp)
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
        )

        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = { searchViewModel.onQueryChanged(it) },
            onClearClick = { searchViewModel.onQueryChanged("") },
            onSearchSubmit = { searchViewModel.submitSearch() },
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        CategoryTabs(
            categories = categories,
            selectedCategory = uiState.activeCategory,
            onCategorySelected = { searchViewModel.onCategoryChanged(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.searchQuery.isEmpty()) {
            // Apple Music shows curated genres here; we don't have a genre feed, and inventing
            // one would put music in front of the user that the backend never returned. The
            // honest version of this screen is "here's what you searched for before".
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
            ) {
                if (uiState.recentSearches.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Recent")
                    }
                    items(uiState.recentSearches) { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { searchViewModel.recallSearch(recent) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = recent,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 14.dp)
                                )
                            }
                            IconButton(
                                onClick = { searchViewModel.removeRecentSearch(recent) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else {
                    item {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "Search Crank Music",
                            message = "Find songs, artists and albums from the YouTube Music catalogue."
                        )
                    }
                }
            }
        } else if (uiState.isLoading) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(6) {
                    SongRowSkeleton()
                }
            }
        } else {
            val hasTopResult = uiState.filteredSongs.isNotEmpty() || uiState.filteredAlbums.isNotEmpty()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (hasTopResult) {
                    // One decisive "Top Result" card. An album whose title exactly
                    // matches the query wins (the user named the album, not a song);
                    // otherwise a song wins because tapping it plays immediately.
                    val exactAlbum = uiState.filteredAlbums.firstOrNull {
                        it.title.equals(uiState.searchQuery.trim(), ignoreCase = true)
                    }
                    item {
                        SectionHeader(title = "Top Result")
                        TopResultCard(
                            song = if (exactAlbum == null) uiState.filteredSongs.firstOrNull() else null,
                            album = exactAlbum ?: uiState.filteredAlbums.firstOrNull(),
                            onClick = {
                                if (exactAlbum != null) {
                                    onAlbumClick(exactAlbum)
                                } else {
                                    val song = uiState.filteredSongs.firstOrNull()
                                    if (song != null) {
                                        onSongSelectWithContext(song, uiState.filteredSongs)
                                    } else {
                                        uiState.filteredAlbums.firstOrNull()?.let { onAlbumClick(it) }
                                    }
                                }
                            }
                        )
                    }
                }

                if (uiState.filteredAlbums.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Albums")
                    }
                    items(uiState.filteredAlbums) { album ->
                        AlbumCard(
                            album = album,
                            modifier = Modifier.fillMaxWidth(),
                            // Pass the whole album: the destination resolves it by title and
                            // artist, because an opaque album id is not something the search
                            // backend can be queried with.
                            onClick = { onAlbumClick(album) }
                        )
                    }
                }

                if (uiState.filteredSongs.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Songs")
                    }
                    items(uiState.filteredSongs) { song ->
                        SongRow(
                            song = song,
                            isCurrentlyPlaying = song.id == currentSongId,
                            isPlaying = isPlaying && song.id == currentSongId,
                            onClick = {
                                val contextList = uiState.filteredSongs
                                onSongSelectWithContext(song, contextList)
                            }
                        )
                    }
                }

                if (!hasTopResult) {
                    item {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = "No Results Found",
                            message = "We couldn't find any songs or albums matching \"${uiState.searchQuery}\"."
                        )
                    }
                }
            }
        }
    }
}

/**
 * The single hero card at the top of a results page. Prefers a song when one matched, because
 * tapping it plays immediately; falls back to an album when only albums matched.
 */
@Composable
private fun TopResultCard(
    song: Song?,
    album: Album?,
    onClick: () -> Unit
) {
    val title = song?.title ?: album?.title ?: return
    val subtitle = song?.artistName ?: album?.artistName.orEmpty()
    val artworkUrl = song?.artworkUrl ?: album?.artworkUrl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchArtwork(
            url = artworkUrl,
            modifier = Modifier.size(72.dp)
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

/** Square, cropped artwork with a neutral placeholder while the image loads. */
@Composable
private fun SearchArtwork(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
