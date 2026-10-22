package com.aero.browser.features.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.net.URI

/** URL bar that shows only the page's host until the user taps to edit. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Omnibox(
    url: String,
    isLoading: Boolean,
    progress: Int,
    secure: Boolean,
    certificateDetails: CertificateDetails?,
    onNavigate: (String) -> Unit,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onReload: () -> Unit = {},
    incognito: Boolean = false,
    onEditorBoundsChanged: (Rect) -> Unit = {},
    onEditingChanged: (Boolean) -> Unit = {},
    dismissEditingRequest: Int = 0,
    onTorClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var editingValue by remember { mutableStateOf(TextFieldValue(url)) }
    var showSecurity by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)

    LaunchedEffect(url, expanded) {
        if (!expanded) editingValue = TextFieldValue(url)
    }
    SideEffect { onEditingChanged(expanded) }
    LaunchedEffect(incognito) {
        if (expanded) {
            focusManager.clearFocus()
            expanded = false
            editingValue = TextFieldValue(url)
            keyboard?.hide()
        }
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(dismissEditingRequest) {
        if (dismissEditingRequest > 0 && expanded) {
            expanded = false
            editingValue = TextFieldValue(url)
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expanded) {
                OutlinedTextField(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    modifier = Modifier.weight(1f)
                        .onGloballyPositioned { onEditorBoundsChanged(it.boundsInWindow()) }
                        .focusRequester(focusRequester),
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    placeholder = { Text("Search or enter address") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ).withPrivateIme(incognito),
                    keyboardActions = KeyboardActions(onGo = {
                        onNavigate(editingValue.text)
                        expanded = false
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textAlign = TextAlign.Center,
                        textDirection = TextDirection.Ltr,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { editingValue = TextFieldValue("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear address")
                        }
                    },
                )

                IconButton(
                    onClick = {
                        onNavigate(editingValue.text)
                        expanded = false
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                    enabled = editingValue.text.isNotBlank(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go")
                }
            } else {
                var addressActionsVisible by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier.weight(1f).combinedClickable(
                        onClick = {
                            editingValue = TextFieldValue(url, selection = TextRange(0, url.length))
                            expanded = true
                        },
                        onLongClick = { addressActionsVisible = true },
                    ),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(onClick = onTorClick, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = "Tor Browser options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        IconButton(onClick = { showSecurity = true }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = if (secure) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = if (secure) "Connection is secure" else "Connection is not secure",
                                tint = if (secure) Color(0xFF82B1FF) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Text(
                            text = when {
                                url.startsWith("about:", true) -> "Search or enter address"
                                else -> displayDomain(url)
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium.copy(
                                textAlign = TextAlign.Center,
                                textDirection = TextDirection.Ltr,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(
                            onClick = onToggleBookmark,
                            enabled = url.startsWith("http", ignoreCase = true),
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onReload, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = if (isLoading) "Stop loading" else "Reload page",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(expanded = addressActionsVisible, onDismissRequest = { addressActionsVisible = false }) {
                        DropdownMenuItem(text = { Text("Copy address") }, onClick = {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Address", url))
                            addressActionsVisible = false
                        })
                        DropdownMenuItem(text = { Text("Cut address") }, onClick = {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Address", url))
                            editingValue = TextFieldValue("")
                            addressActionsVisible = false
                            expanded = true
                        })
                        DropdownMenuItem(text = { Text("Paste address") }, onClick = {
                            val pasted = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            editingValue = TextFieldValue(pasted, selection = TextRange(pasted.length))
                            addressActionsVisible = false
                            expanded = true
                        })
                        DropdownMenuItem(text = { Text("Edit address") }, onClick = {
                            editingValue = TextFieldValue(url, selection = TextRange(0, url.length))
                            addressActionsVisible = false
                            expanded = true
                        })
                    }
                }
            }
        }
        // Reserve the progress strip permanently so a page load never resizes the chrome or the
        // WebView underneath it (which repainted the whole bar on every navigation).
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }
    }

    if (showSecurity) {
        ModalBottomSheet(onDismissRequest = { showSecurity = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = if (secure) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = null,
                        tint = if (secure) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(if (secure) "Connection is secure" else "Connection is not secure", style = MaterialTheme.typography.titleLarge)
                }
                Text("Address: ${displayDomain(url)}")
                Text("Issuer: ${certificateDetails?.issuer ?: "Certificate details unavailable"}")
                Text("Valid from: ${certificateDetails?.validFrom ?: "Unavailable"}")
                Text("Valid until: ${certificateDetails?.validUntil ?: "Unavailable"}")
                TextButton(onClick = { showSecurity = false }, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}

@Composable
fun OmniboxLoadingIndicator(isLoading: Boolean, progress: Int) {
    if (isLoading) LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
}

private fun displayDomain(url: String): String = try {
    URI(url).host?.removePrefix("www.") ?: if (url.startsWith("about:")) "New tab" else url
} catch (_: Exception) {
    url
}

data class CertificateDetails(
    val issuer: String,
    val validFrom: String,
    val validUntil: String,
)
