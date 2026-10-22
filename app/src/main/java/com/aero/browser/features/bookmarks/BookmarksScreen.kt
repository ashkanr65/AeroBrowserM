package com.aero.browser.features.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.aero.browser.core.storage.BookmarkEntity

@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val bookmarks by viewModel.bookmarks.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Bookmarks", style = MaterialTheme.typography.headlineSmall)
        }
        if (bookmarks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkRow(bookmark, onOpen = { onOpenUrl(bookmark.url) }, onDelete = { viewModel.remove(bookmark.url) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkRow(bookmark: BookmarkEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onDelete()
            true
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        content = {
            Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!bookmark.favicon.isNullOrBlank()) {
                        AsyncImage(bookmark.favicon, contentDescription = null, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(bookmark.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(bookmark.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Filled.BookmarkBorder, contentDescription = "Bookmarked", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
    )
}
