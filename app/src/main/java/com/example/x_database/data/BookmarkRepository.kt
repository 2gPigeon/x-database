package com.example.x_database.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class BookmarkRepository(
    private val context: Context,
    private val dao: BookmarkDao,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    fun observeBookmarks(): Flow<List<Bookmark>> = dao.observeAll()

    suspend fun deleteBookmark(bookmark: Bookmark): Boolean = withContext(Dispatchers.IO) {
        val deleted = dao.deleteById(bookmark.id) > 0
        if (deleted) {
            runCatching { File(bookmark.filePath).delete() }
        }
        deleted
    }

    suspend fun saveSharedImage(uri: Uri, sourceUrl: String? = null, tweetId: Long? = null, postedAt: Long? = null): Long {
        val filePath = withContext(Dispatchers.IO) {
            val extension = resolveExtension(context.contentResolver, uri)
            val outFile = createImageFile(context, extension)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open shared image stream")
            outFile.absolutePath
        }

        return dao.insert(
            Bookmark(
                tweetId = tweetId,
                filePath = filePath,
                sourceUrl = sourceUrl,
                savedAt = System.currentTimeMillis(),
                postedAt = postedAt
            )
        )
    }

    suspend fun downloadAndSaveImage(imageUrl: String, sourceUrl: String? = null, tweetId: Long? = null, postedAt: Long? = null): Long {
        val filePath = withContext(Dispatchers.IO) {
            val targetUrl = normalizeXImageUrl(imageUrl)
            val request = Request.Builder().url(targetUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Failed to download image: ${response.code}")
                }
                val extension = extensionFromUrl(targetUrl, response)
                val outFile = createImageFile(context, extension)
                response.body?.byteStream()?.use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Empty response body")
                outFile.absolutePath
            }
        }

        return dao.insert(
            Bookmark(
                tweetId = tweetId,
                filePath = filePath,
                sourceUrl = sourceUrl,
                savedAt = System.currentTimeMillis(),
                postedAt = postedAt
            )
        )
    }

    private fun createImageFile(context: Context, extension: String): File {
        val imageDir = File(context.filesDir, "images").apply { mkdirs() }
        return File(imageDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
    }

    private fun resolveExtension(contentResolver: ContentResolver, uri: Uri): String {
        val mime = contentResolver.getType(uri)
        val ext = mime?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
        return ext ?: "jpg"
    }

    private fun normalizeXImageUrl(url: String): String {
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
        if (!decoded.contains("pbs.twimg.com/media/")) {
            return decoded
        }

        val uri = Uri.parse(decoded)
        val formatFromQuery = uri.getQueryParameter("format")
        val formatFromPath = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{3,5}")) }
        val format = (formatFromQuery ?: formatFromPath)?.lowercase()

        val builder = uri.buildUpon().clearQuery()
        if (!format.isNullOrBlank()) {
            builder.appendQueryParameter("format", format)
        }
        builder.appendQueryParameter("name", "orig")
        return builder.build().toString()
    }

    private fun extensionFromUrl(url: String, response: Response): String {
        val decoded = URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
        val formatInQuery = Regex("""[?&]format=([a-zA-Z0-9]+)""")
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
        if (!formatInQuery.isNullOrBlank()) {
            return formatInQuery.lowercase()
        }

        val filename = decoded.substringAfterLast('/').substringBefore('?')
        val pathExtension = filename.substringAfterLast('.', "")
        if (pathExtension.isNotBlank() && pathExtension.length <= 5) {
            return pathExtension.lowercase()
        }

        val mimeSubtype = response.body?.contentType()?.subtype
        if (!mimeSubtype.isNullOrBlank()) {
            return mimeSubtype.lowercase()
        }

        return "jpg"
    }
}
