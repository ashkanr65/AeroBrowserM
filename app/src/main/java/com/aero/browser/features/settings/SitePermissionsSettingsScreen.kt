package com.aero.browser.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aero.browser.core.webengine.SitePermission

@Composable
fun SitePermissionsSettingsScreen(
    permissions: Map<String, Map<String, SitePermission>>,
    onDecisionChanged: (String, String, SitePermission) -> Unit,
    onClear: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var clearAllConfirmation by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Site permissions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (permissions.isNotEmpty()) {
                IconButton(onClick = { clearAllConfirmation = true }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Reset all site permissions")
                }
            }
        }
        Text("Websites start at Ask. A saved choice applies only to that website and permission.", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (permissions.isEmpty()) {
            Text("No saved site permissions", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                permissions.toSortedMap().forEach { (origin, site) ->
                    item(key = "site:$origin") {
                        Text(origin, modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 18.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
                    }
                    items(site.toSortedMap().toList(), key = { (type, _) -> "$origin:$type" }) { (type, decision) ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                            Text(type, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SitePermission.entries.forEach { option ->
                                    Row(modifier = Modifier.weight(1f).clickable { onDecisionChanged(origin, type, option) }, verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = decision == option, onClick = { onDecisionChanged(origin, type, option) })
                                        Text(option.title())
                                    }
                                }
                            }
                        }
                    }
                    item(key = "reset:$origin") {
                        TextButton(onClick = { onClear(origin) }, modifier = Modifier.padding(start = 12.dp)) { Text("Reset this website") }
                    }
                }
            }
        }
    }
    if (clearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { clearAllConfirmation = false },
            title = { Text("Reset all site permissions?") },
            text = { Text("All websites will ask again next time they request access.") },
            confirmButton = { TextButton(onClick = { onClear(null); clearAllConfirmation = false }) { Text("Reset all") } },
            dismissButton = { TextButton(onClick = { clearAllConfirmation = false }) { Text("Cancel") } },
        )
    }
}

private fun SitePermission.title(): String = when (this) {
    SitePermission.ASK -> "Ask"
    SitePermission.ALLOW -> "Allow"
    SitePermission.BLOCK -> "Block"
}
