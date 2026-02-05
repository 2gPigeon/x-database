package com.example.x_database.util

import android.content.Intent
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SaveFailureLogger {
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

    suspend fun append(context: android.content.Context, intent: Intent, throwable: Throwable): File {
        return withContext(Dispatchers.IO + NonCancellable) {
            val logFile = getLogFile(context)

            val payload = JSONObject()
                .put("timeUtc", timestampFormatter.format(Instant.now()))
                .put("action", intent.action ?: "")
                .put("mimeType", intent.type ?: "")
                .put("sharedText", intent.getStringExtra(Intent.EXTRA_TEXT)?.take(1000) ?: "")
                .put(
                    "sharedUri",
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)?.toString()
                        ?: ""
                )
                .put("errorType", throwable::class.java.simpleName)
                .put("errorMessage", throwable.message ?: "")
                .put("stackTrace", throwable.stackTraceToString().take(4000))

            logFile.appendText(payload.toString() + System.lineSeparator())
            logFile
        }
    }

    suspend fun appendEvent(
        context: android.content.Context,
        intent: Intent,
        status: String,
        message: String,
        extra: Map<String, String> = emptyMap()
    ): File {
        return withContext(Dispatchers.IO + NonCancellable) {
            val logFile = getLogFile(context)
            val payload = JSONObject()
                .put("timeUtc", timestampFormatter.format(Instant.now()))
                .put("status", status)
                .put("message", message)
                .put("action", intent.action ?: "")
                .put("mimeType", intent.type ?: "")
                .put("sharedText", intent.getStringExtra(Intent.EXTRA_TEXT)?.take(1000) ?: "")
                .put(
                    "sharedUri",
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)?.toString()
                        ?: ""
                )
            extra.forEach { (k, v) -> payload.put(k, v) }
            logFile.appendText(payload.toString() + System.lineSeparator())
            logFile
        }
    }

    fun getLogFile(context: android.content.Context): File {
        val logDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").apply { mkdirs() }
        return File(logDir, "save_failures.log")
    }

    suspend fun readLatest(context: android.content.Context, maxChars: Int = 12_000): String {
        return withContext(Dispatchers.IO) {
            val logFile = getLogFile(context)
            if (!logFile.exists()) {
                return@withContext "ログはまだありません。"
            }
            val content = logFile.readText()
            if (content.length <= maxChars) {
                content
            } else {
                "...(省略)\n" + content.takeLast(maxChars)
            }
        }
    }
}
