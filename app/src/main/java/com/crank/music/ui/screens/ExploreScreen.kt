package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.crank.music.domain.model.Album
import com.crank.music.domain.model.Song
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldDark
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.ExploreViewModel

@Composable
fun ExploreScreen(
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onArtistClick: (String) -> Unit = {},
    /**
     * Opens a collection by its slug — either a [Collection] or a `genre:<name>` id. The screen
     * previously had no way to navigate to anything at all, so every genre and featured-playlist
     * card was wired to a haptic tick and did nothing when tapped.
     */
    onPlaylistClick: (String) -> Unit = {},
    /** Opens the album destination for a new-release card. */
    onAlbumClick: (Album) -> Unit = {}
) {
    val uiState by exploreViewModel.uiState.collectAsState()
    val view = LocalView.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                ExploreHeader(
                    query = uiState.searchQuery,
                    isExpanded = uiState.isSearchExpanded,
                    suggestions = uiState.searchSuggestions,
                    recentSearches = uiState.recentSearches,
                    trendingSearches = uiState.trendingSearches,
                    onQueryChange = { exploreViewModel.onSearchQueryChange(it) },
                    onExpand = {
                        exploreViewModel.expandSearch()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onCollapse = { exploreViewModel.collapseSearch() },
                    onSuggestionClick = { suggestion ->
                        exploreViewModel.addRecentSearch(suggestion.title)
                        val song = com.crank.music.domain.model.Song(
                            id = suggestion.id,
                            title = suggestion.title,
                            artistName = suggestion.subtitle.split(" • ").firstOrNull() ?: "Unknown Artist",
                            albumId = null,
                            durationMs = 0L,
                            artworkUrl = suggestion.imageUrl,
                            isLocal = false
                        )
                        onSongSelect(song)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onRecentClick = { query ->
                        exploreViewModel.onSearchQueryChange(query)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onClearRecent = {
                        exploreViewModel.clearRecentSearches()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onDeleteRecent = { query ->
                        exploreViewModel.removeRecentSearch(query)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            if (!uiState.isSearchExpanded) {
                item {
                    GenresSection(
                        genres = uiState.genres,
                        // Was a bare haptic tick. A genre is now addressable as `genre:<name>`,
                        // which PlaylistDetailViewModel resolves without needing an enum entry per
                        // genre.
                        onGenreClick = { genreId ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onPlaylistClick(genreId)
                        }
                    )
                }

                item {
                    FeaturedPlaylistsSection(
                        playlists = uiState.featuredPlaylists,
                        // Was a bare haptic tick, which is why tapping "Today's Top Hits" did
                        // nothing. The card id is a Collection slug, so this now opens that
                        // collection.
                        onPlaylistClick = { playlistId ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onPlaylistClick(playlistId)
                        }
                    )
                }

                item {
                    TopChartsSection(
                        charts = uiState.topCharts,
                        selectedFilter = uiState.selectedChartFilter,
                        selectedRegion = uiState.selectedChartRegion,
                        onFilterSelect = { exploreViewModel.setChartFilter(it) },
                        onRegionSelect = { exploreViewModel.setChartRegion(it) },
                        onSongSelect = onSongSelect,
                        onSongSelectWithContext = onSongSelectWithContext
                    )
                }

                item {
                    NewReleasesSection(
                        releases = uiState.newReleases,
                        // Was another haptic-only no-op. A new release card carries an Album, so
                        // this opens the album destination.
                        onAlbumClick = { album ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onAlbumClick(album)
                        }
                    )
                }

                item {
                    BrowseAllSection(
                        artists = uiState.browseArtists,
                        onArtistClick = { artistName ->
                            onArtistClick(artistName)
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreHeader(
    query: String,
    isExpanded: Boolean,
    suggestions: List<com.crank.music.ui.viewmodel.SearchSuggestion>,
    recentSearches: List<String>,
    trendingSearches: List<String>,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSuggestionClick: (com.crank.music.ui.viewmodel.SearchSuggestion) -> Unit,
    onRecentClick: (String) -> Unit,
    onClearRecent: () -> Unit,
    onDeleteRecent: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (!isExpanded) {
            Text(
                text = "Explore",
                style = MaterialTheme.typography.displayLarge,
                color = WarmWhite,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onExpand() },
                color = CharcoalSurface,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Search songs, artists, albums...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        // maxLines/softWrap are set explicitly: the placeholder does not inherit
                        // singleLine from the field, so with a narrow field it wrapped to two
                        // lines and doubled the height of the control.
                        Text(
                            "Search songs, artists, albums...",
                            color = TextTertiary,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, "Search", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        // The focus border used to be the accent colour. On this palette the
                        // accent is a saturated red, so focusing the field drew a red ring that
                        // is indistinguishable from the error state — it read as "this input is
                        // invalid" the moment you tapped it. Focus is now shown by lifting the
                        // border to a brighter neutral; the accent stays on the cursor, where it
                        // signals "you are typing" rather than "something is wrong".
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = CharcoalSurface,
                        focusedContainerColor = CharcoalSurface,
                        unfocusedContainerColor = CharcoalSurface,
                        cursorColor = ChampagneGold
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // A plain clickable Text, not an IconButton.
                //
                // IconButton always reserves Material's 48dp *minimum interactive size* as real
                // layout width, regardless of the content it wraps, and it clamps its child to
                // that square. "Cancel" is wider than 48dp, so it was measured against a box it
                // could not fit in and rendered clipped — first as "Canc el", then, once the
                // content was allowed to wrap, still cut off at the screen edge. The row also
                // gives the field `weight(1f)`, so the button's reserved 48dp came straight out
                // of the field's budget and pushed the placeholder onto two lines.
                //
                // Sizing to the text and keeping a 48dp-tall touch target is what the label
                // actually needs; the full width of "Cancel" is comfortably tappable on its own.
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = ChampagneGold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clickable(onClick = onCollapse)
                        .heightIn(min = 48.dp)
                        .padding(start = 12.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }

            AnimatedVisibility(
                visible = query.isEmpty(),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (recentSearches.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = WarmWhite
                            )
                            Text(
                                text = "Clear All",
                                style = MaterialTheme.typography.labelMedium,
                                color = ChampagneGold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onClearRecent() }
                                    .padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        recentSearches.forEach { search ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onRecentClick(search) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = search,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WarmWhite,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteRecent(search) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = TextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (trendingSearches.isNotEmpty()) {
                        Text(
                            text = "Trending Searches",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = WarmWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        trendingSearches.forEach { search ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onRecentClick(search) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔥",
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = search,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WarmWhite
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = query.isNotEmpty() && suggestions.isNotEmpty(),
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    suggestions.forEach { suggestion ->
                        SearchSuggestionRow(
                            suggestion = suggestion,
                            onClick = { onSuggestionClick(suggestion) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionRow(
    suggestion: com.crank.music.ui.viewmodel.SearchSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = if (suggestion.type == "artist") CircleShape else RoundedCornerShape(6.dp),
            color = CharcoalSurface
        ) {
            AsyncImage(
                model = suggestion.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = WarmWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = suggestion.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = CharcoalElevated,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = suggestion.type.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun GenresSection(
    genres: List<com.crank.music.ui.viewmodel.GenreItem>,
    onGenreClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionHeaderRow(title = "Genres & Moods")

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(genres.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { genre ->
                        GenreCard(genre = genre, onClick = { onGenreClick(genre.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreCard(
    genre: com.crank.music.ui.viewmodel.GenreItem,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale"
    )

    Surface(
        modifier = Modifier
            .size(160.dp, 100.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clickable {
                onClick()
                isPressed = true
            },
        shape = RoundedCornerShape(12.dp),
        color = CharcoalSurface
    ) {
        Box {
            AsyncImage(
                model = genre.imageUrl,
                contentDescription = genre.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            Text(
                text = genre.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun FeaturedPlaylistsSection(
    playlists: List<com.crank.music.ui.viewmodel.FeaturedPlaylist>,
    onPlaylistClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeaderRow(title = "Featured Playlists")

        val hero = playlists.firstOrNull { it.isHero }
        if (hero != null) {
            HeroPlaylistCard(playlist = hero, onClick = { onPlaylistClick(hero.id) })
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists.filter { !it.isHero }) { playlist ->
                FeaturedPlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun HeroPlaylistCard(
    playlist: com.crank.music.ui.viewmodel.FeaturedPlaylist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(200.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = CharcoalSurface
    ) {
        Box {
            AsyncImage(
                model = playlist.artworkUrl,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Surface(
                    color = ChampagneGold,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "FEATURED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ObsidianBlack,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )
                Text(
                    text = playlist.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun FeaturedPlaylistCard(
    playlist: com.crank.music.ui.viewmodel.FeaturedPlaylist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier
                .size(150.dp)
                .shadow(6.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = CharcoalSurface
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
            color = WarmWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.description,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopChartsSection(
    charts: List<com.crank.music.ui.viewmodel.ChartItem>,
    selectedFilter: String,
    selectedRegion: String,
    onFilterSelect: (String) -> Unit,
    onRegionSelect: (String) -> Unit,
    onSongSelect: (Song) -> Unit,
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) }
) {
    val filters = listOf("Songs", "Albums", "Artists", "Playlists")
    val regions = listOf("Global", "US", "UK", "India")

    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeaderRow(title = "Top Charts")

        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            regions.forEach { region ->
                val isSelected = region == selectedRegion
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onRegionSelect(region) },
                    color = if (isSelected) ChampagneGold else CharcoalSurface,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = region,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) ObsidianBlack else TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { filter ->
                val isSelected = filter == selectedFilter
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onFilterSelect(filter) },
                    color = if (isSelected) ChampagneGold.copy(alpha = 0.15f) else CharcoalElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, ChampagneGold) else null
                ) {
                    Text(
                        text = filter,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) ChampagneGold else TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        charts.forEach { item ->
            ChartRow(
                item = item,
                onClick = {
                    val contextList = charts.map { it.song }
                    onSongSelectWithContext(item.song, contextList)
                }
            )
        }
    }
}

@Composable
private fun ChartRow(
    item: com.crank.music.ui.viewmodel.ChartItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${item.rank}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = when (item.rank) {
                1 -> ChampagneGold
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> TextTertiary
            },
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = CharcoalSurface
        ) {
            AsyncImage(
                model = item.song.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = WarmWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.song.artistName,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            // Trend and play count are only rendered when the data actually provides them. Both
            // used to be fabricated (arrows from `index % 3`, counts from a formula), which made the
            // chart look informative while conveying nothing. Render nothing instead.
            if (item.trend.isNotEmpty()) {
                Icon(
                    imageVector = when (item.trend) {
                        "down" -> Icons.AutoMirrored.Filled.TrendingDown
                        "flat" -> Icons.AutoMirrored.Filled.TrendingFlat
                        else -> Icons.AutoMirrored.Filled.TrendingUp
                    },
                    contentDescription = null,
                    tint = when (item.trend) {
                        "up" -> Color(0xFF4CAF50)
                        "down" -> Color(0xFFFF5252)
                        else -> TextTertiary
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
            if (item.playCount.isNotEmpty()) {
                Text(
                    text = item.playCount,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun NewReleasesSection(
    releases: List<com.crank.music.ui.viewmodel.NewReleaseItem>,
    onAlbumClick: (Album) -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeaderRow(title = "New Releases")

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(releases) { release ->
                NewReleaseCard(release = release, onClick = { onAlbumClick(release.album) })
            }
        }
    }
}

@Composable
private fun NewReleaseCard(
    release: com.crank.music.ui.viewmodel.NewReleaseItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier
                .size(150.dp)
                .shadow(6.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = CharcoalSurface
        ) {
            Box {
                AsyncImage(
                    model = release.album.artworkUrl,
                    contentDescription = release.album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (release.isNew) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        color = Color(0xFFE53935),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = WarmWhite,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp),
                    shape = CircleShape,
                    color = ChampagneGold
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to library",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = release.album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = WarmWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = release.album.artistName,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = release.releaseDate,
            style = MaterialTheme.typography.labelSmall,
            color = ChampagneGold
        )
    }
}

@Composable
private fun BrowseAllSection(
    artists: List<com.crank.music.ui.viewmodel.BrowseArtist>,
    onArtistClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeaderRow(title = "Browse All Artists")

        val grouped = artists.groupBy { it.letter }

        grouped.forEach { (letter, letterArtists) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$letter",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ChampagneGold,
                    modifier = Modifier.width(24.dp)
                )
            }

            letterArtists.forEach { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onArtistClick(artist.name) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = CharcoalSurface
                    ) {
                        AsyncImage(
                            model = artist.artworkUrl,
                            contentDescription = artist.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = WarmWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    showRefresh: Boolean = false,
    onRefresh: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )

        Text(
            text = "See All",
            style = MaterialTheme.typography.labelLarge,
            color = ChampagneGold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
