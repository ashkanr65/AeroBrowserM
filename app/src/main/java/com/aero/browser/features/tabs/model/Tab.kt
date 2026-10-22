package com.aero.browser.features.tabs.model

import android.os.Bundle

/** In-memory representation of a normal or private browser tab. */
data class Tab(
    val id: String,
    val url: String,
    val title: String,
    val favicon: String? = null,
    val isLoading: Boolean = false,
    val isPrivate: Boolean = false,
    /** WebView's opaque saveState payload; never persisted for private tabs. */
    val webViewState: Bundle? = null,
)
