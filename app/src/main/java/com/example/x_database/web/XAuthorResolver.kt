package com.example.x_database.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object XAuthorResolver {
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun resolveFromTweetId(tweetId: Long): String? = withContext(Dispatchers.IO) {
        val url = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId"
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) return@withContext null
            val root = JSONObject(raw)
            val user = root.optJSONObject("user") ?: return@withContext null
            val candidate = user.optString("screen_name")
                .ifBlank { user.optString("username") }
                .ifBlank { user.optString("user_name") }
                .ifBlank { user.optString("handle") }
            candidate.takeIf { it.isNotBlank() && it != "i" }
        }
    }
}
