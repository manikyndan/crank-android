package com.crank.music.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Plays the "confirmed" gesture haptic on a plain [View], on every API this app supports.
 *
 * `HapticFeedbackConstants.CONFIRM` is API 30, and it is a compile-time `static final int`, so it
 * is *inlined* into the bytecode rather than resolved at runtime. On Android 7-11 that means no
 * crash — the id simply does not exist there, so the gesture feedback is silently dropped and
 * taps, toggles and dismissals feel dead. The guard keeps the intended `CONFIRM` on 30+ and uses
 * `CONTEXT_CLICK` (API 23) below it, which is the nearest equivalent that exists on minSdk 24.
 */
fun confirmHaptic(view: View) {
    val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.CONTEXT_CLICK
    }
    view.performHapticFeedback(feedback)
}

@Composable
fun rememberHaptics(): HapticFeedback {
    return LocalHapticFeedback.current
}

fun HapticFeedback.lightClick() {
    this.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

fun HapticFeedback.heavyClick() {
    this.performHapticFeedback(HapticFeedbackType.LongPress)
}

fun Modifier.clickableWithHaptics(
    haptic: HapticFeedback,
    isHeavy: Boolean = false,
    onClick: () -> Unit
): Modifier {
    return this.clickable {
        if (isHeavy) {
            haptic.heavyClick()
        } else {
            haptic.lightClick()
        }
        onClick()
    }
}
