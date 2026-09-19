package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldDark
import com.crank.music.ui.theme.GoldMuted
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.Badge
import com.crank.music.ui.viewmodel.HeatmapCell
import com.crank.music.ui.viewmodel.MusicDnaUiState
import com.crank.music.ui.viewmodel.RadarDimension
import com.crank.music.ui.viewmodel.StatsViewModel
import com.crank.music.ui.viewmodel.TimePeriod
import com.crank.music.ui.viewmodel.TimelinePoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDNAScreen(
    statsViewModel: StatsViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by statsViewModel.uiState.collectAsState()
    val view = LocalView.current
    var showBadgeDetail by remember { mutableStateOf<Badge?>(null) }

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your Music DNA",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )
                Text(
                    text = "Your listening identity",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Stats",
                    tint = ChampagneGold,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Nothing is shown until the user has actually played something. Every metric on
            // this screen is derived from playback history, so with an empty history the
            // honest answer is "come back after you listen" — not a grid of zeroes dressed up
            // as a profile, and certainly not the fixed numbers this screen used to display.
            if (uiState.isLoaded && !uiState.hasListeningHistory) {
                item { EmptyDnaState() }
                return@LazyColumn
            }

            if (uiState.radarDimensions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    RadarChartSection(
                        dimensions = uiState.radarDimensions,
                        onDimensionClick = { dimension ->
                            statsViewModel.selectDimension(dimension)
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                StatsCardsSection(stats = uiState.statCards)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                HeatmapSection(
                    data = uiState.heatmapData,
                    onCellClick = { cell ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                TimelineSection(
                    data = uiState.timelineData,
                    selectedPeriod = uiState.selectedPeriod,
                    onPeriodSelect = { statsViewModel.selectPeriod(it) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                BadgesSection(
                    badges = uiState.badges,
                    onBadgeClick = { badge ->
                        showBadgeDetail = badge
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }
        }
    }

    showBadgeDetail?.let { badge ->
        BadgeDetailDialog(
            badge = badge,
            onDismiss = { showBadgeDetail = null }
        )
    }
}

@Composable
private fun EmptyDnaState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "\uD83E\uDDEC", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Listening History Yet",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Play a few songs and your stats will build up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RadarChartSection(
    dimensions: List<RadarDimension>,
    onDimensionClick: (RadarDimension) -> Unit
) {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sonic Profile",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(280.dp)
                .shadow(16.dp, CircleShape)
                .clip(CircleShape)
                .background(CharcoalSurface)
                .drawBehind {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val maxRadius = size.minDimension / 2 - 20.dp.toPx()

                    for (ring in 1..4) {
                        val radius = maxRadius * ring / 4f
                        drawCircle(
                            color = Color(0xFF2A2A2A),
                            radius = radius,
                            style = Stroke(1.dp.toPx())
                        )
                    }

                    for (i in dimensions.indices) {
                        val angle = (i * 360f / dimensions.size - 90f) * (Math.PI / 180f).toFloat()
                        val endX = centerX + cos(angle.toDouble()).toFloat() * maxRadius
                        val endY = centerY + sin(angle.toDouble()).toFloat() * maxRadius
                        drawLine(
                            color = Color(0xFF2A2A2A),
                            start = Offset(centerX, centerY),
                            end = Offset(endX, endY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val path = Path()
                    dimensions.forEachIndexed { index, dim ->
                        val angle = (index * 360f / dimensions.size - 90f) * (Math.PI / 180f).toFloat()
                        val radius = maxRadius * dim.value * animationProgress.value
                        val x = centerX + cos(angle.toDouble()).toFloat() * radius
                        val y = centerY + sin(angle.toDouble()).toFloat() * radius
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()

                    drawPath(
                        path = path,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ChampagneGold.copy(alpha = 0.4f),
                                ChampagneGold.copy(alpha = 0.1f)
                            )
                        )
                    )
                    drawPath(
                        path = path,
                        color = ChampagneGold,
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    dimensions.forEachIndexed { index, dim ->
                        val angle = (index * 360f / dimensions.size - 90f) * (Math.PI / 180f).toFloat()
                        val radius = maxRadius * dim.value * animationProgress.value
                        val x = centerX + cos(angle.toDouble()).toFloat() * radius
                        val y = centerY + sin(angle.toDouble()).toFloat() * radius
                        drawCircle(
                            color = ChampagneGold,
                            radius = 5.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.4f),
                            radius = 3.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) { }

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(dimensions) { dim ->
                val animatedValue by animateFloatAsState(
                    targetValue = dim.value * animationProgress.value,
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    label = "radar_value"
                )
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDimensionClick(dim) },
                    color = CharcoalSurface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = dim.icon, fontSize = 18.sp)
                        Text(
                            text = "${(animatedValue * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = ChampagneGold
                        )
                        Text(
                            text = dim.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCardsSection(stats: List<com.crank.music.ui.viewmodel.StatCard>) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Your Numbers",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(stats) { stat ->
                AnimatedStatCard(stat = stat)
            }
        }
    }
}

@Composable
private fun AnimatedStatCard(stat: com.crank.music.ui.viewmodel.StatCard) {
    val animatedValue = remember { Animatable(0f) }

    LaunchedEffect(stat.numericValue) {
        if (stat.numericValue > 0) {
            animatedValue.animateTo(
                targetValue = stat.numericValue,
                animationSpec = tween(1500, easing = FastOutSlowInEasing)
            )
        }
    }

    val displayValue = when {
        stat.label == "Total Listening" -> "${animatedValue.value.toInt()}h"
        stat.label == "Songs Played" -> "${animatedValue.value.toInt().let { if (it > 1000) "${it / 1000}.${(it % 1000) / 100}k" else "$it" }}"
        stat.label == "Avg. Session" -> "${animatedValue.value.toInt()} min"
        stat.label == "Unique Artists" -> "${animatedValue.value.toInt()}"
        else -> stat.value
    }

    Surface(
        modifier = Modifier.width(150.dp),
        color = CharcoalSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stat.icon, fontSize = 20.sp)
                if (stat.trend != 0f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (stat.trend > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = if (stat.trend > 0) Color(0xFF4CAF50) else Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${ kotlin.math.abs(stat.trend).toInt() }%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (stat.trend > 0) Color(0xFF4CAF50) else Color(0xFFFF5252)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = displayValue,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = ChampagneGold
            )
            Text(
                text = stat.label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun HeatmapSection(
    data: List<HeatmapCell>,
    onCellClick: (HeatmapCell) -> Unit
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val hours = listOf("12a", "3a", "6a", "9a", "12p", "3p", "6p", "9p")

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Listening Heatmap",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Text(
            text = "When you listen most",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(32.dp))
                    hours.forEach { hour ->
                        Text(
                            text = hour,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = TextTertiary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                days.forEachIndexed { dayIndex, day ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextSecondary,
                            modifier = Modifier.width(32.dp)
                        )

                        for (hour in 0..23) {
                            val cell = data.find { it.day == dayIndex && it.hour == hour }
                            val minutes = cell?.minutes ?: 0
                            val maxMinutes = 120
                            val intensity = (minutes.toFloat() / maxMinutes).coerceIn(0f, 1f)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp)
                                    .padding(0.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF1A1A1A),
                                                if (intensity > 0.1f)
                                                    ChampagneGold.copy(alpha = intensity * 0.9f)
                                                else Color(0xFF1A1A1A)
                                            )
                                        )
                                    )
                                    .clickable {
                                        cell?.let { onCellClick(it) }
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Less",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).forEach { alpha ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ChampagneGold.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "More",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineSection(
    data: List<TimelinePoint>,
    selectedPeriod: TimePeriod,
    onPeriodSelect: (TimePeriod) -> Unit
) {
    val maxValue = data.maxOfOrNull { it.value } ?: 1f
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        )
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Listening Timeline",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(TimePeriod.entries.toList()) { period ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPeriodSelect(period) },
                    color = if (selectedPeriod == period) ChampagneGold else CharcoalSurface,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = period.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedPeriod == period) ObsidianBlack else WarmWhite,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                data.forEach { point ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.width(40.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1A1A1A))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((point.value / maxValue) * animationProgress.value)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                ChampagneGold.copy(alpha = 0.6f),
                                                ChampagneGold
                                            )
                                        )
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (point.value >= 1000) "${(point.value / 1000).toInt()}k" else "${point.value.toInt()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ChampagneGold,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgesSection(
    badges: List<Badge>,
    onBadgeClick: (Badge) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Milestones",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        val earned = badges.count { it.isEarned }
        Text(
            text = "$earned of ${badges.size} earned",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        Spacer(modifier = Modifier.height(12.dp))

        badges.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { badge ->
                    BadgeItem(
                        badge = badge,
                        onClick = { onBadgeClick(badge) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun BadgeItem(
    badge: Badge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge_shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Surface(
        modifier = modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = if (badge.isEarned) CharcoalElevated else Color(0xFF151515),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (badge.isEarned) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val shimmerX = shimmerOffset * size.width
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        ChampagneGold.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    start = Offset(shimmerX - 50.dp.toPx(), 0f),
                                    end = Offset(shimmerX + 50.dp.toPx(), size.height)
                                )
                            )
                        }
                )
            }

            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = badge.icon,
                    fontSize = 28.sp,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (badge.isEarned) 1f else 0.3f
                        scaleX = if (badge.isEarned) 1f else 0.8f
                        scaleY = if (badge.isEarned) 1f else 0.8f
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = badge.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 9.sp
                    ),
                    color = if (badge.isEarned) WarmWhite else TextTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!badge.isEarned && badge.progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF2A2A2A))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(badge.progress)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(2.dp))
                                .background(GoldMuted)
                        )
                    }
                }
                if (badge.isEarned && badge.earnedDate.isNotBlank()) {
                    Text(
                        text = badge.earnedDate,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeDetailDialog(
    badge: Badge,
    onDismiss: () -> Unit
) {
    val scale = remember { Animatable(0.8f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = badge.icon, fontSize = 32.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = badge.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                    Text(
                        text = if (badge.isEarned) "Earned" else "Locked",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (badge.isEarned) ChampagneGold else TextTertiary
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                if (badge.isEarned && badge.earnedDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Earned on ${badge.earnedDate}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary
                    )
                }
                if (!badge.isEarned && badge.progress > 0f) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val percentage = (badge.progress * 100).toInt()
                    Text(
                        text = "Progress: $percentage%",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = ChampagneGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2A2A2A))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(badge.progress)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(GoldMuted, ChampagneGold)
                                    )
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = ChampagneGold)
            }
        }
    )
}
