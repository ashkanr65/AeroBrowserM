package com.aero.browser.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aero.browser.core.storage.HistoryEntity

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenUrl: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState()
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val groups = viewModel.grouped(history, query)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("History", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) { Text("Clear") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            label = { Text("Search history") },
            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
        )
        if (groups.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (query.isBlank()) "No history yet" else "No matching history")
            }
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                groups.forEach { (date, entries) ->
                    item(key = "header-$date") {
                        Text(date, modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    items(entries, key = { it.id }) { entry ->
                        HistoryRow(entry, onOpen = { onOpenUrl(entry.url) }, onDelete = { viewModel.remove(entry.url) })
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing history?") },
            text = { Text("This removes all saved history from this device.") },
            confirmButton = { TextButton(onClick = { viewModel.clear(); confirmClear = false }) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove from history") }
        }
    }
}
