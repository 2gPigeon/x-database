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
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class XWebScrapeResult(
    val imageUrls: List<String>,
    val canonicalUrl: String?
)

object XImageScraper {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(
        context: Context,
        tweetUrl: String,
        maxWaitMs: Long = 6000L
    ): XWebScrapeResult {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context)
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)

                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.loadsImagesAutomatically = false
                webView.settings.blockNetworkImage = true
                webView.settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
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
                              var canonical = '';
                              var link = document.querySelector('link[rel=canonical]');
                              if (link && link.href) { canonical = link.href; }
                              var og = '';
                              var meta = document.querySelector('meta[property="og:url"]');
                              if (meta && meta.content) { og = meta.content; }
                              var locationHref = '';
                              if (window.location && window.location.href) { locationHref = window.location.href; }
                              if (!canonical && og) { canonical = og; }
                              if (!canonical && locationHref) { canonical = locationHref; }
                              return JSON.stringify({
                                images: Array.from(new Set(images)),
                                canonical: canonical || '',
                                ogUrl: og || '',
                                locationUrl: locationHref || ''
                              });
                            })();
                        """.trimIndent()

                        val handler = Handler(Looper.getMainLooper())
                        val startTime = System.currentTimeMillis()
                        val maxWaitMsLocal = maxWaitMs

                        fun shouldAccept(canonical: String?): Boolean {
                            if (canonical.isNullOrBlank()) return false
                            return !canonical.contains("/i/status/")
                        }

                        fun evaluateLoop(delayMs: Long) {
                            handler.postDelayed({
                                webView.evaluateJavascript(script) { result ->
                                    try {
                                        val parsed = parseJsonResult(result)
                                        val canonical = parsed.canonicalUrl
                                        if (!continuation.isCompleted) {
                                            val elapsed = System.currentTimeMillis() - startTime
                                            if (shouldAccept(canonical) || elapsed >= maxWaitMsLocal) {
                                                continuation.resume(parsed)
                                            } else {
                                                evaluateLoop(1500)
                                            }
                                        }
                                    } catch (error: Throwable) {
                                        if (!continuation.isCompleted) {
                                            continuation.resumeWithException(error)
                                        }
                                    } finally {
                                        if (continuation.isCompleted) {
                                            webView.destroy()
                                        }
                                    }
                                }
                            }, delayMs)
                        }

                        evaluateLoop(1500)
                    }
                }

                continuation.invokeOnCancellation {
                    webView.destroy()
                }

                webView.loadUrl(tweetUrl)
            }
        }
    }

    private fun parseJsonResult(raw: String): XWebScrapeResult {
        if (raw.isBlank() || raw == "null") {
            return XWebScrapeResult(emptyList(), null)
        }
        val cleaned = if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.substring(1, raw.length - 1).replace("\\\\", "\\").replace("\\\"", "\"")
        } else {
            raw
        }
        val json = JSONObject(cleaned)
        val imagesArray = json.optJSONArray("images") ?: JSONArray()
        val images = buildList(imagesArray.length()) {
            for (index in 0 until imagesArray.length()) {
                val url = imagesArray.optString(index)
                if (url.isNotBlank()) {
                    add(url)
                }
            }
        }
        val canonical = json.optString("canonical")
            .ifBlank { json.optString("ogUrl") }
            .ifBlank { json.optString("locationUrl") }
            .ifBlank { null }
        return XWebScrapeResult(images, canonical)
    }
}
