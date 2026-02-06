package com.example.x_database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.x_database.data.Bookmark
import com.example.x_database.data.BookmarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneOffset

class MainViewModel(
    private val repository: BookmarkRepository
) : ViewModel() {
    enum class PostDateFilter(val label: String) {
        ALL("All"),
        YEAR_2024("2024"),
        YEAR_2025("2025")
    }

    private val selectedFilter = MutableStateFlow(PostDateFilter.ALL)
    val activeFilter: StateFlow<PostDateFilter> = selectedFilter.asStateFlow()

    val bookmarks: StateFlow<List<Bookmark>> = combine(
        repository.observeBookmarks(),
        selectedFilter
    ) { bookmarks, filter ->
        val (start, end) = when (filter) {
            PostDateFilter.ALL -> null to null
            PostDateFilter.YEAR_2024 -> LocalDate.of(2024, 1, 1) to LocalDate.of(2024, 12, 31)
            PostDateFilter.YEAR_2025 -> LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 12, 31)
        }
        val startMillis = start?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
        val endExclusiveMillis = end?.plusDays(1)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

        bookmarks.filter { bookmark ->
            val postedAt = bookmark.postedAt ?: return@filter startMillis == null && endExclusiveMillis == null
            val matchesStart = startMillis == null || postedAt >= startMillis
            val matchesEnd = endExclusiveMillis == null || postedAt < endExclusiveMillis
            matchesStart && matchesEnd
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun setFilter(filter: PostDateFilter) {
        selectedFilter.value = filter
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
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
