package com.aero.browser.features.browser.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContextWrapper
import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.http.SslCertificate
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aero.browser.R
import com.aero.browser.core.network.HttpsEnforcer
import com.aero.browser.core.network.TrackingParamStripper
import com.aero.browser.core.network.TrackingProtection
import com.aero.browser.features.settings.BrowserSettings
import com.aero.browser.features.history.ClearDataRange
import com.aero.browser.core.webengine.SecureWebView
import com.aero.browser.core.webengine.SitePermission
import com.aero.browser.core.webengine.SitePermissionController
import com.aero.browser.core.webengine.SitePermissionStore
import com.aero.browser.core.webengine.StateListenerRegistry
import com.aero.browser.core.webengine.WebViewUiState
import com.aero.browser.features.bookmarks.BookmarksScreen
import com.aero.browser.features.bookmarks.BookmarksViewModel
import com.aero.browser.features.downloads.DownloadsScreen
import com.aero.browser.features.downloads.DownloadsViewModel
import com.aero.browser.features.history.HistoryScreen
import com.aero.browser.features.history.HistoryViewModel
import com.aero.browser.features.settings.SettingsScreen
import com.aero.browser.features.settings.SettingsViewModel
import com.aero.browser.features.tabs.TabManagerViewModel
import com.aero.browser.features.tabs.model.Tab
import com.aero.browser.features.tabs.ui.TabGrid
import com.aero.browser.ui.theme.ThemePreferences
import java.text.DateFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    sharedUrl: String? = null,
    sharedUrlRequestId: Int = 0,
    viewModel: TabManagerViewModel = hiltViewModel(),
) {
    val normalTabs by viewModel.normalTabs.collectAsStateWithLifecycle()
    val privateTabs by viewModel.privateTabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val overviewVisible by viewModel.isTabOverviewVisible.collectAsStateWithLifecycle()
    val privateMode by viewModel.isPrivateMode.collectAsStateWithLifecycle()
    val visibleTabs = if (privateMode) privateTabs else normalTabs
    val activeTab = visibleTabs.firstOrNull { it.id == activeTabId }
    val context = LocalContext.current
    val webViews = remember { mutableStateMapOf<String, WebView>() }
    var webState by remember(activeTab?.id) { mutableStateOf(WebViewUiState(url = activeTab?.url)) }
    var webProgress by remember(activeTab?.id) { mutableIntStateOf(0) }
    var certificate by remember(activeTab?.id) { mutableStateOf<CertificateDetails?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var linkTarget by remember { mutableStateOf<LinkTarget?>(null) }
    var addressEditing by remember { mutableStateOf(false) }
    var addressBounds by remember { mutableStateOf(Rect.Zero) }
    var scaffoldWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    var addressDismissRequest by remember { mutableIntStateOf(0) }
    var windowTopBarBottom by remember { mutableIntStateOf(0) }
    var findDialog by remember { mutableStateOf(false) }
    var secondaryScreen by remember { mutableStateOf<String?>(null) }
    var findText by remember { mutableStateOf("") }
    var rendererGeneration by remember { mutableIntStateOf(0) }
    var rendererCrashed by remember(activeTab?.id) { mutableStateOf(false) }
    var pageLoadFailed by remember(activeTab?.id) { mutableStateOf(false) }
    var recoveredRenderer by remember { mutableStateOf(false) }
    var crashedWebViewTabId by remember { mutableStateOf<String?>(null) }
    var permissionPrompt by remember { mutableStateOf<PermissionPrompt?>(null) }
    val platformPermissionQueue = remember { mutableStateListOf<String>() }
    var platformPermissionResult by remember { mutableStateOf<(Set<String>) -> Unit>({}) }
    val platformPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = platformPermissionQueue.filter { results[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        platformPermissionQueue.clear()
        platformPermissionResult(granted.mapNotNull { it.permissionName() }.toSet())
    }

    val bookmarksViewModel: BookmarksViewModel = hiltViewModel()
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val currentSettings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val permissionStore = remember(context) { SitePermissionStore(com.aero.browser.core.storage.SecurePreferences.get(context)) }
    val sitePermissionController = remember(settingsViewModel, permissionStore) {
        SitePermissionController(
            permissionStore,
            requestDecision = { origin, permissions, decide -> permissionPrompt = PermissionPrompt(origin, permissions, decide) },
            onDecisionChanged = settingsViewModel::setSitePermission,
            requestPlatformPermissions = { permissions, complete ->
                val required = permissions.flatMap { it.androidPermissions() }.distinct()
                val missing = required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isEmpty()) complete(permissions)
                else {
                    platformPermissionQueue.clear()
                    platformPermissionQueue.addAll(missing)
                    platformPermissionResult = { androidGranted -> complete(androidGranted) }
                    platformPermissionLauncher.launch(missing.toTypedArray())
                }
            },
        )
    }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.attachSitePermissionPrompt(settingsViewModel::requestSitePermission)
    }
    var desktopSite by remember(activeTab?.id, currentSettings.desktopSiteByDefault) {
        mutableStateOf(currentSettings.desktopSiteByDefault)
    }
    // Never let the page paint over a full-screen destination or the tab overview.
    val pageVisible = secondaryScreen == null && !overviewVisible
    val bookmarkStatus by remember(webState.url) {
        bookmarksViewModel.observeBookmarked(webState.url.orEmpty())
    }.collectAsState(initial = false)
    val density = LocalDensity.current
    val tabCanvasColor = if (privateMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background
    // Derive the system-bar icon tint from the colour actually painted behind the bars instead of
    // assuming private mode is always dark; a light canvas needs dark icons in both modes.
    val isDarkSurface = tabCanvasColor.luminance() < 0.5f
    DisposableEffect(context.findActivity()?.window, isDarkSurface) {
        context.findActivity()?.window?.let { window ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDarkSurface
                isAppearanceLightNavigationBars = !isDarkSurface
            }
        }
        onDispose { }
    }
    var connected by remember(context) { mutableStateOf(context.hasNetworkConnection()) }
    val haptics = LocalHapticFeedback.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val trackingProtection = remember { TrackingProtection.forApp(currentSettings.trackingLevel, currentSettings.blockBannerImages) }

    // HTML5 fullscreen (video fullscreen button / Element.requestFullscreen). The page's view is
    // reparented into a window-level container above the Compose chrome, system bars are hidden and
    // the device rotates so video fills a landscape screen.
    var fullscreenHost by remember { mutableStateOf<FrameLayout?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val enterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit = { view, callback ->
        val activity = context.findActivity()
        if (fullscreenHost != null || activity == null) {
            callback.onCustomViewHidden()
        } else {
            fullscreenCallback?.onCustomViewHidden()
            fullscreenCallback = callback
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            fullscreenHost = activity.addFullscreenContent(view)
        }
    }
    val leaveFullscreen: (Boolean) -> Unit = { notifyPage ->
        val host = fullscreenHost
        val callback = fullscreenCallback
        fullscreenHost = null
        fullscreenCallback = null
        host?.let { container -> (container.parent as? ViewGroup)?.removeView(container) }
        if (notifyPage) callback?.onCustomViewHidden()
        context.findActivity()?.let { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // The composable is leaving: drop the window-level host and restore the window state.
            fullscreenHost?.let { container -> (container.parent as? ViewGroup)?.removeView(container) }
            context.findActivity()?.let { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(context, lifecycleOwner, activeTab?.id, pageLoadFailed) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val updateConnectivity = { connected = context.hasNetworkConnection() }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = updateConnectivity()
            override fun onLost(network: Network) = updateConnectivity()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateConnectivity()
            override fun onUnavailable() = updateConnectivity()
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val restoredConnectivity = context.hasNetworkConnection()
                val wasConnected = connected
                connected = restoredConnectivity
                if (restoredConnectivity && (!wasConnected || pageLoadFailed)) {
                    pageLoadFailed = false
                    activeTab?.let { webViews[it.id]?.reload() }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { manager.unregisterNetworkCallback(callback) }
        }
    }
    LaunchedEffect(sharedUrlRequestId) {
        sharedUrl?.let { incoming ->
            viewModel.awaitDatabaseInitialization()
            val target = normalizeBrowserInput(incoming, currentSettings.searchEngine, currentSettings.httpsOnly)
            if (viewModel.activeTab() == null) viewModel.createTab(target) else viewModel.createTab(target)
        }
    }
    LaunchedEffect(currentSettings.trackingLevel) { trackingProtection.updateLevel(currentSettings.trackingLevel) }
    LaunchedEffect(currentSettings.blockBannerImages) { trackingProtection.updateBannerImageBlocking(currentSettings.blockBannerImages) }
    LaunchedEffect(currentSettings) {
        webViews.values.forEach { webView ->
            SecureWebView.applyBrowserSettings(webView, currentSettings)
            SecureWebView.updateDoNotTrack(webView, currentSettings.doNotTrack)
            SecureWebView.updateWebRtcRestriction(webView, currentSettings.blockWebRtc)
        }
    }
    LaunchedEffect(currentSettings.theme, currentSettings.dynamicColor) {
        ThemePreferences.mode = currentSettings.theme
        ThemePreferences.dynamicColor = currentSettings.dynamicColor
    }
    LaunchedEffect(currentSettings.homepage) { viewModel.updateStartPage(currentSettings.homepage) }
    LaunchedEffect(viewModel) {
        viewModel.awaitDatabaseInitialization()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().allProfileNames.filter { it.startsWith("aero_private_") }.forEach(ProfileStore.getInstance()::deleteProfile)
        }
        if (viewModel.activeTab() == null) viewModel.createTab(viewModel.startPage())
    }
    LaunchedEffect(activeTab?.id) {
        webState = WebViewUiState(url = activeTab?.url)
        webProgress = 0
        certificate = null
        rendererCrashed = false
        pageLoadFailed = false
    }

    BackHandler(findDialog) { findDialog = false; activeTabId?.let(webViews::get)?.clearMatches() }
    BackHandler(overviewVisible) { viewModel.toggleTabOverview() }
    BackHandler(menuVisible) { menuVisible = false }
    BackHandler(secondaryScreen != null) { secondaryScreen = null }
    BackHandler(enabled = fullscreenHost != null) { leaveFullscreen(true) }

    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = !findDialog && !overviewVisible && !menuVisible && secondaryScreen == null) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (shouldExitBrowserOnBack(lastBackPressAt, now)) {
            lastBackPressAt = 0L
            context.findActivity()?.finish()
        } else {
            lastBackPressAt = now
            val activeWebView = activeTabId?.let(webViews::get)
            when {
                activeWebView?.canGoBack() == true -> activeWebView.goBack()
                currentSettings.closeTabOnBack && activeTab != null && visibleTabs.size > 1 -> viewModel.closeTab(activeTab.id)
                else -> scope.launch { snackbarHost.showSnackbar("Press back again to exit") }
            }
        }
    }

    val openSelectedUrl: (String) -> Unit = { url ->
        activeTab?.let { openUrlInTab(url, it, viewModel, webViews) }
        secondaryScreen = null
    }
    val secondaryContent: @Composable () -> Unit = {
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Render the destination directly instead of through AnimatedContent: its default size
            // animation clipped the screen to a growing rectangle, so while Settings opened the web
            // page behind stayed visible around the new content (it looked transparent).
            when (val destination = secondaryScreen) {
                    "Tor" -> androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.IconButton(onClick = { secondaryScreen = null }) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text("Tor and censorship circumvention", style = MaterialTheme.typography.titleLarge)
                    }
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Aero uses Android WebView and cannot route its own traffic through Tor bridges. This shortcut opens the official Tor Browser, which protects only its own browsing; it does not proxy Aero or other apps.")
                        Text("For Iran, Tor Project recommends starting with Connection Assist: in Tor Browser tap ‘Try a bridge’; if it cannot detect your location, choose Iran and try again. You can also configure a bridge in Settings → Connection → Config Bridge. Snowflake is built in; for obfs4, request current bridge addresses from the official @GetBridgesBot and choose ‘Provide a Bridge I know’. WebTunnel support and success vary by network and region.")
                        Text("Tor connections may still fail during severe internet restrictions or international connectivity disruptions, especially on mobile networks. Bridge availability changes and connection speeds vary. Install/use the official Tor Browser; Aero traffic remains outside Tor and is not protected by this shortcut.")
                        Text("Using Tor via a separate VPN/proxy app is not configured by Aero and may expose identifying browser characteristics. Tor Project recommends Tor Browser for Tor browsing.", style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.Button(onClick = {
                            launchTorBrowser(context, webState.url ?: activeTab?.url)
                        }) { Text("Open this page in Tor Browser") }
                    }
                    }
                    "Bookmarks" -> BookmarksScreen(
                        onBack = { secondaryScreen = null },
                        onOpenUrl = openSelectedUrl,
                        viewModel = bookmarksViewModel,
                    )
                    "History" -> HistoryScreen(
                        onBack = { secondaryScreen = null },
                        onOpenUrl = openSelectedUrl,
                        viewModel = historyViewModel,
                    )
                    "Downloads" -> DownloadsScreen(onBack = { secondaryScreen = null }, viewModel = downloadsViewModel)
                    "Settings" -> SettingsScreen(
                        onBack = { secondaryScreen = null },
                    onThemeChanged = { theme, dynamic -> ThemePreferences.mode = theme; ThemePreferences.dynamicColor = dynamic },
                    onSetHomepage = { homepage -> activeTab?.let { openUrlInTab(homepage, it, viewModel, webViews) } },
                    onClearBrowsingData = { range ->
                        scope.launch {
                            val cutoff = range.cutoff()
                            if (cutoff == null) {
                                historyViewModel.clear()
                                webViews.values.forEach { it.clearHistory(); it.clearCache(true) }
                                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                                    runCatching { ProfileStore.getInstance().getOrCreateProfile("aero_normal").cookieManager.removeAllCookies(null) }
                                    webViews.values.filter { it.getTag(R.id.webview_private_mode) != true }.forEach { web ->
                                        runCatching { WebViewCompat.getProfile(web).cookieManager.removeAllCookies(null) }
                                    }
                                } else {
                                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                                }
                                android.webkit.CookieManager.getInstance().flush()
                                settingsViewModel.clearSitePermissions()
                            } else {
                                historyViewModel.clearSince(cutoff)
                            }
                            snackbarHost.showSnackbar("${range.title} browsing data cleared.")
                        }
                    },
                    viewModel = settingsViewModel,
                )
                    else -> Unit
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(tabCanvasColor)
            .onGloballyPositioned { scaffoldWindowOrigin = it.localToWindow(Offset.Zero) }
            .pointerInput(addressEditing, addressBounds, scaffoldWindowOrigin) {
                if (addressEditing) {
                    awaitEachGesture {
                        val down = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.pressed && !it.previousPressed }
                        val positionInWindow = down?.position?.plus(scaffoldWindowOrigin)
                        if (down != null && positionInWindow != null && !addressBounds.contains(positionInWindow)) {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                            } while (event.changes.any { it.pressed })
                            addressDismissRequest++
                            addressEditing = false
                        }
                    }
                }
            },
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = tabCanvasColor,
        bottomBar = {
            // The overview owns the bottom of the screen: hide (without a fade) so its own buttons
            // stay tappable and dismissing it never animates the chrome in.
            if (secondaryScreen == null && !overviewVisible) {
                BottomToolbar(
                    canGoBack = webState.canGoBack,
                    canGoForward = webState.canGoForward,
                    onBack = { activeTabId?.let(webViews::get)?.takeIf(WebView::canGoBack)?.goBack() },
                    onForward = { activeTabId?.let(webViews::get)?.takeIf(WebView::canGoForward)?.goForward() },
                    onNewTab = { viewModel.createTab(currentSettings.homepage) },
                    onTabs = viewModel::toggleTabOverview,
                    tabCount = visibleTabs.size,
                    onMenu = { menuVisible = true },
                )
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().background(tabCanvasColor).padding(contentPadding)) {
            Column(Modifier.fillMaxSize().background(tabCanvasColor)) {
                // The omnibox stays composed under the tab overview (which is opaque and drawn on
                // top). Fading it in and out made the whole top bar flash on every tab action.
                if (secondaryScreen == null) {
                    Omnibox(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            windowTopBarBottom = coordinates.boundsInWindow().bottom.toInt()
                        },
                        url = webState.url ?: activeTab?.url.orEmpty(),
                        isLoading = webState.isLoading,
                        progress = webProgress,
                        secure = (webState.url ?: activeTab?.url).orEmpty().startsWith("https://", true),
                        certificateDetails = certificate,
                        onNavigate = { navigateInActiveTab(it, activeTab, currentSettings, viewModel, webViews) },
                        isBookmarked = bookmarkStatus,
                        onToggleBookmark = { toggleBookmark(bookmarkStatus, webState, activeTab, bookmarksViewModel) },
                        onReload = { activeTabId?.let(webViews::get)?.let { if (webState.isLoading) it.stopLoading() else it.reload() } },
                        incognito = privateMode,
                        onEditorBoundsChanged = { addressBounds = it },
                        onEditingChanged = { addressEditing = it },
                        dismissEditingRequest = if (addressEditing) 0 else addressDismissRequest,
                        onTorClick = { secondaryScreen = "Tor" },
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(tabCanvasColor)) {
                        if (activeTab == null) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        } else androidx.compose.runtime.key(activeTab.id, rendererGeneration) {
                                val tab = activeTab
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { viewContext ->
                                        val webView = SecureWebView.create(
                                            context = viewContext,
                                            trackingProtection = trackingProtection,
                                            httpsEnforcer = HttpsEnforcer,
                                            privateMode = tab.isPrivate,
                                            privateProfileName = "aero_private_${tab.id}",
                                            httpsOnly = { settingsViewModel.httpsOnlyEnabled() },
                                            doNotTrackEnabled = { settingsViewModel.doNotTrackEnabled() },
                                            settings = currentSettings,
                                            onPageFinished = { _, url, title ->
                                                pageLoadFailed = false
                                                if (!tab.isPrivate && url != "about:blank") historyViewModel.record(url, title)
                                            },
                                        )
                                        webView.setDownloadListener { url, userAgent, disposition, mimeType, _ ->
                                            downloadsViewModel.start(
                                                url, userAgent, disposition, mimeType, webView.title,
                                                onStarted = { id -> scope.launch { snackbarHost.showSnackbar("Download started: $id") } },
                                                onFailure = { failure -> scope.launch { snackbarHost.showSnackbar(failure.message ?: "Unable to start download") } },
                                            )
                                        }
                                        webView.tag = tab.id
                                        webView.visibility = if (pageVisible) View.VISIBLE else View.INVISIBLE
                                        installNavigationGestures(webView)
                                        installLinkLongPress(webView, pageUrl = { webView.url ?: tab.url }) { linkTarget = it }
                                        if (tab.isPrivate) viewModel.attachPrivateWebView(tab.id)
                                        webView.setBackgroundColor(tabCanvasColor.toArgb())
                                        webView.webChromeClient = com.aero.browser.core.webengine.SecureWebChromeClient(
                                            progressListener = { changedView, progress ->
                                                if (changedView === webView && tab.id == activeTabId) webProgress = progress
                                            },
                                            sitePermissions = sitePermissionController,
                                            onEnterFullscreen = enterFullscreen,
                                            onExitFullscreen = { leaveFullscreen(false) },
                                        )
                                        webViews[tab.id] = webView
                                        installBrowserListener(webView, tab, viewModel, onRendererGone = {
                                            rendererCrashed = true
                                            crashedWebViewTabId = tab.id
                                            recoveredRenderer = false
                                        }) { state ->
                                            if (tab.id == activeTabId) {
                                                webState = state
                                                webProgress = if (state.isLoading) webProgress.coerceAtLeast(1) else 100
                                                certificate = webView.certificate?.toDetails()
                                                if (!state.isLoading && state.title?.startsWith("Load error:") == true) {
                                                    pageLoadFailed = !context.hasNetworkConnection()
                                                }
                                            }
                                        }
                                        SecureWebView.updateWebRtcRestriction(webView, currentSettings.blockWebRtc)
                                        val restored = if (recoveredRenderer) null else viewModel.restoreTabState(tab.id)?.let { webView.restoreState(Bundle(it)) }
                                        recoveredRenderer = false
                                        if (restored == null) webView.loadUrl(if (currentSettings.httpsOnly) HttpsEnforcer.upgrade(tab.url) ?: tab.url else tab.url)
                                        webView
                                    },
                                    update = { webView ->
                                        webViews[tab.id] = webView
                                        webView.setBackgroundColor(tabCanvasColor.toArgb())
                                        webView.visibility = if (pageVisible) View.VISIBLE else View.INVISIBLE
                                        webView.settings.userAgentString = if (desktopSite) desktopUserAgent(webView.settings.userAgentString) else mobileUserAgent(webView.settings.userAgentString)
                                        if (webView.url == null) webView.loadUrl(if (currentSettings.httpsOnly) HttpsEnforcer.upgrade(tab.url) ?: tab.url else tab.url)
                                    },
                                )
                                DisposableEffect(tab.id, lifecycleOwner, rendererGeneration) {
                                    val observer = LifecycleEventObserver { _, event ->
                                        if (event == Lifecycle.Event.ON_PAUSE && crashedWebViewTabId != tab.id) webViews[tab.id]?.let { persistWebView(it, tab, viewModel) }
                                    }
                                    lifecycleOwner.lifecycle.addObserver(observer)
                                    onDispose {
                                        lifecycleOwner.lifecycle.removeObserver(observer)
                                        val released = webViews.remove(tab.id)
                                        if (released != null) {
                                            if (crashedWebViewTabId != tab.id) persistWebView(released, tab, viewModel)
                                            (released.getTag(R.id.webview_state_listener) as? StateListenerRegistry.Listener)?.let(StateListenerRegistry::removeListener)
                                            if (crashedWebViewTabId != tab.id) {
                                                released.stopLoading()
                                                SecureWebView.removeDoNotTrackScript(released)
                                                SecureWebView.removeWebRtcRestriction(released)
                                                SecureWebView.removeVideoGestureSeek(released)
                                                released.webChromeClient = null
                                                released.webViewClient = android.webkit.WebViewClient()
                                                released.destroy()
                                            }
                                            if (tab.isPrivate) viewModel.detachPrivateWebView(tab.id)
                                        }
                                    }
                                }
                            }
                            when {
                                rendererCrashed -> ErrorOverlay("Page crashed", "The page stopped responding. Reload it to continue.", "Reload") {
                                    activeTab?.let { tab ->
                                        viewModel.updateTab(tab.id) { it.copy(webViewState = null, isLoading = false) }
                                        recoveredRenderer = true
                                        rendererCrashed = false
                                        rendererGeneration++
                                    }
                                }
                                !connected || pageLoadFailed -> ErrorOverlay("No Internet", "Check your connection, then try loading this page again.", "Retry") {
                                    connected = context.hasNetworkConnection()
                                    pageLoadFailed = false
                                    activeTab?.let { webViews[it.id]?.reload() }
                                }
                            }
                        }
                    }
                    if (secondaryScreen != null) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { secondaryContent() }
                    }
                }
            }

            TabGrid(
                tabs = visibleTabs,
                activeTabId = activeTabId,
                visible = overviewVisible,
                isPrivateMode = privateMode,
                normalTabCount = normalTabs.size,
                privateTabCount = privateTabs.size,
                onSelectTab = viewModel::switchTab,
                onCloseTab = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.closeTab(it)
                },
                onCloseAll = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.closeAllTabs()
                },
                onTogglePrivate = { viewModel.setPrivateMode(!privateMode) },
                onDismiss = viewModel::toggleTabOverview,
                onNewTab = { viewModel.createTab(currentSettings.homepage) },
                modifier = Modifier.fillMaxSize().background(tabCanvasColor),
                topBarBottom = (windowTopBarBottom - WindowInsets.safeDrawing.getTop(density)).coerceAtLeast(0),
            )
            BrowserMenu(
                visible = menuVisible,
                desktopSite = desktopSite,
                onDismiss = { menuVisible = false },
                onNewPrivateTab = {
                    menuVisible = false
                    val hadPrivateTabs = viewModel.hasPrivateTabs()
                    val target = when {
                        !privateMode -> { viewModel.setPrivateMode(true); viewModel.activeTab()?.id }
                        hadPrivateTabs -> viewModel.createTab(currentSettings.homepage)
                        else -> activeTabId
                    }
                    target?.let { id ->
                        val destinationTab = if (privateMode) privateTabs.firstOrNull { it.id == id } else normalTabs.firstOrNull { it.id == id }
                        destinationTab?.let { openUrlInTab(currentSettings.homepage, it, viewModel, webViews) }
                    }
                },
                onBookmarks = { menuVisible = false; secondaryScreen = "Bookmarks" },
                onHistory = { menuVisible = false; secondaryScreen = "History" },
                onDownloads = { menuVisible = false; secondaryScreen = "Downloads" },
                onSettings = { menuVisible = false; secondaryScreen = "Settings" },
                onFindInPage = { menuVisible = false; findDialog = true },
                onDoNotTrackChanged = { enabled ->
                    settingsViewModel.setDoNotTrack(enabled)
                    webViews.values.forEach { SecureWebView.updateDoNotTrack(it, enabled) }
                },
                trackingLevel = currentSettings.trackingLevel,
                doNotTrackEnabled = currentSettings.doNotTrack,
                blockWebRtc = currentSettings.blockWebRtc,
                onBlockWebRtcChanged = { enabled ->
                    settingsViewModel.setBlockWebRtc(enabled)
                    webViews.values.forEach { SecureWebView.updateWebRtcRestriction(it, enabled) }
                },
                blockedHostCount = trackingProtection.blockedHostCount,
                onDesktopSiteChanged = { enabled ->
                    desktopSite = enabled
                    activeTabId?.let(webViews::get)?.let { web ->
                        web.settings.userAgentString = if (enabled) desktopUserAgent(web.settings.userAgentString) else mobileUserAgent(web.settings.userAgentString)
                        web.reload()
                    }
                },
                onShare = {
                    menuVisible = false
                    val current = webState.url ?: activeTab?.url.orEmpty()
                    if (current.isNotBlank()) {
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, current)
                        }
                        context.startActivity(android.content.Intent.createChooser(share, "Share page"))
                    }
                },
            )
            LinkContextMenu(
                target = linkTarget,
                privateMode = privateMode,
                onDismiss = { linkTarget = null },
                onOpenInNewTab = { url, wantPrivate ->
                    linkTarget = null
                    if (wantPrivate && !privateMode) viewModel.setPrivateMode(true)
                    viewModel.createTab(url)
                },
                onCopy = { url ->
                    linkTarget = null
                    copyToClipboard(context, url)
                    scope.launch { snackbarHost.showSnackbar("Link copied") }
                },
                onShare = { url ->
                    linkTarget = null
                    shareText(context, url, "Share link")
                },
                onDownloadImage = { url ->
                    linkTarget = null
                    val page = activeTabId?.let(webViews::get)
                    downloadsViewModel.start(
                        url, page?.settings?.userAgentString, null, "image/*", page?.title,
                        onStarted = { id -> scope.launch { snackbarHost.showSnackbar("Download started: $id") } },
                        onFailure = { failure -> scope.launch { snackbarHost.showSnackbar(failure.message ?: "Unable to start download") } },
                    )
                },
            )
        }

    permissionPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = {
                prompt.decide(prompt.permissions.associateWith { SitePermission.ASK })
                permissionPrompt = null
            },
            title = { Text("Allow ${prompt.permissions.joinToString().lowercase()}?") },
            text = { Text("${prompt.origin} is requesting access. You can change this decision later in Settings > Site permissions.") },
            confirmButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = {
                        prompt.decide(prompt.permissions.associateWith { SitePermission.ALLOW })
                        permissionPrompt = null
                    }) { Text("Allow") }
                    TextButton(onClick = {
                        prompt.decide(prompt.permissions.associateWith { SitePermission.BLOCK })
                        permissionPrompt = null
                    }) { Text("Block") }
                }
            },
            dismissButton = { TextButton(onClick = {
                prompt.decide(prompt.permissions.associateWith { SitePermission.ASK })
                permissionPrompt = null
            }) { Text("Ask next time") } },
        )
    }

    if (findDialog) {
        AlertDialog(
            onDismissRequest = { findDialog = false; activeTabId?.let(webViews::get)?.clearMatches() },
            title = { Text("Find in page") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it; activeTabId?.let(webViews::get)?.findAllAsync(it) },
                    singleLine = true,
                    label = { Text("Text to find") },
                )
            },
            confirmButton = { TextButton(onClick = { activeTabId?.let(webViews::get)?.findNext(true) }) { Text("Next") } },
            dismissButton = { TextButton(onClick = { findDialog = false; activeTabId?.let(webViews::get)?.clearMatches() }) { Text("Done") } },
        )
    }
}

private data class PermissionPrompt(
    val origin: String,
    val permissions: Set<String>,
    val decide: (Map<String, SitePermission>) -> Unit,
)

private fun String.androidPermissions(): List<String> = when (this) {
    "Camera" -> listOf(Manifest.permission.CAMERA)
    "Microphone" -> listOf(Manifest.permission.RECORD_AUDIO)
    "Location" -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    else -> emptyList()
}

private fun String.permissionName(): String? = when (this) {
    Manifest.permission.CAMERA -> "Camera"
    Manifest.permission.RECORD_AUDIO -> "Microphone"
    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION -> "Location"
    else -> null
}

internal fun shouldExitBrowserOnBack(lastBackPressAt: Long, now: Long): Boolean {
    return lastBackPressAt > 0L && now - lastBackPressAt in 1..2_000
}

@Composable
private fun ErrorOverlay(title: String, message: String, actionLabel: String, onAction: () -> Unit) {
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f), modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(message, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAction, modifier = Modifier.padding(top = 20.dp)) { Text(actionLabel) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Hosts an HTML5 fullscreen view above the browser chrome for as long as the page needs it. */
private fun Activity.addFullscreenContent(view: View): FrameLayout {
    (view.parent as? ViewGroup)?.removeView(view)
    val host = FrameLayout(this).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        keepScreenOn = true
        addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
    addContentView(
        host,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
    return host
}

private fun Context.hasNetworkConnection(): Boolean {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun launchTorBrowser(context: Context, currentUrl: String?) {
    val target = currentUrl?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
    val torBrowserPackage = "org.torproject.torbrowser"
    if (target != null) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(target))
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
        val torHandler = handlers.firstOrNull { it.activityInfo.packageName == torBrowserPackage }
        if (torHandler != null) {
            intent.setPackage(torBrowserPackage)
            context.startActivity(intent)
            return
        }
    }

    val installPage = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$torBrowserPackage"),
    )
    context.startActivity(installPage)
}

private fun navigateInActiveTab(raw: String, tab: Tab?, settings: BrowserSettings, viewModel: TabManagerViewModel, webViews: Map<String, WebView>) {
    val target = normalizeBrowserInput(raw, settings.searchEngine, settings.httpsOnly)
    tab?.let { openUrlInTab(target, it, viewModel, webViews) }
}

private fun openUrlInTab(url: String, tab: Tab, viewModel: TabManagerViewModel, webViews: Map<String, WebView>) {
    viewModel.updateTab(tab.id) { it.copy(url = url, title = url, isLoading = true) }
    webViews[tab.id]?.loadUrl(url)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link", text))
}

private fun shareText(context: Context, text: String, title: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(send, title))
}

private fun toggleBookmark(bookmarked: Boolean, state: WebViewUiState, tab: Tab?, viewModel: BookmarksViewModel) {
    val url = state.url ?: tab?.url.orEmpty()
    if (bookmarked) viewModel.remove(url) else viewModel.add(state.title ?: tab?.title.orEmpty(), url, tab?.favicon)
}

private fun normalizeBrowserInput(text: String, engine: String = "DuckDuckGo", httpsOnly: Boolean = true): String {
    val value = text.trim()
    return when {
        value.isEmpty() -> "https://duckduckgo.com/"
        value.startsWith("https://", true) -> TrackingParamStripper.strip(value) ?: value
        value.startsWith("http://", true) -> {
            val target = if (httpsOnly) HttpsEnforcer.upgrade(value) ?: value else value
            TrackingParamStripper.strip(target) ?: target
        }
        value.startsWith("about:", true) -> value
        value.contains('.') && !value.contains(' ') -> "https://$value"
        else -> searchUrl(engine, value)
    }
}

private fun searchUrl(engine: String, query: String): String {
    val encoded = android.net.Uri.encode(query)
    val base = when (engine) {
        "Google" -> "https://www.google.com/search?q="
        "Bing" -> "https://www.bing.com/search?q="
        "Brave" -> "https://search.brave.com/search?q="
        "Ecosia" -> "https://www.ecosia.org/search?q="
        else -> "https://duckduckgo.com/?q="
    }
    return base + encoded
}

private fun SslCertificate.toDetails(): CertificateDetails = CertificateDetails(
    issuer = issuedBy?.toString().orEmpty().ifBlank { "Unknown" },
    validFrom = DateFormat.getDateTimeInstance().format(validNotBeforeDate),
    validUntil = DateFormat.getDateTimeInstance().format(validNotAfterDate),
)

private fun desktopUserAgent(userAgent: String): String = userAgent.replace("; wv)", ")").replace(" Mobile", "")
private fun mobileUserAgent(userAgent: String): String = if (userAgent.contains(" Mobile")) userAgent else userAgent.replace(" (", " Mobile (")

private fun persistWebView(webView: WebView, tab: Tab, viewModel: TabManagerViewModel) {
    val state = Bundle()
    webView.saveState(state)
    viewModel.saveTabState(tab.id, state)
    viewModel.updatePage(tab.id, webView.url ?: tab.url, webView.title, webView.progress in 1..99)
}

private fun installBrowserListener(
    webView: WebView,
    tab: Tab,
    viewModel: TabManagerViewModel,
    onRendererGone: () -> Unit,
    onPageFinished: (String, String?) -> Unit = { _, _ -> },
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
            onPageFinished(url, title)
            onState(WebViewUiState(url = url, title = title, isLoading = false, canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward()))
        }
        override fun onSslBlocked(view: WebView, primaryError: Int) {
            if (view === webView) onState(WebViewUiState(url = view.url, title = "Blocked: certificate error", isLoading = false))
        }
        override fun onLoadError(view: WebView, url: String, description: String?) {
            if (view === webView) onState(WebViewUiState(url = url, title = "Load error: $description", isLoading = false))
        }
        override fun onRenderProcessGone(view: WebView) {
            if (view === webView && viewModel.isTabOpen(tab.id)) {
                webView.setTag(R.id.webview_state_listener, null)
                view.webViewClient = android.webkit.WebViewClient()
                view.webChromeClient = null
                viewModel.updateTab(tab.id) { it.copy(webViewState = null, isLoading = false) }
                onRendererGone()
            }
        }
    }
    StateListenerRegistry.addListener(listener)
    webView.setTag(R.id.webview_state_listener, listener)
}

@SuppressLint("ClickableViewAccessibility")
private fun installNavigationGestures(webView: WebView) {
    var downX = 0f
    var downY = 0f
    var startScrollY = 0
    var refreshed = false
    val density = webView.resources.displayMetrics.density
    webView.setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startScrollY = view.scrollY
                refreshed = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - downY
                val currentWebView = view as? WebView
                if (!refreshed && currentWebView != null && startScrollY <= 0 && dy > 96 * density && dy > kotlin.math.abs(event.x - downX) * 1.3f) {
                    refreshed = true
                    currentWebView.reload()
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val currentWebView = view as? WebView
                if (downX <= 24 * density && dx > 100 * density && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.4f && currentWebView?.canGoBack() == true) currentWebView.goBack()
            }
        }
        false
    }
}
