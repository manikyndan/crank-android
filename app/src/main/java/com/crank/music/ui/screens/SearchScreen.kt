package com.crank.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.AlbumCard
import com.crank.music.ui.components.CategoryTabs
import com.crank.music.ui.components.EmptyState
import com.crank.music.ui.components.SearchBar
import com.crank.music.ui.components.SectionHeader
import com.crank.music.ui.components.SongRow
import com.crank.music.ui.components.SongRowSkeleton
import com.crank.music.ui.theme.CrankTypography
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.WarmWhite
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
            .background(ObsidianBlack)
            .padding(top = 16.dp)
    ) {
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = { searchViewModel.onQueryChanged(it) },
            onClearClick = { searchViewModel.onQueryChanged("") },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        CategoryTabs(
            categories = categories,
            selectedCategory = uiState.activeCategory,
            onCategorySelected = { searchViewModel.onCategoryChanged(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.searchQuery.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    SectionHeader(title = "Recent Searches")
                }
                items(uiState.recentSearches) { recent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { searchViewModel.onQueryChanged(recent) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = recent,
                                style = CrankTypography.bodyLarge,
                                color = WarmWhite,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                        IconButton(
                            onClick = { searchViewModel.removeRecentSearch(recent) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Search",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        } else if (uiState.isLoading) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(6) {
                    SongRowSkeleton()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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

                if (uiState.filteredSongs.isEmpty() && uiState.filteredAlbums.isEmpty()) {
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
