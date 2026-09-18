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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.domain.model.DownloadState
import com.crank.music.domain.model.Song
import com.crank.music.ui.components.DownloadIndicator
import com.crank.music.ui.components.SongRow
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
    onSongSelect: (Song) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val uiState by downloadsViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = WarmWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Downloads",
                    style = MaterialTheme.typography.displayLarge,
                    color = WarmWhite
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = "Downloads Icon",
                    tint = ChampagneGold,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = CharcoalSurface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Offline Mode",
                        style = MaterialTheme.typography.titleLarge,
                        color = WarmWhite
                    )
                    Text(
                        text = "Only show downloaded content",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }

                Switch(
                    checked = uiState.isOfflineModeEnabled,
                    onCheckedChange = { downloadsViewModel.toggleOfflineMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBlack,
                        checkedTrackColor = ChampagneGold,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CharcoalElevated
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.downloadedSongs) { song ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SongRow(
                            song = song,
                            onClick = { onSongSelect(song) }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    DownloadIndicator(state = DownloadState.COMPLETED)
                }
            }

            if (uiState.downloadedSongs.isEmpty()) {
                item {
                    Text(
                        text = "No downloaded songs found. Download tracks to listen offline.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 32.dp, start = 8.dp, end = 8.dp)
                    )
                }
            }
        }
    }
}
