package com.crank.music.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * retryWithBackoff is a pure suspend function. These tests use runBlocking (coroutines-core, which
 * is already on the test classpath) and tiny 1ms backoff windows, so they never spend real time.
 * The two safety properties matter most:
 *  - CancellationException is rethrown immediately (never retried); and
 *  - a [shouldRetry] predicate can short-circuit retry on pointless errors.
 */
class RetryTest {

    @Test
    fun `returns result on first success`() = runBlocking {
        var calls = 0
        val result = retryWithBackoff(maxAttempts = 3) {
            calls++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries then succeeds`() = runBlocking {
        var calls = 0
        val result = retryWithBackoff(
            maxAttempts = 3,
            initialDelayMillis = 1,
            maxDelayMillis = 1
        ) {
            calls++
            if (calls < 3) throw RuntimeException("flaky") else "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, calls)
    }

    @Test
    fun `throws last error when all attempts fail`() = runBlocking {
        var calls = 0
        try {
            retryWithBackoff(
                maxAttempts = 3,
                initialDelayMillis = 1,
                maxDelayMillis = 1
            ) {
                calls++
                throw RuntimeException("boom")
            }
            fail("expected exception to propagate")
        } catch (e: RuntimeException) {
            assertEquals("boom", e.message)
        }
        assertEquals(3, calls) // exactly maxAttempts, no fourth try
    }

    @Test
    fun `cancellation is rethrown and never retried`() = runBlocking {
        var calls = 0
        try {
            retryWithBackoff(maxAttempts = 3) {
                calls++
                throw CancellationException("job cancelled")
            }
            fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected
        }
        assertEquals(1, calls) // must NOT have retried
    }

    @Test
    fun `shouldRetry false short-circuits without retrying`() = runBlocking {
        var calls = 0
        try {
            retryWithBackoff(
                maxAttempts = 3,
                initialDelayMillis = 1,
                maxDelayMillis = 1,
                shouldRetry = { false }
            ) {
                calls++
                throw RuntimeException("auth 403")
            }
            fail("expected exception to propagate")
        } catch (e: RuntimeException) {
            assertEquals("auth 403", e.message)
        }
        assertEquals(1, calls)
    }
}
