package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.domain.model.Song
import com.crank.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class Sender { USER, AI }

enum class BackgroundMood {
    DEFAULT,
    RAIN_NIGHT,
    WORKOUT,
    CHILL,
    PARTY,
    FOCUS,
    SLEEP
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val songs: List<Song> = emptyList(),
    val isAnimated: Boolean = true
)

data class CrankAiUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            sender = Sender.AI,
            text = "Hey, I'm CRANK AI. Tell me what you're feeling — a mood, a moment, or just a vibe — and I'll find the perfect sound."
        )
    ),
    val currentInput: String = "",
    val isThinking: Boolean = false,
    val moodValue: Float = 0.5f,
    val backgroundMood: BackgroundMood = BackgroundMood.DEFAULT,
    val isRecording: Boolean = false,
    val conversationContext: List<String> = emptyList()
)

data class QuickAction(
    val label: String,
    val icon: String,
    val query: String,
    val mood: BackgroundMood
)

@HiltViewModel
class CrankAiViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CrankAiUiState())
    val uiState: StateFlow<CrankAiUiState> = _uiState.asStateFlow()

    val quickActions = listOf(
        QuickAction("Surprise Me", "🎲", "random fun music", BackgroundMood.DEFAULT),
        QuickAction("Deep Focus", "🧠", "ambient study concentration", BackgroundMood.FOCUS),
        QuickAction("Party Mode", "🎉", "dance party bangers", BackgroundMood.PARTY),
        QuickAction("Sleep Sounds", "🌙", "ambient sleep relaxation", BackgroundMood.SLEEP)
    )

    fun onInputChanged(input: String) {
        _uiState.value = _uiState.value.copy(currentInput = input)
    }

    fun onMoodChanged(value: Float) {
        _uiState.value = _uiState.value.copy(moodValue = value)
    }

    fun onQuickAction(action: QuickAction) {
        _uiState.value = _uiState.value.copy(currentInput = action.query)
        sendMessage()
    }

    fun toggleRecording() {
        _uiState.value = _uiState.value.copy(isRecording = !_uiState.value.isRecording)
    }

    fun sendMessage() {
        val prompt = _uiState.value.currentInput.trim()
        if (prompt.isBlank() || _uiState.value.isThinking) return

        val userMsg = ChatMessage(sender = Sender.USER, text = prompt)
        val updatedMessages = _uiState.value.messages + userMsg
        val updatedContext = _uiState.value.conversationContext + prompt

        val detectedMood = detectBackgroundMood(prompt)
        val moodLabel = when {
            _uiState.value.moodValue < 0.3f -> "chill and relaxed"
            _uiState.value.moodValue > 0.7f -> "high energy and intense"
            else -> "moderate vibes"
        }

        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            currentInput = "",
            isThinking = true,
            backgroundMood = detectedMood,
            conversationContext = updatedContext.takeLast(10)
        )

        viewModelScope.launch {
            delay(800L)

            val searchQuery = translateMoodToQuery(prompt, moodLabel)
            val songs = try {
                musicRepository.search(searchQuery)
            } catch (e: Exception) {
                emptyList()
            }

            val contextHint = if (_uiState.value.conversationContext.size > 1) {
                val previous = _uiState.value.conversationContext.dropLast(1).lastOrNull()
                if (previous != null) "Building on your earlier thought about \"$previous\" — " else ""
            } else ""

            val responseText = when {
                songs.isNotEmpty() && detectedMood == BackgroundMood.RAIN_NIGHT ->
                    "${contextHint}Setting the mood for a rainy night drive. Here are some atmospheric tracks:"
                songs.isNotEmpty() && detectedMood == BackgroundMood.WORKOUT ->
                    "${contextHint}Turning up the heat! Here's your workout fuel:"
                songs.isNotEmpty() && detectedMood == BackgroundMood.PARTY ->
                    "${contextHint}Party mode activated! Here's the energy you need:"
                songs.isNotEmpty() && detectedMood == BackgroundMood.FOCUS ->
                    "${contextHint}Dialing in the focus zone. These tracks will keep you in the flow:"
                songs.isNotEmpty() && detectedMood == BackgroundMood.SLEEP ->
                    "${contextHint}Winding down. Here's some calm for the night:"
                songs.isNotEmpty() ->
                    "${contextHint}Here are tracks matching \"$prompt\":"
                else ->
                    "No exact matches for \"$prompt\", but here are some popular picks:"
            }

            val fallbackSongs = if (songs.isEmpty()) {
                try {
                    musicRepository.search("top hits trending")
                } catch (e: Exception) {
                    emptyList()
                }
            } else songs

            val aiMsg = ChatMessage(
                sender = Sender.AI,
                text = responseText,
                songs = fallbackSongs
            )

            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + aiMsg,
                isThinking = false
            )
        }
    }

    private fun detectBackgroundMood(prompt: String): BackgroundMood {
        val lower = prompt.lowercase()
        return when {
            lower.contains("driving") && (lower.contains("rain") || lower.contains("night")) -> BackgroundMood.RAIN_NIGHT
            lower.contains("rain") || lower.contains("night drive") || lower.contains("moody") -> BackgroundMood.RAIN_NIGHT
            lower.contains("workout") || lower.contains("gym") || lower.contains("run") || lower.contains("power") -> BackgroundMood.WORKOUT
            lower.contains("chill") || lower.contains("relax") || lower.contains("calm") || lower.contains("lofi") -> BackgroundMood.CHILL
            lower.contains("party") || lower.contains("dance") || lower.contains("club") -> BackgroundMood.PARTY
            lower.contains("focus") || lower.contains("study") || lower.contains("work") || lower.contains("concentrate") -> BackgroundMood.FOCUS
            lower.contains("sleep") || lower.contains("bedtime") || lower.contains("ambient") -> BackgroundMood.SLEEP
            else -> BackgroundMood.DEFAULT
        }
    }

    private fun translateMoodToQuery(mood: String, moodLabel: String): String {
        val lower = mood.lowercase()

        val energyModifier = when {
            _uiState.value.moodValue < 0.3f -> "soft gentle quiet "
            _uiState.value.moodValue > 0.7f -> "energetic upbeat powerful "
            else -> ""
        }

        val baseQuery = when {
            lower.contains("relax") || lower.contains("chill") || lower.contains("lofi") -> "lofi chill beats"
            lower.contains("workout") || lower.contains("gym") || lower.contains("run") -> "workout motivation high energy"
            lower.contains("focus") || lower.contains("study") || lower.contains("work") -> "deep focus ambient study"
            lower.contains("party") || lower.contains("dance") || lower.contains("club") -> "dance party hits"
            lower.contains("sad") || lower.contains("melancholy") -> "emotional melancholy songs"
            lower.contains("rain") || lower.contains("driving") || lower.contains("night") -> "rainy night atmospheric"
            lower.contains("rock") || lower.contains("metal") -> "rock metal energy"
            lower.contains("jazz") || lower.contains("blues") -> "smooth jazz blues"
            lower.contains("pop") || lower.contains("top") -> "popular top hits"
            lower.contains("surprise") || lower.contains("random") -> "random fun trending"
            lower.contains("sleep") || lower.contains("bedtime") -> "ambient sleep sounds"
            else -> mood
        }

        return "$energyModifier$baseQuery".trim()
    }
}
