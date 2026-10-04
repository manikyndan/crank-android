package com.crank.music.ui.screens

import java.util.Locale

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.MiniEqualizerBars
import com.crank.music.ui.viewmodel.LikedMusicViewModel
import com.crank.music.ui.viewmodel.LikedSort
import kotlin.math.roundToInt

/**
 * Liked Songs, laid out like Spotify's playlist of the same name.
 *
 * Every value on this screen comes from the real liked set: the list is the same Room query the
 * Library and the player's heart button read and write, and the count is its length. Nothing
 * here is sample data.
 *
 * Two brief elements that earlier could not be built are now wired to real data:
 *
 * - **Explicit badge** — `Song.isExplicit` is parsed from InnerTube's `MUSIC_EXPLICIT_BADGE` and
 *   iTunes' `trackExplicitness`, stored on the Room `songs` row, and shown as a small grey 'E'.
 * - **Album name in the subtitle** — `Song.albumName` is captured from the album/playlist shelf
 *   (or iTunes `collectionName`) and stored alongside, so the subtitle reads "Artist • Album".
 *   Tracks discovered via search have no album name and fall back to the artist alone; this is a
 *   real-data gap, not a fabrication.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedMusicScreen(
    onBackClick: () -> Unit,
    /** Plays [Song] with the whole liked list as the queue, so Next/Previous walk the list. */
    onPlaySongs: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onToggleShuffle: () -> Unit,
    onAddToQueue: (Song) -> Unit,
    shuffleEnabled: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    viewModel: LikedMusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSortSheet by remember { mutableStateOf(false) }
    var optionsTarget by remember { mutableStateOf<Song?>(null) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }

    // Fades the header in on entry rather than snapping it into place.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val headerAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "header_fade",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                LikedHeader(
                    count = uiState.allSongs.size,
                    alpha = headerAlpha,
                    onBackClick = onBackClick,
                )
            }

            item {
                LikedActionBar(
                    alpha = headerAlpha,
                    isSearchActive = uiState.isSearchActive,
                    shuffleEnabled = shuffleEnabled,
                    isDownloadingAll = uiState.isDownloadingAll,
                    hasSongs = uiState.allSongs.isNotEmpty(),
                    onPlay = { onPlayAll(uiState.allSongs) },
                    onToggleShuffle = onToggleShuffle,
                    onDownloadAll = { viewModel.toggleDownloadAll() },
                    onToggleSearch = { viewModel.setSearchActive(!uiState.isSearchActive) },
                    onOpenSort = { showSortSheet = true },
                )
            }

            if (uiState.isSearchActive) {
                item {
                    LikedSearchField(
                        query = uiState.query,
                        onQueryChange = { viewModel.onQueryChange(it) },
                        onClear = { viewModel.setSearchActive(false) },
                    )
                }
            }

            if (uiState.isLoading) {
                item { LikedMessage("Loading…") }
            } else if (uiState.allSongs.isEmpty()) {
                item {
                    LikedMessage("Songs you like will appear here.")
                }
            } else if (uiState.visibleSongs.isEmpty()) {
                item {
                    LikedMessage("No songs match \"${uiState.query}\".")
                }
            } else {
                itemsIndexed(
                    items = uiState.visibleSongs,
                    key = { _, song -> song.id },
                ) { index, song ->
                    SwipeToRemove(
                        onRemove = { viewModel.removeFromLiked(song) },
                    ) {
                        LikedTrackRow(
                            index = index,
                            song = song,
                            isCurrent = song.id == currentSongId,
                            isPlaying = isPlaying,
                            onClick = {
                                // The queue is the whole liked set, not the filtered view.
                                // Queueing `visibleSongs` would make Next/Previous stop at the
                                // edge of whatever the user had typed in the search box, which is
                                // not what "play through my liked songs" means.
                                onPlaySongs(song, uiState.allSongs)
                            },
                            onMoreClick = { optionsTarget = song },
                        )
                    }
                }
            }
        }
    }

    if (showSortSheet) {
        SortSheet(
            selected = uiState.sort,
            onSelect = {
                viewModel.setSort(it)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false },
        )
    }

    optionsTarget?.let { song ->
        TrackOptionsSheet(
            onAddToQueue = {
                onAddToQueue(song)
                optionsTarget = null
            },
            onAddToPlaylist = {
                optionsTarget = null
                playlistTarget = song
            },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artistName}")
                }
                context.startActivity(Intent.createChooser(intent, null))
                optionsTarget = null
            },
            onRemove = {
                viewModel.removeFromLiked(song)
                optionsTarget = null
            },
            onDismiss = { optionsTarget = null },
        )
    }

    playlistTarget?.let { song ->
        PlaylistPickerSheet(
            playlists = uiState.playlists,
            onPick = { playlistId ->
                viewModel.addToPlaylist(song, playlistId)
                playlistTarget = null
            },
            onDismiss = { playlistTarget = null },
        )
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────────

@Composable
private fun LikedHeader(
    count: Int,
    alpha: Float,
    onBackClick: () -> Unit,
) {
    // The gradient is built from the theme's accent so it follows light and dark mode rather
    // than hard-coding Spotify's green into a red-accented app.
    val accent = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent,
                        accent.copy(alpha = 0.55f),
                        background,
                    )
                )
            )
            .graphicsLayer { this.alpha = alpha },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(64.dp))

            Text(
                text = "Liked Songs",
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "$count ${if (count == 1) "song" else "songs"}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ── Action bar ─────────────────────────────────────────────────────────────────────

@Composable
private fun LikedActionBar(
    alpha: Float,
    isSearchActive: Boolean,
    shuffleEnabled: Boolean,
    isDownloadingAll: Boolean,
    hasSongs: Boolean,
    onPlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    onDownloadAll: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenSort: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val iconTint = MaterialTheme.colorScheme.onSurface
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = if (hasSongs) accent else accent.copy(alpha = 0.4f),
            onClick = onPlay,
            enabled = hasSongs,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play all",
                    tint = onAccent,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(onClick = onToggleShuffle, enabled = hasSongs) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = if (shuffleEnabled) "Shuffle on" else "Shuffle off",
                tint = if (shuffleEnabled) accent else iconTint,
            )
        }

        // Stays enabled while a bulk download runs, because a second tap is what stops it.
        IconButton(onClick = onDownloadAll, enabled = hasSongs) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = if (isDownloadingAll) {
                    "Stop downloading all"
                } else {
                    "Download all"
                },
                tint = if (isDownloadingAll) accent else iconTint,
            )
        }

        IconButton(onClick = onToggleSearch, enabled = hasSongs) {
            Icon(
                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                contentDescription = if (isSearchActive) "Close search" else "Search liked songs",
                tint = if (isSearchActive) accent else iconTint,
            )
        }

        IconButton(onClick = onOpenSort, enabled = hasSongs) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
                tint = inactiveTint,
            )
        }
    }
}

// ── Search field ───────────────────────────────────────────────────────────────────

@Composable
private fun LikedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    // Focused on appearance, so tapping the search icon leaves the user ready to type. Without
    // this the field renders but the keyboard stays down and typing goes nowhere, which reads as
    // the search being broken rather than as needing a second tap.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = hintColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search in Liked Songs",
                    color = hintColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search text",
                    tint = hintColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── Tracklist row ──────────────────────────────────────────────────────────────────

@Composable
internal fun LikedTrackRow(
    index: Int,
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    /** False on surfaces that have no per-row menu (e.g. Downloads), so no dead button shows. */
    showMoreButton: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    val titleColor = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val artworkBg = MaterialTheme.colorScheme.surfaceVariant

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrent) {
                    // The index is replaced, not decorated: at a glance the row should read as
                    // "this is playing", and a number plus a glyph reads as two facts.
                    MiniEqualizerBars(
                        isPlaying = isPlaying,
                        modifier = Modifier.size(width = 14.dp, height = 14.dp),
                        activeColor = accent,
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        color = subtitleColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(4.dp),
                color = artworkBg,
            ) {
                if (song.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.title,
                        color = titleColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Explicit badge: a small grey 'E' box, shown only when the source flagged the
                    // track. Carried on Song.isExplicit, never invented.
                    if (song.isExplicit) {
                        Spacer(modifier = Modifier.width(6.dp))
                        ExplicitBadge()
                    }
                }
                Text(
                    text = if (!song.albumName.isNullOrBlank()) {
                        "${song.artistName} • ${song.albumName}"
                    } else {
                        song.artistName
                    },
                    color = subtitleColor,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatLikedDuration(song.durationMs),
                color = subtitleColor,
                style = MaterialTheme.typography.bodySmall,
            )

            if (showMoreButton) {
                IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = subtitleColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Hairline divider. Drawn rather than a Divider so its alpha stays subtle against the
        // dark body without competing with the row content.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        )
    }
}

/**
 * The small grey 'E' box shown beside an explicit track's title, matching Spotify's convention.
 *
 * Purely presentational: the caller decides whether to show it from [Song.isExplicit], which is
 * sourced from InnerTube's explicit badge or iTunes' track explicitness — never fabricated.
 */
@Composable
internal fun ExplicitBadge() {
    Box(
        modifier = Modifier
            .size(width = 16.dp, height = 16.dp)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(3.dp)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "E",
            color = MaterialTheme.colorScheme.surface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ── Swipe to remove ────────────────────────────────────────────────────────────────

/**
 * Wraps a row so dragging it left reveals a red Remove panel, as Spotify does.
 *
 * The panel is drawn *behind* the row and the row slides over it, so the reveal needs no layout
 * animation of its own — only the row's offset changes, which keeps the gesture on the cheap
 * path. Releasing past the halfway point snaps fully open rather than removing immediately, so a
 * stray horizontal scroll cannot delete a song.
 *
 * The panel is composed only while the row is displaced — including the retract animation — never
 * at rest. That is not just an optimisation for a long list: an always-present panel puts a live
 * "Remove" click target underneath every row, and any pixel the row's own surface does not cover
 * — the hairline divider between rows, most obviously — would fall through to it and delete a song
 * on an ordinary tap. Not composing it makes the resting state genuinely inert.
 *
 * It is keyed on `animated` (the smoothed offset), not on `offset` itself: on release-to-close
 * `offset` snaps to 0 instantly, but `animated` eases the rest of the way, so gating on `offset`
 * would drop the panel while the row is still sliding home and the red would pop out a beat early.
 */
@Composable
private fun SwipeToRemove(
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    val removeColor = MaterialTheme.colorScheme.error
    val background = MaterialTheme.colorScheme.background
    val revealPx = 128f

    var offset by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(targetValue = offset, label = "swipe_offset")

    Box(modifier = Modifier.fillMaxWidth()) {
        if (animated != 0f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(removeColor)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Remove",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            offset = 0f
                            onRemove()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animated.roundToInt(), 0) }
                .background(background)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offset = if (offset < -revealPx / 2f) -revealPx else 0f
                        },
                        onDragCancel = { offset = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offset = (offset + dragAmount).coerceIn(-revealPx, 0f)
                        },
                    )
                },
        ) {
            content()
        }
    }
}

// ── Sheets ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    selected: LikedSort,
    onSelect: (LikedSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LikedSort.entries.forEach { sort ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(sort) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sort.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (sort == selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackOptionsSheet(
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            LikedSheetRow(Icons.Default.Add, "Add to Queue", onAddToQueue)
            LikedSheetRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", onAddToPlaylist)
            LikedSheetRow(Icons.Default.Share, "Share", onShare)
            LikedSheetRow(
                Icons.Default.Delete,
                "Remove from Liked Songs",
                onRemove,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    playlists: List<com.crank.music.data.local.PlaylistEntity>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Add to playlist",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (playlists.isEmpty()) {
                Text(
                    text = "You don't have any playlists yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                playlists.forEach { playlist ->
                    LikedSheetRow(
                        icon = Icons.Default.Add,
                        label = playlist.title,
                        onClick = { onPick(playlist.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LikedSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    val color = tint ?: MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LikedMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** `3:45` for a track length, matching the format used elsewhere in the app. */
private fun formatLikedDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "—"
    val totalSeconds = durationMs / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
