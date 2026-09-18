package com.crank.music.feature.recognition

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin ViewModel whose only job is to expose the Hilt-provided
 * [MusicRecognitionRepository] to [RecognitionScreen]. Recognition itself is
 * stateless request/response, so the screen owns its own UI state and this
 * class deliberately holds none.
 */
@HiltViewModel
class RecognitionViewModel @Inject constructor(
    val repository: MusicRecognitionRepository
) : ViewModel()
