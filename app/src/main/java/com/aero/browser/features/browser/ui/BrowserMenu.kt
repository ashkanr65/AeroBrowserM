package com.aero.browser.features.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenu(
    visible: Boolean,
    desktopSite: Boolean,
    onDismiss: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onFindInPage: () -> Unit,
    onDoNotTrackChanged: (Boolean) -> Unit,
    trackingLevel: String,
    doNotTrackEnabled: Boolean,
    blockWebRtc: Boolean,
    onBlockWebRtcChanged: (Boolean) -> Unit,
    blockedHostCount: Int,
    onDesktopSiteChanged: (Boolean) -> Unit,
    onShare: () -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Aero", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 18.dp)) {
                    Text("Ad and tracker blocking · $trackingLevel")
                    Text("$blockedHostCount blocked domains", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MenuAction("New private tab", Icons.Filled.VisibilityOff, onNewPrivateTab)
            MenuAction("Bookmarks", Icons.Filled.Bookmarks, onBookmarks)
            MenuAction("History", Icons.Filled.History, onHistory)
            MenuAction("Downloads", Icons.Filled.Download, onDownloads)
            MenuAction("Settings", Icons.Filled.Settings, onSettings)
            MenuAction("Find in page", Icons.Filled.FindInPage, onFindInPage)
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onDoNotTrackChanged(!doNotTrackEnabled) }.padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Security, contentDescription = null)
                Text("Do Not Track signal", modifier = Modifier.weight(1f).padding(start = 18.dp))
                Switch(checked = doNotTrackEnabled, onCheckedChange = onDoNotTrackChanged)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onBlockWebRtcChanged(!blockWebRtc) }.padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Security, contentDescription = null)
                Text("Block WebRTC", modifier = Modifier.weight(1f).padding(start = 18.dp))
                Switch(checked = blockWebRtc, onCheckedChange = onBlockWebRtcChanged)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onDesktopSiteChanged(!desktopSite) }.padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DesktopWindows, contentDescription = null)
                Text("Desktop site", modifier = Modifier.weight(1f).padding(start = 18.dp))
                Switch(checked = desktopSite, onCheckedChange = onDesktopSiteChanged)
            }
            MenuAction("Share", Icons.Filled.Share, onShare)
        }
    }
}

@Composable
private fun MenuAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 18.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
