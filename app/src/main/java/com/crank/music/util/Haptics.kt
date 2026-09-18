package com.crank.music.util

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

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
