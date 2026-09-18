package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.data.local.SongDao
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "User",
    val email: String = "",
    val isPremium: Boolean = false,
    val totalHours: String = "0",
    val topGenre: String = "N/A",
    val monthlyStreak: String = "0 Days",
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
                // Calculate real listening stats from history
                val historyCount = songDao.getHistoryCount()
                val totalMinutes = songDao.getTotalListeningMinutes()
                // Keep one decimal so short sessions don't all collapse to "0".
                val totalHours = totalMinutes / 60.0
                val totalHoursLabel = if (totalHours >= 10.0) {
                    totalHours.toLong().toString()
                } else {
                    String.format(java.util.Locale.US, "%.1f", totalHours)
                }

                // Get top artist from history
                val topArtist = songDao.getTopArtist() ?: "N/A"

                _uiState.value = ProfileUiState(
                    name = "Music Lover",
                    email = "",
                    isPremium = false,
                    totalHours = totalHoursLabel,
                    topGenre = topArtist,
                    monthlyStreak = "$historyCount sessions",
                    avatarUrl = ""
                )
            } catch (e: Exception) {
                Log.e("CRANK_PROFILE", "Failed to load profile: ${e.message}")
            }
        }
    }
}
