package com.crank.music.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.crank.music.ui.components.EmptyState
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.LyricsState
import com.crank.music.ui.viewmodel.PlayerViewModel

@Composable
fun LyricsScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit = {}
) {
    val playerState by playerViewModel.playerState.collectAsState()
    val lyricsState by playerViewModel.lyricsState.collectAsState()
    val song = playerState.currentSong

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        if (song?.artworkUrl.orEmpty().isNotBlank()) {
            AsyncImage(
                model = song?.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.18f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack.copy(alpha = 0.85f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = WarmWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.displayLarge,
                        color = WarmWhite
                    )
                    Text(
                        text = song?.title ?: "Unknown Song",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = lyricsState) {
                is LyricsState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ChampagneGold)
                    }
                }

                is LyricsState.Unavailable -> {
                    EmptyState(
                        icon = Icons.Default.MusicNote,
                        title = "Lyrics Unavailable",
                        message = "Lyrics not available for this track yet."
                    )
                }

                is LyricsState.Success -> {
                    val lines = state.lines
                    val currentProgress = playerState.progress

                    val activeIndex = lines.indexOfLast { it.timestampMs <= currentProgress }.coerceAtLeast(0)

                    val lazyListState = rememberLazyListState()

                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0 && lines.isNotEmpty()) {
                            lazyListState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(lines) { index, line ->
                            val isActive = index == activeIndex

                            Text(
                                text = line.text,
                                style = if (isActive) {
                                    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                color = if (isActive) WarmWhite else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
