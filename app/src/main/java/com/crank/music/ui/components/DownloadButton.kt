package com.crank.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crank.music.domain.model.DownloadState
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated

@Composable
fun DownloadButton(
    downloadState: DownloadState,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .border(1.dp, ChampagneGold, CircleShape)
            .clip(CircleShape)
            .background(CharcoalElevated)
            .clickable { onDownloadClick() },
        contentAlignment = Alignment.Center
    ) {
        DownloadIndicator(state = downloadState)
    }
}
