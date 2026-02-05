package com.example.x_database.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object XImageFallbackExtractor {
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
    private val mediaRegex = Regex("""https?:\\/\\/pbs\.twimg\.com\\/media\\/[A-Za-z0-9_.-]+(?:\\?[^"' <\\]*)?""")
    private val mediaRegexPlain = Regex("""https?://pbs\.twimg\.com/media/[A-Za-z0-9_.-]+(?:\?[^"' <\\]*)?""")

    suspend fun extract(tweetUrl: String, sharedText: String): List<String> = withContext(Dispatchers.IO) {
        val result = linkedSetOf<String>()
        result += findInText(sharedText)
        result += findInHtml(fetch(tweetUrl))

        // fxtwitter mirrors usually keep media URLs in static HTML and are easier to parse.
        val fxUrl = tweetUrl
            .replace("https://x.com/", "https://fxtwitter.com/")
            .replace("https://twitter.com/", "https://fxtwitter.com/")
        if (fxUrl != tweetUrl) {
            result += findInHtml(fetch(fxUrl))
        }
        result.toList()
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@use ""
            }
            response.body?.string().orEmpty()
        }
    }

    private fun findInText(text: String): List<String> {
        return mediaRegexPlain.findAll(text)
            .map { normalize(it.value) }
            .distinct()
            .toList()
    }

    private fun findInHtml(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val escaped = mediaRegex.findAll(html).map { it.value.replace("\\/", "/") }
        val plain = mediaRegexPlain.findAll(html).map { it.value }
        return (escaped + plain)
            .map(::normalize)
            .distinct()
            .toList()
    }

    private fun normalize(url: String): String {
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
        return decoded.replace("\\u0026", "&")
    }
}
