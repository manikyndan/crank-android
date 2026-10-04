package com.crank.music.ui.screens

import com.crank.music.util.confirmHaptic
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.crank.music.R
import com.crank.music.ui.components.ErrorState
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldDark
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.AvailableRelease
import com.crank.music.ui.viewmodel.UpdateCheckerViewModel
import com.crank.music.ui.viewmodel.UpdatePhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCheckerScreen(
    viewModel: UpdateCheckerViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = WarmWhite,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "App Updates",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                AppInfoCard(
                    version = "${uiState.currentVersionName} (build ${uiState.currentVersionCode})",
                    build = uiState.minSdk,
                    lastUpdate = uiState.installDate,
                    appSize = uiState.appSize
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = uiState.phase,
                    transitionSpec = {
                        fadeIn(tween(400)) + scaleIn(initialScale = 0.9f, animationSpec = tween(400)) togetherWith
                        fadeOut(tween(300)) + scaleOut(targetScale = 0.9f, animationSpec = tween(300))
                    },
                    label = "phase_transition"
                ) { phase ->
                    when (phase) {
                        // IDLE and CHECKING both show the spinner: the id request runs on entry.
                        UpdatePhase.IDLE, UpdatePhase.CHECKING -> CheckingView()

                        UpdatePhase.UPDATE_AVAILABLE -> UpdateAvailableView(
                            release = uiState.availableRelease,
                            currentVersionName = uiState.currentVersionName,
                            onOpenReleasePage = {
                                val url = uiState.availableRelease?.downloadUrl
                                if (!url.isNullOrBlank()) {
                                    view.context.openUrl(url)
                                    confirmHaptic(view)
                                }
                            }
                        )

                        UpdatePhase.UP_TO_DATE -> UpToDateView(
                            onCheckAgain = { viewModel.checkForUpdates() }
                        )

                        UpdatePhase.CANNOT_CHECK -> ErrorState(
                            title = stringResource(R.string.error_updates_title),
                            // The ViewModel owns the reason; the fallback only covers a phase change
                            // that arrived without a message, which should not happen.
                            message = uiState.errorMessage
                                ?: stringResource(R.string.error_updates_title),
                            onRetry = { viewModel.checkForUpdates() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppInfoCard(
    version: String,
    build: String,
    lastUpdate: String,
    appSize: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ChampagneGold, GoldDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = ObsidianBlack,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Crank Music",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                    Text(
                        text = "Version $version • $build",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoPill(label = "Last Update", value = lastUpdate)
                InfoPill(label = "App Size", value = appSize)
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
    }
}

@Composable
private fun CheckingView() {
    val infiniteTransition = rememberInfiniteTransition(label = "check_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer { rotationZ = rotation },
            contentAlignment = Alignment.Center
        ) {
            val hoistedChampagneGold = ChampagneGold

            Canvas(modifier = Modifier.size(80.dp)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(hoistedChampagneGold, Color.Transparent, hoistedChampagneGold)
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = ChampagneGold,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Checking for updates...",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Text(
            text = "Looking for the latest version",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

/**
 * A release that is genuinely newer than the installed build.
 *
 * Everything shown here comes from the published manifest: the version, the size and the changelog.
 * There is no download button unless the manifest actually carries a URL, and even then it only opens
 * that page in a browser — the app cannot install an APK it has no signing relationship with, so
 * pretending to download and install it (as the previous screen did) would be a lie.
 */
@Composable
private fun UpdateAvailableView(
    release: AvailableRelease?,
    currentVersionName: String,
    onOpenReleasePage: () -> Unit
) {
    // Reaching this branch without a release means the state machine is inconsistent; render nothing
    // rather than inventing a version to display.
    if (release == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(ChampagneGold.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = ChampagneGold,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Update available",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Text(
            text = "$currentVersionName → ${release.versionName}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        if (release.sizeBytes != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatBytes(release.sizeBytes),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
        }

        if (release.changelog.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "What's new",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = ChampagneGold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    release.changelog.forEach { entry ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = ChampagneGold,
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (release.downloadUrl.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onOpenReleasePage) {
                Text(
                    text = "Open download page",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = ChampagneGold
                )
            }
        }
    }
}

/**
 * The published release is not newer than this build — including the case where it is older, which is
 * deliberately not treated as an update.
 */
@Composable
private fun UpToDateView(onCheckAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = ChampagneGold,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "You're up to date",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Text(
            text = "This is the latest published version",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onCheckAgain) {
            Text(
                text = "Check again",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = ChampagneGold
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** Opens a published release page in whatever browser the user has; does nothing if nothing handles it. */
private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

