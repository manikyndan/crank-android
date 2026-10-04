package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.crank.music.data.audio.AudioEffectsController
import com.crank.music.data.audio.AudioEffectsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The equalizer screen's view of [AudioEffectsController].
 *
 * This used to own the `Equalizer`, `BassBoost` and `LoudnessEnhancer` instances itself, which meant
 * they were released by [onCleared] the moment the user left the screen and the chosen preset stopped
 * applying. It also kept band levels, the preamp and the custom presets in memory only, so every one
 * of them was gone after a restart. None of that state lives here any more: the controller owns the
 * effects and persists the settings, and this class no longer does anything except expose them.
 *
 * The visualizer is gone with it. It drew a spectrum from `Math.random()` and was only ever animated
 * by numbers generated on the screen, so there was nothing to make real without wiring the platform
 * `Visualizer`, which this build does not do.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val audioEffects: AudioEffectsController,
) : ViewModel() {

    val uiState: StateFlow<AudioEffectsState> = audioEffects.state

    /** Built-in presets plus the user's saved ones. */
    fun getAllPresets(): Map<String, List<Int>> = audioEffects.allPresets()

    /**
     * Binds the effects to the player's audio session.
     *
     * Keeping this call on the screen is deliberate: it is a cheap no-op when the session is
     * unchanged, and the controller stays attached after the screen leaves.
     */
    fun attachAudioSession(sessionId: Int?) = audioEffects.attach(sessionId)

    fun toggleEqualizer(enabled: Boolean) = audioEffects.setEnabled(enabled)

    fun selectPreset(presetName: String) = audioEffects.selectPreset(presetName)

    fun onBandLevelChanged(bandIndex: Int, newDbLevel: Int) =
        audioEffects.setBandLevel(bandIndex, newDbLevel)

    fun onPreampChanged(newDb: Int) = audioEffects.setPreampBoost(newDb)

    fun toggleBassBoost(enabled: Boolean) = audioEffects.setBassBoost(enabled)

    fun saveCustomPreset(name: String, icon: String) =
        audioEffects.saveCustomPreset(name, icon)

    fun deleteCustomPreset(name: String) = audioEffects.deleteCustomPreset(name)
}
