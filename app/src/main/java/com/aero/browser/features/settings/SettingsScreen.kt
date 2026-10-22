package com.aero.browser.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.aero.browser.core.network.doh.DnsStatus
import com.aero.browser.core.network.doh.DohProvider
import com.aero.browser.features.history.ClearDataRange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (theme: String, dynamicColor: Boolean) -> Unit,
    onSetHomepage: (String) -> Unit,
    onClearBrowsingData: (ClearDataRange) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val sitePermissions by viewModel.sitePermissions.collectAsState()
    val dnsStatus by viewModel.dnsStatus.collectAsState()
    var showSecureDns by remember { mutableStateOf(false) }
    var searchEngineScreen by remember { mutableStateOf(false) }
    var homepageEditing by remember { mutableStateOf(false) }
    var homepageDraft by remember(settings.homepage) { mutableStateOf(settings.homepage) }
    var clearDataRangePicker by remember { mutableStateOf(false) }
    var pendingClearRange by remember { mutableStateOf<ClearDataRange?>(null) }
    var showSitePermissions by remember { mutableStateOf(false) }
    var trackingLevelDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                }
                androidx.compose.foundation.lazy.LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp)) {
                    item { CategoryHeader("General") }
                    item { SettingRow("Pop-up windows", "Blocked for safety; websites cannot open unsolicited windows") }
                    item { SettingRow("Autoplay media", "Require a user gesture before audio or video can play", trailing = { Switch(checked = settings.mediaPlaybackRequiresUserGesture, onCheckedChange = viewModel::setMediaPlaybackRequiresUserGesture) }) }
                    item { SettingRow("Desktop site by default", "Request desktop versions of websites", trailing = { Switch(checked = settings.desktopSiteByDefault, onCheckedChange = viewModel::setDesktopSiteByDefault) }) }
                    item { SettingRow("Close tab on back", "Close this tab when it has no page history", trailing = { Switch(checked = settings.closeTabOnBack, onCheckedChange = viewModel::setCloseTabOnBack) }) }
                    item { SettingRow("Search engine", settings.searchEngine, leading = { Icon(Icons.Filled.Search, contentDescription = null) }, onClick = { searchEngineScreen = true }) }
                    item { SettingRow("Homepage", settings.homepage, onClick = { homepageDraft = settings.homepage; homepageEditing = true }) }
                    item { SettingRow("JavaScript", "Required by most modern websites", trailing = { Switch(checked = settings.javaScriptEnabled, onCheckedChange = viewModel::setJavaScriptEnabled) }) }
                    item { SettingRow("Load images", "Disable images to reduce mobile data usage", trailing = { Switch(checked = settings.imagesEnabled, onCheckedChange = viewModel::setImagesEnabled) }) }
                    item {
                        SettingRow("Page zoom", "${settings.pageZoom}%", trailing = {
                            Slider(value = settings.pageZoom.toFloat(), onValueChange = { viewModel.setPageZoom(it.toInt()) }, valueRange = 50f..200f, steps = 14, modifier = Modifier.fillMaxWidth(0.42f))
                        })
                    }

                    item { CategoryHeader("Privacy & security") }
                    item { SettingRow("Block third-party cookies", "Reduce cross-site tracking; some sign-ins may be affected", trailing = { Switch(checked = settings.blockThirdPartyCookies, onCheckedChange = viewModel::setBlockThirdPartyCookies) }) }
                    item { SettingRow("Block WebRTC", "Limit WebRTC APIs in page JavaScript; may affect calls", trailing = { Switch(checked = settings.blockWebRtc, onCheckedChange = viewModel::setBlockWebRtc) }) }
                    item { SettingRow("Do Not Track", "Optional browser signal; websites may ignore it", trailing = { Switch(checked = settings.doNotTrack, onCheckedChange = viewModel::setDoNotTrack) }) }
                    item { SettingRow("Safe Browsing", "Use Android WebView's supported threat protection", trailing = { Switch(checked = settings.safeBrowsingEnabled, onCheckedChange = viewModel::setSafeBrowsingEnabled) }) }
                    item { SettingRow("DOM storage", "Required by many websites; disable to reduce local site storage", trailing = { Switch(checked = settings.domStorageEnabled, onCheckedChange = viewModel::setDomStorageEnabled) }) }
                    item { SettingRow("Clear browsing data", "Choose Today, Last week, Last month, or Full", leading = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) }, onClick = { clearDataRangePicker = true }) }

                    item { CategoryHeader("Privacy") }
                    item { SettingRow("HTTPS-only mode", "Upgrade insecure HTTP connections", trailing = { Switch(checked = settings.httpsOnly, onCheckedChange = viewModel::setHttpsOnly) }) }
                    item {
                        SettingRow(
                            "Secure DNS (DNS over HTTPS)",
                            when {
                                !settings.secureDns -> "Encrypt name lookups inside Aero only; other apps keep the system resolver"
                                settings.secureDns && viewModel.dnsEndpoint() == null -> "Choose a resolver or enter a valid HTTPS DoH URL"
                                dnsStatus.error != null -> dnsStatus.error!!
                                dnsStatus.active -> "Active in Aero only · ${DohProvider.fromId(settings.dnsProvider).label} · ${dnsStatus.lookups} lookups"
                                else -> "Starting…"
                            },
                            leading = { Icon(Icons.Filled.Public, contentDescription = null) },
                            trailing = { Switch(checked = settings.secureDns, onCheckedChange = viewModel::setSecureDns) },
                            onClick = { showSecureDns = true },
                        )
                    }
                    item {
                        SettingRow(
                            "DNS provider",
                            when {
                                settings.dnsProvider == DohProvider.CUSTOM.id -> settings.dnsCustomUrl.ifBlank { "Custom HTTPS endpoint not set" }
                                else -> DohProvider.fromId(settings.dnsProvider).label + " · encrypts DNS for Aero only"
                            },
                            onClick = { showSecureDns = true },
                        )
                    }
                    item { SettingRow("Block banner-like images", "Block image requests with ad/banner tokens in the URL path (can occasionally hide wanted images)", trailing = { Switch(checked = settings.blockBannerImages, onCheckedChange = viewModel::setBlockBannerImages) }) }
                    item {
                        SettingRow(
                            "Block ads and trackers",
                            when (settings.trackingLevel) {
                                "Strict" -> "Strict · curated tracker domains"
                                "Balanced" -> "Balanced · smaller list for fewer site breakages"
                                else -> "Off · allow all page requests"
                            },
                            leading = { Icon(Icons.Filled.Security, contentDescription = null) },
                            onClick = { trackingLevelDialog = true },
                        )
                    }
                    item { CategoryHeader("Site permissions") }
                    item {
                        SettingRow(
                            "Manage website permissions",
                            if (sitePermissions.isEmpty()) "Default: Ask before a website can access your camera, microphone, or location" else "${sitePermissions.size} websites have saved decisions · default: Ask",
                            leading = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                            onClick = { showSitePermissions = true },
                        )
                    }
                    item { SettingRow("Fingerprinting protection", "WebView exposes some device and rendering details; engine-level masking is unavailable", leading = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) }) }
                    item { CategoryHeader("Appearance") }
                    item { SettingRow("Smooth transitions", "Use animated screen changes", trailing = { Switch(checked = settings.smoothTransitions, onCheckedChange = viewModel::setSmoothTransitions) }) }
                    item {
                        SettingRow("Theme", settings.theme, onClick = {
                            val next = when (settings.theme) { "System" -> "Light"; "Light" -> "Dark"; else -> "System" }
                            viewModel.setTheme(next)
                            onThemeChanged(next, settings.dynamicColor)
                        })
                    }
                    item {
                        SettingRow("Dynamic color", "Use wallpaper colors on supported Android versions", trailing = {
                            Switch(checked = settings.dynamicColor, onCheckedChange = {
                                viewModel.setDynamicColor(it)
                                onThemeChanged(settings.theme, it)
                            })
                        })
                    }
                }
            }
        }

        AnimatedVisibility(visible = showSitePermissions, enter = fadeIn(), exit = fadeOut()) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SitePermissionsSettingsScreen(
                    permissions = sitePermissions,
                    onDecisionChanged = viewModel::setSitePermission,
                    onClear = viewModel::clearSitePermissions,
                    onBack = { showSitePermissions = false },
                )
            }
        }
        AnimatedVisibility(visible = showSecureDns, enter = fadeIn(), exit = fadeOut()) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SecureDnsScreen(
                    settings = settings,
                    status = dnsStatus,
                    endpoint = viewModel.dnsEndpoint(),
                    onProvider = viewModel::setDnsProvider,
                    onCustomUrl = viewModel::setDnsCustomUrl,
                    onFallbackChanged = viewModel::setDnsFallbackToSystem,
                    onBack = { showSecureDns = false },
                )
            }
        }
        AnimatedVisibility(visible = searchEngineScreen, enter = fadeIn(), exit = fadeOut()) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SearchEngineScreen(
                    selected = settings.searchEngine,
                    onSelect = { engine -> viewModel.setSearchEngine(engine); searchEngineScreen = false },
                    onBack = { searchEngineScreen = false },
                )
            }
        }
    }

    if (trackingLevelDialog) {
        AlertDialog(
            onDismissRequest = { trackingLevelDialog = false },
            title = { Text("Tracking protection") },
            text = {
                Column {
                    listOf("Strict", "Balanced", "Off").forEach { level ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.setTrackingLevel(level); trackingLevelDialog = false }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = settings.trackingLevel == level, onClick = { viewModel.setTrackingLevel(level); trackingLevelDialog = false })
                            Text(level, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Text("Uses domain matching in Android WebView. Cosmetic rules and all browser-engine requests are not covered.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { trackingLevelDialog = false }) { Text("Done") } },
        )
    }

    if (homepageEditing) {
        AlertDialog(
            onDismissRequest = { homepageEditing = false },
            title = { Text("Homepage URL") },
            text = { OutlinedTextField(value = homepageDraft, onValueChange = { homepageDraft = it }, singleLine = true, label = { Text("HTTPS URL") }) },
            confirmButton = {
                TextButton(onClick = {
                    val safeUrl = homepageDraft.trim().let { if (it.startsWith("https://", true)) it else "https://$it" }
                    viewModel.setHomepage(safeUrl, onSetHomepage)
                    homepageEditing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { homepageEditing = false }) { Text("Cancel") } },
        )
    }

    if (clearDataRangePicker) {
        AlertDialog(
            onDismissRequest = { clearDataRangePicker = false },
            title = { Text("Choose a time range") },
            text = {
                Column {
                    ClearDataRange.entries.forEach { range ->
                        TextButton(onClick = { clearDataRangePicker = false; pendingClearRange = range }) {
                            Text(range.title, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { clearDataRangePicker = false }) { Text("Cancel") } },
        )
    }
    pendingClearRange?.let { range ->
        AlertDialog(
            onDismissRequest = { pendingClearRange = null },
            title = { Text("Clear ${range.title.lowercase()} data?") },
            text = {
                Text(
                    if (range == ClearDataRange.FULL) "This clears all history, cached pages, and cookies. This cannot be undone."
                    else "This clears history from the selected period. Android WebView does not support removing cookies or cache by date range, so those are left unchanged.",
                )
            },
            confirmButton = { TextButton(onClick = { onClearBrowsingData(range); pendingClearRange = null }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { pendingClearRange = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SearchEngineScreen(selected: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Search engine", style = MaterialTheme.typography.headlineSmall)
        }
        listOf("DuckDuckGo", "Google", "Bing", "Brave", "Ecosia").forEach { engine ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(engine) }.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == engine, onClick = { onSelect(engine) })
                Text(engine, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun SecureDnsScreen(
    settings: BrowserSettings,
    status: DnsStatus,
    endpoint: String?,
    onProvider: (DohProvider) -> Unit,
    onCustomUrl: (String) -> Unit,
    onFallbackChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember(settings.dnsCustomUrl) { mutableStateOf(settings.dnsCustomUrl) }
    val statusText = when {
        !settings.secureDns -> "Off · Aero uses the system resolver"
        status.error != null -> status.error
        status.active -> buildString {
            append("Active for Aero only · 127.0.0.1:").append(status.port)
            append(" · ").append(status.lookups).append(" encrypted lookups")
            if (status.fallbacks > 0) append(" · ").append(status.fallbacks).append(" system fallbacks")
            if (status.failures > 0) append(" · ").append(status.failures).append(" failed lookups")
        }
        endpoint == null -> "Choose a resolver below"
        else -> "Starting…"
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Secure DNS", style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Text(statusText, style = MaterialTheme.typography.bodyLarge, color = if (status.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(
                "Aero resolves names for its own WebView through DNS over HTTPS. The tunnel is a loopback proxy registered with WebView, so the system resolver, the system proxy/VPN settings, and other apps are untouched. HTTPS traffic is tunnelled without inspection, so certificate checks still happen in the browser. Only the DoH server's own hostname is looked up by the system once, to open the encrypted connection.",
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Resolver", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            DohProvider.entries.forEach { provider ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onProvider(provider) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.dnsProvider == provider.id, onClick = { onProvider(provider) })
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(provider.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            provider.endpoint.ifEmpty { "Your own HTTPS DNS-over-HTTPS URL" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (settings.dnsProvider == DohProvider.CUSTOM.id) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("HTTPS DoH endpoint") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onCustomUrl(draft) },
                        enabled = DohProvider.normalizeEndpoint(draft) != null,
                    ) { Text("Save endpoint") }
                    if (draft.isNotBlank() && DohProvider.normalizeEndpoint(draft) == null) {
                        Text("Must be an https:// URL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fall back to system DNS", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "When the DoH server is unreachable, resolve through the device instead of failing the load. Disable for strict privacy (pages fail rather than leak lookups).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.dnsFallbackToSystem, onCheckedChange = onFallbackChanged)
            }
            Text(
                "Secure DNS is off by default. Changes apply immediately to open tabs after they reload.",
                modifier = Modifier.padding(top = 18.dp, bottom = 28.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(title, modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 22.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f).padding(horizontal = if (leading != null) 14.dp else 0.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}
