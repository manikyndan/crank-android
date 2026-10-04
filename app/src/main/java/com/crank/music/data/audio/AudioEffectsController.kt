package com.crank.music.data.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import com.crank.music.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CustomPreset(
    val name: String,
    val icon: String,
    val bandLevels: List<Int>
)

/**
 * Everything the equalizer screen shows, in one place.
 *
 * There is no `animatedBandLevels`, `spectrumBars` or `isVisualizerEnabled` here any more: those only
 * existed to drive a spectrum display that was fed by random numbers, and a toggle that turned the
 * random numbers on.
 */
data class AudioEffectsState(
    val isEnabled: Boolean = true,
    val currentPreset: String = AudioEffectsController.DEFAULT_PRESET,
    val bandLevels: List<Int> = List(AudioEffectsController.BAND_COUNT) { 0 },
    /**
     * Boost applied through `LoudnessEnhancer`, in dB.
     *
     * Never negative: the platform effect only implements attenuation as 0, so a negative preamp
     * would silently do nothing. The slider is 0..+12 for that reason.
     */
    val preampBoostDb: Int = 0,
    val isBassBoostEnabled: Boolean = false,
    val customPresets: List<CustomPreset> = emptyList(),
    val audioSessionId: Int? = null,
    /** Centre frequencies of the connected device's own bands, not a hardcoded guess. */
    val bandFrequencies: List<String> = AudioEffectsController.NOMINAL_BAND_LABELS,
    val bandLevelRangeDb: Int = 12,
    val statusMessage: String = "Waiting for audio session"
)

/**
 * Process-scoped owner of the platform audio effects.
 *
 * ## Why this is not a ViewModel any more
 *
 * The effects used to live in `EqualizerViewModel`, which is scoped to the equalizer screen. Leaving
 * the screen destroyed the ViewModel, whose `onCleared` released the `Equalizer`, `BassBoost` and
 * `LoudnessEnhancer` — so a preset the user had just chosen stopped applying the moment they navigated
 * away, and the equalizer came back on the next visit with no effect attached. Nothing about a
 * listening preference should depend on which screen happens to be on top.
 *
 * This singleton attaches itself to whatever audio session the player publishes, applies the saved
 * settings to it, and keeps them applied for the life of the process. Persisting the settings means a
 * preset survives a restart too, which it previously did not: `customPresets`, band levels and the
 * preamp were all in-memory only.
 *
 * ## Preamp and "normalization" were the same control
 *
 * Both the preamp slider and the separate Audio Normalization switch wrote to the same
 * `LoudnessEnhancer.setTargetGain`, so whichever moved last won and the other appeared broken. There
 * is one gain control now. It is a *boost* control, because the platform effect cannot attenuate.
 */
@Singleton
class AudioEffectsController @Inject constructor(
    private val settingsStore: SettingsStore,
    private val json: Json,
) {

    private val _state = MutableStateFlow(AudioEffectsState())
    val state: StateFlow<AudioEffectsState> = _state.asStateFlow()

    /**
     * Outlives every screen, which is the point. Effects are released only when a new session
     * replaces the old one, or when the process ends.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var nativeEqualizer: Equalizer? = null
    private var nativeBassBoost: BassBoost? = null
    private var nativeLoudnessEnhancer: LoudnessEnhancer? = null

    /** Device band range in millibels, read once at attach time. */
    private var deviceRangeMillibels: ShortArray = shortArrayOf(-1200, 1200)

    val presets: Map<String, List<Int>> = linkedMapOf(
        "Flat" to listOf(0, 0, 0, 0, 0),
        "Bass Boost" to listOf(12, 8, 2, -1, -2),
        "Vocal" to listOf(-2, 2, 8, 6, 1),
        "Rock" to listOf(8, 5, -2, 4, 7),
        "Pop" to listOf(-1, 3, 6, 4, -2),
        "Jazz" to listOf(4, 2, -1, 5, 8),
        "Classical" to listOf(6, 4, 0, 2, 6)
    )

    init {
        scope.launch { loadPersisted() }
    }

    /** All presets the user can pick: the built-ins plus whatever they have saved. */
    fun allPresets(): Map<String, List<Int>> =
        presets + _state.value.customPresets.associate { it.name to it.bandLevels }

    /**
     * Binds the effects to [sessionId].
     *
     * A null, zero or -1 session is normal before playback starts — ExoPlayer only publishes one once
     * an audio track exists — so it clears the handle and reports that rather than pretending to be
     * connected.
     */
    fun attach(sessionId: Int?) {
        if (sessionId == null || sessionId == 0 || sessionId == -1) {
            _state.value = _state.value.copy(
                audioSessionId = null,
                statusMessage = "Waiting for audio session"
            )
            return
        }

        if (_state.value.audioSessionId == sessionId && nativeEqualizer != null) return

        releaseEffects()

        try {
            val equalizer = Equalizer(0, sessionId)
            val bassBoost = BassBoost(0, sessionId)
            val loudness = LoudnessEnhancer(sessionId)

            nativeEqualizer = equalizer
            nativeBassBoost = bassBoost
            nativeLoudnessEnhancer = loudness
            deviceRangeMillibels = runCatching { equalizer.bandLevelRange }.getOrDefault(deviceRangeMillibels)

            val bandCount = runCatching { equalizer.numberOfBands.toInt() }
                .getOrDefault(BAND_COUNT)
                .coerceAtLeast(0)

            _state.value = _state.value.copy(
                audioSessionId = sessionId,
                statusMessage = "Connected to session $sessionId",
                bandFrequencies = centerFrequencies(equalizer, bandCount),
                bandLevelRangeDb = (deviceRangeMillibels.lastOrNull()?.div(100)) ?: 12
            )

            // Apply the persisted settings to the freshly created effects.
            applyEnabled()
            applyBandLevels()
            applyPreamp()
            applyBassBoost()
        } catch (e: Exception) {
            // Some devices refuse an equalizer on certain output routes (Bluetooth hands-free, USB
            // DACs without an effect framework). Reporting it beats silently doing nothing.
            Log.w(TAG, "Could not attach audio effects to session $sessionId", e)
            releaseEffects()
            _state.value = _state.value.copy(
                audioSessionId = null,
                statusMessage = "Audio effects unavailable on this device"
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        applyEnabled()
        persist()
    }

    fun selectPreset(name: String) {
        val levels = allPresets()[name] ?: return
        _state.value = _state.value.copy(currentPreset = name, bandLevels = levels.toList())
        applyBandLevels()
        persist()
    }

    fun setBandLevel(bandIndex: Int, dbLevel: Int) {
        val levels = _state.value.bandLevels.toMutableList()
        if (bandIndex !in levels.indices) return
        levels[bandIndex] = dbLevel.coerceIn(-MAX_BAND_DB, MAX_BAND_DB)
        _state.value = _state.value.copy(bandLevels = levels, currentPreset = CUSTOM_PRESET)
        applyBandLevel(bandIndex, levels[bandIndex])
        persist()
    }

    fun setPreampBoost(db: Int) {
        val clamped = db.coerceIn(0, MAX_PREAMP_DB)
        _state.value = _state.value.copy(preampBoostDb = clamped)
        applyPreamp()
        persist()
    }

    fun setBassBoost(enabled: Boolean) {
        _state.value = _state.value.copy(isBassBoostEnabled = enabled)
        applyBassBoost()
        persist()
    }

    fun saveCustomPreset(name: String, icon: String) {
        val preset = CustomPreset(
            name = name,
            icon = icon,
            bandLevels = _state.value.bandLevels.toList()
        )
        val updated = _state.value.customPresets.filterNot { it.name == name } + preset
        _state.value = _state.value.copy(customPresets = updated, currentPreset = name)
        persist()
    }

    fun deleteCustomPreset(name: String) {
        val updated = _state.value.customPresets.filterNot { it.name == name }
        _state.value = _state.value.copy(
            customPresets = updated,
            currentPreset = if (_state.value.currentPreset == name) DEFAULT_PRESET else _state.value.currentPreset
        )
        persist()
    }

    private fun applyEnabled() {
        runCatching { nativeEqualizer?.enabled = _state.value.isEnabled }
            .onFailure { Log.w(TAG, "Could not toggle equalizer", it) }
    }

    private fun applyBandLevels() {
        _state.value.bandLevels.forEachIndexed { index, db -> applyBandLevel(index, db) }
    }

    private fun applyBandLevel(bandIndex: Int, dbLevel: Int) {
        val equalizer = nativeEqualizer ?: return
        runCatching {
            if (bandIndex >= equalizer.numberOfBands) return
            val min = deviceRangeMillibels.getOrElse(0) { -1200 }.toInt()
            val max = deviceRangeMillibels.getOrElse(1) { 1200 }.toInt()
            val millibels = (dbLevel * 100).coerceIn(min, max)
            equalizer.setBandLevel(bandIndex.toShort(), millibels.toShort())
        }.onFailure { Log.w(TAG, "Could not set band $bandIndex to $dbLevel dB", it) }
    }

    private fun applyPreamp() {
        runCatching {
            val enhancer = nativeLoudnessEnhancer
            if (enhancer != null) {
                enhancer.enabled = _state.value.preampBoostDb > 0
                enhancer.setTargetGain(_state.value.preampBoostDb * 100)
            }
        }.onFailure { Log.w(TAG, "Could not apply preamp", it) }
    }

    private fun applyBassBoost() {
        runCatching {
            val bassBoost = nativeBassBoost ?: return
            bassBoost.enabled = _state.value.isBassBoostEnabled
            if (_state.value.isBassBoostEnabled) bassBoost.setStrength(BASS_BOOST_STRENGTH)
        }.onFailure { Log.w(TAG, "Could not toggle bass boost", it) }
    }

    private fun releaseEffects() {
        runCatching { nativeEqualizer?.release() }
        runCatching { nativeBassBoost?.release() }
        runCatching { nativeLoudnessEnhancer?.release() }
        nativeEqualizer = null
        nativeBassBoost = null
        nativeLoudnessEnhancer = null
    }

    private fun centerFrequencies(equalizer: Equalizer, bandCount: Int): List<String> {
        val count = minOf(BAND_COUNT, bandCount)
        if (count <= 0) return NOMINAL_BAND_LABELS
        return (0 until count).mapNotNull { band ->
            runCatching { formatFrequency(equalizer.getCenterFreq(band.toShort())) }.getOrNull()
        }.ifEmpty { NOMINAL_BAND_LABELS }
    }

    private fun formatFrequency(milliHertz: Int): String = when {
        milliHertz >= 1_000_000 -> String.format(Locale.US, "%.1f kHz", milliHertz / 1_000_000.0)
        milliHertz > 0 -> String.format(Locale.US, "%d Hz", milliHertz / 1000)
        else -> "—"
    }

    private suspend fun loadPersisted() {
        runCatching {
            val defaults = AudioEffectsState()
            val storedBands = settingsStore
                .getString(KEY_BANDS, "")
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .takeIf { it.size == BAND_COUNT }

            val custom = runCatching {
                json.decodeFromString<List<CustomPreset>>(settingsStore.getString(KEY_CUSTOM_PRESETS, "[]"))
            }.getOrDefault(emptyList())

            _state.value = _state.value.copy(
                isEnabled = settingsStore.getBoolean(KEY_ENABLED, defaults.isEnabled),
                currentPreset = settingsStore.getString(KEY_PRESET, defaults.currentPreset),
                bandLevels = storedBands ?: defaults.bandLevels,
                preampBoostDb = settingsStore.getInt(KEY_PREAMP, defaults.preampBoostDb)
                    .coerceIn(0, MAX_PREAMP_DB),
                isBassBoostEnabled = settingsStore.getBoolean(KEY_BASS_BOOST, defaults.isBassBoostEnabled),
                customPresets = custom
            )

            // The effects may already exist if a session arrived before the read finished.
            applyEnabled()
            applyBandLevels()
            applyPreamp()
            applyBassBoost()
        }.onFailure { Log.w(TAG, "Could not restore equalizer settings", it) }
    }

    private fun persist() {
        val current = _state.value
        scope.launch {
            runCatching {
                settingsStore.putBoolean(KEY_ENABLED, current.isEnabled)
                settingsStore.putString(KEY_PRESET, current.currentPreset)
                settingsStore.putString(KEY_BANDS, current.bandLevels.joinToString(","))
                settingsStore.putInt(KEY_PREAMP, current.preampBoostDb)
                settingsStore.putBoolean(KEY_BASS_BOOST, current.isBassBoostEnabled)
                settingsStore.putString(KEY_CUSTOM_PRESETS, json.encodeToString(current.customPresets))
            }.onFailure { Log.w(TAG, "Could not save equalizer settings", it) }
        }
    }

    companion object {
        const val BAND_COUNT = 5
        const val MAX_BAND_DB = 12
        const val MAX_PREAMP_DB = 12
        const val DEFAULT_PRESET = "Flat"
        const val CUSTOM_PRESET = "Custom"

        private const val TAG = "CRANK_EQ"
        private const val BASS_BOOST_STRENGTH: Short = 800

        private const val KEY_ENABLED = "settings.equalizer.enabled"
        private const val KEY_PRESET = "settings.equalizer.preset"
        private const val KEY_BANDS = "settings.equalizer.bands"
        private const val KEY_PREAMP = "settings.equalizer.preamp"
        private const val KEY_BASS_BOOST = "settings.equalizer.bass_boost"
        private const val KEY_CUSTOM_PRESETS = "settings.equalizer.custom_presets"

        val NOMINAL_BAND_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "4 kHz", "14 kHz")
    }
}
