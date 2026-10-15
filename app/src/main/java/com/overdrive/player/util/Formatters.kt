package com.overdrive.player.util

import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** "1 hr 12 min" / "48 min" style total for albums and queues. */
fun formatTotalDuration(ms: Long): String {
    val totalMinutes = (ms / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes min"
        hours > 0 -> "$hours hr"
        ms in 1..59_999 -> "< 1 min"
        else -> "$minutes min"
    }
}

fun pluralize(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"
