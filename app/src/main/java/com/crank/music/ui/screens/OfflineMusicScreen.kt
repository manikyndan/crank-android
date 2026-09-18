package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldDark
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.DownloadQuality
import com.crank.music.ui.viewmodel.DownloadStatus
import com.crank.music.ui.viewmodel.OfflineMusicViewModel
import com.crank.music.ui.viewmodel.StorageLimit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMusicScreen(
    viewModel: OfflineMusicViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = WarmWhite,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Offline Music",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SmartOfflineHubSection(
                    totalStorage = uiState.totalStorage,
                    usedStorage = uiState.usedStorage,
                    playlistCount = uiState.playlists.size,
                    albumCount = uiState.albums.size,
                    artistCount = uiState.artists.size,
                    songCount = uiState.songs.size,
                    expandedSection = uiState.expandedSection,
                    playlists = uiState.playlists,
                    albums = uiState.albums,
                    artists = uiState.artists,
                    songs = uiState.songs,
                    swipedItemId = uiState.swipedItemId,
                    onToggleSection = {
                        viewModel.toggleSection(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onSwiped = { id ->
                        viewModel.setSwipedItemId(id)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onRemoveItem = {
                        viewModel.removeItem(it)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    },
                    onCancelSwipe = { viewModel.setSwipedItemId(null) }
                )
            }

            item {
                AutoDownloadRulesSection(
                    autoDownloadOnWifi = uiState.autoDownloadOnWifi,
                    autoDownloadLiked = uiState.autoDownloadLiked,
                    autoDownloadArtistReleases = uiState.autoDownloadArtistReleases,
                    autoDownloadDailyMix = uiState.autoDownloadDailyMix,
                    downloadQuality = uiState.downloadQuality,
                    storageLimit = uiState.storageLimit,
                    onToggleWifi = {
                        viewModel.toggleAutoDownloadOnWifi(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onToggleLiked = {
                        viewModel.toggleAutoDownloadLiked(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onToggleArtistReleases = {
                        viewModel.toggleAutoDownloadArtistReleases(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onToggleDailyMix = {
                        viewModel.toggleAutoDownloadDailyMix(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onQualitySelect = {
                        viewModel.setDownloadQuality(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onStorageLimitChange = {
                        viewModel.setStorageLimit(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                DownloadManagerSection(
                    activeDownloads = uiState.activeDownloads,
                    onPause = {
                        viewModel.pauseDownload(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onResume = {
                        viewModel.resumeDownload(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onCancel = {
                        viewModel.cancelDownload(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onRetry = {
                        viewModel.retryDownload(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }
        }
    }
}

@Composable
private fun SmartOfflineHubSection(
    totalStorage: Long,
    usedStorage: Long,
    playlistCount: Int,
    albumCount: Int,
    artistCount: Int,
    songCount: Int,
    expandedSection: String?,
    playlists: List<com.crank.music.ui.viewmodel.OfflineItem>,
    albums: List<com.crank.music.ui.viewmodel.OfflineItem>,
    artists: List<com.crank.music.ui.viewmodel.OfflineItem>,
    songs: List<com.crank.music.ui.viewmodel.OfflineItem>,
    swipedItemId: String?,
    onToggleSection: (String) -> Unit,
    onSwiped: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onCancelSwipe: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Folder, title = "Smart Offline Hub")
        Spacer(modifier = Modifier.height(14.dp))

        StorageIndicator(totalStorage = totalStorage, usedStorage = usedStorage)

        Spacer(modifier = Modifier.height(16.dp))

        val sections = listOf(
            Triple("playlists", "Playlists", playlistCount),
            Triple("albums", "Albums", albumCount),
            Triple("artists", "Artists", artistCount),
            Triple("songs", "Songs", songCount)
        )

        sections.forEach { (key, label, count) ->
            OfflineCategoryCard(
                title = label,
                count = count,
                isExpanded = expandedSection == key,
                items = when (key) {
                    "playlists" -> playlists
                    "albums" -> albums
                    "artists" -> artists
                    "songs" -> songs
                    else -> emptyList()
                },
                swipedItemId = swipedItemId,
                onToggle = {
                    onToggleSection(key)
                },
                onSwiped = onSwiped,
                onRemoveItem = onRemoveItem,
                onCancelSwipe = onCancelSwipe
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StorageIndicator(totalStorage: Long, usedStorage: Long) {
    val usedFraction = (usedStorage.toFloat() / totalStorage.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = usedFraction,
        animationSpec = tween(1200, easing = LinearEasing),
        label = "storage"
    )
    val freeStorage = totalStorage - usedStorage

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CharcoalSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(90.dp)) {
                    drawArc(
                        color = CharcoalElevated,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(ChampagneGold, GoldDark, ChampagneGold)
                        ),
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatBytes(usedStorage),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                    Text(
                        text = "used",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                Text(
                    text = "Storage Used",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatBytes(usedStorage)} of ${formatBytes(totalStorage)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "${formatBytes(freeStorage)} available",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ChampagneGold,
                    trackColor = CharcoalElevated,
                )
            }
        }
    }
}

@Composable
private fun OfflineCategoryCard(
    title: String,
    count: Int,
    isExpanded: Boolean,
    items: List<com.crank.music.ui.viewmodel.OfflineItem>,
    swipedItemId: String?,
    onToggle: () -> Unit,
    onSwiped: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onCancelSwipe: () -> Unit
) {
    val rotationAnim = animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "chevron"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CharcoalSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (title) {
                        "Playlists" -> Icons.Default.Folder
                        "Albums" -> Icons.Default.LibraryMusic
                        "Artists" -> Icons.Default.Person
                        else -> Icons.Default.MusicNote
                    },
                    contentDescription = null,
                    tint = ChampagneGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                }
                Surface(
                    color = ChampagneGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ChampagneGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = rotationAnim.value }
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    items.forEach { item ->
                        SwipeableOfflineItem(
                            item = item,
                            isSwiped = swipedItemId == item.id,
                            onSwiped = { onSwiped(item.id) },
                            onRemove = { onRemoveItem(item.id) },
                            onCancelSwipe = onCancelSwipe
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableOfflineItem(
    item: com.crank.music.ui.viewmodel.OfflineItem,
    isSwiped: Boolean,
    onSwiped: () -> Unit,
    onRemove: () -> Unit,
    onCancelSwipe: () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        if (isSwiped) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                color = Color(0xFFFF5252).copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onCancelSwipe) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value < -120f) {
                                onSwiped()
                            }
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    )
                },
            color = if (isSwiped) Color.Transparent else CharcoalElevated,
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ChampagneGold.copy(alpha = 0.3f), GoldDark.copy(alpha = 0.3f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Canvas(modifier = Modifier.size(44.dp)) {
                        drawArc(
                            color = ChampagneGold,
                            startAngle = -90f,
                            sweepAngle = 360f * item.downloadProgress,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = WarmWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.fileSize,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = item.downloadDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoDownloadRulesSection(
    autoDownloadOnWifi: Boolean,
    autoDownloadLiked: Boolean,
    autoDownloadArtistReleases: Boolean,
    autoDownloadDailyMix: Boolean,
    downloadQuality: DownloadQuality,
    storageLimit: StorageLimit,
    onToggleWifi: (Boolean) -> Unit,
    onToggleLiked: (Boolean) -> Unit,
    onToggleArtistReleases: (Boolean) -> Unit,
    onToggleDailyMix: (Boolean) -> Unit,
    onQualitySelect: (DownloadQuality) -> Unit,
    onStorageLimitChange: (StorageLimit) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.CloudDownload, title = "Auto-Download Rules")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column {
                AutoDownloadToggle(
                    icon = Icons.Default.Wifi,
                    title = "Auto-download on Wi-Fi",
                    subtitle = "Download over Wi-Fi only",
                    isEnabled = autoDownloadOnWifi,
                    onToggle = onToggleWifi
                )
                AutoDownloadRuleRow(
                    icon = Icons.Default.Favorite,
                    title = "When I add song to Liked Songs",
                    subtitle = "→ Auto-download",
                    isEnabled = autoDownloadLiked,
                    onToggle = onToggleLiked
                )
                AutoDownloadRuleRow(
                    icon = Icons.Default.Person,
                    title = "When I follow artist",
                    subtitle = "→ Download new releases",
                    isEnabled = autoDownloadArtistReleases,
                    onToggle = onToggleArtistReleases
                )
                AutoDownloadRuleRow(
                    icon = Icons.Default.MusicNote,
                    title = "Download my Daily Mix every night",
                    subtitle = "Overnight when charging",
                    isEnabled = autoDownloadDailyMix,
                    onToggle = onToggleDailyMix,
                    showDivider = false
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Quality",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(DownloadQuality.entries.toList()) { quality ->
                        val isSelected = quality == downloadQuality
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) ChampagneGold else Color.Transparent,
                            animationSpec = tween(200),
                            label = "border"
                        )

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                .clickable { onQualitySelect(quality) },
                            color = if (isSelected) ChampagneGold.copy(alpha = 0.15f) else CharcoalElevated,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    text = quality.label.split(" (")[0],
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) ChampagneGold else TextSecondary
                                )
                                Text(
                                    text = quality.label.substringAfter("(").removeSuffix(")"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Storage Limit",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = storageLimit.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = ChampagneGold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(StorageLimit.entries.toList()) { limit ->
                        val isSelected = limit == storageLimit
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onStorageLimitChange(limit) },
                            color = if (isSelected) ChampagneGold else CharcoalElevated,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = limit.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) ObsidianBlack else TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoDownloadToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isEnabled) ChampagneGold else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = WarmWhite
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBlack,
                checkedTrackColor = ChampagneGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CharcoalElevated
            )
        )
    }
}

@Composable
private fun AutoDownloadRuleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isEnabled) ChampagneGold else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = WarmWhite
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (isEnabled) ChampagneGold else TextTertiary
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBlack,
                checkedTrackColor = ChampagneGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CharcoalElevated
            )
        )
    }
}

@Composable
private fun DownloadManagerSection(
    activeDownloads: List<com.crank.music.ui.viewmodel.DownloadTask>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Download, title = "Download Manager")
        Spacer(modifier = Modifier.height(10.dp))

        if (activeDownloads.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CharcoalSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All downloads complete",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                    Text(
                        text = "No active downloads",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            activeDownloads.forEach { task ->
                DownloadTaskCard(
                    task = task,
                    pulseAlpha = pulseAlpha,
                    onPause = { onPause(task.id) },
                    onResume = { onResume(task.id) },
                    onCancel = { onCancel(task.id) },
                    onRetry = { onRetry(task.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: com.crank.music.ui.viewmodel.DownloadTask,
    pulseAlpha: Float,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = task.progress,
        animationSpec = tween(300),
        label = "progress"
    )
    val statusColor by animateColorAsState(
        targetValue = when (task.status) {
            DownloadStatus.DOWNLOADING -> ChampagneGold
            DownloadStatus.PAUSED -> Color(0xFFFF9800)
            DownloadStatus.FAILED -> Color(0xFFFF5252)
            DownloadStatus.QUEUED -> TextSecondary
            DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
        },
        animationSpec = tween(200),
        label = "status_color"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CharcoalSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (task.status) {
                            DownloadStatus.DOWNLOADING -> Icons.Default.CloudDownload
                            DownloadStatus.PAUSED -> Icons.Default.Pause
                            DownloadStatus.FAILED -> Icons.Default.Refresh
                            DownloadStatus.QUEUED -> Icons.Default.MusicNote
                            DownloadStatus.COMPLETED -> Icons.Default.Check
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                    Text(
                        text = task.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                Text(
                    text = task.fileSize,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = statusColor,
                        trackColor = CharcoalElevated,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (task.status == DownloadStatus.DOWNLOADING) {
                        Text(
                            text = task.speed,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (task.status == DownloadStatus.FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Download failed — tap retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF5252)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPause() },
                            color = CharcoalElevated,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Pause",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onResume() },
                            color = ChampagneGold.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    tint = ChampagneGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Resume",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = ChampagneGold
                                )
                            }
                        }
                    }
                    DownloadStatus.FAILED -> {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRetry() },
                            color = Color(0xFFFF5252).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Retry",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCancel() },
                    color = CharcoalElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFF5252)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ChampagneGold,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format("%.0f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
