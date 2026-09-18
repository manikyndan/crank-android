package com.crank.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class DeviceType {
    PHONE, TABLET, DESKTOP
}

@Composable
fun rememberDeviceType(): DeviceType {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    return when {
        widthDp >= 840 -> DeviceType.DESKTOP
        widthDp >= 600 -> DeviceType.TABLET
        else -> DeviceType.PHONE
    }
}

@Composable
fun rememberHorizontalPadding(): Dp {
    val device = rememberDeviceType()
    return when (device) {
        DeviceType.PHONE -> 20.dp
        DeviceType.TABLET -> 32.dp
        DeviceType.DESKTOP -> 48.dp
    }
}

@Composable
fun rememberContentMaxWidth(): Dp {
    val device = rememberDeviceType()
    return when (device) {
        DeviceType.PHONE -> Dp.Unspecified
        DeviceType.TABLET -> 720.dp
        DeviceType.DESKTOP -> 960.dp
    }
}
