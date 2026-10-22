package com.aero.browser.features.downloads

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DownloadRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager: DownloadManager
        get() = requireNotNull(ContextCompat.getSystemService(context, DownloadManager::class.java)) { "Download service unavailable" }

    fun enqueue(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, pageTitle: String?): Long {
        require(url.startsWith("https://", true)) { "Only HTTPS downloads are permitted" }
        val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
            .ifBlank { "download" }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(filename)
            setDescription(pageTitle?.takeIf(String::isNotBlank) ?: "Downloading from ${Uri.parse(url).host.orEmpty()}")
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            userAgent?.let { addRequestHeader("User-Agent", it) }
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }
        return manager.enqueue(request)
    }

    fun queryAll(): List<DownloadItem> {
        val cursor = manager.query(DownloadManager.Query()) ?: return emptyList()
        return cursor.use { result ->
            buildList {
                while (result.moveToNext()) {
                    val status = result.int(DownloadManager.COLUMN_STATUS)
                    val total = result.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES).coerceAtLeast(0)
                    val downloaded = result.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR).coerceAtLeast(0)
                    add(
                        DownloadItem(
                            id = result.long(DownloadManager.COLUMN_ID),
                            fileName = result.string(DownloadManager.COLUMN_TITLE),
                            uri = result.string(DownloadManager.COLUMN_LOCAL_URI),
                            status = status,
                            totalBytes = total,
                            downloadedBytes = downloaded,
                            reason = result.int(DownloadManager.COLUMN_REASON),
                        ),
                    )
                }
            }
        }
    }

    fun remove(id: Long) { manager.remove(id) }
    private fun Cursor.column(name: String): Int = getColumnIndex(name).takeIf { it >= 0 } ?: -1
    private fun Cursor.long(name: String): Long = column(name).takeIf { it >= 0 }?.let(::getLong) ?: 0L
    private fun Cursor.int(name: String): Int = column(name).takeIf { it >= 0 }?.let(::getInt) ?: 0
    private fun Cursor.string(name: String): String = column(name).takeIf { it >= 0 }?.let(::getString).orEmpty()
}

data class DownloadItem(
    val id: Long,
    val fileName: String,
    val uri: String,
    val status: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val reason: Int,
) {
    val progressPercent: Int get() = if (totalBytes <= 0) 0 else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
}
