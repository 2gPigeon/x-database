package com.example.x_database.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object XUrlResolver {
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun resolveUsernameFromCanonical(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        parseUsernameFromUrl(url)?.let { return@withContext it }

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            parseUsernameFromUrl(response.request.url.toString())?.let { return@withContext it }
            parseUsernameFromUrl(response.header("Location").orEmpty())?.let { return@withContext it }
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) return@withContext null
            extractCanonicalUsername(html)
        }
    }

    fun extractUsernameFromUrl(url: String): String? {
        return parseUsernameFromUrl(url)
    }

    private fun extractCanonicalUsername(html: String): String? {
        val regex = Regex("""<link[^>]*rel=["']canonical["'][^>]*href=["']([^"']+)["']""")
        val match = regex.find(html) ?: return null
        val href = match.groupValues.getOrNull(1) ?: return null
        return parseUsernameFromUrl(href)
    }

    private fun parseUsernameFromUrl(url: String): String? {
        if (url.isBlank()) return null
        val userRegex = Regex("""https?://(?:x|twitter)\.com/([A-Za-z0-9_]+)/status/\d+""")
        val userMatch = userRegex.find(url) ?: return null
        val user = userMatch.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return if (user == "i") null else user
    }
}
