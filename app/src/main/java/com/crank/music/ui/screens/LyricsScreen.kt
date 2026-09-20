package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.crank.music.ui.viewmodel.LyricsState
import com.crank.music.ui.viewmodel.LyricsTiming
import com.crank.music.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

// Apple Music lyrics: always a dark immersive surface, independent of app theme.
private val LyricsBlack = Color(0xFF000000)
private val LyricsActive = Color.White
private val LyricsInactive = Color.White.copy(alpha = 0.38f)
private val LyricsMuted = Color.White.copy(alpha = 0.55f)
private val LyricsFaint = Color.White.copy(alpha = 0.35f)

/** Lines that mean "no words to show" rather than missing data. */
private fun isInstrumental(lines: List<String>): Boolean {
    if (lines.isEmpty()) return false
    if (lines.size > 6) return false
    return lines.all { line ->
        val t = line.trim().lowercase()
        t.isEmpty() || t == "♪" || t == "♫" || t == "..." ||
            t.contains("instrumental")
    }
}

@Composable
fun LyricsScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit = {}
) {
    val playerState by playerViewModel.playerState.collectAsState()
    val lyricsState by playerViewModel.lyricsState.collectAsState()
    val song = playerState.currentSong
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LyricsBlack)
    ) {
        // ── 1. Immersive background: heavy-blurred artwork, darkened to ~35% ──
        if (song?.artworkUrl.orEmpty().isNotBlank()) {
            AsyncImage(
                model = song?.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(70.dp)
                    .graphicsLayer { alpha = 0.38f }
            )
        }
        // Darken so text is readable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LyricsBlack.copy(alpha = 0.62f))
        )
        // Vignette: darker at the edges, transparent in the middle.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            LyricsBlack.copy(alpha = 0.55f)
                        )
                    )
                )
        )
        // Top/bottom scrims for header/footer legibility.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LyricsBlack.copy(alpha = 0.72f),
                            Color.Transparent,
                            Color.Transparent,
                            LyricsBlack.copy(alpha = 0.78f)
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // ── 2. Header: collapse chevron + title/artist ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close lyrics",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song?.title ?: "Unknown Song",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = song?.artistName ?: "Unknown Artist",
                        color = LyricsMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                // Balance the chevron so the titles stay optically centered.
                Spacer(modifier = Modifier.width(48.dp))
            }

            // ── 3. Body ──
            Box(modifier = Modifier.weight(1f)) {
                when (val state = lyricsState) {
                    is LyricsState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.85f),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    is LyricsState.Unavailable -> {
                        EmptyLyricsMessage(
                            title = "No lyrics available for this track."
                        )
                    }

                    is LyricsState.Success -> {
                        if (isInstrumental(state.lines.map { it.text })) {
                            EmptyLyricsMessage(title = "Instrumental")
                        } else if (state.timing == LyricsTiming.ESTIMATED) {
                            // Unsynced plain text: clean scrollable document, no karaoke.
                            UnsyncedLyricsDocument(
                                lines = state.lines.map { it.text }
                            )
                        } else {
                            SyncedKaraokeLyrics(
                                lines = state.lines.map { it.text to it.timestampMs },
                                progressMs = playerState.progress,
                                onSeekTo = { timestampMs ->
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    playerViewModel.seekTo(timestampMs)
                                }
                            )
                        }
                    }
                }
            }

            // ── Footer ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Lyrics",
                    color = LyricsFaint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Provided by YouTube Music • LRCLIB",
                    color = LyricsFaint.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EmptyLyricsMessage(title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = LyricsFaint,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = LyricsMuted,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UnsyncedLyricsDocument(lines: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(lines, key = { index, _ -> index }) { _, text ->
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 20.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SyncedKaraokeLyrics(
    lines: List<Pair<String, Long>>,
    progressMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val lazyListState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    // Active line: last line whose timestamp is at or before now.
    // -1 while in the intro (nothing highlighted yet, Apple-style).
    val activeIndex = remember(lines, progressMs) {
        lines.indexOfLast { (_, timestampMs) -> timestampMs <= progressMs }
    }

    // Reset scroll position on track change.
    LaunchedEffect(lines) {
        lazyListState.scrollToItem(0)
        autoScroll = true
    }

    // Manual scroll pauses auto-scroll; resume ~1.2s after the user lets go
    // and snap back to the live line.
    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            autoScroll = false
        } else if (!autoScroll) {
            delay(1200)
            autoScroll = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Aim the active line at the upper-middle third of the viewport.
        val centerOffsetPx = with(density) { -(maxHeight * 0.32f).toPx().toInt() }

        LaunchedEffect(activeIndex, autoScroll) {
            if (autoScroll && activeIndex >= 0) {
                runCatching {
                    lazyListState.animateScrollToItem(
                        index = activeIndex,
                        scrollOffset = centerOffsetPx
                    )
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            // Large top/bottom padding so the first/last lines can also center.
            contentPadding = PaddingValues(
                horizontal = 28.dp,
                vertical = maxHeight * 0.38f
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, (text, timestampMs) ->
                KaraokeLine(
                    text = text,
                    isActive = index == activeIndex,
                    isPast = index < activeIndex,
                    onClick = { onSeekTo(timestampMs) }
                )
            }
        }

        // "Back to live line" pill while the user is browsing away.
        if (!autoScroll && activeIndex >= 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable {
                        autoScroll = true
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Back to current line",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun KaraokeLine(
    text: String,
    isActive: Boolean,
    isPast: Boolean,
    onClick: () -> Unit
) {
    // Apple depth-of-field: past lines fall away darkest, upcoming dimmed.
    val color by animateColorAsState(
        targetValue = when {
            isActive -> LyricsActive
            isPast -> Color.White.copy(alpha = 0.25f)
            else -> LyricsInactive
        },
        animationSpec = tween(200),
        label = "lyric_color"
    )
    val fontSizeScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.78f,
        animationSpec = tween(200),
        label = "lyric_scale"
    )
    val baseSp = 30f
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.9f,
        animationSpec = tween(200),
        label = "lyric_alpha"
    )

    Text(
        text = text,
        color = color,
        fontSize = (baseSp * fontSizeScale).sp,
        lineHeight = (baseSp * fontSizeScale * 1.22f).sp,
        fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    )
}
