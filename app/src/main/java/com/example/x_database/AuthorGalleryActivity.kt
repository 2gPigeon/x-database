package com.example.x_database

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

class AuthorGalleryActivity : ComponentActivity() {
    private val repository by lazy {
        BookmarkRepository(
            applicationContext,
            AppDatabase.getInstance(applicationContext).bookmarkDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val author = intent.getStringExtra(EXTRA_AUTHOR).orEmpty()
        setContent {
            XdatabaseTheme {
                val bookmarks = repository.observeBookmarks()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val filtered = bookmarks.value
                    .filter { (it.authorUsername ?: "Unknown") == author }
                    .sortedByDescending { it.savedAt }
                AuthorGalleryScreen(
                    author = author,
                    bookmarks = filtered
                )
            }
        }
    }

    companion object {
        const val EXTRA_AUTHOR = "extra_author"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AuthorGalleryScreen(author: String, bookmarks: List<Bookmark>) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(author) },
                navigationIcon = {
                    IconButton(onClick = { drawerOpen = !drawerOpen }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${bookmarks.size} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (bookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No images for this author.",
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
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clickable {
                                        val index = bookmarks.indexOfFirst { it.id == bookmark.id }
                                        if (index >= 0) {
                                            expandedIndex = index
                                        }
                                    },
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
                    }
                }
            }

            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x33000000))
                        .clickable { drawerOpen = false }
                )
            }

            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.5f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Navigate",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DrawerItem(
                        label = "All images",
                        selected = false,
                        onClick = {
                            drawerOpen = false
                            context.startActivity(Intent(context, MainActivity::class.java))
                        }
                    )
                    DrawerItem(
                        label = "Authors",
                        selected = false,
                        onClick = {
                            drawerOpen = false
                            context.startActivity(Intent(context, AuthorListActivity::class.java))
                        }
                    )
                    DrawerItem(
                        label = author,
                        selected = true,
                        onClick = { drawerOpen = false }
                    )
                }
            }
        }
    }

    expandedIndex?.let { index ->
        AuthorPagerDialog(
            bookmarks = bookmarks,
            initialIndex = index,
            onDismiss = { expandedIndex = null }
        )
    }
}

@Composable
private fun AuthorPagerDialog(
    bookmarks: List<Bookmark>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val context = LocalContext.current
        val pagerState = rememberPagerState(
            initialPage = initialIndex.coerceIn(0, (bookmarks.size - 1).coerceAtLeast(0)),
            pageCount = { bookmarks.size }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (bookmarks.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                ) { page ->
                    val bookmark = bookmarks[page]
                    AsyncImage(
                        model = File(bookmark.filePath),
                        contentDescription = bookmark.sourceUrl ?: "saved image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val total = bookmarks.size
                val currentIndex = pagerState.currentPage.coerceIn(0, (total - 1).coerceAtLeast(0))
                val currentBookmark = bookmarks.getOrNull(currentIndex)
                Button(
                    onClick = {
                        val url = currentBookmark?.let(::resolveTweetUrl) ?: return@Button
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    },
                    enabled = currentBookmark?.let(::resolveTweetUrl) != null
                ) {
                    Text(text = "Open X")
                }
                Button(onClick = onDismiss) {
                    Text(text = "Close")
                }
                Text(
                    text = if (total == 0) "0/0" else "${currentIndex + 1}/$total",
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val indicator = if (selected) "▶ " else "  "
    val base = MaterialTheme.colorScheme.surface
    Text(
        text = indicator + label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) base else base.copy(alpha = 0.6f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

private fun resolveTweetUrl(bookmark: Bookmark): String? {
    if (!bookmark.sourceUrl.isNullOrBlank()) {
        return bookmark.sourceUrl
    }
    val tweetId = bookmark.tweetId ?: return null
    return "https://x.com/i/status/$tweetId"
}
