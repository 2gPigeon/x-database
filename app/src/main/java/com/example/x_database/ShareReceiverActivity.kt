package com.example.x_database

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import com.example.x_database.data.AppDatabase
import com.example.x_database.data.BookmarkRepository
import com.example.x_database.util.SaveFailureLogger
import com.example.x_database.util.extractTweetId
import com.example.x_database.util.tweetIdToPostedAt
import com.example.x_database.web.XApiExtractor
import com.example.x_database.web.XAuthorResolver
import com.example.x_database.web.XImageFallbackExtractor
import com.example.x_database.web.XImageScraper
import com.example.x_database.web.XUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

class ShareReceiverActivity : ComponentActivity() {
    private val repository by lazy {
        BookmarkRepository(
            applicationContext,
            AppDatabase.getInstance(applicationContext).bookmarkDao()
        )
    }

    private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Do not intercept touch/scroll on the underlying app while saving.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        val incomingIntent = intent
        if (!ShareSaveGate.tryAcquire(applicationContext)) {
            Toast.makeText(this, "保存中のため共有をスキップしました", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val appContext = applicationContext
        shareScope.launch {
            processShare(appContext, incomingIntent, repository)
        }
        finish()
    }
}

private suspend fun processShare(
    context: Context,
    incomingIntent: Intent,
    repository: BookmarkRepository
) {
    try {
        runCatching {
            SaveFailureLogger.appendEvent(
                context = context,
                intent = incomingIntent,
                status = "START",
                message = "Share receiver started"
            )
        }

        val result = runCatching {
            withTimeout(30_000) {
                handleShareIntent(context, repository, incomingIntent)
            }
        }
        val message = if (result.isSuccess) {
            runCatching {
                SaveFailureLogger.appendEvent(
                    context = context,
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
                    context = context,
                    intent = incomingIntent,
                    status = "FAIL",
                    message = cause.message ?: "unknown error",
                    extra = mapOf("errorType" to cause::class.java.simpleName)
                )
            }
            val logSuffix = runCatching {
                val logFile = SaveFailureLogger.append(context, incomingIntent, cause)
                " (logged: ${logFile.name})"
            }.getOrDefault(" (failed to write log)")
            "Save failed: ${cause.message ?: "unknown error"}$logSuffix"
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    } finally {
        ShareSaveGate.release(context)
    }
}

private suspend fun handleShareIntent(
    context: Context,
    repository: BookmarkRepository,
    intent: Intent
) {
    when (intent.action) {
        Intent.ACTION_SEND -> handleSingleShare(context, repository, intent)
        Intent.ACTION_SEND_MULTIPLE -> handleMultipleShare(repository, intent)
        else -> error("Unsupported action")
    }
}

private suspend fun handleSingleShare(
    context: Context,
    repository: BookmarkRepository,
    intent: Intent
) {
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

    if (authorUsername.isNullOrBlank()) {
        authorUsername = XUrlResolver.resolveUsernameFromCanonical(tweetUrl)
    }

    val apiResult = if (tweetId != null) {
        val apiMedia = XApiExtractor.extractMedia(tweetId)
        debug += "apiPhotoCount=${apiMedia.photoUrls.size}"
        debug += "apiVideoCount=${apiMedia.videoUrls.size}"
        apiMedia
    } else {
        debug += "tweetIdMissing=true"
        com.example.x_database.web.XApiMediaResult(emptyList(), emptyList())
    }
    val apiUrls = apiResult.photoUrls
    val apiVideoUrls = apiResult.videoUrls

    val fallbackUrls = if (apiUrls.isEmpty()) {
        XImageFallbackExtractor.extract(tweetUrl, sharedText).also {
            debug += "fallbackCount=${it.size}"
        }
    } else {
        emptyList()
    }

    val webViewResult = if (authorUsername.isNullOrBlank() || (apiUrls.isEmpty() && fallbackUrls.isEmpty() && apiVideoUrls.isEmpty())) {
        runCatching {
            withTimeout(6_000) {
                XImageScraper.extract(context, tweetUrl, maxWaitMs = 3000L)
            }
        }.getOrNull()
    } else {
        null
    }
    if (authorUsername.isNullOrBlank() && !webViewResult?.canonicalUrl.isNullOrBlank()) {
        authorUsername = XUrlResolver.resolveUsernameFromCanonical(webViewResult!!.canonicalUrl!!)
    }
    val webViewUrls = if (apiUrls.isEmpty() && fallbackUrls.isEmpty() && apiVideoUrls.isEmpty()) {
        val urls = webViewResult?.imageUrls.orEmpty()
        debug += "webViewCount=${urls.size}"
        urls
    } else {
        emptyList()
    }

    val imageUrls = (apiUrls + fallbackUrls + webViewUrls + apiVideoUrls).distinct()

    if (imageUrls.isEmpty()) {
        runCatching {
            SaveFailureLogger.appendEvent(
                context = context,
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
            val result = runCatching {
                withTimeout(30_000) {
                    repository.downloadAndSaveImage(
                        imageUrl = imageUrl,
                        sourceUrl = tweetUrl,
                        tweetId = tweetId,
                        postedAt = postedAt,
                        authorUsername = authorUsername
                    )
                }
            }
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: IllegalStateException("download failed")
            }
        }
    }
}

private suspend fun handleMultipleShare(
    repository: BookmarkRepository,
    intent: Intent
) {
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
