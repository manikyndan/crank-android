package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// Apple Music lyrics: always a dark immersive surface, independent of app theme.
private val LyricsBlack = Color(0xFF000000)
private val LyricsActive = Color.White
private val LyricsInactive = Color.White.copy(alpha = 0.38f)

/**
 * Bounds for the per-line scroll glide, in ms.
 *
 * The floor stops a very short line (a one-word ad-lib) from producing a near-instant jump; the
 * ceiling stops a long instrumental gap from leaving the text drifting for half a minute.
 */
private const val MIN_GLIDE_MS = 320L
private const val MAX_GLIDE_MS = 9_000L


/**
 * Where the current line sits vertically, as a fraction of the viewport.
 *
 * Upper-middle, so there is more context below (the lyrics still to come) than above — the same
 * bias Apple Music uses, and it keeps the eye from having to travel far as lines advance.
 */
private const val READING_POSITION_FRACTION = 0.32f

/**
 * How long the scroll to [index] should take: the length of that line.
 *
 * Matching the animation to the line's own duration is what produces continuous motion — the
 * scroll finishes exactly as the next line becomes current, so there is no gap where the text
 * sits still before jumping again.
 */
private fun glideDurationMs(lines: List<Pair<String, Long>>, index: Int): Long {
    val start = lines.getOrNull(index)?.second ?: return MIN_GLIDE_MS
    val next = lines.getOrNull(index + 1)?.second
    return if (next != null) {
        (next - start).coerceIn(MIN_GLIDE_MS, MAX_GLIDE_MS)
    } else {
        MIN_GLIDE_MS
    }
}

/**
 * Scrolls [index] to the reading position over [glideMs].
 *
 * Expressed as a scroll-by rather than `animateScrollToItem` because that API takes no
 * `animationSpec` in this Compose version, so it always applies its own short curve — which is
 * exactly what made the movement read as a per-line snap. Measuring the item's current offset and
 * animating the delta lets both the duration and the easing be controlled, and LinearEasing keeps
 * the velocity constant so the text never stalls part-way down a line.
 */
private suspend fun LazyListState.glideToLine(index: Int, glideMs: Long) {
    val layout = layoutInfo
    val info = layout.visibleItemsInfo.firstOrNull { it.index == index }
    if (info == null) {
        // Not composed yet — a long jump such as a seek. Place it immediately; the glide takes
        // over from the next line change.
        runCatching { animateScrollToItem(index) }
        return
    }

    val viewportHeight = layout.viewportEndOffset - layout.viewportStartOffset
    val target = layout.viewportStartOffset + (viewportHeight * READING_POSITION_FRACTION)
    val delta = (info.offset - target).toFloat()
    if (delta == 0f) return

    // Frame-synced interpolation, NOT a fixed `delay(16)` step loop.
    //
    // The previous version slept 16ms then scrolled a fixed amount. Two things made that stutter:
    // `delay` only guarantees "at least 16ms" so every frame drifted a little later, and each step
    // had to re-acquire the scroll lock, adding its own unpredictable latency. The errors did not
    // cancel — they accumulated into visible juddering.
    //
    // Driving off `withFrameNanos` instead ties the work to the display's own refresh callback, so
    // it runs at the panel's rate (60/90/120Hz) and each frame computes its position from ELAPSED
    // TIME rather than from a step count. A late or dropped frame therefore lands on the correct
    // position instead of falling permanently behind, which is what removes the judder.
    val startNanos = withFrameNanos { it }
    var applied = 0f
    while (currentCoroutineContext().isActive) {
        val now = withFrameNanos { it }
        val elapsedMs = (now - startNanos) / 1_000_000f
        val fraction = (elapsedMs / glideMs).coerceIn(0f, 1f)
        val target = delta * fraction
        val step = target - applied
        if (step != 0f) {
            // One brief lock per frame, so a manual drag can still interject mid-glide. Holding a
            // single lock for the line's whole duration would block the user's own scrolling.
            runCatching { scroll { scrollBy(step) } }
            applied = target
        }
        if (fraction >= 1f) break
    }
}
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
                            // Unsynced plain text: glides on the estimated timings, but stays
                            // unhighlighted because those timings are inferred, not published.
                            UnsyncedLyricsDocument(
                                lines = state.lines.map { it.text to it.timestampMs },
                                progressMs = playerState.progress,
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

            // ── Transport controls ──
            //
            // Replaces the old "Lyrics / Provided by YouTube Music • LRCLIB" footer. That text
            // told the user nothing they could act on and took the most reachable strip on the
            // screen; transport belongs here instead, so the lyrics can be driven without
            // collapsing back to Now Playing.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        playerViewModel.playPrevious()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        playerViewModel.togglePlayPause()
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        playerViewModel.playNext()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
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

/**
 * Plain-lyrics document: uniform styling, no karaoke highlight — but it still GLIDES.
 *
 * The timings here are estimated because the source carried no LRC timestamps, so highlighting a
 * line as "now singing" would overstate what is actually known. Scrolling to the estimate does
 * not overstate anything: the page stays roughly where the song is, which is what makes the screen
 * feel alive, while the missing highlight keeps it honest about the precision.
 *
 * Previously this was a static list with no scrolling at all, so any track whose lyrics came back
 * unsynced looked frozen.
 */
@Composable
private fun UnsyncedLyricsDocument(
    lines: List<Pair<String, Long>>,
    progressMs: Long,
) {
    val lazyListState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    val activeIndex = remember(lines, progressMs) {
        lines.indexOfLast { (_, timestampMs) -> timestampMs <= progressMs }
    }

    LaunchedEffect(lines) {
        lazyListState.scrollToItem(0)
        autoScroll = true
    }

    // Same contract as the karaoke view, and for the same reason keyed on drag interactions:
    // `isScrollInProgress` is also true during this screen's own glide, so it cannot be used to
    // detect a user takeover.
    LaunchedEffect(lazyListState) {
        lazyListState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> autoScroll = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    delay(1200)
                    autoScroll = true
                }
                else -> Unit
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(activeIndex, autoScroll) {
            if (autoScroll && activeIndex >= 0) {
                lazyListState.glideToLine(
                    index = activeIndex,
                    glideMs = glideDurationMs(lines, activeIndex),
                )
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            // Same generous padding as the karaoke view, so the first and last lines can also
            // reach the reading position instead of being stranded at an edge.
            contentPadding = PaddingValues(
                horizontal = 28.dp,
                vertical = maxHeight * 0.38f
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { _, (text, _) ->
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 20.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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

    // How far through the active line we are, driving the karaoke wipe. The last line has no
    // successor to interpolate against, so it gets a nominal 4s span rather than sitting at 0.
    val activeLineProgress = remember(lines, progressMs, activeIndex) {
        if (activeIndex < 0) {
            0f
        } else {
            val start = lines[activeIndex].second
            val end = lines.getOrNull(activeIndex + 1)?.second ?: (start + 4_000L)
            if (end <= start) {
                1f
            } else {
                ((progressMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    // Reset scroll position on track change.
    LaunchedEffect(lines) {
        lazyListState.scrollToItem(0)
        autoScroll = true
    }

    // Manual scroll pauses auto-scroll; resume ~1.2s after the user lets go.
    //
    // Keyed on DRAG interactions rather than `isScrollInProgress`. That flag is also true while
    // the screen's own glide is running, so using it here made the view treat its own scrolling as
    // a user takeover and switch auto-scroll off — the "Back to current line" pill appeared and
    // the lyrics stopped following the song. Only a real finger drag should pause it.
    LaunchedEffect(lazyListState) {
        lazyListState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> autoScroll = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    delay(1200)
                    autoScroll = true
                }
                else -> Unit
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Glide to the new line over the line's OWN duration rather than a fixed ~300ms.
        //
        // This is what makes it read as Apple Music rather than as a list that jumps: the scroll
        // starts when a line becomes current and finishes exactly as the next one does, so the
        // text is moving continuously instead of snapping, then sitting still, then snapping
        // again. LinearEasing keeps the velocity constant across the line — an eased curve would
        // stall at each end and reintroduce the stutter.
        LaunchedEffect(activeIndex, autoScroll) {
            if (autoScroll && activeIndex >= 0) {
                lazyListState.glideToLine(
                    index = activeIndex,
                    glideMs = glideDurationMs(lines, activeIndex),
                )
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
                    progress = if (index == activeIndex) activeLineProgress else 0f,
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
    /** 0..1 through the active line. Ignored unless [isActive]. */
    progress: Float,
    onClick: () -> Unit
) {
    // Apple depth-of-field: past lines fall away darkest, upcoming dimmed.
    val baseColor by animateColorAsState(
        targetValue = when {
            // The un-sung remainder of the active line sits at the "upcoming" tone, so the wipe
            // reads as light moving across the line rather than as a colour switch.
            isActive -> LyricsInactive
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
    val baseSp = 30f * fontSizeScale
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.9f,
        animationSpec = tween(200),
        label = "lyric_alpha"
    )
    val weight = if (isActive) FontWeight.Black else FontWeight.Bold

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        // Base copy: the whole line in the dim tone.
        Text(
            text = text,
            color = baseColor,
            fontSize = baseSp.sp,
            lineHeight = (baseSp * 1.22f).sp,
            fontWeight = weight,
            textAlign = TextAlign.Start
        )

        // Word-level wipe: a second copy in the accent colour, clipped to how far through the
        // line we are. LRC only carries per-line timings, so the fraction is interpolated across
        // the line's own span — the same trick Apple Music's non-word-timed tracks use. It tracks
        // playback rate for free, because the fraction is derived from the player's position.
        if (isActive) {
            Text(
                text = text,
                color = LyricsActive,
                fontSize = baseSp.sp,
                lineHeight = (baseSp * 1.22f).sp,
                fontWeight = weight,
                textAlign = TextAlign.Start,
                modifier = Modifier.drawWithContent {
                    clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
                        this@drawWithContent.drawContent()
                    }
                }
            )
        }
    }
}
