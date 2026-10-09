package com.rst.player.util

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val hours = minutes / 60
    if (hours > 0) {
        return "$hours:${(minutes % 60).toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

fun formatCount(n: Long): String = when {
    n >= 1_000_000_000 -> String.format("%.1fB", n / 1_000_000_000.0)
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
    else -> n.toString()
}
