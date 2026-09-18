package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.data.local.HistoryEntity
import com.crank.music.data.local.SongDao
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class RecentlyPlayedItem(
    val song: Song,
    val lastPlayedText: String
)

data class RecommendedItem(
    val song: Song,
    val reason: String,
    val isDismissed: Boolean = false
)

data class TrendingItem(
    val song: Song,
    val rank: Int,
    val trend: String
)

data class HomeQuickAction(
    val id: String,
    val label: String,
    val icon: String
)

data class MadeForYouPlaylist(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkColor: Long,
    val lastUpdated: String
)

data class HomeUiState(
    val greeting: String = "",
    val isLoading: Boolean = false,
    val recentlyPlayed: List<RecentlyPlayedItem> = emptyList(),
    val recommended: List<RecommendedItem> = emptyList(),
    val trending: List<TrendingItem> = emptyList(),
    val quickActions: List<HomeQuickAction> = emptyList(),
    val madeForYou: List<MadeForYouPlaylist> = emptyList(),
    val profileImageUrl: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val songDao: SongDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val greeting = getGreeting()

                // Load recently played from actual history
                val recentHistory = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val entities = songDao.getHistoryList(10)
                        entities.map { entity ->
                            RecentlyPlayedItem(
                                song = Song(
                                    id = entity.songId,
                                    title = entity.title,
                                    artistName = entity.artistName,
                                    albumId = entity.albumId,
                                    durationMs = entity.durationMs,
                                    artworkUrl = entity.artworkUrl,
                                    isLocal = false,
                                    streamUrl = entity.streamUrl
                                ),
                                lastPlayedText = formatTimeAgo(entity.playedAt)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CRANK_HOME", "Failed to load history: ${e.message}")
                    emptyList()
                }

                // Load trending from search
                val trendingSongs = try {
                    musicRepository.search("top hits").take(10)
                } catch (e: Exception) {
                    Log.e("CRANK_HOME", "Failed to load trending: ${e.message}")
                    emptyList()
                }

                // Load recommendations from different genres
                val recommendedSongs = try {
                    val queries = listOf("pop hits", "rock classics", "chill vibes", "new releases")
                    val allRecs = mutableListOf<Song>()
                    for (query in queries) {
                        val results = musicRepository.search(query).take(2)
                        allRecs.addAll(results)
                        if (allRecs.size >= 8) break
                    }
                    allRecs.take(8)
                } catch (e: Exception) {
                    Log.e("CRANK_HOME", "Failed to load recommendations: ${e.message}")
                    emptyList()
                }

                // Made for you playlists
                val madeForYou = listOf(
                    MadeForYouPlaylist("dw", "Discover Weekly", "Fresh picks for you", 0xFF6B3FA0, "Updated weekly"),
                    MadeForYouPlaylist("rr", "Release Radar", "New releases you'll love", 0xFF1DB954, "Updated daily"),
                    MadeForYouPlaylist("dm1", "Daily Mix 1", "Pop & Charts", 0xFFE8115B, "Updated daily"),
                    MadeForYouPlaylist("tc", "Time Capsule", "Your nostalgic favorites", 0xFFE13300, "Updated weekly"),
                    MadeForYouPlaylist("or", "On Repeat", "Songs you can't stop playing", 0xFF1E3264, "Updated daily"),
                    MadeForYouPlaylist("rrw", "Repeat Rewind", "Your past favorites", 0xFF8D67AB, "Updated weekly")
                )

                _uiState.value = HomeUiState(
                    greeting = greeting,
                    recentlyPlayed = recentHistory,
                    recommended = recommendedSongs.mapIndexed { index, song ->
                        RecommendedItem(
                            song = song,
                            reason = when (index % 4) {
                                0 -> "Because you listened to ${song.artistName}"
                                1 -> "Based on your taste"
                                2 -> "Popular in your area"
                                3 -> "Similar to your library"
                                else -> "Trending now"
                            }
                        )
                    },
                    trending = trendingSongs.mapIndexed { index, song ->
                        TrendingItem(
                            song = song,
                            rank = index + 1,
                            trend = if (index % 3 == 0) "up" else if (index % 5 == 0) "down" else "same"
                        )
                    },
                    quickActions = listOf(
                        HomeQuickAction("1", "Daily Mix 1", "mix"),
                        HomeQuickAction("2", "Daily Mix 2", "mix"),
                        HomeQuickAction("3", "Daily Mix 3", "mix"),
                        HomeQuickAction("4", "On Repeat", "repeat"),
                        HomeQuickAction("5", "Time Capsule", "time"),
                        HomeQuickAction("6", "Discovery", "discover")
                    ),
                    madeForYou = madeForYou,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("CRANK_HOME", "Failed to load home data: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun dismissRecommendation(index: Int) {
        val updated = _uiState.value.recommended.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(isDismissed = true)
            _uiState.value = _uiState.value.copy(recommended = updated.filter { !it.isDismissed })
        }
    }

    fun refreshRecommendations() {
        viewModelScope.launch {
            try {
                val queries = listOf("trending now", "new music", "popular songs", "best of 2024")
                val newRecs = mutableListOf<Song>()
                for (query in queries) {
                    val results = musicRepository.search(query).take(2)
                    newRecs.addAll(results)
                    if (newRecs.size >= 4) break
                }
                val current = _uiState.value.recommended
                val newItems = newRecs.take(4).mapIndexed { index, song ->
                    RecommendedItem(
                        song = song,
                        reason = when (index % 3) {
                            0 -> "New for you"
                            1 -> "Based on your recent listening"
                            else -> "You might like this"
                        }
                    )
                }
                _uiState.value = _uiState.value.copy(recommended = current + newItems)
            } catch (e: Exception) {
                Log.e("CRANK_HOME", "Failed to refresh: ${e.message}")
            }
        }
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = diff / (1000 * 60 * 60)
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "${days / 7}w ago"
        }
    }

    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }
}
