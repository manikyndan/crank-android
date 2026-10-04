package com.manikyndan.crank.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manikyndan.crank.core.util.Result
import com.manikyndan.crank.domain.model.Item
import com.manikyndan.crank.domain.usecase.GetItemsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for [com.manikyndan.crank.presentation.ui.screen.ItemScreen]. */
data class ItemsUiState(
    val result: Result<List<Item>> = Result.Loading(),
)

/**
 * MVVM ViewModel: collects the [GetItemsUseCase] flow in [viewModelScope]
 * and exposes an immutable [StateFlow] for Compose.
 */
@HiltViewModel
class ItemViewModel @Inject constructor(
    private val getItemsUseCase: GetItemsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemsUiState())
    val uiState: StateFlow<ItemsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-fetches items; safe to call from swipe-to-refresh or retry buttons. */
    fun refresh() {
        viewModelScope.launch {
            getItemsUseCase().collect { result: Result<List<Item>> ->
                _uiState.value = ItemsUiState(result = result)
            }
        }
    }
}
