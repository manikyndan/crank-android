package com.crank.music.ui.screens

import com.crank.music.util.confirmHaptic
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.service.UpdateNotificationHelper
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldDark
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.UpdateCheckerViewModel
import com.crank.music.ui.viewmodel.UpdatePhase
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCheckerScreen(
    viewModel: UpdateCheckerViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val notificationHelper = remember { UpdateNotificationHelper::class.java }

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
                    version = uiState.currentVersion,
                    build = uiState.currentBuild,
                    lastUpdate = uiState.lastUpdateDate,
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
                        UpdatePhase.CHECKING -> CheckingView()
                        UpdatePhase.UPDATE_AVAILABLE -> UpdateAvailableView(
                            updateInfo = uiState.updateInfo,
                            onDownload = {
                                viewModel.startDownload()
                                confirmHaptic(view)
                            },
                            onRemindLater = {
                                viewModel.remindLater()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        )
                        UpdatePhase.DOWNLOADING -> DownloadingView(
                            progress = uiState.downloadProgress,
                            speed = uiState.downloadSpeed,
                            timeRemaining = uiState.timeRemaining,
                            isPaused = uiState.isPaused,
                            onPause = {
                                viewModel.pauseDownload()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            },
                            onResume = {
                                viewModel.resumeDownload()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        )
                        UpdatePhase.DOWNLOAD_COMPLETE -> DownloadCompleteView(
                            onInstall = {
                                viewModel.installUpdate()
                                confirmHaptic(view)
                            },
                            onInstallLater = {
                                viewModel.remindLater()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        )
                        UpdatePhase.INSTALLING -> InstallingView()
                        UpdatePhase.INSTALLED -> InstalledView(
                            onRelaunch = {
                                confirmHaptic(view)
                            }
                        )
                        UpdatePhase.NO_UPDATE -> NoUpdateView()
                    }
                }
            }
        }
    }

    if (uiState.phase == UpdatePhase.CHECKING) {
        LaunchedEffect(Unit) {
            delay(2000)
            viewModel.checkForUpdates()
        }
    }

    if (uiState.phase == UpdatePhase.DOWNLOADING && !uiState.isPaused) {
        LaunchedEffect(uiState.downloadProgress) {
            if (uiState.downloadProgress < 1f) {
                delay(100)
                viewModel.updateDownloadProgress((uiState.downloadProgress + 0.02f).coerceAtMost(1f))
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

@Composable
private fun UpdateAvailableView(
    updateInfo: com.crank.music.ui.viewmodel.UpdateInfo?,
    onDownload: () -> Unit,
    onRemindLater: () -> Unit
) {
    if (updateInfo == null) return

    val itemAnimations = remember { List(updateInfo.changelog.size) { Animatable(0f) } }

    LaunchedEffect(Unit) {
        itemAnimations.forEachIndexed { index, animatable ->
            delay(150L * index)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ChampagneGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Update Available!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ChampagneGold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = ChampagneGold.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Version ${updateInfo.versionName}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = WarmWhite
                        )
                        Text(
                            text = updateInfo.buildNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                    Surface(
                        color = ChampagneGold,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = updateInfo.updateSize,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = ObsidianBlack,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "What's New",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
            Spacer(modifier = Modifier.height(8.dp))

            updateInfo.changelog.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = itemAnimations[index].value
                            translationY = (1f - itemAnimations[index].value) * 20f
                        }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ChampagneGold)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDownload() },
                color = ChampagneGold,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = ObsidianBlack,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Update",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ObsidianBlack
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Remind Me Later",
                style = MaterialTheme.typography.labelLarge,
                color = TextTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRemindLater() }
                    .padding(12.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DownloadingView(
    progress: Float,
    speed: String,
    timeRemaining: String,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(200),
        label = "download_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            val hoistedChampagneGold = ChampagneGold
            val hoistedCharcoalElevated = CharcoalElevated
            val hoistedGoldDark = GoldDark

            Canvas(modifier = Modifier.size(180.dp)) {
                drawArc(
                    color = hoistedCharcoalElevated,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(hoistedGoldDark, hoistedChampagneGold, hoistedGoldDark)
                    ),
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite,
                    modifier = Modifier.graphicsLayer {
                        scaleX = if (!isPaused) pulseScale else 1f
                        scaleY = if (!isPaused) pulseScale else 1f
                    }
                )
                Text(
                    text = "downloading",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Speed",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Text(
                    text = speed,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isPaused) Color(0xFFFF9800) else ChampagneGold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Text(
                    text = timeRemaining,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { if (isPaused) onResume() else onPause() },
                color = if (isPaused) ChampagneGold else CharcoalElevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = if (isPaused) ObsidianBlack else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPaused) "Resume" else "Pause",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isPaused) ObsidianBlack else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCompleteView(
    onInstall: () -> Unit,
    onInstallLater: () -> Unit
) {
    val scale = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        glowAlpha.animateTo(
            targetValue = 0.6f,
            animationSpec = tween(800)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = glowAlpha.value
                    }
                    .shadow(24.dp, CircleShape, spotColor = ChampagneGold)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ChampagneGold.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(ChampagneGold, GoldDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ObsidianBlack,
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Download Complete!",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Text(
            text = "Update is ready to install",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onInstall() },
            color = ChampagneGold,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = ObsidianBlack,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Install Now",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ObsidianBlack
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Install Later",
            style = MaterialTheme.typography.labelLarge,
            color = TextTertiary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onInstallLater() }
                .padding(12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InstallingView() {
    val infiniteTransition = rememberInfiniteTransition(label = "install_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
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
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            val hoistedChampagneGold = ChampagneGold
            val hoistedGoldDark = GoldDark

            Canvas(modifier = Modifier.size(100.dp)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(hoistedChampagneGold, Color.Transparent, hoistedGoldDark, hoistedChampagneGold)
                    ),
                    startAngle = rotation,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = ChampagneGold,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Installing Update...",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Text(
            text = "Please don't close the app",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun InstalledView(onRelaunch: () -> Unit) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .shadow(16.dp, CircleShape, spotColor = Color(0xFF4CAF50))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4CAF50).copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Update Installed!",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Text(
            text = "You're now running the latest version",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onRelaunch() },
            color = ChampagneGold,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = ObsidianBlack,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Relaunch App",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ObsidianBlack
                )
            }
        }
    }
}

@Composable
private fun NoUpdateView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "You're up to date!",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Text(
            text = "Crank Music v1.0.0 is the latest version",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
