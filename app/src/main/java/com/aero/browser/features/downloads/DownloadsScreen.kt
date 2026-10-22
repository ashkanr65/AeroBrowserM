package com.aero.browser.features.downloads

import android.app.DownloadManager
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.DecimalFormat

@Composable
fun DownloadsScreen(onBack: () -> Unit, viewModel: DownloadsViewModel = hiltViewModel()) {
    val downloads by viewModel.downloads.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Downloads", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh downloads") }
        }
        if (downloads.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No downloads yet", modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(downloads.size, key = { downloads[it].id }) { index ->
                    val item = downloads[index]
                    val openDownload: (() -> Unit)? = if (item.status == DownloadManager.STATUS_SUCCESSFUL && item.uri.isNotBlank()) {
                        {
                            val uri = android.net.Uri.parse(item.uri)
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/octet-stream")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }
                    } else null
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = openDownload ?: {},
                        enabled = openDownload != null,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text("${formatSize(item.downloadedBytes)}${if (item.totalBytes > 0) " / ${formatSize(item.totalBytes)}" else ""}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(statusLabel(item.status), style = MaterialTheme.typography.labelMedium)
                                IconButton(onClick = { viewModel.remove(item.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove download") }
                            }
                            when (item.status) {
                                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> LinearProgressIndicator(progress = { item.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                                DownloadManager.STATUS_PAUSED -> Text("Paused (reason ${item.reason})")
                                DownloadManager.STATUS_FAILED -> Text("Failed (reason ${item.reason})", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: Int): String = when (status) {
    DownloadManager.STATUS_PENDING -> "Pending"
    DownloadManager.STATUS_RUNNING -> "Downloading"
    DownloadManager.STATUS_PAUSED -> "Paused"
    DownloadManager.STATUS_SUCCESSFUL -> "Complete"
    DownloadManager.STATUS_FAILED -> "Failed"
    else -> "Unknown"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${DecimalFormat("0.0").format(bytes / 1024.0)} KB"
    else -> "${DecimalFormat("0.0").format(bytes / (1024.0 * 1024.0))} MB"
}
