package com.crank.music.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Runs [block] up to [maxAttempts] times with exponential backoff, returning its result as soon as
 * it succeeds.
 *
 * This is the "retries" half of the error-recovery story. Two rules make it safe to use inside the
 * music app's cancellable coroutines:
 *
 *  1. **Cancellation is sacred.** A [CancellationException] is rethrown immediately and never
 *     retried. Retrying a cancelled coroutine is exactly what previously left search jobs alive past
 *     their cancellation point (see CoroutineDiscipline) — the job kept running while the UI moved
 *     on, so searches never settled.
 *  2. **Don't retry the pointless.** [shouldRetry] lets callers skip retry on errors that will not
 *     improve on a second attempt (e.g. a 403 auth failure). Defaults to retrying everything.
 *
 * Backoff grows `initialDelayMillis * 2^(attempt-1)` and is capped at [maxDelayMillis]. When all
 * attempts are exhausted, the last error is thrown so the caller's own try/catch (which returns an
 * empty list / null / local fallback) still applies.
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelayMillis: Long = 400L,
    maxDelayMillis: Long = 3_000L,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend () -> T
): T {
    var attempt = 0
    var lastError: Throwable? = null

    while (attempt < maxAttempts) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!shouldRetry(e)) throw e
            lastError = e
            attempt++
            if (attempt >= maxAttempts) break
            val delayMs = min(initialDelayMillis * (1L shl (attempt - 1)), maxDelayMillis)
            delay(delayMs)
        }
    }

    throw lastError ?: IllegalStateException("retryWithBackoff exhausted with no recorded error")
}
