package com.crank.music.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The sleep timer's two public numbers, so the UI never has to read a job. */
data class SleepTimerState(
    /** The duration the user picked, or 0 when no timer is running. */
    val totalMinutes: Int = 0,
    /** Time left before playback is paused. 0 when no timer is running. */
    val remainingMs: Long = 0L
) {
    val isActive: Boolean get() = totalMinutes > 0
}

/**
 * Pauses playback after a chosen interval.
 *
 * ## Why this is its own class
 *
 * This was ~30 lines inside `PlayerViewModel`, a 1600-line class that had become the place everything
 * went. The timer is genuinely separable: it owns one [Job], one countdown, and one callback — it has
 * no idea what a queue, a download or a lyric is. Both of its public methods were already exactly the
 * two a dedicated object would expose.
 *
 * The countdown interval is injectable so the behaviour can be tested without waiting a real minute
 * for a one-minute timer: a test advances virtual time instead.
 *
 * ## Why expiry is a callback and not a `pause()` call
 *
 * The controller does not hold the player, so it cannot pause it — and that is the point. It also means
 * the *decision* to pause stays with the owner, which matters because the original implementation
 * called `player.pause()` unconditionally: reached while already paused it was harmless, but it also
 * meant the timer had to know about `Player`. The owner passes `if (isPlaying) pause()` instead.
 *
 * @param scope lifetime for the countdown. In production this is `viewModelScope`, so the timer cannot
 *   outlive the player it belongs to.
 */
class SleepTimerController(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Starts a countdown of [minutes] and invokes [onExpired] when it reaches zero.
     *
     * A non-positive [minutes] cancels any running timer and leaves the state idle, which is how the
     * "Off" option in the sheet is expressed. Starting a new timer replaces the previous one; the two
     * used to be able to run at once, and the older one would fire first and pause playback seconds
     * after the user had extended their timer.
     *
     * [onExpired] runs in [scope], on whatever dispatcher that scope uses. It is not called if the
     * timer is cancelled or the scope dies first.
     */
    fun start(minutes: Int, tickMs: Long = TICK_MS, onExpired: () -> Unit) {
        cancel()
        if (minutes <= 0) return

        val totalMs = minutes * MS_PER_MINUTE
        _state.value = SleepTimerState(totalMinutes = minutes, remainingMs = totalMs)

        job = scope.launch {
            var remaining = totalMs
            // Loop on remaining time rather than counting ticks, so the displayed value is always
            // "what is left" and cannot drift past zero if a tick is late.
            while (remaining > 0) {
                delay(tickMs)
                remaining -= tickMs
                _state.value = _state.value.copy(remainingMs = remaining.coerceAtLeast(0L))
            }
            onExpired()
            _state.value = SleepTimerState()
        }
    }

    /** Stops the countdown and clears the state. Safe to call when no timer is running. */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SleepTimerState()
    }

    companion object {
        /** One second: fast enough that the remaining-time label visibly counts down. */
        const val TICK_MS = 1_000L

        private const val MS_PER_MINUTE = 60_000L
    }
}
