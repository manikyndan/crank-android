package com.crank.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.SongRow
import com.crank.music.ui.components.SongRowSkeleton
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.AlbumDetailViewModel

@Composable
fun AlbumDetailScreen(
    albumDetailViewModel: AlbumDetailViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onBackClick: () -> Unit = {}
) {
    val uiState by albumDetailViewModel.uiState.collectAsState()
    val album = uiState.album

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = WarmWhite,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .aspectRatio(1f)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = ChampagneGold
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(CharcoalElevated),
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
                            tint = ChampagneGold,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = album?.title ?: "Album",
                    style = MaterialTheme.typography.displayLarge,
                    color = WarmWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    // Build the line from the parts we actually have. Previously this fell back to
                    // a hardcoded "2024", so an unknown year silently rendered as a specific one.
                    text = listOfNotNull(
                        album?.artistName?.takeIf { it.isNotBlank() },
                        album?.releaseYear?.takeIf { it.isNotBlank() },
                    ).joinToString(" • ").ifBlank { "Artist" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val firstSong = uiState.songs.firstOrNull()
                        if (firstSong != null) {
                            onSongSelectWithContext(firstSong, uiState.songs)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianBlack,
                        contentColor = WarmWhite
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .height(48.dp)
                        .border(1.dp, ChampagneGold, CircleShape)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Album",
                            tint = ChampagneGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play Album",
                            style = MaterialTheme.typography.labelLarge,
                            color = WarmWhite
                        )
                    }
                }
            }
        }

        if (uiState.isLoading) {
            items(5) { SongRowSkeleton() }
        } else {
            items(uiState.songs) { song ->
                SongRow(
                    song = song,
                    onClick = {
                        onSongSelectWithContext(song, uiState.songs)
                    }
                )
            }
        }
    }
}
