package com.crank.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crank.music.core.catchingCancellationSafe
import com.crank.music.data.local.SettingsStore
import com.crank.music.data.remote.gaana.GaanaApi
import com.crank.music.data.remote.gaana.GaanaUrlValidation
import com.crank.music.data.remote.gaana.validateGaanaBaseUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Result of an explicit connection probe. `IDLE` means no probe has been run since the input changed. */
enum class ConnectionTest { IDLE, RUNNING, OK, FAILED }

data class SourceSettingsState(
    /** Exactly what the field shows, including anything half-typed. Never persisted directly. */
    val input: String = "",
    /** Why the input cannot be used, safe to render. `null` when usable or untouched. */
    val inputError: String? = null,
    /** What is actually persisted right now — the source is configured when this is non-blank. */
    val savedUrl: String = "",
    val test: ConnectionTest = ConnectionTest.IDLE,
)

/**
 * Configuration for the optional self-hosted Gaana source.
 *
 * ## Why this is explicit Save rather than save-on-change
 *
 * Every other settings screen in this app persists immediately, and that is right for a toggle or
 * a picker — the value is always complete. A URL field is not: persisting on every keystroke would
 * store `http://192` mid-edit, and a later failure would leave a setting the user never agreed to.
 * So the field edits local state and `save()` commits it.
 *
 * ## Why the test is explicit too
 *
 * Validating reachability on every keystroke would fire a request per character at a server that
 * is usually not running yet. The user presses a button when they want to know.
 */
@HiltViewModel
class SourceSettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val gaanaApi: GaanaApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourceSettingsState())
    val uiState: StateFlow<SourceSettingsState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Shown as both the field and the "saved" baseline so an edit away from it is visible.
            val stored = settingsStore.getString(SettingsStore.GAANA_BASE_URL, "")
            _uiState.update { it.copy(input = stored, savedUrl = stored) }
        }
    }

    /** An edit invalidates both the previous verdict and any test result, which is now stale. */
    fun onInputChanged(value: String) {
        _uiState.update { it.copy(input = value, inputError = null, test = ConnectionTest.IDLE) }
    }

    /**
     * Commits the field.
     *
     * Blank is a valid commit meaning "switch the source off", which is why it is not an error —
     * an unset source is the documented default and the UI must be able to return to it.
     */
    fun save() {
        when (val verdict = validateGaanaBaseUrl(_uiState.value.input)) {
            is GaanaUrlValidation.Rejected -> _uiState.update { it.copy(inputError = verdict.reason) }
            GaanaUrlValidation.Disabled -> persist("")
            is GaanaUrlValidation.Ok -> persist(verdict.value)
        }
    }

    private fun persist(value: String) {
        // Optimistic: the field now shows what was requested.
        _uiState.update {
            it.copy(input = value, savedUrl = value, inputError = null, test = ConnectionTest.IDLE)
        }
        viewModelScope.launch {
            val written = settingsStore.putString(SettingsStore.GAANA_BASE_URL, value)
            if (!written) {
                // SettingsStore logs the underlying failure. Reporting it here matters: silently
                // reverting on the next screen is the exact bug this store exists to prevent.
                _uiState.update {
                    it.copy(
                        inputError = "Could not save. The value was not written to the database.",
                        savedUrl = "",
                    )
                }
            }
        }
    }

    /** Probes `/health` on the address as typed, without persisting it. */
    fun testConnection() {
        val url = when (val verdict = validateGaanaBaseUrl(_uiState.value.input)) {
            is GaanaUrlValidation.Ok -> verdict.value
            is GaanaUrlValidation.Rejected -> {
                _uiState.update { it.copy(inputError = verdict.reason) }
                return
            }
            GaanaUrlValidation.Disabled -> {
                _uiState.update { it.copy(inputError = "Enter a server address first") }
                return
            }
        }

        _uiState.update { it.copy(test = ConnectionTest.RUNNING, inputError = null) }
        viewModelScope.launch {
            // catchingCancellationSafe, not runCatching: runCatching would swallow the
            // CancellationException and leave this collector alive past a screen change.
            val healthy = catchingCancellationSafe(
                onError = { name, t -> Log.d(TAG, "Gaana health probe failed: $name: ${t.message}") },
            ) { gaanaApi.isHealthy(url) } ?: false

            _uiState.update {
                it.copy(test = if (healthy) ConnectionTest.OK else ConnectionTest.FAILED)
            }
        }
    }

    companion object {
        private const val TAG = "CRANK_SOURCES"
    }
}
