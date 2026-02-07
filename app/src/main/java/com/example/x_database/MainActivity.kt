package com.example.x_database

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
                BookmarkGallery(
                    bookmarks = bookmarks.value,
                    onDeleteBookmark = viewModel::deleteBookmark,
                    onSync = { ctx -> viewModel.syncUrlsAndAuthors(ctx) }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BookmarkGallery(
    bookmarks: List<Bookmark>,
    onDeleteBookmark: (Bookmark) -> Unit,
    onSync: (android.content.Context) -> Unit
) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val authorFiltered = bookmarks
    var drawerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Images") },
                navigationIcon = {
                    IconButton(onClick = { drawerOpen = !drawerOpen }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val cookieManager = CookieManager.getInstance()
                        if (!cookieManager.hasCookies()) {
                            context.startActivity(Intent(context, XLoginActivity::class.java))
                        } else {
                            onSync(context)
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sync")
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
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                            text = "Sync to update URLs and authors",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (authorFiltered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Share an image from X to save it here.",
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
                        items(authorFiltered, key = { it.id }) { bookmark ->
                            ImageCard(
                                bookmark = bookmark,
                                onClick = {
                                    val index = authorFiltered.indexOfFirst { it.id == bookmark.id }
                                    if (index >= 0) {
                                        expandedIndex = index
                                    }
                                }
                            )
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
                        selected = true,
                        onClick = { drawerOpen = false }
                    )
                    DrawerItem(
                        label = "Authors",
                        selected = false,
                        onClick = {
                            drawerOpen = false
                            context.startActivity(Intent(context, AuthorListActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    expandedIndex?.let { index ->
        ZoomablePagerDialog(
            bookmarks = authorFiltered,
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
        val context = LocalContext.current
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp)
                ) { page ->
                    val bookmark = bookmarks[page]
                    ZoomableImagePage(bookmark = bookmark)
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

private fun resolveTweetUrl(bookmark: Bookmark): String? {
    if (!bookmark.sourceUrl.isNullOrBlank()) {
        return bookmark.sourceUrl
    }
    val tweetId = bookmark.tweetId ?: return null
    return "https://x.com/i/status/$tweetId"
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
