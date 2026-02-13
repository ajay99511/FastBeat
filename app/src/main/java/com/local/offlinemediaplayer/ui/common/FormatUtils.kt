package com.local.offlinemediaplayer.ui.common

import java.text.DecimalFormat
import java.util.Formatter
import java.util.Locale

object FormatUtils {
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        val mFormatter = Formatter(StringBuilder(), Locale.getDefault())
        return if (hours > 0) {
            mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        } else {
            mFormatter.format("%d:%02d", minutes, seconds).toString()
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) +
                " " +
                units[digitGroups]
    }

    fun formatMinutesToHours(minutes: Int): String {
        if (minutes < 60) return "${minutes}m"
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (remainingMinutes > 0) {
            "${hours}h ${remainingMinutes}m"
        } else {
            "${hours}h"
        }
    }
}
