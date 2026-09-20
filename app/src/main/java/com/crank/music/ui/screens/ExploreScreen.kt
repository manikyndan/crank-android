package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.ShimmerBox
import com.crank.music.ui.viewmodel.BrowseArtist
import com.crank.music.ui.viewmodel.ChartItem
import com.crank.music.ui.viewmodel.ExploreViewModel
import com.crank.music.ui.viewmodel.FeaturedPlaylist
import com.crank.music.ui.viewmodel.GenreItem
import com.crank.music.ui.viewmodel.NewReleaseItem

/**
 * Apple Music "New"-style Browse tab.
 *
 * Same public contract as before (identical parameters, so MainScreen needs no
 * changes) and the same data (ExploreViewModel — no new endpoints, no invented
 * content). Only the internal layout changed. Sections render only when their
 * lists are non-empty; each fetch already degrades independently upstream.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onArtistClick: (String) -> Unit = {},
    /**
     * Opens a collection by its slug — either a [com.crank.music.domain.model.Collection]
     * or a `genre:<name>` id.
     */
    onPlaylistClick: (String) -> Unit = {},
    /** Opens the album destination for a new-release card. */
    onAlbumClick: (Album) -> Unit = {}
) {
    val uiState by exploreViewModel.uiState.collectAsState()
    val view = LocalView.current

    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    val isInitialLoading = uiState.isLoading &&
        uiState.featuredPlaylists.all { it.artworkUrl.isBlank() } &&
        uiState.newReleases.isEmpty() &&
        uiState.topCharts.isEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Text(
                text = "Browse",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        if (isInitialLoading) {
            item { AppleLoadingBlock() }
        } else {
            if (uiState.featuredPlaylists.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppleHeroPager(
                        pages = uiState.featuredPlaylists,
                        onPageClick = { id ->
                            haptic()
                            onPlaylistClick(id)
                        }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            if (uiState.newReleases.isNotEmpty()) {
                item {
                    AppleSectionHeader(title = "New Music")
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.newReleases, key = { it.album.id }) { release ->
                            AppleAlbumCard(
                                album = release.album,
                                onClick = {
                                    haptic()
                                    onAlbumClick(release.album)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            val madeForYou = uiState.featuredPlaylists.filter { !it.isHero }
            if (madeForYou.isNotEmpty()) {
                item {
                    AppleSectionHeader(title = "Made For You")
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(madeForYou, key = { it.id }) { playlist ->
                            AppleMixCard(
                                playlist = playlist,
                                onClick = {
                                    haptic()
                                    onPlaylistClick(playlist.id)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            if (uiState.genres.isNotEmpty()) {
                item {
                    AppleSectionHeader(title = "Genres & Moods")
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    AppleGenreGrid(
                        genres = uiState.genres,
                        onGenreClick = { genreId ->
                            haptic()
                            onPlaylistClick(genreId)
                        }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            if (uiState.topCharts.isNotEmpty()) {
                item {
                    AppleSectionHeader(title = "Top Charts")
                    Spacer(modifier = Modifier.height(12.dp))
                }
                val chartSongs = uiState.topCharts.map { it.song }
                items(uiState.topCharts, key = { it.song.id }) { chart ->
                    AppleChartRow(
                        chart = chart,
                        onClick = {
                            haptic()
                            onSongSelectWithContext(chart.song, chartSongs)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }

            if (uiState.browseArtists.isNotEmpty()) {
                item {
                    AppleSectionHeader(title = "Artist Spotlight")
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.browseArtists, key = { it.id }) { artist ->
                            AppleArtistCard(
                                artist = artist,
                                onClick = {
                                    haptic()
                                    onArtistClick(artist.name)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AppleSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

/** Edge-to-edge-feel hero pager with pagination dots. */
@Composable
private fun AppleHeroPager(
    pages: List<FeaturedPlaylist>,
    onPageClick: (String) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { index ->
            val page = pages[index]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPageClick(page.id) }
            ) {
                if (page.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = page.artworkUrl,
                        contentDescription = page.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = page.description.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (pages.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val isSelected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(
                                width = if (isSelected) 16.dp else 6.dp,
                                height = 6.dp
                            )
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleAlbumCard(
    album: Album,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(140.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (album.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = album.artworkUrl,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AppleArtworkFallback(size = 40.dp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artistName,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AppleMixCard(
    playlist: FeaturedPlaylist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(170.dp)
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(170.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (playlist.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = playlist.artworkUrl,
                        contentDescription = playlist.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AppleArtworkFallback(size = 48.dp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.description,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Vibrant genre tiles. The gradients are deterministic per-name presentation
 * constants (not data) — the tile a genre gets never changes between loads.
 */
private val GENRE_GRADIENTS = listOf(
    0xFFE6223C to 0xFF7A0F1E,
    0xFF7D2AE8 to 0xFF2E0A5E,
    0xFF0A84FF to 0xFF002E6E,
    0xFF00C7BE to 0xFF005E5A,
    0xFFFF9F0A to 0xFF8A4B00,
    0xFFFF375F to 0xFF7A0F2B,
    0xFF30D158 to 0xFF0B5A26,
    0xFF64D2FF to 0xFF0A4A6E,
    0xFFBF5AF2 to 0xFF4A1068,
    0xFFFF6482 to 0xFF6E0F22,
)

@Composable
private fun AppleGenreGrid(
    genres: List<GenreItem>,
    onGenreClick: (String) -> Unit,
) {
    // Chunked rows inside the outer LazyColumn: no nested scrolling containers.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth > 600.dp) 4 else 2
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            genres.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { genre ->
                        val (start, end) = GENRE_GRADIENTS[
                            (genre.title.hashCode() and 0x7fffffff) % GENRE_GRADIENTS.size
                        ]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(start), Color(end))
                                    )
                                )
                                .clickable { onGenreClick(genre.id) }
                                .padding(14.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = genre.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Keep the last row aligned when it is short.
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleChartRow(
    chart: ChartItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${chart.rank}",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Surface(
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (chart.song.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = chart.song.artworkUrl,
                        contentDescription = chart.song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AppleArtworkFallback(size = 22.dp)
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chart.song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = chart.song.artistName,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (chart.duration.isNotBlank()) {
            Text(
                text = chart.duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppleArtistCard(
    artist: BrowseArtist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (artist.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = artist.artworkUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AppleArtworkFallback(size = 36.dp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AppleArtworkFallback(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.material3.Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size)
    )
}

@Composable
private fun AppleLoadingBlock() {
    Column(modifier = Modifier.fillMaxWidth()) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(32.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false
        ) {
            items(4) {
                Column(modifier = Modifier.width(140.dp)) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}
