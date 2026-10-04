package com.manikyndan.crank.core.util

/**
 * A generic wrapper for handling UI states in a clean, type-safe way.
 *
 * Usage in ViewModel:
 * ```
 * private val _uiState = MutableStateFlow<Result<List<Item>>>(Result.Loading)
 * val uiState: StateFlow<Result<List<Item>>> = _uiState.asStateFlow()
 * ```
 */
sealed class Result<out T> {
    /** Successful state holding [data]. */
    data class Success<out T>(val data: T) : Result<T>()

    /** Error state holding an [exception] and optional [message]. */
    data class Error(
        val exception: Throwable? = null,
        val message: String = exception?.message ?: "Something went wrong",
    ) : Result<Nothing>()

    /** Loading state, optionally holding cached [data] to show behind a spinner. */
    data class Loading<out T>(val data: T? = null) : Result<T>()
}
