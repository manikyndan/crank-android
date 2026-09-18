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
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.NotificationPanel
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldDark
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
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
    onPlaylistClick: (String) -> Unit = {}
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val notifState by notificationViewModel.uiState.collectAsState()
    val view = LocalView.current

    NotificationPanel(viewModel = notificationViewModel)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
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
                    onSeeAllClick = { onPlaylistClick("recently_played") }
                )
            }

            item {
                QuickActionsRow(
                    actions = uiState.quickActions,
                    onActionClick = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
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
                    onSeeAllClick = { onPlaylistClick("recommended") }
                )
            }

            item {
                TrendingSection(
                    items = uiState.trending,
                    onSongSelect = onSongSelect,
                    onSongSelectWithContext = onSongSelectWithContext,
                    onSeeAllClick = { onPlaylistClick("trending") }
                )
            }

            item {
                MadeForYouSection(
                    playlists = uiState.madeForYou,
                    onPlaylistClick = { playlistId ->
                        onPlaylistClick(playlistId)
                    },
                    onSeeAllClick = { onPlaylistClick("made_for_you") }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onProfileClick() }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(4.dp, CircleShape),
                shape = CircleShape,
                color = CharcoalSurface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ChampagneGold, GoldDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ObsidianBlack
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )
            }
        }

        Box {
            IconButton(
                onClick = onNotificationClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = WarmWhite,
                    modifier = Modifier.size(26.dp)
                )
            }

            if (notificationCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 6.dp, top = 6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(ChampagneGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (notificationCount > 9) "9+" else "$notificationCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ObsidianBlack,
                        fontSize = 9.sp
                    )
                }
            }
        }
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
        targetValue = if (isPressed) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .width(150.dp)
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
        Surface(
            modifier = Modifier
                .size(150.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = CharcoalSurface
        ) {
            Box {
                AsyncImage(
                    model = item.song.artworkUrl,
                    contentDescription = item.song.title,
                    modifier = Modifier.fillMaxSize()
                )

                if (isPressed) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Black.copy(alpha = 0.4f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = ChampagneGold
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = ObsidianBlack,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.song.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = WarmWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.lastPlayedText,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            maxLines = 1
        )
    }
}

@Composable
private fun QuickActionsRow(
    actions: List<com.crank.music.ui.viewmodel.HomeQuickAction>,
    onActionClick: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(actions) { action ->
                QuickActionChip(
                    action = action,
                    onClick = onActionClick
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
        else -> listOf(ChampagneGold, GoldDark)
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
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
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
            modifier = Modifier
                .size(160.dp)
                .shadow(6.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = CharcoalSurface
        ) {
            Box {
                AsyncImage(
                    model = item.song.artworkUrl,
                    contentDescription = item.song.title,
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
                        tint = WarmWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmWhite,
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
            color = if (item.rank <= 3) ChampagneGold else TextTertiary,
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

        Icon(
            imageVector = when (item.trend) {
                "up" -> Icons.AutoMirrored.Filled.TrendingUp
                "down" -> Icons.AutoMirrored.Filled.TrendingDown
                else -> Icons.AutoMirrored.Filled.TrendingUp
            },
            contentDescription = null,
            tint = when (item.trend) {
                "up" -> Color(0xFF4CAF50)
                "down" -> Color(0xFFFF5252)
                else -> TextTertiary
            },
            modifier = Modifier.size(18.dp)
        )
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
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite,
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
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.lastUpdated,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary.copy(alpha = 0.7f),
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "See All",
                style = MaterialTheme.typography.labelLarge,
                color = ChampagneGold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSeeAllClick?.invoke() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            if (showRefresh && onRefresh != null) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = ChampagneGold,
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
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shimmerColor = ChampagneGold.copy(alpha = 0.08f),
                baseColor = CharcoalSurface
            )
        }
    }
}
