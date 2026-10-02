package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.core.rethrowIfCancellation
import com.crank.music.data.local.HistoryEntity
import com.crank.music.data.local.SongDao
import com.crank.music.domain.model.Collection
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
    val icon: String,
    val rank: Int = 0,
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

/** Pairs a collection with the glyph its quick-action chip shows. */
private data class QuickActionChipSpec(val collection: Collection, val icon: String)

/**
 * The made-for-you row, in display order. Each entry is a real [Collection], so the id that opens
 * it and the label shown for it are guaranteed to agree.
 */
private val MADE_FOR_YOU = listOf(
    Collection.DISCOVER_WEEKLY,
    Collection.RELEASE_RADAR,
    Collection.DAILY_MIX_1,
    Collection.TIME_CAPSULE,
    Collection.ON_REPEAT,
    Collection.REPEAT_REWIND,
)

/**
 * Artwork tint per collection. This is presentation, so it legitimately lives near the UI. It is
 * keyed by [Collection] rather than by a bare string, so a new collection cannot silently render
 * with the wrong colour.
 */
private val MADE_FOR_YOU_COLORS = mapOf(
    Collection.DISCOVER_WEEKLY to 0xFF6B3FA0,
    Collection.RELEASE_RADAR to 0xFF1DB954,
    Collection.DAILY_MIX_1 to 0xFFE8115B,
    Collection.DAILY_MIX_2 to 0xFF2E77D0,
    Collection.DAILY_MIX_3 to 0xFFE13300,
    Collection.TIME_CAPSULE to 0xFFE13300,
    Collection.ON_REPEAT to 0xFF1E3264,
    Collection.REPEAT_REWIND to 0xFF8D67AB,
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
                    e.rethrowIfCancellation()
                    Log.e("CRANK_HOME", "Failed to load history: ${e.message}")
                    emptyList()
                }

                // Load trending from search
                val trendingSongs = try {
                    musicRepository.search("top hits").take(10)
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
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
                    e.rethrowIfCancellation()
                    Log.e("CRANK_HOME", "Failed to load recommendations: ${e.message}")
                    emptyList()
                }

                // Made for you playlists.
                //
                // These previously carried invented ids ("dw", "dm1", …) that the destination
                // could not resolve, so every one of them opened the same generic search results.
                // They are now addressed by Collection.slug, the single vocabulary shared with
                // PlaylistDetailViewModel. The labels come from the enum too, so a collection's
                // title cannot drift out of sync with the id that opens it.
                val madeForYou = MADE_FOR_YOU.map { collection ->
                    MadeForYouPlaylist(
                        id = collection.slug,
                        title = collection.title,
                        subtitle = collection.subtitle,
                        artworkColor = MADE_FOR_YOU_COLORS.getValue(collection),
                        lastUpdated = if (collection == Collection.RELEASE_RADAR) "Updated daily" else "Updated weekly",
                    )
                }

                _uiState.value = HomeUiState(
                    greeting = greeting,
                    recentlyPlayed = recentHistory,
                    recommended = recommendedSongs.mapIndexed { index, song ->
                        RecommendedItem(
                            song = song,
                            // "Because you listened to X" claims a real listening relationship we
                            // have not established for every row. Keep the phrasing honest: this
                            // is a search result for a taste query, not a personalisation claim.
                            reason = when (index % 4) {
                                0 -> "Similar to ${song.artistName}"
                                1 -> "Based on your taste"
                                2 -> "Popular right now"
                                3 -> "You might like this"
                                else -> "Trending now"
                            }
                        )
                    },
                    trending = trendingSongs.mapIndexed { index, song ->
                        TrendingItem(
                            song = song,
                            rank = index + 1,
                            // The direction arrow was decided by `index % 3` — pure decoration
                            // dressed up as chart movement. The backend does not tell us whether a
                            // track is climbing, so we no longer imply that it does.
                            trend = ""
                        )
                    },
                    // Quick action chips. These were literals with no destination at all — the
                    // click handler only played a haptic tick. They now carry a Collection slug so
                    // the same navigation contract applies to them as to every other card.
                    quickActions = listOf(
                        QuickActionChipSpec(Collection.DAILY_MIX_1, "mix"),
                        QuickActionChipSpec(Collection.DAILY_MIX_2, "mix"),
                        QuickActionChipSpec(Collection.DAILY_MIX_3, "mix"),
                        QuickActionChipSpec(Collection.ON_REPEAT, "repeat"),
                        QuickActionChipSpec(Collection.TIME_CAPSULE, "time"),
                        QuickActionChipSpec(Collection.DISCOVER_WEEKLY, "discover"),
                    ).mapIndexed { index, action ->
                        HomeQuickAction(
                            id = action.collection.slug,
                            label = action.collection.title,
                            icon = action.icon,
                            rank = index,
                        )
                    },
                    madeForYou = madeForYou,
                    isLoading = false
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
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
                val queries = listOf("trending now", "new music", "popular songs", "top hits")
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
                e.rethrowIfCancellation()
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
