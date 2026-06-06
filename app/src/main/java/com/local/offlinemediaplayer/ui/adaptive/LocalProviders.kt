package com.local.offlinemediaplayer.ui.adaptive

import androidx.compose.runtime.staticCompositionLocalOf

val LocalWindowSizeClass = staticCompositionLocalOf { AppWidthClass.Compact }
val LocalDevicePosture = staticCompositionLocalOf<DevicePosture> { DevicePosture.Normal }
