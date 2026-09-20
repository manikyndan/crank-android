package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.crank.music.ui.components.SongRowSkeleton
import com.crank.music.ui.viewmodel.AlbumDetailViewModel

/**
 * Apple Music-style album page.
 *
 * Same contract as before (plus optional artist/current-track hooks with
 * defaults, so existing call sites keep compiling). The tracklist comes from
 * the fixed browse path — complete, in true album order, never re-sorted —
 * and tapping a row passes the whole list as context so Next/Previous walk
 * the album.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumDetailViewModel: AlbumDetailViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onBackClick: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    currentSongId: String? = null,
    isPlaying: Boolean = false,
) {
    val uiState by albumDetailViewModel.uiState.collectAsState()
    val album = uiState.album
    val songs = uiState.songs
    val view = LocalView.current

    fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    var optionsSong by remember { mutableStateOf<Song?>(null) }
    var showAlbumOptions by remember { mutableStateOf(false) }

    val totalRuntimeMs = songs.sumOf { it.durationMs }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Header: centred artwork, title, accent artist, real-only sub-line.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(8.dp),
                            spotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (album?.artworkUrl.orEmpty().isNotBlank()) {
                        AsyncImage(
                            model = album?.artworkUrl,
                            contentDescription = album?.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = "Album Artwork",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = album?.title ?: "Album",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = album?.artistName?.ifBlank { "Unknown Artist" } ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        album?.artistName?.ifBlank { null }?.let {
                            onArtistClick(it)
                            haptic()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Only parts the backend actually provides. Record label and
                // genre are unknown to every endpoint here, so they are omitted
                // rather than printed as guesses.
                val subLine = buildList {
                    album?.releaseYear?.takeIf { it.isNotBlank() }?.let { add(it) }
                    add("Album")
                    if (songs.isNotEmpty()) add(formatCount(songs.size))
                }.joinToString(" • ")
                Text(
                    text = subLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Action row: Play / Shuffle / Save / Download / More.
        if (songs.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val first = songs.first()
                            onSongSelectWithContext(first, songs)
                            haptic()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Button(
                        onClick = {
                            val shuffled = songs.shuffled()
                            onSongSelectWithContext(shuffled.first(), shuffled)
                            haptic()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Shuffle",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        albumDetailViewModel.saveToLibrary()
                        haptic()
                    }) {
                        Icon(
                            imageVector = if (uiState.isSavedToLibrary) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = "Add to Library",
                            tint = if (uiState.isSavedToLibrary) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        albumDetailViewModel.downloadAlbum()
                        haptic()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        showAlbumOptions = true
                        haptic()
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Numbered tracklist in true album order.
        if (uiState.isLoading) {
            items(8) {
                SongRowSkeleton()
            }
        } else {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                val isCurrent = song.id == currentSongId
                AppleTrackRow(
                    number = index + 1,
                    song = song,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying && isCurrent,
                    onClick = {
                        onSongSelectWithContext(song, songs)
                        haptic()
                    },
                    onMenuClick = {
                        optionsSong = song
                        haptic()
                    }
                )
                if (index < songs.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }

        // Footer: real-data-only credits.
        if (!uiState.isLoading && songs.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val footer = buildList {
                        add(formatCount(songs.size))
                        if (totalRuntimeMs > 0) add(formatRuntime(totalRuntimeMs))
                        album?.releaseYear?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }.joinToString(" • ")
                    Text(
                        text = footer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    // Copyright and label lines are intentionally absent: no
                    // endpoint provides them, and printing them would be fiction.
                }
            }
        }

        // More by this artist.
        if (uiState.moreByArtist.isNotEmpty() && album != null) {
            item {
                Text(
                    text = "More By ${album.artistName}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.moreByArtist, key = { it.id }) { other ->
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clickable {
                                    onAlbumClick(other)
                                    haptic()
                                }
                        ) {
                            Surface(
                                modifier = Modifier.size(120.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 1.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (other.artworkUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = other.artworkUrl,
                                            contentDescription = other.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = other.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = other.artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    optionsSong?.let { target ->
        AlbumTrackOptionsSheet(
            song = target,
            songs = songs,
            onPlay = {
                onSongSelectWithContext(target, songs)
                optionsSong = null
            },
            onGoToArtist = {
                onArtistClick(target.artistName)
                optionsSong = null
            },
            onDismiss = { optionsSong = null }
        )
    }

    if (showAlbumOptions && album != null) {
        AlbumOptionsSheet(
            albumTitle = album.title,
            onPlay = {
                onSongSelectWithContext(songs.first(), songs)
                showAlbumOptions = false
            },
            onShuffle = {
                val shuffled = songs.shuffled()
                onSongSelectWithContext(shuffled.first(), shuffled)
                showAlbumOptions = false
            },
            onGoToArtist = {
                album.artistName.ifBlank { null }?.let { onArtistClick(it) }
                showAlbumOptions = false
            },
            onSave = {
                albumDetailViewModel.saveToLibrary()
                showAlbumOptions = false
            },
            onDownload = {
                albumDetailViewModel.downloadAlbum()
                showAlbumOptions = false
            },
            onDismiss = { showAlbumOptions = false }
        )
    }
}

@Composable
private fun AppleTrackRow(
    number: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent) {
                PlayingBars(
                    active = isPlaying,
                    modifier = Modifier.size(width = 18.dp, height = 16.dp)
                )
            } else {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artistName,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatTrackDuration(song.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Track Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Subtle three-bar playing indicator in the accent color. Pauses frozen when paused. */
@Composable
private fun PlayingBars(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "playing_bars")
    val bar1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar3"
    )
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val levels = if (active) listOf(bar1, bar2, bar3) else listOf(0.35f, 0.35f, 0.35f)
        val barWidth = size.width / 5f
        levels.forEachIndexed { index, level ->
            val barHeight = size.height * level
            drawRect(
                color = accent,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * 2f * barWidth,
                    y = size.height - barHeight
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumTrackOptionsSheet(
    song: Song,
    songs: List<Song>,
    onPlay: () -> Unit,
    onGoToArtist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
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
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            AlbumSheetAction(
                icon = Icons.Default.PlayArrow,
                label = "Play",
                onClick = onPlay
            )
            AlbumSheetAction(
                icon = Icons.Default.MusicNote,
                label = "Go to artist",
                onClick = onGoToArtist
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumOptionsSheet(
    albumTitle: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onGoToArtist: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
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
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = albumTitle,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            AlbumSheetAction(icon = Icons.Default.PlayArrow, label = "Play", onClick = onPlay)
            AlbumSheetAction(icon = Icons.Default.Shuffle, label = "Shuffle", onClick = onShuffle)
            AlbumSheetAction(icon = Icons.Default.MusicNote, label = "Go to artist", onClick = onGoToArtist)
            AlbumSheetAction(icon = Icons.Default.Add, label = "Add to Library", onClick = onSave)
            AlbumSheetAction(icon = Icons.Default.Download, label = "Download", onClick = onDownload)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AlbumSheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun formatCount(count: Int): String = if (count == 1) "1 song" else "$count songs"

private fun formatRuntime(ms: Long): String {
    val totalMinutes = (ms / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes} min"
}

private fun formatTrackDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
