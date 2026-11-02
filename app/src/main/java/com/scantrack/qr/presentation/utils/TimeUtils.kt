package com.scantrack.qr.presentation.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    /**
     * Formats an epoch timestamp into an accurate, human-readable relative time string.
     * Accurately distinguishes between moments ago, hours ago, yesterday, and specific calendar dates.
     */
    fun formatRelativeTime(timestamp: Long, currentTime: Long = System.currentTimeMillis()): String {
        val diff = currentTime - timestamp
        if (diff < 0) return "just now"

        return when {
            diff < 60_000L -> "just now"
            diff < 3_600_000L -> "${diff / 60_000L}m ago"
            diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
            diff < 172_800_000L -> "yesterday"
            else -> {
                val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}

