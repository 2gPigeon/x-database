package com.example.x_database

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.example.x_database.data.AppDatabase
import com.example.x_database.data.BookmarkRepository
import com.example.x_database.util.SaveFailureLogger
import com.example.x_database.util.extractTweetId
import com.example.x_database.util.tweetIdToPostedAt
import com.example.x_database.web.XAuthorResolver
import com.example.x_database.web.XUrlResolver
import com.example.x_database.web.XApiExtractor
import com.example.x_database.web.XImageFallbackExtractor
import com.example.x_database.web.XImageScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

class ShareReceiverActivity : ComponentActivity() {
    private val repository by lazy {
        BookmarkRepository(
            applicationContext,
            AppDatabase.getInstance(applicationContext).bookmarkDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do not intercept touch/scroll on the underlying app while saving.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        val incomingIntent = intent
        lifecycleScope.launch {
            runCatching {
                SaveFailureLogger.appendEvent(
                    context = applicationContext,
                    intent = incomingIntent,
                    status = "START",
                    message = "Share receiver started"
                )
            }

            val result = runCatching {
                withTimeout(30_000) {
                    handleShareIntent(incomingIntent)
                }
            }
            val message = if (result.isSuccess) {
                runCatching {
                    SaveFailureLogger.appendEvent(
                        context = applicationContext,
                        intent = incomingIntent,
                        status = "SUCCESS",
                        message = "Saved successfully"
                    )
                }
                "Saved successfully"
            } else {
                val cause = result.exceptionOrNull() ?: IllegalStateException("unknown error")
                runCatching {
                    SaveFailureLogger.appendEvent(
                        context = applicationContext,
                        intent = incomingIntent,
                        status = "FAIL",
                        message = cause.message ?: "unknown error",
                        extra = mapOf("errorType" to cause::class.java.simpleName)
                    )
                }
                val logSuffix = runCatching {
                    val logFile = SaveFailureLogger.append(applicationContext, incomingIntent, cause)
                    " (logged: ${logFile.name})"
                }.getOrDefault(" (failed to write log)")
                "Save failed: ${cause.message ?: "unknown error"}$logSuffix"
            }
            Toast.makeText(this@ShareReceiverActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private suspend fun handleShareIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> handleSingleShare(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleShare(intent)
            else -> error("Unsupported action")
        }
    }

    private suspend fun handleSingleShare(intent: Intent) {
        val imageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (imageUri != null) {
            repository.saveSharedImage(imageUri)
            return
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val tweetUrl = extractXUrl(sharedText) ?: error("X URL not found in shared text")
        val tweetId = extractTweetId(tweetUrl)
        val postedAt = tweetId?.let(::tweetIdToPostedAt)
        val debug = mutableListOf<String>()
        var authorUsername: String? = null

        if (tweetId != null) {
            authorUsername = XAuthorResolver.resolveFromTweetId(tweetId)
        }

        // A) Try canonical URL via HTTP (works when i/status expands).
        if (authorUsername.isNullOrBlank()) {
            authorUsername = XUrlResolver.resolveUsernameFromCanonical(tweetUrl)
        }

        val apiUrls = if (tweetId != null) {
            val apiMedia = XApiExtractor.extractMedia(tweetId)
            debug += "apiPhotoCount=${apiMedia.photoUrls.size}"
            debug += "apiHasVideo=${apiMedia.hasVideo}"
            if (apiMedia.photoUrls.isEmpty() && apiMedia.hasVideo) {
                error("This post has video/GIF but no photos. ${debug.joinToString(", ")}")
            }
            apiMedia.photoUrls
        } else {
            debug += "tweetIdMissing=true"
            emptyList()
        }

        val fallbackUrls = if (apiUrls.isEmpty()) {
            XImageFallbackExtractor.extract(tweetUrl, sharedText).also {
                debug += "fallbackCount=${it.size}"
            }
        } else {
            emptyList()
        }

        val webViewResult = if (authorUsername.isNullOrBlank() || (apiUrls.isEmpty() && fallbackUrls.isEmpty())) {
            runCatching {
                withTimeout(6_000) {
                    XImageScraper.extract(this@ShareReceiverActivity, tweetUrl)
                }
            }.getOrNull()
        } else {
            null
        }
        if (authorUsername.isNullOrBlank() && !webViewResult?.canonicalUrl.isNullOrBlank()) {
            authorUsername = XUrlResolver.resolveUsernameFromCanonical(webViewResult!!.canonicalUrl!!)
        }
        val webViewUrls = if (apiUrls.isEmpty() && fallbackUrls.isEmpty()) {
            val urls = webViewResult?.imageUrls.orEmpty()
            debug += "webViewCount=${urls.size}"
            urls
        } else {
            emptyList()
        }

        val imageUrls = (apiUrls + fallbackUrls + webViewUrls).distinct()

        if (imageUrls.isEmpty()) {
            runCatching {
                SaveFailureLogger.appendEvent(
                    context = applicationContext,
                    intent = intent,
                    status = "EMPTY_RESULT",
                    message = "No image URL extracted",
                    extra = mapOf("debug" to debug.joinToString(", "))
                )
            }
            error("No image URL extracted. ${debug.joinToString(", ")}")
        }

        withContext(Dispatchers.IO) {
            imageUrls.distinct().forEach { imageUrl ->
                repository.downloadAndSaveImage(
                    imageUrl = imageUrl,
                    sourceUrl = tweetUrl,
                    tweetId = tweetId,
                    postedAt = postedAt,
                    authorUsername = authorUsername
                )
            }
        }
    }

    private suspend fun handleMultipleShare(intent: Intent) {
        val streams = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        if (streams.isEmpty()) {
            error("No shared images found")
        }
        streams.forEach { uri ->
            repository.saveSharedImage(uri)
        }
    }

    private fun extractXUrl(text: String): String? {
        val regex = Regex("""https://(?:x|twitter)\.com/[^\s]+""")
        return regex.find(text)?.value
    }
}
