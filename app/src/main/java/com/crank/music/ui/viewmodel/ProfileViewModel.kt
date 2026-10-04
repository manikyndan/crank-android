package com.crank.music.ui.viewmodel

import android.util.Log
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
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "User",
    val email: String = "",
    val isPremium: Boolean = false,
    val totalHours: String = "0",
    /** The artist played most often, by play count. */
    val favoriteArtist: String = "N/A",
    /** Lifetime play count, including repeats. */
    val totalPlays: String = "0 plays",
    val avatarUrl: String = ""
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val songDao: SongDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfileData()
    }

    private fun loadProfileData() {
        viewModelScope.launch {
            try {
                // Real listening stats, read off the main thread. The DAO calls were previously made
                // directly on the Main dispatcher, which is a disk read in a composable's backing
                // ViewModel init.
                val (topArtist, totalHoursLabel, playCount) = withContext(Dispatchers.IO) {
                    val listenedMs = songDao.getTotalListenedMs()
                    val totalHours = listenedMs / 3_600_000.0
                    // Keep one decimal so short sessions don't all collapse to "0".
                    val hoursLabel = if (totalHours >= 10.0) {
                        totalHours.toLong().toString()
                    } else {
                        String.format(java.util.Locale.US, "%.1f", totalHours)
                    }
                    Triple(songDao.getTopArtist(), hoursLabel, songDao.getTotalPlayCount())
                }

                _uiState.value = ProfileUiState(
                    name = "Music Lover",
                    email = "",
                    isPremium = false,
                    totalHours = totalHoursLabel,
                    // Labelled "Favorite Artist" by the screen. It previously read "Top Genre" while
                    // being filled with an artist name.
                    favoriteArtist = topArtist ?: "N/A",
                    // Was "monthlyStreak" holding a lifetime session count under a label that said
                    // "Days". Now it is explicitly a play count.
                    totalPlays = "$playCount plays",
                    avatarUrl = ""
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Log.e("CRANK_PROFILE", "Failed to load profile: ${e.message}")
            }
        }
    }
}
