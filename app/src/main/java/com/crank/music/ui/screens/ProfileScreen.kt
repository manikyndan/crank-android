package com.crank.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.crank.music.ui.components.GroupedSettingsSection
import com.crank.music.ui.components.SectionHeader
import com.crank.music.ui.components.SettingsNavRow
import com.crank.music.ui.components.StatCard
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.ProfileViewModel

data class QuickActionItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onRecognizeClick: () -> Unit = {}
) {
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()

    val quickActions = listOf(
        QuickActionItem("Settings", Icons.Default.Settings, onSettingsClick),
        QuickActionItem("Downloads", Icons.Default.DownloadForOffline, onDownloadsClick),
        QuickActionItem("Recognize Song", Icons.Default.Mic, onRecognizeClick)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(1.5.dp, ChampagneGold, CircleShape)
                        .clip(CircleShape)
                        .background(CharcoalElevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = uiState.avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar Placeholder",
                            tint = ChampagneGold,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = uiState.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = CharcoalElevated,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (uiState.isPremium) "Premium Member" else "Free Member",
                        style = MaterialTheme.typography.labelMedium,
                        color = ChampagneGold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onStatsClick() }
                    .padding(4.dp)
            ) {
                SectionHeader(title = "Listening Stats")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = "Hours",
                        value = uiState.totalHours,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Favorite Artist",
                        value = uiState.favoriteArtist,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Total Plays",
                        value = uiState.totalPlays,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            // One grouped list rather than a tile grid. Same actions, but each row carries a
            // chevron so it reads as navigation, and the section matches Settings' shape — which
            // is the consistency Apple Music's Profile/Settings screens have.
            GroupedSettingsSection(title = "Quick Actions") {
                quickActions.forEachIndexed { index, action ->
                    SettingsNavRow(
                        icon = action.icon,
                        title = action.title,
                        onClick = action.onClick,
                        showDivider = index < quickActions.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(
    action: QuickActionItem,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { action.onClick() },
        color = CharcoalSurface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.title,
                tint = ChampagneGold,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = action.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = WarmWhite
            )
        }
    }
}
