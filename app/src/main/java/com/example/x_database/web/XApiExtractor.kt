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
    val videoUrls: List<String>
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
                return@withContext XApiMediaResult(emptyList(), emptyList())
            }

            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) {
                return@withContext XApiMediaResult(emptyList(), emptyList())
            }

            val root = JSONObject(raw)
            val tweet = root.optJSONObject("tweet") ?: return@withContext XApiMediaResult(emptyList(), emptyList())
            val media = tweet.optJSONObject("media") ?: return@withContext XApiMediaResult(emptyList(), emptyList())

            val photos = parsePhotoUrls(media.optJSONArray("photos"))
            val videos = parseVideoUrls(media.optJSONArray("videos"))
            XApiMediaResult(photos, videos)
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

    private fun parseVideoUrls(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val direct = item.optString("url").ifBlank { item.optString("raw_url") }
            if (direct.isNotBlank()) {
                result += direct
                continue
            }
            val variants = item.optJSONArray("variants") ?: continue
            var bestUrl: String? = null
            var bestBitrate = -1
            for (j in 0 until variants.length()) {
                val variant = variants.optJSONObject(j) ?: continue
                val url = variant.optString("url")
                val contentType = variant.optString("content_type")
                val bitrate = variant.optInt("bitrate", 0)
                if (url.isBlank()) continue
                if (!contentType.contains("video/mp4")) {
                    continue
                }
                if (bitrate > bestBitrate) {
                    bestBitrate = bitrate
                    bestUrl = url
                }
            }
            if (!bestUrl.isNullOrBlank()) {
                result += bestUrl
            }
        }
        return result.toList()
    }
}
