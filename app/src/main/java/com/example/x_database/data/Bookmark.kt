package com.example.x_database.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tweetId: Long?,
    val filePath: String,
    val sourceUrl: String?,
    val savedAt: Long,
    val postedAt: Long?
)
