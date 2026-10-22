package com.aero.browser.features.tabs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.aero.browser.features.browser.ui.withPrivateIme
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.aero.browser.features.tabs.model.Tab

@Composable
fun TabGrid(
    tabs: List<Tab>,
    activeTabId: String?,
    visible: Boolean,
    isPrivateMode: Boolean,
    normalTabCount: Int = tabs.count { !it.isPrivate },
    privateTabCount: Int = tabs.count { it.isPrivate },
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseAll: () -> Unit,
    onTogglePrivate: () -> Unit,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
    topBarBottom: Int = 0,
) {
    val density = LocalDensity.current
    var searchQuery by remember(isPrivateMode) { mutableStateOf("") }
    val matchingTabs = tabs.filter { tab ->
        searchQuery.isBlank() || tab.title.contains(searchQuery, ignoreCase = true) || tab.url.contains(searchQuery, ignoreCase = true)
    }
    if (visible) {
        Column(
            modifier = modifier.fillMaxSize()
                .background(if (isPrivateMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                .padding(top = with(density) { topBarBottom.toDp() }),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Tabs", style = MaterialTheme.typography.headlineSmall)
                    Text("${tabs.size} open", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close tab overview")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 13.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search).withPrivateIme(isPrivateMode),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Search your tabs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            inner()
                        },
                    )
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear tab search")
                    }
                }
            }

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeButton("Normal", selected = !isPrivateMode, count = normalTabCount, onClick = { if (isPrivateMode) onTogglePrivate() }, modifier = Modifier.weight(1f))
                ModeButton("Private", selected = isPrivateMode, count = privateTabCount, onClick = { if (!isPrivateMode) onTogglePrivate() }, isPrivate = true, modifier = Modifier.weight(1f))
            }

            if (matchingTabs.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (tabs.isEmpty()) if (isPrivateMode) "No private tabs" else "No tabs" else "No matching tabs",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(matchingTabs, key = { it.id }) { tab ->
                        SwipeDismissTabCard(
                            tab = tab,
                            active = tab.id == activeTabId,
                            onSelect = { onSelectTab(tab.id) },
                            onClose = { onCloseTab(tab.id) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onCloseAll, modifier = Modifier.weight(1f)) { Text("Close all") }
                Button(onClick = { onNewTab(); onDismiss() }, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(22.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "New tab")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit,
    isPrivate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isPrivate) Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, modifier = Modifier.padding(start = if (isPrivate) 6.dp else 0.dp))
            count?.let { Text(" $it", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun SwipeDismissTabCard(
    tab: Tab,
    active: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    var dragX by remember(tab.id) { mutableFloatStateOf(0f) }
    Card(
        onClick = onSelect,
        modifier = Modifier
            .then(if (active) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large).padding(3.dp) else Modifier)
            .graphicsLayer { translationX = dragX; alpha = (1f - kotlin.math.abs(dragX) / 700f).coerceIn(0.3f, 1f) }
            .pointerInput(tab.id) {
                val widthAtStart = size.width.toFloat().coerceAtLeast(1f)
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, delta ->
                        totalDrag += delta
                        dragX = (dragX + delta).coerceIn(-widthAtStart, widthAtStart)
                    },
                    onDragEnd = {
                        if (kotlin.math.abs(totalDrag) > widthAtStart * 0.28f) onClose()
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.6f)
                    .background(if (tab.isPrivate) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (tab.isPrivate) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = "Private tab", modifier = Modifier.align(Alignment.Center).size(28.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                } else if (tab.favicon != null) {
                    AsyncImage(
                        model = tab.favicon,
                        contentDescription = "Favicon for ${tab.title}",
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                    )
                } else {
                    Icon(Icons.Filled.Language, contentDescription = "Page preview", modifier = Modifier.align(Alignment.Center).size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = tab.url.substringAfter("//").substringBefore('/').ifBlank { "New tab" },
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close ${tab.title}", tint = if (tab.isPrivate) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tab.title.ifBlank { tab.url },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(if (tab.isLoading) "…" else "", fontSize = 12.sp)
            }
        }
    }
}
