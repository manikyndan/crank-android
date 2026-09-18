package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isLoaded: Boolean = false
)

@HiltViewModel
class StatsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MusicDnaUiState())
    val uiState: StateFlow<MusicDnaUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        _uiState.value = MusicDnaUiState(
            radarDimensions = listOf(
                RadarDimension("Energy", 0.82f, "⚡", "High-energy tracks dominate your playlist"),
                RadarDimension("Danceability", 0.68f, "💃", "You love moving to the beat"),
                RadarDimension("Acousticness", 0.45f, "🎸", "A balanced mix of organic sounds"),
                RadarDimension("Valence", 0.71f, "😊", "Your music leans positive and uplifting"),
                RadarDimension("Popularity", 0.76f, "🌟", "You follow trends but keep it personal")
            ),
            heatmapData = generateHeatmapData(),
            statCards = listOf(
                StatCard("Total Listening", "1,247h", 1247f, "⏱️", 12.5f),
                StatCard("Songs Played", "8,432", 8432f, "🎵", 8.3f),
                StatCard("Favorite Artist", "Radiohead", 0f, "🎤", 0f),
                StatCard("Top Genre", "Indie Rock", 0f, "🎸", 0f),
                StatCard("Avg. Session", "42 min", 42f, "⏰", -3.2f),
                StatCard("Unique Artists", "312", 312f, "👥", 15.7f)
            ),
            badges = listOf(
                Badge("first_play", "First Listen", "🎵", "Played your very first song", true, "Jan 15, 2025"),
                Badge("night_owl", "Night Owl", "🦉", "50+ hours of midnight listening", true, "Mar 8, 2025"),
                Badge("marathon", "Marathon Listener", "🏃", "8+ hours in a single day", true, "Feb 22, 2025"),
                Badge("explorer", "Music Explorer", "🗺️", "Played 200+ unique artists", true, "Apr 1, 2025"),
                Badge("century", "Century Club", "💯", "Played 100 different songs", true, "Jan 28, 2025"),
                Badge("early_bird", "Early Bird", "🌅", "30+ hours before 7 AM", false, "", 0.65f),
                Badge("genre_master", "Genre Master", "🎭", "Listened to 15+ genres", true, "May 12, 2025"),
                Badge("streak_7", "7-Day Streak", "🔥", "7 consecutive days of listening", true, "Jun 3, 2025"),
                Badge("streak_30", "30-Day Streak", "🏆", "30 consecutive days", false, "", 0.87f),
                Badge("vinyl", "Vinyl Soul", "🎶", "100+ hours of acoustic music", false, "", 0.42f),
                Badge("party_animal", "Party Animal", "🎉", "50+ hours of dance music", true, "Jul 14, 2025"),
                Badge("zen", "Zen Master", "🧘", "100+ hours of ambient/chill", false, "", 0.28f)
            ),
            timelineData = generateWeeklyTimeline(),
            isLoaded = true
        )
    }

    fun selectPeriod(period: TimePeriod) {
        val timelineData = when (period) {
            TimePeriod.DAILY -> generateDailyTimeline()
            TimePeriod.WEEKLY -> generateWeeklyTimeline()
            TimePeriod.MONTHLY -> generateMonthlyTimeline()
            TimePeriod.YEARLY -> generateYearlyTimeline()
        }
        _uiState.value = _uiState.value.copy(
            selectedPeriod = period,
            timelineData = timelineData
        )
    }

    fun selectDimension(dimension: RadarDimension?) {
        _uiState.value = _uiState.value.copy(selectedDimension = dimension)
    }

    fun selectBadge(badge: Badge?) {
        _uiState.value = _uiState.value.copy(selectedBadge = badge)
    }

    private fun generateHeatmapData(): List<HeatmapCell> {
        val data = mutableListOf<HeatmapCell>()
        for (day in 0..6) {
            for (hour in 0..23) {
                val baseMinutes = when (hour) {
                    in 0..5 -> (5..25).random()
                    in 6..8 -> (15..45).random()
                    in 9..11 -> (20..60).random()
                    in 12..13 -> (30..70).random()
                    in 14..17 -> (25..55).random()
                    in 18..20 -> (40..90).random()
                    in 21..23 -> (20..65).random()
                    else -> 10
                }
                val weekendBonus = if (day >= 5) 1.3f else 1f
                data.add(HeatmapCell(day, hour, (baseMinutes * weekendBonus).toInt().coerceAtMost(120)))
            }
        }
        return data
    }

    private fun generateDailyTimeline(): List<TimelinePoint> {
        return listOf(
            TimelinePoint("6AM", 12f, "", "Morning Coffee"),
            TimelinePoint("9AM", 45f, "", "Commute Mix"),
            TimelinePoint("12PM", 30f, "", "Lunch Beats"),
            TimelinePoint("3PM", 55f, "", "Afternoon Focus"),
            TimelinePoint("6PM", 70f, "", "Evening Vibes"),
            TimelinePoint("9PM", 85f, "", "Night Session"),
            TimelinePoint("12AM", 25f, "", "Late Night")
        )
    }

    private fun generateWeeklyTimeline(): List<TimelinePoint> {
        return listOf(
            TimelinePoint("Mon", 120f, "", "Monday Motivation"),
            TimelinePoint("Tue", 95f, "", "Tunes Tuesday"),
            TimelinePoint("Wed", 140f, "", "Midweek Mix"),
            TimelinePoint("Thu", 110f, "", "Throwback Thursday"),
            TimelinePoint("Fri", 180f, "", "Friday Night"),
            TimelinePoint("Sat", 220f, "", "Saturday Session"),
            TimelinePoint("Sun", 160f, "", "Sunday Chill")
        )
    }

    private fun generateMonthlyTimeline(): List<TimelinePoint> {
        return listOf(
            TimelinePoint("Jan", 2800f, "", "New Year Energy"),
            TimelinePoint("Feb", 2400f, "", "Winter Beats"),
            TimelinePoint("Mar", 3100f, "", "Spring Awakening"),
            TimelinePoint("Apr", 2900f, "", "April Grooves"),
            TimelinePoint("May", 3400f, "", "May Madness"),
            TimelinePoint("Jun", 3200f, "", "Summer Prep"),
            TimelinePoint("Jul", 3800f, "", "Summer Hits"),
            TimelinePoint("Aug", 3600f, "", "Late Summer"),
            TimelinePoint("Sep", 3000f, "", "Back to Rhythm"),
            TimelinePoint("Oct", 2700f, "", "Autumn Sounds"),
            TimelinePoint("Nov", 2500f, "", "Cozy Nights"),
            TimelinePoint("Dec", 3200f, "", "Holiday Mix")
        )
    }

    private fun generateYearlyTimeline(): List<TimelinePoint> {
        return listOf(
            TimelinePoint("2021", 8000f, "", "The Beginning"),
            TimelinePoint("2022", 14500f, "", "Discovery Year"),
            TimelinePoint("2023", 22000f, "", "Peak Listening"),
            TimelinePoint("2024", 28000f, "", "Your Best Year"),
            TimelinePoint("2025", 18500f, "", "Current Year")
        )
    }
}
