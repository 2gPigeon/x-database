package com.example.x_database.util

private const val X_EPOCH = 1288834974657L
private val STATUS_REGEX = Regex("""(?:x|twitter)\.com\/(?:\w+|i|i\/web)\/status\/(\d+)""")

fun extractTweetId(url: String): Long? {
    val match = STATUS_REGEX.find(url) ?: return null
    return match.groupValues.getOrNull(1)?.toLongOrNull()
}

fun tweetIdToPostedAt(tweetId: Long): Long {
    return (tweetId shr 22) + X_EPOCH
}
