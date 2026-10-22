package com.aero.browser.features.tabs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aero.browser.features.tabs.model.Tab

@Composable
fun AdaptiveTabRail(
    tabs: List<Tab>,
    activeTabId: String?,
    isPrivateMode: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onShowOverview: () -> Unit,
    onTogglePrivate: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Aero", modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNewTab) { Icon(Icons.Filled.Add, contentDescription = "New tab") }
                IconButton(onClick = onShowOverview) { Text("${tabs.size}", style = MaterialTheme.typography.labelLarge) }
                IconButton(onClick = onTogglePrivate) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = if (isPrivateMode) "Switch to normal tabs" else "Open private tabs", tint = if (isPrivateMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tabs, key = { it.id }) { tab ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (tab.id == activeTabId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(Modifier.clickable { onSelectTab(tab.id) }.padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tab.favicon != null && !tab.isPrivate) {
                                AsyncImage(model = tab.favicon, contentDescription = null, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(if (tab.isPrivate) Icons.Filled.VisibilityOff else Icons.Filled.Bookmarks, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(tab.title.ifBlank { tab.url }, modifier = Modifier.weight(1f).padding(horizontal = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onCloseTab(tab.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close ${tab.title}")
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            IconButton(onClick = onBookmarks) { Icon(Icons.Filled.Bookmarks, contentDescription = "Bookmarks") }
            IconButton(onClick = onHistory) { Icon(Icons.Filled.History, contentDescription = "History") }
            IconButton(onClick = onDownloads) { Icon(Icons.Filled.Download, contentDescription = "Downloads") }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        }
    }
}
