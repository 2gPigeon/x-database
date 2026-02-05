package com.example.x_database.web

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object XImageScraper {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractImageUrls(context: Context, tweetUrl: String): List<String> {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context)
                CookieManager.getInstance().setAcceptCookie(true)

                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.webChromeClient = WebChromeClient()
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val script = """
                            (function() {
                              var images = Array.from(document.querySelectorAll('img'))
                                .map(function(img) { return img.src || ''; })
                                .filter(function(src) { return src.indexOf('pbs.twimg.com/media/') >= 0; })
                                .concat(
                                  Array.from(document.querySelectorAll('source'))
                                    .map(function(source) { return source.src || ''; })
                                    .filter(function(src) { return src.indexOf('pbs.twimg.com/media/') >= 0; })
                                );
                              return Array.from(new Set(images));
                            })();
                        """.trimIndent()

                        Handler(Looper.getMainLooper()).postDelayed({
                            webView.evaluateJavascript(script) { result ->
                                try {
                                    val parsed = parseJsonArray(result)
                                    if (!continuation.isCompleted) {
                                        continuation.resume(parsed)
                                    }
                                } catch (error: Throwable) {
                                    if (!continuation.isCompleted) {
                                        continuation.resumeWithException(error)
                                    }
                                } finally {
                                    webView.destroy()
                                }
                            }
                        }, 1500)
                    }
                }

                continuation.invokeOnCancellation {
                    webView.destroy()
                }

                webView.loadUrl(tweetUrl)
            }
        }
    }

    private fun parseJsonArray(raw: String): List<String> {
        val json = JSONArray(raw)
        return buildList(json.length()) {
            for (index in 0 until json.length()) {
                val url = json.optString(index)
                if (url.isNotBlank()) {
                    add(url)
                }
            }
        }
    }
}
