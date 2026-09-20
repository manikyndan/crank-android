package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.feature.ytmusic.YtArtistCard
import com.crank.music.feature.ytmusic.YtAlbumCard
import com.crank.music.feature.ytmusic.YtCategoryChip
import com.crank.music.feature.ytmusic.YtContinueCard
import com.crank.music.feature.ytmusic.YtHorizontalScrollSection
import com.crank.music.feature.ytmusic.YtLibraryPickCard
import com.crank.music.feature.ytmusic.YtLoadingRow
import com.crank.music.feature.ytmusic.YtMixCard
import com.crank.music.feature.ytmusic.YtMoodTile
import com.crank.music.feature.ytmusic.YtMusicHomeViewModel
import com.crank.music.feature.ytmusic.YtQuickPickCard
import com.crank.music.feature.ytmusic.YtSectionHeader
import com.crank.music.feature.ytmusic.YtTrendingCard
import com.crank.music.ui.components.NotificationPanel
import com.crank.music.ui.theme.CrankSpacing
import com.crank.music.ui.viewmodel.NotificationViewModel

/**
 * Home = YouTube Music-style header (Home title, search, notifications,
 * profile) + the Discover feed as its body, bound to [YtMusicHomeViewModel].
 * Player wiring is unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    ytMusicHomeViewModel: YtMusicHomeViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onPlaylistClick: (String) -> Unit = {},
    /** Opens Settings. Kept on the profile disc: it is the only route there. */
    onProfileClick: () -> Unit = {},
    /** Opens the Search tab. */
    onSearchClick: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
) {
    val notifState by notificationViewModel.uiState.collectAsState()
    val feedState by ytMusicHomeViewModel.uiState.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current

    NotificationPanel(viewModel = notificationViewModel)

    PullToRefreshBox(
        isRefreshing = feedState.isLoading && feedState.quickPicks.isNotEmpty(),
        onRefresh = { ytMusicHomeViewModel.load() },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            item {
                YtHomeHeader(
                    notificationCount = if (notifState.hasUnread) notifState.unreadCount else 0,
                    onSearchClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onSearchClick()
                    },
                    onNotificationClick = {
                        notificationViewModel.openPanel()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onProfileClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onProfileClick()
                    },
                    onRefresh = { ytMusicHomeViewModel.load() }
                )
            }

            // Quick Picks: hidden entirely when empty (never an empty box).
            if (feedState.isLoading && feedState.quickPicks.isEmpty()) {
                item {
                    YtSectionHeader(title = "Quick picks")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtLoadingRow()
                }
            } else if (feedState.quickPicks.isNotEmpty()) {
                item {
                    YtSectionHeader(
                        title = "Quick picks",
                        onSeeAllClick = {
                            feedState.quickPicks.firstOrNull()?.let {
                                onSongSelectWithContext(it, feedState.quickPicks)
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = feedState.quickPicks) { song ->
                        YtQuickPickCard(song = song, onClick = {
                            onSongSelectWithContext(song, feedState.quickPicks)
                        })
                    }
                }
            }

            // Mixed for you -------------------------------------------------
            if (feedState.isLoading && feedState.mixes.isEmpty()) {
                item {
                    YtSectionHeader(title = "Mixed for you")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtLoadingRow(cardWidth = 160, cardHeight = 160)
                }
            } else if (feedState.mixes.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Mixed for you")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = feedState.mixes) { mix ->
                        YtMixCard(mix = mix, onClick = { onPlaylistClick(mix.id) })
                    }
                }
            }

            // Recommended albums --------------------------------------------
            if (feedState.isLoading && feedState.recommendedAlbums.isEmpty()) {
                item {
                    YtSectionHeader(title = "Recommended albums")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtLoadingRow(cardWidth = 140, cardHeight = 140)
                }
            } else if (feedState.recommendedAlbums.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Recommended albums")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = feedState.recommendedAlbums) { album ->
                        YtAlbumCard(album = album, onClick = { onAlbumClick(album) })
                    }
                }
            }

            // From your library (before similar artists, per YT order) ------
            if (feedState.libraryPicks.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Quick picks from your library")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = feedState.libraryPicks) { song ->
                        YtLibraryPickCard(song = song, onClick = {
                            onSongSelectWithContext(song, feedState.libraryPicks)
                        })
                    }
                }
            }

            // Similar artists -----------------------------------------------
            if (feedState.similarArtists.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Similar to artists you like")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = feedState.similarArtists) { artist ->
                        YtArtistCard(artist = artist, onClick = { onArtistClick(artist.name) })
                    }
                }
            }

            // New releases ----------------------------------------------------
            if (feedState.isLoading && feedState.newReleases.isEmpty()) {
                item {
                    YtSectionHeader(title = "New releases for you")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtLoadingRow(cardWidth = 140, cardHeight = 140)
                }
            } else if (feedState.newReleases.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "New releases for you")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = feedState.newReleases) { song ->
                        YtQuickPickCard(song = song, onClick = {
                            onSongSelectWithContext(song, feedState.newReleases)
                        })
                    }
                }
            }

            // Recommended playlists -------------------------------------------
            if (feedState.recommendedPlaylists.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Recommended playlists")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = feedState.recommendedPlaylists) { mix ->
                        YtMixCard(mix = mix, onClick = { onPlaylistClick(mix.id) })
                    }
                }
            }

            // Recap -------------------------------------------------------------
            feedState.recap?.let { recap ->
                item {
                    YtSectionHeader(title = "Your recap")
                    Spacer(modifier = Modifier.height(12.dp))
                    HomeRecapCard(
                        recap = recap,
                        onPlay = { song -> onSongSelectWithContext(song, recap.topSongs) }
                    )
                }
            }

            // Mood & genres: 2-column gradient grid ---------------------------
            if (feedState.moods.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Moods & genres")
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CrankSpacing.M),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        feedState.moods.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { mood ->
                                    YtMoodTile(
                                        category = mood,
                                        onClick = { onPlaylistClick(mood.collectionSlug) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                // Keep tiles half-width on an odd last row.
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Continue listening: genuine saved progress only ------------------
            if (feedState.continueListening.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Continue listening")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = feedState.continueListening) { song ->
                        YtContinueCard(
                            song = song,
                            progress = feedState.resumeProgress[song.id],
                            onClick = {
                                onSongSelectWithContext(song, feedState.continueListening)
                            }
                        )
                    }
                }
            }

            // Trending: chart order = backend result order, ranks are real ------
            if (feedState.isLoading && feedState.trending.isEmpty()) {
                item {
                    YtSectionHeader(title = "Trending")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtLoadingRow(cardWidth = 280, cardHeight = 64)
                }
            } else if (feedState.trending.isNotEmpty()) {
                item {
                    YtSectionHeader(title = "Trending")
                    Spacer(modifier = Modifier.height(12.dp))
                    YtHorizontalScrollSection(items = feedState.trending) { entry ->
                        YtTrendingCard(entry = entry, onClick = {
                            onSongSelectWithContext(
                                entry.song,
                                feedState.trending.map { it.song }
                            )
                        })
                    }
                }
            }

            // Error / retry -------------------------------------------------------------
            feedState.errorMessage?.let { message ->
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
                            ytMusicHomeViewModel.dismissError()
                            ytMusicHomeViewModel.load()
                        }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

/** YouTube Music home header: title left; search, bell, profile right. */
@Composable
private fun YtHomeHeader(
    notificationCount: Int,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }

        Box {
            IconButton(
                onClick = onNotificationClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (notificationCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        // Profile disc. There is no avatar backend, so a person glyph stands
        // in — and it keeps its job: the only route into Settings.
        Surface(
            onClick = onProfileClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun HomeRecapCard(
    recap: com.crank.music.feature.ytmusic.YtRecap,
    onPlay: (Song) -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CrankSpacing.M),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            com.crank.music.ui.theme.CrankRadius.Large
        ),
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
                HomeRecapRow(rank = index + 1, song = song, onClick = { onPlay(song) })
            }
        }
    }
}

@Composable
private fun HomeRecapRow(
    rank: Int,
    song: Song,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            com.crank.music.ui.theme.CrankRadius.Medium
        ),
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
