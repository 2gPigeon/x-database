package com.example.x_database

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity

class WebViewDebugActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var inputUrl: EditText
    private lateinit var loadButton: Button
    private lateinit var statusText: TextView
    private lateinit var currentUrlText: TextView
    private lateinit var canonicalText: TextView
    private lateinit var locationText: TextView
    private lateinit var ogText: TextView
    private lateinit var twitterCreatorText: TextView
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview_debug)

        webView = findViewById(R.id.webview)
        inputUrl = findViewById(R.id.input_url)
        loadButton = findViewById(R.id.btn_load)
        statusText = findViewById(R.id.text_status)
        currentUrlText = findViewById(R.id.text_current_url)
        canonicalText = findViewById(R.id.text_canonical_url)
        locationText = findViewById(R.id.text_location_url)
        ogText = findViewById(R.id.text_og_url)
        twitterCreatorText = findViewById(R.id.text_twitter_creator)
        progressBar = findViewById(R.id.progress_loading)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
            userAgentString =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                updateStatus("Loading...")
                updateCurrentUrl(request?.url?.toString().orEmpty())
                scheduleTimeout()
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                updateCurrentUrl(url.orEmpty())
                updateStatus("Finished")
                cancelTimeout()
                fetchCanonicalAndMeta()
                handler.postDelayed({ fetchCanonicalAndMeta() }, 2000)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                updateStatus("Error: ${error?.description ?: "unknown"}")
                cancelTimeout()
            }
        }

        loadButton.setOnClickListener {
            val url = inputUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                clearMeta()
                updateStatus("Loading...")
                scheduleTimeout()
                webView.loadUrl(url)
            }
        }
    }

    override fun onDestroy() {
        cancelTimeout()
        webView.destroy()
        super.onDestroy()
    }

    private fun fetchCanonicalAndMeta() {
        webView.evaluateJavascript(
            """
                (function() {
                  var link = document.querySelector('link[rel=canonical]');
                  return (link && link.href) ? link.href : '';
                })();
            """.trimIndent()
        ) { result ->
            updateCanonical(result.trim('"'))
        }
        webView.evaluateJavascript(
            """
                (function() { return window.location.href || ''; })();
            """.trimIndent()
        ) { result ->
            updateLocation(result.trim('"'))
        }
        webView.evaluateJavascript(
            """
                (function() {
                  var meta = document.querySelector('meta[property="og:url"]');
                  return meta && meta.content ? meta.content : '';
                })();
            """.trimIndent()
        ) { result ->
            updateOgUrl(result.trim('"'))
        }
        webView.evaluateJavascript(
            """
                (function() {
                  var meta = document.querySelector('meta[name="twitter:creator"]');
                  return meta && meta.content ? meta.content : '';
                })();
            """.trimIndent()
        ) { result ->
            updateTwitterCreator(result.trim('"'))
        }
    }

    private fun scheduleTimeout() {
        cancelTimeout()
        timeoutRunnable = Runnable {
            updateStatus("Timeout: loading too long")
            webView.stopLoading()
        }
        handler.postDelayed(timeoutRunnable!!, 20000)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun clearMeta() {
        updateCanonical("")
        updateLocation("")
        updateOgUrl("")
        updateTwitterCreator("")
    }

    private fun updateStatus(text: String) {
        statusText.text = "Status: $text"
    }

    private fun updateCurrentUrl(text: String) {
        currentUrlText.text = "Current URL: $text"
    }

    private fun updateCanonical(text: String) {
        canonicalText.text = "Canonical URL: $text"
    }

    private fun updateLocation(text: String) {
        locationText.text = "Location href: $text"
    }

    private fun updateOgUrl(text: String) {
        ogText.text = "OG URL: $text"
    }

    private fun updateTwitterCreator(text: String) {
        twitterCreatorText.text = "Twitter Creator: $text"
    }
}
