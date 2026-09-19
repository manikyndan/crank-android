package com.crank.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.AlbumCard
import com.crank.music.ui.components.SectionHeader
import com.crank.music.ui.components.SongRow
import com.crank.music.ui.components.SongRowSkeleton
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.ArtistViewModel

@Composable
fun ArtistDetailScreen(
    artistViewModel: ArtistViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onSongSelectWithContext: (Song, List<Song>) -> Unit = { song, _ -> onSongSelect(song) },
    onBackClick: () -> Unit = {}
) {
    val uiState by artistViewModel.uiState.collectAsState()
    val artist = uiState.artist

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
                        .size(140.dp)
                        .border(2.dp, ChampagneGold, CircleShape)
                        .clip(CircleShape)
                        .background(CharcoalElevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (artist?.imageUrl.orEmpty().isNotBlank()) {
                        AsyncImage(
                            model = artist?.imageUrl,
                            contentDescription = artist?.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Artist Image",
                            tint = ChampagneGold,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = artist?.name ?: "Artist",
                    style = MaterialTheme.typography.displayLarge,
                    color = WarmWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // The listener count is only rendered when the API actually provided one.
                // The search layer does not, so this line is normally absent — better than
                // a permanent "0 listeners" that reads as a real measurement of zero.
                val listenerCount = artist?.followerCount ?: 0L
                if (listenerCount > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$listenerCount listeners",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val firstSong = uiState.topSongs.firstOrNull()
                        if (firstSong != null) {
                            onSongSelectWithContext(firstSong, uiState.topSongs)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGold,
                        contentColor = ObsidianBlack
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Artist",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play Popular",
                            style = MaterialTheme.typography.labelLarge,
                            color = ObsidianBlack
                        )
                    }
                }
            }
        }

        if (uiState.albums.isNotEmpty()) {
            item {
                SectionHeader(title = "Discography")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(uiState.albums) { album ->
                        AlbumCard(album = album)
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Popular Tracks")
        }

        if (uiState.isLoading) {
            items(5) { SongRowSkeleton() }
        } else {
            items(uiState.topSongs) { song ->
                SongRow(
                    song = song,
                    onClick = {
                        onSongSelectWithContext(song, uiState.topSongs)
                    }
                )
            }
        }
    }
}
