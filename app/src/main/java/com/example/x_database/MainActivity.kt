package com.example.x_database

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
                val startDate = viewModel.selectedStartDate.collectAsStateWithLifecycle()
                val endDate = viewModel.selectedEndDate.collectAsStateWithLifecycle()
                BookmarkGallery(
                    bookmarks = bookmarks.value,
                    onDeleteBookmark = viewModel::deleteBookmark,
                    selectedStartDate = startDate.value,
                    selectedEndDate = endDate.value,
                    onSelectStartDate = viewModel::setStartDate,
                    onSelectEndDate = viewModel::setEndDate,
                    onClearFilter = viewModel::clearPostedDateFilter
                )
            }
        }
    }
}

@Composable
private fun BookmarkGallery(
    bookmarks: List<Bookmark>,
    onDeleteBookmark: (Bookmark) -> Unit,
    selectedStartDate: LocalDate?,
    selectedEndDate: LocalDate?,
    onSelectStartDate: (LocalDate?) -> Unit,
    onSelectEndDate: (LocalDate?) -> Unit,
    onClearFilter: () -> Unit
) {
    var expandedBookmark by remember { mutableStateOf<Bookmark?>(null) }
    val context = LocalContext.current
    val imageDirPath = remember(context) { File(context.filesDir, "images").absolutePath }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showDatePicker(
                                context = context,
                                initialDate = selectedStartDate,
                                onPicked = onSelectStartDate
                            )
                        }
                    ) {
                        Text("From: ${selectedStartDate?.format(dateFormatter) ?: "-"}")
                    }
                    Button(
                        onClick = {
                            showDatePicker(
                                context = context,
                                initialDate = selectedEndDate,
                                onPicked = onSelectEndDate
                            )
                        }
                    ) {
                        Text("To: ${selectedEndDate?.format(dateFormatter) ?: "-"}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSelectStartDate(LocalDate.of(2024, 1, 1)); onSelectEndDate(LocalDate.of(2024, 12, 31)) }) {
                        Text("2024")
                    }
                    Button(onClick = { onSelectStartDate(LocalDate.of(2025, 1, 1)); onSelectEndDate(LocalDate.of(2025, 12, 31)) }) {
                        Text("2025")
                    }
                    Button(onClick = onClearFilter) {
                        Text("Clear")
                    }
                }
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
                    text = if (selectedStartDate != null || selectedEndDate != null) {
                        "No images in selected post date range."
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
                    ImageCard(bookmark = bookmark, onClick = { expandedBookmark = bookmark })
                }
            }
        }
    }

    expandedBookmark?.let { bookmark ->
        ZoomableImageDialog(
            bookmark = bookmark,
            onDismiss = { expandedBookmark = null },
            onDelete = {
                onDeleteBookmark(bookmark)
                expandedBookmark = null
            }
        )
    }
}

private fun showDatePicker(
    context: android.content.Context,
    initialDate: LocalDate?,
    onPicked: (LocalDate) -> Unit
) {
    val base = initialDate ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onPicked(LocalDate.of(year, month + 1, dayOfMonth))
        },
        base.year,
        base.monthValue - 1,
        base.dayOfMonth
    ).show()
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
private fun ZoomableImageDialog(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = File(bookmark.filePath),
                contentDescription = bookmark.sourceUrl ?: "saved image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset += pan
                            if (scale <= 1f) {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onDelete) {
                    Text(text = "Delete")
                }
                Button(onClick = onDismiss) {
                    Text(text = "Close")
                }
            }
        }
    }
}
