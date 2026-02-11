package com.example.x_database.util

fun isVideoFile(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".mp4") ||
        lower.endsWith(".m4v") ||
        lower.endsWith(".mov") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".mkv")
}
