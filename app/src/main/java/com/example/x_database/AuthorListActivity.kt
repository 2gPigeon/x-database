package com.example.x_database

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.x_database.data.AppDatabase
import com.example.x_database.data.Bookmark
import com.example.x_database.data.BookmarkRepository
import com.example.x_database.ui.theme.XdatabaseTheme
import java.io.File

class AuthorListActivity : ComponentActivity() {
    private val repository by lazy {
        BookmarkRepository(
            applicationContext,
            AppDatabase.getInstance(applicationContext).bookmarkDao()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XdatabaseTheme {
                val bookmarks = repository.observeBookmarks()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                AuthorListScreen(bookmarks = bookmarks.value)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AuthorListScreen(bookmarks: List<Bookmark>) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val grouped = bookmarks
        .groupBy { it.authorUsername?.takeIf { name -> name.isNotBlank() } ?: "Unknown" }
        .map { (author, items) ->
            val sorted = items.sortedByDescending { it.savedAt }
            AuthorGroup(author, sorted, sorted.take(3))
        }
        .sortedBy { it.author.lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Authors") },
                navigationIcon = {
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All images") },
                                onClick = {
                                    menuExpanded = false
                                    context.startActivity(Intent(context, MainActivity::class.java))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Authors") },
                                onClick = { menuExpanded = false }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${grouped.size} users",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(grouped, key = { it.author }) { group ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(
                                    Intent(context, AuthorGalleryActivity::class.java)
                                        .putExtra(AuthorGalleryActivity.EXTRA_AUTHOR, group.author)
                                )
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = group.author,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${group.items.size} posts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                group.previews.forEach { bookmark ->
                                    AsyncImage(
                                        model = File(bookmark.filePath),
                                        contentDescription = bookmark.sourceUrl ?: "preview",
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                                if (group.previews.isEmpty()) {
                                    Spacer(modifier = Modifier.height(72.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class AuthorGroup(
    val author: String,
    val items: List<Bookmark>,
    val previews: List<Bookmark>
)
