package com.crank.music.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred

/**
 * Rethrows [this] when it is a [CancellationException]. Otherwise does nothing.
 *
 * ### Why this exists
 *
 * `catch (e: Exception)` also catches [CancellationException], because it is an `Exception`.
 * Swallowing it is a correctness bug, not a style issue: a cancelled coroutine that catches its
 * own cancellation and returns normally keeps running, so structured concurrency is broken and
 * the caller can no longer tell "cancelled" apart from "returned an empty result".
 *
 * In this app the visible symptom was searches that never settled. The user types, the previous
 * `searchJob` is cancelled, the in-flight `search()` throws [CancellationException], the generic
 * handler logged it as a failure and returned `emptyList()`, and the coroutine carried on past
 * the cancellation point. Logcat showed a stream of `StandaloneCoroutine was cancelled` while the
 * UI waited for a result that had already been discarded.
 *
 * Needed when catching a type wide enough to include [CancellationException] — most commonly
 * `catch (e: Throwable)`. A `catch (e: Exception)` in a `suspend` function should instead be
 * rewritten with [catchingCancellationSafe], which encodes the whole pattern.
 */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

/**
 * Runs [block], returning its result, or `null` if it failed with a non-cancellation throwable.
 *
 * Cancellation is always rethrown so the coroutine terminates as its caller intended. [onError]
 * receives the failure and its throwable for logging; returning `null` from it suppresses logging.
 *
 * Use this instead of a hand-written `try/catch (e: Exception)` around a suspend call — it is the
 * same thing without the cancellation bug.
 */
inline fun <T> catchingCancellationSafe(
    onError: (String, Throwable) -> Unit = { _, _ -> },
    block: () -> T,
): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    onError(e.javaClass.simpleName, e)
    null
}

/**
 * Awaits [this] and returns its value, or `null` if it failed for a reason other than cancellation.
 *
 * The `Deferred` counterpart of [catchingCancellationSafe], for the common fan-out shape where
 * several `async` results are collected independently and a failure in one should not lose the
 * others. Prefer this over `runCatching { deferred.await() }.getOrNull()`, which catches
 * [CancellationException] and therefore keeps the collecting coroutine running after cancellation.
 */
suspend fun <T> Deferred<T>.awaitOrNull(
    onError: (String, Throwable) -> Unit = { _, _ -> },
): T? = try {
    await()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    onError(e.javaClass.simpleName, e)
    null
}
