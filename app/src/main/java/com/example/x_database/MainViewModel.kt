package com.example.x_database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.x_database.data.Bookmark
import com.example.x_database.data.BookmarkRepository
import com.example.x_database.web.XImageScraper
import com.example.x_database.web.XUrlResolver
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import android.content.Context
import com.example.x_database.util.SaveFailureLogger

class MainViewModel(
    private val repository: BookmarkRepository
) : ViewModel() {
    val bookmarks: StateFlow<List<Bookmark>> = repository.observeBookmarks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    fun refreshUnknownAuthors() {
        viewModelScope.launch {
            repository.refreshUnknownAuthors()
        }
    }

    fun refreshUnknownAuthorsWithWebView(context: Context) {
        viewModelScope.launch {
            repository.refreshUnknownAuthors { bookmark ->
                val url = bookmark.sourceUrl ?: bookmark.tweetId?.let { "https://x.com/i/status/$it" } ?: return@refreshUnknownAuthors null
                val resolved = runCatching {
                    val result = XImageScraper.extract(context, url)
                    val canonical = result.canonicalUrl ?: return@runCatching null
                    XUrlResolver.resolveUsernameFromCanonical(canonical)
                }.getOrNull()
                if (!resolved.isNullOrBlank()) {
                    runCatching {
                        SaveFailureLogger.appendEvent(
                            context = context,
                            intent = android.content.Intent("AUTHOR_REFRESH"),
                            status = "AUTHOR_REFRESH_OK",
                            message = resolved,
                            extra = mapOf("url" to url)
                        )
                    }
                } else {
                    runCatching {
                        SaveFailureLogger.appendEvent(
                            context = context,
                            intent = android.content.Intent("AUTHOR_REFRESH"),
                            status = "AUTHOR_REFRESH_FAIL",
                            message = "Unresolved",
                            extra = mapOf("url" to url)
                        )
                    }
                }
                delay(300)
                resolved
            }
        }
    }

    fun refreshExpandedUrlsWithWebView(context: Context) {
        viewModelScope.launch {
            expandUrlsWithWebView(context)
        }
    }

    fun refreshAuthorsFromSourceUrls() {
        viewModelScope.launch {
            repository.refreshAuthorsFromSourceUrls()
        }
    }

    fun syncUrlsAndAuthors(context: Context) {
        viewModelScope.launch {
            expandUrlsWithWebView(context)
            repository.refreshAuthorsFromSourceUrls()
        }
    }

    private suspend fun expandUrlsWithWebView(context: Context) {
        repository.refreshExpandedSourceUrls { bookmark ->
            val url = bookmark.sourceUrl ?: bookmark.tweetId?.let { "https://x.com/i/status/$it" } ?: return@refreshExpandedSourceUrls null
            val canonical = runCatching {
                val result = XImageScraper.extract(context, url)
                result.canonicalUrl
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (!canonical.isNullOrBlank()) {
                runCatching {
                    SaveFailureLogger.appendEvent(
                        context = context,
                        intent = android.content.Intent("URL_EXPAND"),
                        status = "URL_EXPAND_OK",
                        message = canonical,
                        extra = mapOf("url" to url)
                    )
                }
            } else {
                runCatching {
                    SaveFailureLogger.appendEvent(
                        context = context,
                        intent = android.content.Intent("URL_EXPAND"),
                        status = "URL_EXPAND_FAIL",
                        message = "Unresolved",
                        extra = mapOf("url" to url)
                    )
                }
            }
            delay(300)
            canonical
        }
    }
}

class MainViewModelFactory(
    private val repository: BookmarkRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
