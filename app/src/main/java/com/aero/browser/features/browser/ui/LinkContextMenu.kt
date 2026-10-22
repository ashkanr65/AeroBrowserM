package com.aero.browser.features.browser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class LinkTargetKind { LINK, IMAGE, IMAGE_LINK }

/**
 * What the long press landed on. [linkUrl] is the anchor target, [imageUrl] the image source; an
 * image inside a link carries both.
 */
data class LinkTarget(
    val kind: LinkTargetKind,
    val linkUrl: String? = null,
    val imageUrl: String? = null,
) {
    val primaryUrl: String? get() = linkUrl ?: imageUrl
}

/**
 * Turns WebView hit-test values into a menu model. Returns null when there is nothing actionable
 * (plain text, form fields, non-web schemes), in which case the press is left to WebView so text
 * selection keeps working.
 */
fun linkTargetFor(href: String?, imageUrl: String?, pageUrl: String? = null): LinkTarget? {
    val link = href.normalizedWebUrl(pageUrl)
    val image = imageUrl.normalizedWebUrl(pageUrl)
    return when {
        link != null && image != null -> LinkTarget(LinkTargetKind.IMAGE_LINK, link, image)
        link != null -> LinkTarget(LinkTargetKind.LINK, link)
        image != null -> LinkTarget(LinkTargetKind.IMAGE, imageUrl = image)
        else -> null
    }
}

/** Resolves relative references and drops schemes a tab cannot open (mailto:, tel:, javascript:). */
private fun String?.normalizedWebUrl(base: String?): String? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    raw.toHttpUrlOrNull()?.let { return it.toString() }
    val baseUrl = base?.toHttpUrlOrNull() ?: return null
    return baseUrl.resolve(raw)?.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkContextMenu(
    target: LinkTarget?,
    privateMode: Boolean,
    onDismiss: () -> Unit,
    onOpenInNewTab: (url: String, private: Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDownloadImage: (String) -> Unit,
) {
    val link = target?.linkUrl
    val image = target?.imageUrl
    if (target == null || (link == null && image == null)) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            val host = (link ?: image).orEmpty().toHttpUrlOrNull()?.host.orEmpty()
            Text(
                host.ifBlank { "Link" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            )
            Text(
                (link ?: image).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 22.dp).padding(bottom = 10.dp),
            )
            HorizontalDivider()
            if (link != null) {
                LinkAction("Open in new tab", Icons.Filled.Add) { onDismiss(); onOpenInNewTab(link, false) }
                if (!privateMode) {
                    LinkAction("Open in private tab", Icons.Filled.VisibilityOff) { onDismiss(); onOpenInNewTab(link, true) }
                }
                LinkAction("Copy link address", Icons.Filled.ContentCopy) { onDismiss(); onCopy(link) }
                LinkAction("Share link", Icons.Filled.Share) { onDismiss(); onShare(link) }
            }
            if (image != null) {
                LinkAction("Open image in new tab", Icons.Filled.Image) { onDismiss(); onOpenInNewTab(image, false) }
                LinkAction("Copy image address", Icons.Filled.ContentCopy) { onDismiss(); onCopy(image) }
                if (image.startsWith("https://", ignoreCase = true)) {
                    LinkAction("Download image", Icons.Filled.Download) { onDismiss(); onDownloadImage(image) }
                }
            }
        }
    }
}

@Composable
private fun LinkAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, modifier = Modifier.padding(start = 18.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
