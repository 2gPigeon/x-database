package com.example.x_database

import com.example.x_database.util.extractTweetId
import com.example.x_database.util.tweetIdToPostedAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun extract_tweet_id_from_x_url() {
        val tweetId = extractTweetId("https://x.com/user/status/1933136925198545287")
        assertEquals(1933136925198545287L, tweetId)
    }

    @Test
    fun extract_tweet_id_from_i_status_url() {
        val tweetId = extractTweetId("https://x.com/i/status/2019398660367479089")
        assertEquals(2019398660367479089L, tweetId)
    }

    @Test
    fun extract_tweet_id_returns_null_for_non_status_url() {
        val tweetId = extractTweetId("https://x.com/home")
        assertNull(tweetId)
    }

    @Test
    fun convert_tweet_id_to_timestamp() {
        val tweetId = 1933136925198545287L
        val postedAt = tweetIdToPostedAt(tweetId)
        assertEquals(1749730733571L, postedAt)
    }
}
