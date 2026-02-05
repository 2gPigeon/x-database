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
    private val startDate = MutableStateFlow<LocalDate?>(null)
    private val endDate = MutableStateFlow<LocalDate?>(null)

    val selectedStartDate: StateFlow<LocalDate?> = startDate.asStateFlow()
    val selectedEndDate: StateFlow<LocalDate?> = endDate.asStateFlow()

    val bookmarks: StateFlow<List<Bookmark>> = combine(
        repository.observeBookmarks(),
        startDate,
        endDate
    ) { bookmarks, start, end ->
        val startMillis = start?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
        val endExclusiveMillis = end
            ?.plusDays(1)
            ?.atStartOfDay()
            ?.toInstant(ZoneOffset.UTC)
            ?.toEpochMilli()

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

    fun setStartDate(date: LocalDate?) {
        startDate.value = date
    }

    fun setEndDate(date: LocalDate?) {
        endDate.value = date
    }

    fun clearPostedDateFilter() {
        startDate.value = null
        endDate.value = null
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
