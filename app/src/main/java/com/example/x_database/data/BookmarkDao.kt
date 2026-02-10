package com.example.x_database.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteById(bookmarkId: Long): Int

    @Query(
        "SELECT * FROM bookmarks " +
            "WHERE authorUsername IS NULL OR authorUsername = '' " +
            "OR lower(authorUsername) = 'unknown'"
    )
    suspend fun findUnknownAuthors(): List<Bookmark>

    @Query("UPDATE bookmarks SET authorUsername = :authorUsername WHERE id = :bookmarkId")
    suspend fun updateAuthorUsername(bookmarkId: Long, authorUsername: String)

    @Query("SELECT * FROM bookmarks")
    suspend fun findAll(): List<Bookmark>

    @Query(
        "SELECT * FROM bookmarks " +
            "WHERE (sourceUrl LIKE '%/i/status/%' OR sourceUrl IS NULL) " +
            "AND tweetId IS NOT NULL " +
            "AND (authorUsername IS NULL OR authorUsername = '' OR lower(authorUsername) = 'unknown')"
    )
    suspend fun findUnexpandedSourceUrls(): List<Bookmark>

    @Query("UPDATE bookmarks SET sourceUrl = :sourceUrl WHERE id = :bookmarkId")
    suspend fun updateSourceUrl(bookmarkId: Long, sourceUrl: String)
}
