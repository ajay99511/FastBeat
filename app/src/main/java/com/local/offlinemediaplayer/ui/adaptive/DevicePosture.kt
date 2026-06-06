package com.local.offlinemediaplayer.ui.adaptive

import android.graphics.Rect
import androidx.window.layout.FoldingFeature

sealed class DevicePosture {
    object Normal : DevicePosture()
    data class TableTop(val hingePosition: Rect) : DevicePosture()
}

fun FoldingFeature.toDevicePosture(): DevicePosture {
    val isTableTop = state == FoldingFeature.State.HALF_OPENED &&
            orientation == FoldingFeature.Orientation.HORIZONTAL
    return if (isTableTop) {
        DevicePosture.TableTop(bounds)
    } else {
        DevicePosture.Normal
    }
}
