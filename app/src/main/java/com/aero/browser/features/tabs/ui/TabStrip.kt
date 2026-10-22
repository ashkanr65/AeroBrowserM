package com.aero.browser.features.tabs.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tab
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aero.browser.features.tabs.model.Tab

@Composable
fun TabStrip(
    tabs: List<Tab>,
    activeTabId: String?,
    isPrivateMode: Boolean,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onShowOverview: () -> Unit,
    onTogglePrivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.id == activeTabId
            Surface(
                modifier = Modifier.widthIn(min = 84.dp, max = 190.dp),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onSelectTab(tab.id) },
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        tab.isPrivate -> Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = "Private tab",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        tab.favicon != null -> AsyncImage(
                            model = tab.favicon,
                            contentDescription = "Favicon for ${tab.title}",
                            modifier = Modifier.width(18.dp),
                        )
                        else -> Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = "Tab",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = tab.title.ifBlank { tab.url },
                        modifier = Modifier.weight(1f).padding(horizontal = 7.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(onClick = { onCloseTab(tab.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close ${tab.title}")
                    }
                }
            }
        }
        IconButton(onClick = onNewTab) {
            Icon(Icons.Filled.Add, contentDescription = "New tab")
        }
        IconButton(onClick = onTogglePrivate) {
            Icon(
                imageVector = Icons.Filled.VisibilityOff,
                contentDescription = if (isPrivateMode) "Switch to normal tabs" else "Open private tabs",
                tint = if (isPrivateMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            onClick = onShowOverview,
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Tab, contentDescription = null)
                Text(tabs.size.toString(), modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
