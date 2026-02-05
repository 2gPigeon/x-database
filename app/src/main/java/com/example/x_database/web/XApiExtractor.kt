package com.example.x_database.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class XApiMediaResult(
    val photoUrls: List<String>,
    val hasVideo: Boolean
)

object XApiExtractor {
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun extractMedia(tweetId: Long): XApiMediaResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.fxtwitter.com/i/status/$tweetId")
            .header("User-Agent", "x-database/1.0")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext XApiMediaResult(emptyList(), hasVideo = false)
            }

            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) {
                return@withContext XApiMediaResult(emptyList(), hasVideo = false)
            }

            val root = JSONObject(raw)
            val tweet = root.optJSONObject("tweet") ?: return@withContext XApiMediaResult(emptyList(), false)
            val media = tweet.optJSONObject("media") ?: return@withContext XApiMediaResult(emptyList(), false)

            val photos = parsePhotoUrls(media.optJSONArray("photos"))
            val hasVideo = media.has("videos") && (media.optJSONArray("videos")?.length() ?: 0) > 0
            XApiMediaResult(photos, hasVideo)
        }
    }

    private fun parsePhotoUrls(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val url = item.optString("url").ifBlank { item.optString("raw_url") }
            if (url.isNotBlank()) {
                result += url
            }
        }
        return result.toList()
    }
}
