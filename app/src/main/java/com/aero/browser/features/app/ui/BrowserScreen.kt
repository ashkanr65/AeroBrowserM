package com.aero.browser.features.app.ui

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.aero.browser.features.browser.ui.withPrivateIme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.aero.browser.R
import com.aero.browser.core.network.HttpsEnforcer
import com.aero.browser.core.network.TrackingParamStripper
import com.aero.browser.core.network.TrackingProtection
import com.aero.browser.core.webengine.SecureWebView
import com.aero.browser.core.webengine.StateListenerRegistry
import com.aero.browser.core.webengine.WebViewUiState
import com.aero.browser.features.tabs.TabManagerViewModel
import com.aero.browser.features.tabs.model.Tab
import com.aero.browser.features.tabs.ui.TabGrid
import com.aero.browser.features.tabs.ui.TabStrip

@Composable
fun BrowserScreen(
    initialUrl: String = "https://example.com/",
    viewModel: TabManagerViewModel = hiltViewModel(),
) {
    val normalTabs by viewModel.normalTabs.collectAsStateWithLifecycle()
    val privateTabs by viewModel.privateTabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val overviewVisible by viewModel.isTabOverviewVisible.collectAsStateWithLifecycle()
    val isPrivateMode by viewModel.isPrivateMode.collectAsStateWithLifecycle()
    val visibleTabs = if (isPrivateMode) privateTabs else normalTabs
    val activeTab = visibleTabs.firstOrNull { it.id == activeTabId }
    val webViews = remember { mutableStateMapOf<String, WebView>() }
    var omniboxText by remember(activeTab?.id) { mutableStateOf(activeTab?.url ?: initialUrl) }
    var uiState by remember(activeTab?.id) { mutableStateOf(WebViewUiState(url = activeTab?.url)) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var rendererGeneration by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(overviewVisible) { viewModel.toggleTabOverview() }

    LaunchedEffect(viewModel) {
        viewModel.awaitDatabaseInitialization()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().allProfileNames
                .filter { it.startsWith("aero_private_") }
                .forEach(ProfileStore.getInstance()::deleteProfile)
        }
        if (viewModel.activeTab() == null) viewModel.createTab(initialUrl)
    }

    LaunchedEffect(activeTab?.id) {
        omniboxText = activeTab?.url ?: initialUrl
        uiState = WebViewUiState(url = activeTab?.url)
    }

    MaterialTheme(
        colorScheme = if (isPrivateMode) darkColorScheme() else MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = !overviewVisible, enter = fadeIn(), exit = fadeOut()) {
                    Column {
                        TabStrip(
                            tabs = visibleTabs,
                            activeTabId = activeTabId,
                            isPrivateMode = isPrivateMode,
                            onSelectTab = viewModel::switchTab,
                            onCloseTab = viewModel::closeTab,
                            onNewTab = { viewModel.createTab() },
                            onShowOverview = viewModel::toggleTabOverview,
                            onTogglePrivate = viewModel::togglePrivateMode,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            OutlinedTextField(
                                value = omniboxText,
                                onValueChange = { omniboxText = it },
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Go,
                                    keyboardType = KeyboardType.Uri,
                                    autoCorrectEnabled = !isPrivateMode,
                                ).withPrivateIme(isPrivateMode),
                                keyboardActions = KeyboardActions(onGo = {
                                    val target = normalizeInput(omniboxText)
                                    activeTab?.let { tab ->
                                        viewModel.updateTab(tab.id) { it.copy(url = target, title = target, isLoading = true) }
                                        webViews[tab.id]?.loadUrl(target)
                                    }
                                }),
                                placeholder = { Text("Search or enter address") },
                            )
                            IconButton(onClick = { webViews[activeTabId]?.goBack() }, enabled = uiState.canGoBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            IconButton(onClick = { webViews[activeTabId]?.goForward() }, enabled = uiState.canGoForward) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                            }
                            IconButton(onClick = { reloadNonce++; webViews[activeTabId]?.reload() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                            }
                        }
                        if (uiState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    if (activeTab == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        androidx.compose.runtime.key(activeTab.id, rendererGeneration) {
                            val tab = activeTab
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    val webView = SecureWebView.create(
                                        context,
                                        TrackingProtection.forApp(),
                                        HttpsEnforcer,
                                        privateMode = tab.isPrivate,
                                        privateProfileName = privateProfileName(tab.id),
                                    )
                                    webView.tag = tab.id
                                    if (tab.isPrivate) viewModel.attachPrivateWebView(tab.id)
                                    restoreWebViewState(webView, viewModel.restoreTabState(tab.id), tab.url)
                                    installTabListener(webView, tab, viewModel, onRendererGone = { rendererGeneration++ }) { state ->
                                        if (tab.id == activeTabId) {
                                            uiState = state
                                            if (omniboxText.startsWith("http", ignoreCase = true) || !omniboxText.contains(' ')) {
                                                omniboxText = state.url ?: omniboxText
                                            }
                                        }
                                    }
                                    webViews[tab.id] = webView
                                    webView
                                },
                                update = { webView ->
                                    webViews[tab.id] = webView
                                    if (webView.url == null) webView.loadUrl(HttpsEnforcer.upgrade(tab.url) ?: tab.url)
                                },
                            )
                            DisposableEffect(tab.id, lifecycleOwner, rendererGeneration) {
                                val observer = LifecycleEventObserver { _, event ->
                                    if (event == Lifecycle.Event.ON_PAUSE) webViews[tab.id]?.let { saveTabState(it, tab, viewModel) }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                onDispose {
                                    lifecycleOwner.lifecycle.removeObserver(observer)
                                    val released = webViews.remove(tab.id)
                                    if (released != null) {
                                        saveTabState(released, tab, viewModel)
                                        val listener = released.getTag(R.id.webview_state_listener) as? StateListenerRegistry.Listener
                                        if (listener != null) StateListenerRegistry.removeListener(listener)
                                        SecureWebView.removeDoNotTrackScript(released)
                                        released.stopLoading()
                                        released.webChromeClient = null
                                        released.webViewClient = android.webkit.WebViewClient()
                                        released.destroy()
                                        if (tab.isPrivate) viewModel.detachPrivateWebView(tab.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TabGrid(
                tabs = visibleTabs,
                activeTabId = activeTabId,
                visible = overviewVisible,
                isPrivateMode = isPrivateMode,
                normalTabCount = normalTabs.size,
                privateTabCount = privateTabs.size,
                onSelectTab = viewModel::switchTab,
                onCloseTab = viewModel::closeTab,
                onCloseAll = viewModel::closeAllTabs,
                onTogglePrivate = { viewModel.setPrivateMode(!isPrivateMode) },
                onDismiss = viewModel::toggleTabOverview,
                onNewTab = { viewModel.createTab("https://duckduckgo.com/") },
                modifier = Modifier.fillMaxSize(),
            )
            }
        }
    }
}

private fun privateProfileName(tabId: String): String = "aero_private_$tabId"

private fun restoreWebViewState(webView: WebView, state: Bundle?, fallbackUrl: String) {
    val restored = state?.let { webView.restoreState(Bundle(it)) }
    if (restored == null) webView.loadUrl(HttpsEnforcer.upgrade(fallbackUrl) ?: fallbackUrl)
}

private fun saveTabState(webView: WebView, tab: Tab, viewModel: TabManagerViewModel) {
    val state = Bundle()
    webView.saveState(state)
    viewModel.saveTabState(tab.id, state)
    viewModel.updatePage(tab.id, webView.url ?: tab.url, webView.title, webView.progress in 1..99)
}

private fun installTabListener(
    webView: WebView,
    tab: Tab,
    viewModel: TabManagerViewModel,
    onRendererGone: () -> Unit,
    onState: (WebViewUiState) -> Unit,
) {
    val listener = object : StateListenerRegistry.Listener {
        override fun onPageStarted(view: WebView, url: String) {
            if (view !== webView) return
            viewModel.updatePage(tab.id, url, webView.title, true)
            onState(WebViewUiState(url = url, title = webView.title, isLoading = true, canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward()))
        }

        override fun onPageFinished(view: WebView, url: String, title: String?) {
            if (view !== webView) return
            viewModel.updatePage(tab.id, url, title, false)
            onState(WebViewUiState(url = url, title = title, isLoading = false, canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward()))
        }

        override fun onSslBlocked(view: WebView, primaryError: Int) {
            if (view === webView) onState(WebViewUiState(url = view.url, title = "Blocked: certificate error", isLoading = false))
        }

        override fun onLoadError(view: WebView, url: String, description: String?) {
            if (view === webView) onState(WebViewUiState(url = url, title = "Load error: $description", isLoading = false))
        }

        override fun onRenderProcessGone(view: WebView) {
            if (view === webView) {
                onState(WebViewUiState(url = view.url, title = "Page renderer stopped", isLoading = false))
                if (viewModel.isTabOpen(tab.id)) {
                    viewModel.updateTab(tab.id) { it.copy(webViewState = null, isLoading = false) }
                    onRendererGone()
                }
            }
        }
    }
    StateListenerRegistry.addListener(listener)
    webView.setTag(R.id.webview_state_listener, listener)
}

internal fun normalizeInput(text: String): String {
    val trimmed = text.trim()
    return when {
        trimmed.startsWith("http://", ignoreCase = true) -> TrackingParamStripper.strip(HttpsEnforcer.upgrade(trimmed) ?: trimmed) ?: trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> TrackingParamStripper.strip(trimmed) ?: trimmed
        trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"
        else -> "https://duckduckgo.com/?q=${android.net.Uri.encode(trimmed)}"
    }
}
