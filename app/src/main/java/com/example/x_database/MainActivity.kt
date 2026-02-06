package com.example.x_database

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.x_database.data.AppDatabase
import com.example.x_database.data.Bookmark
import com.example.x_database.data.BookmarkRepository
import com.example.x_database.ui.theme.XdatabaseTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val repository = BookmarkRepository(
            applicationContext,
            AppDatabase.getInstance(applicationContext).bookmarkDao()
        )
        MainViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XdatabaseTheme {
                val bookmarks = viewModel.bookmarks.collectAsStateWithLifecycle()
                val selectedFilter = viewModel.activeFilter.collectAsStateWithLifecycle()
                BookmarkGallery(
                    bookmarks = bookmarks.value,
                    onDeleteBookmark = viewModel::deleteBookmark,
                    selectedFilter = selectedFilter.value,
                    onSelectFilter = viewModel::setFilter
                )
            }
        }
    }
}

@Composable
private fun BookmarkGallery(
    bookmarks: List<Bookmark>,
    onDeleteBookmark: (Bookmark) -> Unit,
    selectedFilter: MainViewModel.PostDateFilter,
    onSelectFilter: (MainViewModel.PostDateFilter) -> Unit
) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val imageDirPath = remember(context) { File(context.filesDir, "images").absolutePath }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Saved Images",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "${bookmarks.size} items",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Filter by post date",
                    style = MaterialTheme.typography.titleSmall
                )
                PostDateFilterDropdown(
                    selectedFilter = selectedFilter,
                    onSelectFilter = onSelectFilter
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Image folder: $imageDirPath", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedFilter != MainViewModel.PostDateFilter.ALL) {
                        "No images in selected post date filter."
                    } else {
                        "Share an image from X to save it here."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    ImageCard(
                        bookmark = bookmark,
                        onClick = {
                            val index = bookmarks.indexOfFirst { it.id == bookmark.id }
                            if (index >= 0) {
                                expandedIndex = index
                            }
                        }
                    )
                }
            }
        }
    }

    expandedIndex?.let { index ->
        ZoomablePagerDialog(
            bookmarks = bookmarks,
            initialIndex = index,
            onDismiss = { expandedIndex = null },
            onDeleteBookmark = onDeleteBookmark
        )
    }
}

@Composable
private fun ImageCard(bookmark: Bookmark, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AsyncImage(
            model = File(bookmark.filePath),
            contentDescription = bookmark.sourceUrl ?: "saved image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ZoomablePagerDialog(
    bookmarks: List<Bookmark>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, (bookmarks.size - 1).coerceAtLeast(0)),
            pageCount = { bookmarks.size }
        )
        var pendingPage by remember { mutableStateOf<Int?>(null) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (bookmarks.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val bookmark = bookmarks[page]
                    ZoomableImagePage(bookmark = bookmark)
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val total = bookmarks.size
                val currentIndex = pagerState.currentPage.coerceIn(0, (total - 1).coerceAtLeast(0))
                Button(
                    onClick = {
                        if (total == 0) return@Button
                        val target = if (currentIndex >= bookmarks.lastIndex) {
                            (currentIndex - 1).coerceAtLeast(0)
                        } else {
                            currentIndex
                        }
                        onDeleteBookmark(bookmarks[currentIndex])
                        if (bookmarks.size <= 1) {
                            onDismiss()
                        } else {
                            pendingPage = target
                        }
                    }
                ) {
                    Text(text = "Delete")
                }
                Button(onClick = onDismiss) {
                    Text(text = "Close")
                }
                Text(
                    text = if (total == 0) "0/0" else "${currentIndex + 1}/$total",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        pendingPage?.let { target ->
            LaunchedEffect(target, bookmarks.size) {
                if (bookmarks.isNotEmpty()) {
                    pagerState.scrollToPage(target.coerceIn(0, bookmarks.lastIndex))
                }
                pendingPage = null
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(bookmark: Bookmark) {
    var scale by remember(bookmark.id) { mutableStateOf(1f) }
    var offset by remember(bookmark.id) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        if (scale <= 1f) {
            offset = Offset.Zero
        } else {
            offset += panChange
        }
    }

    AsyncImage(
        model = File(bookmark.filePath),
        contentDescription = bookmark.sourceUrl ?: "saved image",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (scale > 1f) {
                    Modifier.transformable(state = transformState)
                } else {
                    Modifier
                }
            )
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    )
}

@Composable
private fun PostDateFilterDropdown(
    selectedFilter: MainViewModel.PostDateFilter,
    onSelectFilter: (MainViewModel.PostDateFilter) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember { MainViewModel.PostDateFilter.values().toList() }

    Box {
        Button(onClick = { expanded = true }) {
            Text(selectedFilter.label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelectFilter(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
