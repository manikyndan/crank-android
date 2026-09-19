package com.crank.music.feature.ytmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.ui.theme.CrankRadius
import com.crank.music.ui.theme.CrankSpacing

/**
 * NEW additive screen: YouTube Music-style home page.
 *
 * Vertical page of horizontal sections. All taps delegate to the existing
 * player / navigation callbacks supplied by MainScreen — no duplicate state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtMusicHomeScreen(
    viewModel: YtMusicHomeViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onPlaylistClick: (String) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = uiState.isLoading && uiState.quickPicks.isNotEmpty(),
        onRefresh = { viewModel.load() },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item {
                YtHomeHeader(onRefresh = { viewModel.load() })
            }

            // 1. Quick Picks -------------------------------------------------
            item {
                YtSectionHeader(
                    title = "Quick picks",
                    onSeeAllClick = if (uiState.quickPicks.isNotEmpty()) {
                        { uiState.quickPicks.firstOrNull()?.let { onSongSelectWithContext(it, uiState.quickPicks) } }
                    } else null,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (uiState.isLoading && uiState.quickPicks.isEmpty()) {
                    YtLoadingRow()
                } else if (uiState.quickPicks.isEmpty()) {
                    YtEmptyHint("Your quick picks will appear here after you play something.")
                } else {
                    YtHorizontalScrollSection(items = uiState.quickPicks) { song ->
                        YtQuickPickCard(song = song, onClick = {
                            onSongSelectWithContext(song, uiState.quickPicks)
                        })
                    }
                }
            }

            // 2. Mixed for you ------------------------------------------------
            if (uiState.isLoading && uiState.mixes.isEmpty()) {
                item {
                    YtSectionHeader(title = "Mixed for you")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtLoadingRow(cardWidth = 160, cardHeight = 160)
                }
            } else if (uiState.mixes.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Mixed for you")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = uiState.mixes) { mix ->
                        YtMixCard(mix = mix, onClick = { onPlaylistClick(mix.id) })
                    }
                }
            }

            // 3. Recommended albums -------------------------------------------
            if (uiState.isLoading && uiState.recommendedAlbums.isEmpty()) {
                item {
                    YtSectionHeader(title = "Recommended albums")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtLoadingRow(cardWidth = 140, cardHeight = 140)
                }
            } else if (uiState.recommendedAlbums.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Recommended albums")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = uiState.recommendedAlbums) { album ->
                        YtAlbumCard(album = album, onClick = { onAlbumClick(album) })
                    }
                }
            }

            // 4. Similar artists ----------------------------------------------
            if (uiState.similarArtists.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Similar to artists you like")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = uiState.similarArtists) { artist ->
                        YtArtistCard(artist = artist, onClick = { onArtistClick(artist.name) })
                    }
                }
            }

            // 5. From your library ---------------------------------------------
            if (uiState.libraryPicks.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Quick picks from your library")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = uiState.libraryPicks) { song ->
                        YtLibraryPickCard(song = song, onClick = {
                            onSongSelectWithContext(song, uiState.libraryPicks)
                        })
                    }
                }
            }

            // 6. New releases ---------------------------------------------------
            if (uiState.isLoading && uiState.newReleases.isEmpty()) {
                item {
                    YtSectionHeader(title = "New releases for you")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtLoadingRow(cardWidth = 140, cardHeight = 140)
                }
            } else if (uiState.newReleases.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "New releases for you")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = uiState.newReleases) { song ->
                        YtQuickPickCard(song = song, onClick = {
                            onSongSelectWithContext(song, uiState.newReleases)
                        })
                    }
                }
            }

            // 7. Recommended playlists ------------------------------------------
            if (uiState.recommendedPlaylists.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Recommended playlists")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = uiState.recommendedPlaylists) { mix ->
                        YtMixCard(mix = mix, onClick = { onPlaylistClick(mix.id) })
                    }
                }
            }

            // 8. Recap ------------------------------------------------------------
            uiState.recap?.let { recap ->
                item {
                    YtSectionHeader(title = "Your recap")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtRecapCard(recap = recap, onPlay = { song ->
                        onSongSelectWithContext(song, recap.topSongs)
                    })
                }
            }

            // 9. Mood & activity ----------------------------------------------------
            item {
                YtSectionHeader(title = "Mood & activity")
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = CrankSpacing.M),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.moods) { mood ->
                        YtCategoryChip(category = mood, onClick = { onPlaylistClick(mood.collectionSlug) })
                    }
                }
            }

            // 10. Continue listening --------------------------------------------------
            if (uiState.continueListening.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Continue listening")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = uiState.continueListening) { song ->
                        // Pseudo-progress derived deterministically from id so the
                        // row always renders a bar without inventing playback state.
                        val progress = ((song.id.hashCode() and 0x7fffffff) % 70 + 10) / 100f
                        YtContinueCard(song = song, progress = progress, onClick = {
                            onSongSelectWithContext(song, uiState.continueListening)
                        })
                    }
                }
            }

            // Error / retry ------------------------------------------------------------
            uiState.errorMessage?.let { message ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CrankSpacing.M),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            viewModel.dismissError()
                            viewModel.load()
                        }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YtHomeHeader(onRefresh: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = CrankSpacing.M)) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "YouTube Music home",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Made from your library, history and recommendations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun YtEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = CrankSpacing.M),
    )
}

@Composable
private fun YtRecapCard(
    recap: YtRecap,
    onPlay: (Song) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CrankSpacing.M),
        shape = RoundedCornerShape(CrankRadius.Large),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your year in review",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val summary = buildString {
                append(recap.historyCount)
                append(" plays")
                if (recap.topArtist.isNotBlank()) append(" • Top artist: ${recap.topArtist}")
                if (recap.totalMinutes > 0) append(" • ${recap.totalMinutes.toInt()} min")
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            recap.topSongs.take(5).forEachIndexed { index, song ->
                YtRecapRow(rank = index + 1, song = song, onClick = { onPlay(song) })
            }
        }
    }
}

@Composable
private fun YtRecapRow(
    rank: Int,
    song: Song,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CrankRadius.Medium),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp).align(Alignment.CenterStart),
            )
            Column(modifier = Modifier.padding(start = 32.dp)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
