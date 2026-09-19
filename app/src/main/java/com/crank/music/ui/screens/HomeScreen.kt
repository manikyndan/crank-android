package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Collection
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.NotificationPanel
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.components.ShimmerBox
import com.crank.music.ui.components.StaggeredFadeIn
import com.crank.music.ui.viewmodel.HomeViewModel
import com.crank.music.ui.viewmodel.NotificationViewModel
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onPlaylistClick: (String) -> Unit = {},
    /** Opens the profile/You tab. Was previously a haptic tick with no destination. */
    onProfileClick: () -> Unit = {}
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val notifState by notificationViewModel.uiState.collectAsState()
    val view = LocalView.current

    NotificationPanel(viewModel = notificationViewModel)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            GreetingHeader(
                greeting = uiState.greeting,
                notificationCount = if (notifState.hasUnread) notifState.unreadCount else 0,
                onNotificationClick = {
                    notificationViewModel.openPanel()
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onProfileClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onProfileClick()
                }
            )
        }

        if (uiState.isLoading) {
            item {
                LoadingSkeleton()
            }
        } else {
            item {
                RecentlyPlayedSection(
                    items = uiState.recentlyPlayed,
                    onSongSelect = onSongSelect,
                    onSongSelectWithContext = onSongSelectWithContext,
                    onSeeAllClick = { onPlaylistClick(Collection.RECENTLY_PLAYED.slug) }
                )
            }

            item {
                QuickActionsRow(
                    actions = uiState.quickActions,
                    // Was a bare haptic tick with no navigation, so the Daily Mix chips were
                    // buttons that buzzed and did nothing. Each chip now carries its Collection
                    // slug and opens that collection.
                    onActionClick = { action ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onPlaylistClick(action.id)
                    }
                )
            }

            item {
                RecommendedSection(
                    items = uiState.recommended,
                    onSongSelect = onSongSelect,
                    onSongSelectWithContext = onSongSelectWithContext,
                    onDismiss = { index ->
                        homeViewModel.dismissRecommendation(index)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onRefresh = {
                        homeViewModel.refreshRecommendations()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onSeeAllClick = { onPlaylistClick(Collection.RECOMMENDED.slug) }
                )
            }

            item {
                TrendingSection(
                    items = uiState.trending,
                    onSongSelect = onSongSelect,
                    onSongSelectWithContext = onSongSelectWithContext,
                    onSeeAllClick = { onPlaylistClick(Collection.TRENDING.slug) }
                )
            }

            item {
                MadeForYouSection(
                    playlists = uiState.madeForYou,
                    onPlaylistClick = { playlistId ->
                        onPlaylistClick(playlistId)
                    },
                    onSeeAllClick = { onPlaylistClick(Collection.DISCOVER_WEEKLY.slug) }
                )
            }
        }
    }
}

@Composable
private fun GreetingHeader(
    greeting: String,
    notificationCount: Int,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)
    ) {
        // Actions sit above the title, which is the whole point of this arrangement: a greeting
        // is not the most important thing on the screen, so it does not get the top line. It also
        // stops the header from being a row of two unrelated controls with a title sandwiched
        // between them, which is what the previous left-avatar/right-bell layout produced.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Spacer(modifier = Modifier.width(4.dp))

            // Opens Settings. This was the avatar for the removed You tab; with that hub gone it
            // is the only way into Settings, so it navigates straight there and is drawn as a
            // plain glyph rather than a gold gradient disc, which read as a profile photo the app
            // does not actually have.
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (notificationCount > 0) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RecentlyPlayedSection(
    items: List<com.crank.music.ui.viewmodel.RecentlyPlayedItem>,
    onSongSelect: (Song) -> Unit,
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onSeeAllClick: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionHeaderRow(title = "Recently Played", onSeeAllClick = onSeeAllClick)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(items) { index, item ->
                StaggeredFadeIn(index = index, visible = true) {
                    RecentlyPlayedCard(
                        item = item,
                        onClick = {
                            val contextList = items.map { it.song }
                            onSongSelectWithContext(item.song, contextList)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyPlayedCard(
    item: com.crank.music.ui.viewmodel.RecentlyPlayedItem,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .width(156.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { isPressed = true },
                    onDragEnd = { isPressed = false },
                    onDragCancel = { isPressed = false },
                    onVerticalDrag = { _, _ -> }
                )
            }
            .clickable { onClick() }
    ) {
        // The heavy 8dp shadow and the charcoal backing plate are gone. A card is a picture of
        // an album; giving the picture a drop shadow and a coloured mount makes it look like a
        // framed object rather than a cover. The artwork now sits directly on the page at a
        // larger size, which is what gives a music grid its density.
        Surface(
            modifier = Modifier
                .size(156.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                AsyncImage(
                    model = item.song.artworkUrl,
                    contentDescription = item.song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Press feedback is a scrim plus a small play affordance, not a full-size gold
                // disc. The disc covered most of the cover at this card size.
                if (isPressed) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black.copy(alpha = 0.35f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = item.song.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.lastPlayedText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickActionsRow(
    actions: List<com.crank.music.ui.viewmodel.HomeQuickAction>,
    onActionClick: (com.crank.music.ui.viewmodel.HomeQuickAction) -> Unit
) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(actions) { action ->
                QuickActionChip(
                    action = action,
                    onClick = { onActionClick(action) }
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    action: com.crank.music.ui.viewmodel.HomeQuickAction,
    onClick: () -> Unit
) {
    val gradientColors = when (action.icon) {
        "mix" -> listOf(Color(0xFF6B3FA0), Color(0xFF9B59B6))
        "repeat" -> listOf(Color(0xFF1E3264), Color(0xFF3498DB))
        "time" -> listOf(Color(0xFFE13300), Color(0xFFE74C3C))
        "discover" -> listOf(Color(0xFF1DB954), Color(0xFF2ECC71))
        else -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(gradientColors)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (action.icon) {
                    "mix" -> "🎵"
                    "repeat" -> "🔁"
                    "time" -> "⏳"
                    "discover" -> "🔍"
                    else -> "🎶"
                }
                Text(
                    text = icon,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun RecommendedSection(
    items: List<com.crank.music.ui.viewmodel.RecommendedItem>,
    onSongSelect: (Song) -> Unit,
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onDismiss: (Int) -> Unit,
    onRefresh: () -> Unit,
    onSeeAllClick: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeaderRow(
            title = "Recommended For You",
            showRefresh = true,
            onRefresh = onRefresh,
            onSeeAllClick = onSeeAllClick
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items) { index, item ->
                RecommendedCard(
                    item = item,
                    onClick = {
                        val contextList = items.map { it.song }
                        onSongSelectWithContext(item.song, contextList)
                    },
                    onDismiss = { onDismiss(index) }
                )
            }
        }
    }
}

@Composable
private fun RecommendedCard(
    item: com.crank.music.ui.viewmodel.RecommendedItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.width(160.dp)
    ) {
        Surface(
            modifier = Modifier.size(160.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                AsyncImage(
                    model = item.song.artworkUrl,
                    contentDescription = item.song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.song.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.song.artistName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrendingSection(
    items: List<com.crank.music.ui.viewmodel.TrendingItem>,
    onSongSelect: (Song) -> Unit,
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onSeeAllClick: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeaderRow(title = "Trending Now", onSeeAllClick = onSeeAllClick)

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                TrendingRow(
                    item = item,
                    onClick = {
                        val contextList = items.map { it.song }
                        onSongSelectWithContext(item.song, contextList)
                    }
                )
            }
        }
    }
}

@Composable
private fun TrendingRow(
    item: com.crank.music.ui.viewmodel.TrendingItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${item.rank}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = if (item.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AsyncImage(
                model = item.song.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.song.artistName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Only show a direction arrow when we actually know the direction. The previous `else`
        // branch drew an upward arrow for unknown data, which asserted movement the backend never
        // reported. An empty string now renders nothing.
        if (item.trend.isNotEmpty()) {
            Icon(
                imageVector = when (item.trend) {
                    "down" -> Icons.AutoMirrored.Filled.TrendingDown
                    else -> Icons.AutoMirrored.Filled.TrendingUp
                },
                contentDescription = null,
                tint = when (item.trend) {
                    "up" -> Color(0xFF4CAF50)
                    "down" -> Color(0xFFFF5252)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MadeForYouSection(
    playlists: List<com.crank.music.ui.viewmodel.MadeForYouPlaylist>,
    onPlaylistClick: (String) -> Unit,
    onSeeAllClick: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeaderRow(title = "Made For You", onSeeAllClick = onSeeAllClick)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists) { playlist ->
                MadeForYouCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun MadeForYouCard(
    playlist: com.crank.music.ui.viewmodel.MadeForYouPlaylist,
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
            color = Color(playlist.artworkColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(playlist.artworkColor),
                                Color(playlist.artworkColor).copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = when (playlist.id) {
                            "1" -> "🔍"
                            "2" -> "📡"
                            "3" -> "🎵"
                            "4" -> "⏳"
                            "5" -> "🔁"
                            "6" -> "⏪"
                            else -> "🎶"
                        },
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = playlist.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.lastUpdated,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    showRefresh: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onSeeAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // "See All" is secondary to the section title, so it is set in the muted colour at
            // label size rather than in the accent. An accent-coloured "See All" on every row
            // meant six competing calls to action per screen, which is what made the home feed
            // feel busy despite being mostly empty space.
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSeeAllClick?.invoke() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )

            if (showRefresh && onRefresh != null) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        repeat(3) {
            // Neutral shimmer. A tinted shimmer on a card-shaped block reads as content rather
            // than as absence, and it also committed the skeleton to a colour the real artwork
            // would not match.
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                baseColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
