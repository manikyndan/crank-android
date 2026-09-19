package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.data.local.SongDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

data class RadarDimension(
    val name: String,
    val value: Float,
    val icon: String,
    val description: String
)

data class HeatmapCell(
    val day: Int,
    val hour: Int,
    val minutes: Int
)

data class StatCard(
    val label: String,
    val value: String,
    val numericValue: Float,
    val icon: String,
    val trend: Float = 0f
)

data class Badge(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val isEarned: Boolean,
    val earnedDate: String = "",
    val progress: Float = 0f
)

data class TimelinePoint(
    val label: String,
    val value: Float,
    val albumArtUrl: String = "",
    val songTitle: String = ""
)

enum class TimePeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

data class MusicDnaUiState(
    val radarDimensions: List<RadarDimension> = emptyList(),
    val heatmapData: List<HeatmapCell> = emptyList(),
    val statCards: List<StatCard> = emptyList(),
    val badges: List<Badge> = emptyList(),
    val timelineData: List<TimelinePoint> = emptyList(),
    val selectedPeriod: TimePeriod = TimePeriod.WEEKLY,
    val selectedDimension: RadarDimension? = null,
    val selectedBadge: Badge? = null,
    val isLoaded: Boolean = false,
    /** True once the user has played anything. An empty history means nothing to show. */
    val hasListeningHistory: Boolean = false,
)

/**
 * Music DNA / listening statistics.
 *
 * This screen previously showed a fixed set of numbers — "1,247h listened", "Radiohead" as
 * favourite artist, a 7x24 heatmap filled with `Random.nextInt`, twelve badges with dates
 * like "Jan 15, 2025" — none of which had any connection to what the user had played. A
 * brand-new install with zero plays displayed a full, confident, entirely fictional profile.
 *
 * Everything is now computed from `playback_history`, the one table that records real
 * listening. Where a metric genuinely cannot be derived (audio-feature dimensions like
 * "Danceability", which YouTube search does not expose) the metric is omitted rather than
 * invented, so the screen shows less but never lies.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val songDao: SongDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicDnaUiState())
    val uiState: StateFlow<MusicDnaUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        viewModelScope.launch {
            try {
                val history = withContext(Dispatchers.IO) { songDao.getHistoryList(HISTORY_LIMIT) }
                val totalMinutes = withContext(Dispatchers.IO) { songDao.getTotalListeningMinutes() }
                val topArtist = withContext(Dispatchers.IO) { songDao.getTopArtist() }

                val stats = buildStatCards(history, totalMinutes, topArtist)
                lastHistory = history

                _uiState.value = MusicDnaUiState(
                    // Radar is left empty: there is no audio-feature source in the app, and a
                    // radar chart of invented values is worse than no chart.
                    radarDimensions = emptyList(),
                    heatmapData = buildHeatmap(history),
                    statCards = stats,
                    badges = buildBadges(history, totalMinutes),
                    timelineData = buildTimeline(history),
                    isLoaded = true,
                    hasListeningHistory = history.isNotEmpty(),
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _uiState.value = _uiState.value.copy(isLoaded = true)
            }
        }
    }

    fun selectPeriod(period: TimePeriod) {
        _uiState.value = _uiState.value.copy(
            selectedPeriod = period,
            timelineData = buildTimelineFor(period),
        )
    }

    fun selectDimension(dimension: RadarDimension?) {
        _uiState.value = _uiState.value.copy(selectedDimension = dimension)
    }

    fun selectBadge(badge: Badge?) {
        _uiState.value = _uiState.value.copy(selectedBadge = badge)
    }

    // ── Derived metrics ──────────────────────────────────────────────────────────

    /**
     * Aggregate cards.
     *
     * The trend figure is deliberately 0f for every card. A percentage change needs a
     * previous period to compare against, and history rows carry a single `playedAt`
     * timestamp with no prior window retained, so any delta would be fabricated.
     */
    private fun buildStatCards(
        history: List<com.crank.music.data.local.HistoryEntity>,
        totalMinutes: Double,
        topArtist: String?,
    ): List<StatCard> {
        val totalHours = totalMinutes / 60.0
        val cards = mutableListOf<StatCard>()

        cards += StatCard(
            label = "Total Listening",
            value = formatHours(totalHours),
            numericValue = totalHours.toFloat(),
            icon = "\u23F1\uFE0F",
        )
        cards += StatCard(
            label = "Songs Played",
            value = history.size.toString(),
            numericValue = history.size.toFloat(),
            icon = "\uD83C\uDFB5",
        )

        topArtist?.takeIf { it.isNotBlank() }?.let {
            cards += StatCard("Favorite Artist", it, 0f, "\uD83C\uDFA4")
        }

        val uniqueArtists = history.map { it.artistName }.filter { it.isNotBlank() }.distinct().size
        if (uniqueArtists > 0) {
            cards += StatCard(
                label = "Unique Artists",
                value = uniqueArtists.toString(),
                numericValue = uniqueArtists.toFloat(),
                icon = "\uD83D\uDC65",
            )
        }

        if (history.isNotEmpty()) {
            val avgMinutes = totalMinutes / history.size
            cards += StatCard(
                label = "Avg. Per Song",
                value = String.format(Locale.US, "%.1f min", avgMinutes),
                numericValue = avgMinutes.toFloat(),
                icon = "\u23F0",
            )
        }

        return cards
    }

    /**
     * 7x24 heatmap of when the user listens.
     *
     * Real: both axes come from the `playedAt` timestamps in history. Cells with no plays are
     * zero, so an empty grid stays empty. The previous version filled every one of the 168
     * cells with a random number, which drew a convincing "you listen most at 8 PM" pattern
     * for a user who had never opened the app.
     */
    private fun buildHeatmap(
        history: List<com.crank.music.data.local.HistoryEntity>,
    ): List<HeatmapCell> {
        // days[day][hour] = accumulated minutes
        val minutes = Array(7) { IntArray(24) }
        val calendar = Calendar.getInstance()

        history.forEach { entry ->
            calendar.timeInMillis = entry.playedAt
            // Calendar.SUNDAY is 1; shift so Monday is 0 to match the UI's column order.
            val day = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            minutes[day][hour] += (entry.durationMs / 60_000L).toInt()
        }

        return buildList {
            for (day in 0..6) {
                for (hour in 0..23) {
                    add(HeatmapCell(day, hour, minutes[day][hour]))
                }
            }
        }
    }

    /**
     * Badges, evaluated against real counters.
     *
     * Earned badges carry no date: the history table keeps only the most recent play per song,
     * so the date a threshold was first crossed is not recoverable. Showing an empty date is
     * honest; the previous list showed exact-looking dates that were hardcoded.
     */
    private fun buildBadges(
        history: List<com.crank.music.data.local.HistoryEntity>,
        totalMinutes: Double,
    ): List<Badge> {
        val songCount = history.size
        val uniqueArtists = history.map { it.artistName }.filter { it.isNotBlank() }.distinct().size
        val hours = totalMinutes / 60.0

        return listOf(
            Badge(
                id = "first_play",
                name = "First Listen",
                icon = "\uD83C\uDFB5",
                description = "Play your first song",
                isEarned = songCount >= 1,
                progress = if (songCount >= 1) 0f else 0f,
            ),
            Badge(
                id = "century",
                name = "Century Club",
                icon = "\uD83D\uDCAF",
                description = "Play 100 different songs",
                isEarned = songCount >= 100,
                progress = (songCount / 100f).coerceIn(0f, 1f),
            ),
            Badge(
                id = "explorer",
                name = "Music Explorer",
                icon = "\uD83D\uDDFA\uFE0F",
                description = "Play 50 different artists",
                isEarned = uniqueArtists >= 50,
                progress = (uniqueArtists / 50f).coerceIn(0f, 1f),
            ),
            Badge(
                id = "marathon",
                name = "Marathon Listener",
                icon = "\uD83C\uDFC3",
                description = "Listen for 8 hours in total",
                isEarned = hours >= 8.0,
                progress = (hours / 8.0).toFloat().coerceIn(0f, 1f),
            ),
            Badge(
                id = "devoted",
                name = "Devoted",
                icon = "\uD83D\uDD25",
                description = "Listen for 50 hours in total",
                isEarned = hours >= 50.0,
                progress = (hours / 50.0).toFloat().coerceIn(0f, 1f),
            ),
        )
    }

    private fun buildTimeline(history: List<com.crank.music.data.local.HistoryEntity>): List<TimelinePoint> =
        buildTimelineFor(_uiState.value.selectedPeriod, history)

    private fun buildTimelineFor(
        period: TimePeriod,
        history: List<com.crank.music.data.local.HistoryEntity> = lastHistory,
    ): List<TimelinePoint> {
        if (history.isEmpty()) return emptyList()

        val calendar = Calendar.getInstance()
        val buckets = LinkedHashMap<String, Float>()
        val order = mutableListOf<String>()

        fun bucketKey(entry: com.crank.music.data.local.HistoryEntity, label: String): String {
            if (!buckets.containsKey(label)) order += label
            return label
        }

        history.forEach { entry ->
            calendar.timeInMillis = entry.playedAt
            val label = when (period) {
                TimePeriod.DAILY -> String.format(
                    Locale.US, "%02d:00", calendar.get(Calendar.HOUR_OF_DAY)
                )
                TimePeriod.WEEKLY -> DAY_LABELS[(calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7]
                TimePeriod.MONTHLY -> String.format(
                    Locale.US, "%02d", calendar.get(Calendar.DAY_OF_MONTH)
                )
                TimePeriod.YEARLY -> calendar.get(Calendar.YEAR).toString()
            }
            val key = bucketKey(entry, label)
            buckets[key] = (buckets[key] ?: 0f) + entry.durationMs / 60_000f
        }

        return order.map { label -> TimelinePoint(label, buckets[label] ?: 0f) }
    }

    /** Latest history, cached so [selectPeriod] can rebuild without another database read. */
    private var lastHistory: List<com.crank.music.data.local.HistoryEntity> = emptyList()

    private fun formatHours(hours: Double): String = when {
        hours <= 0.0 -> "0h"
        hours >= 10.0 -> "${hours.toLong()}h"
        else -> String.format(Locale.US, "%.1fh", hours)
    }

    private companion object {
        const val HISTORY_LIMIT = 500
        val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
}
