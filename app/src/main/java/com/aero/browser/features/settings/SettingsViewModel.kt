package com.aero.browser.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aero.browser.core.network.doh.BrowserDnsController
import com.aero.browser.core.network.doh.DnsStatus
import com.aero.browser.core.network.doh.DohProvider
import com.aero.browser.core.storage.SecurePreferences
import com.aero.browser.core.storage.StartPage
import com.aero.browser.core.webengine.SitePermission
import com.aero.browser.core.webengine.SitePermissionStore
import com.aero.browser.features.history.HistoryRepository
import com.aero.browser.ui.theme.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.aero.browser.core.webengine.WebViewSecurityConfig

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: SecurePreferences,
    private val historyRepository: HistoryRepository,
    private val dnsController: BrowserDnsController,
) : ViewModel() {
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<BrowserSettings> = _settings.asStateFlow()
    val dnsStatus: StateFlow<DnsStatus> = dnsController.status
    private val sitePermissionStore = SitePermissionStore(preferences)
    private val _sitePermissions = MutableStateFlow(sitePermissionStore.all())
    val sitePermissions: StateFlow<Map<String, Map<String, SitePermission>>> = _sitePermissions.asStateFlow()

    fun setSitePermission(origin: String, permission: String, decision: SitePermission) {
        sitePermissionStore.set(origin, permission, decision)
        _sitePermissions.value = sitePermissionStore.all()
    }

    fun clearSitePermissions(origin: String? = null) {
        if (origin == null) sitePermissionStore.clear() else sitePermissionStore.removeSite(origin)
        _sitePermissions.value = sitePermissionStore.all()
    }

    fun requestSitePermission(
        origin: String,
        permissions: Set<String>,
        decide: (Map<String, SitePermission>) -> Unit,
    ) {
        viewModelScope.launch {
            val decisions = permissions.associateWith { permission -> sitePermissionStore.get(origin, permission) }
            val undecided = decisions.filterValues { it == SitePermission.ASK }.keys
            if (undecided.isEmpty()) {
                decide(decisions)
                return@launch
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                requestPermissionPrompt(origin, undecided) { choices ->
                    choices.filterValues { it != SitePermission.ASK }.forEach { (permission, decision) ->
                        setSitePermission(origin, permission, decision)
                    }
                    _sitePermissions.value = sitePermissionStore.all()
                    decide(decisions + choices)
                }
            }
        }
    }

    @Volatile private var requestPermissionPrompt: (
        origin: String,
        permissions: Set<String>,
        decide: (Map<String, SitePermission>) -> Unit,
    ) -> Unit = { _, permissions, callback -> callback(permissions.associateWith { SitePermission.BLOCK }) }

    fun attachSitePermissionPrompt(
        prompt: (origin: String, permissions: Set<String>, decide: (Map<String, SitePermission>) -> Unit) -> Unit,
    ) {
        requestPermissionPrompt = prompt
    }

    init {
        applyTheme(_settings.value)
        applyDns(_settings.value)
    }

    fun setSearchEngine(value: String) = update { it.copy(searchEngine = value) }
    fun setHomepage(value: String) = update { it.copy(homepage = StartPage.normalize(value)) }
    fun setHttpsOnly(value: Boolean) = update { it.copy(httpsOnly = value) }
    fun setTheme(value: String) = update { it.copy(theme = value) }
    fun setDynamicColor(value: Boolean) = update { it.copy(dynamicColor = value) }
    fun setDoNotTrack(value: Boolean) = update { it.copy(doNotTrack = value) }
    fun setBlockWebRtc(value: Boolean) = update { it.copy(blockWebRtc = value) }
    fun setBlockThirdPartyCookies(value: Boolean) = update { it.copy(blockThirdPartyCookies = value) }
    fun setJavaScriptEnabled(value: Boolean) = update { it.copy(javaScriptEnabled = value) }
    fun setSafeBrowsingEnabled(value: Boolean) = update { it.copy(safeBrowsingEnabled = value) }
    fun setImagesEnabled(value: Boolean) = update { it.copy(imagesEnabled = value) }
    fun setPageZoom(value: Int) = update { it.copy(pageZoom = value.coerceIn(50, 200)) }
    fun setDomStorageEnabled(value: Boolean) = update { it.copy(domStorageEnabled = value) }
    fun setMediaPlaybackRequiresUserGesture(value: Boolean) = update { it.copy(mediaPlaybackRequiresUserGesture = value) }
    fun setDesktopSiteByDefault(value: Boolean) = update { it.copy(desktopSiteByDefault = value) }
    fun setCloseTabOnBack(value: Boolean) = update { it.copy(closeTabOnBack = value) }
    fun setSmoothTransitions(value: Boolean) = update { it.copy(smoothTransitions = value) }
    fun setTrackingLevel(value: String) = update { it.copy(trackingLevel = value) }
    fun setBlockBannerImages(value: Boolean) = update { it.copy(blockBannerImages = value) }
    fun setSecureDns(value: Boolean) = update { it.copy(secureDns = value) }
    fun setDnsProvider(value: DohProvider) = update { it.copy(dnsProvider = value.id) }
    fun setDnsCustomUrl(value: String) = update { it.copy(dnsCustomUrl = value.trim()) }
    fun setDnsFallbackToSystem(value: Boolean) = update { it.copy(dnsFallbackToSystem = value) }

    /** Endpoint the secure resolver would use right now, or null when unusable. */
    fun dnsEndpoint(): String? = DohProvider.endpointFor(_settings.value.dnsProvider, _settings.value.dnsCustomUrl)
    fun setHomepage(value: String, navigate: (String) -> Unit) {
        val homepage = StartPage.normalize(value)
        update { it.copy(homepage = homepage) }
        navigate(homepage)
    }
    fun clearHistory() = viewModelScope.launch { historyRepository.clear() }

    fun httpsOnlyEnabled(): Boolean = _settings.value.httpsOnly
    fun trackingProtectionLevel(): String = _settings.value.trackingLevel
    fun doNotTrackEnabled(): Boolean = _settings.value.doNotTrack

    private fun update(transform: (BrowserSettings) -> BrowserSettings) {
        val updated = transform(_settings.value)
        preferences.putString(KEY_SEARCH_ENGINE, updated.searchEngine)
        preferences.putString(StartPage.PREFERENCES_KEY, updated.homepage)
        preferences.putBoolean(KEY_HTTPS_ONLY, updated.httpsOnly)
        preferences.putString(KEY_TRACKING_LEVEL, updated.trackingLevel)
        preferences.putString(KEY_THEME, updated.theme)
        preferences.putBoolean(KEY_DYNAMIC_COLOR, updated.dynamicColor)
        preferences.putBoolean(KEY_DO_NOT_TRACK, updated.doNotTrack)
        preferences.putBoolean(KEY_BLOCK_WEBRTC, updated.blockWebRtc)
        preferences.putBoolean(KEY_BLOCK_THIRD_PARTY_COOKIES, updated.blockThirdPartyCookies)
        preferences.putBoolean(KEY_JAVASCRIPT_ENABLED, updated.javaScriptEnabled)
        preferences.putBoolean(KEY_SAFE_BROWSING_ENABLED, updated.safeBrowsingEnabled)
        preferences.putBoolean(KEY_IMAGES_ENABLED, updated.imagesEnabled)
        preferences.putInt(KEY_PAGE_ZOOM, updated.pageZoom)
        preferences.putBoolean(KEY_DOM_STORAGE_ENABLED, updated.domStorageEnabled)
        preferences.putBoolean(KEY_MEDIA_GESTURE_REQUIRED, updated.mediaPlaybackRequiresUserGesture)
        preferences.putBoolean(KEY_DESKTOP_SITE_DEFAULT, updated.desktopSiteByDefault)
        preferences.putBoolean(KEY_CLOSE_TAB_ON_BACK, updated.closeTabOnBack)
        preferences.putBoolean(KEY_SMOOTH_TRANSITIONS, updated.smoothTransitions)
        preferences.putBoolean(KEY_BLOCK_BANNER_IMAGES, updated.blockBannerImages)
        preferences.putBoolean(KEY_SECURE_DNS, updated.secureDns)
        preferences.putString(KEY_DNS_PROVIDER, updated.dnsProvider)
        preferences.putString(KEY_DNS_CUSTOM_URL, updated.dnsCustomUrl)
        preferences.putBoolean(KEY_DNS_FALLBACK, updated.dnsFallbackToSystem)
        applyTheme(updated)
        _settings.value = updated
        applyDns(updated)
    }

    private fun applyDns(settings: BrowserSettings) {
        dnsController.configure(
            enabled = settings.secureDns,
            endpoint = DohProvider.endpointFor(settings.dnsProvider, settings.dnsCustomUrl),
            fallbackToSystem = settings.dnsFallbackToSystem,
        )
    }

    private fun applyTheme(settings: BrowserSettings) {
        ThemePreferences.mode = settings.theme
        ThemePreferences.dynamicColor = settings.dynamicColor
    }

    private fun readSettings() = BrowserSettings(
        searchEngine = preferences.getString(KEY_SEARCH_ENGINE, "DuckDuckGo") ?: "DuckDuckGo",
        homepage = StartPage.read(preferences),
        httpsOnly = preferences.getBoolean(KEY_HTTPS_ONLY, true),
        trackingLevel = preferences.getString(KEY_TRACKING_LEVEL, "Strict") ?: "Strict",
        theme = preferences.getString(KEY_THEME, "System") ?: "System",
        dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, true),
        doNotTrack = preferences.getBoolean(KEY_DO_NOT_TRACK, true),
        blockWebRtc = preferences.getBoolean(KEY_BLOCK_WEBRTC, true),
        blockThirdPartyCookies = preferences.getBoolean(KEY_BLOCK_THIRD_PARTY_COOKIES, true),
        javaScriptEnabled = preferences.getBoolean(KEY_JAVASCRIPT_ENABLED, true),
        safeBrowsingEnabled = preferences.getBoolean(KEY_SAFE_BROWSING_ENABLED, true),
        imagesEnabled = preferences.getBoolean(KEY_IMAGES_ENABLED, true),
        pageZoom = preferences.getInt(KEY_PAGE_ZOOM, 100).coerceIn(50, 200),
        domStorageEnabled = preferences.getBoolean(KEY_DOM_STORAGE_ENABLED, true),
        mediaPlaybackRequiresUserGesture = preferences.getBoolean(KEY_MEDIA_GESTURE_REQUIRED, true),
        desktopSiteByDefault = preferences.getBoolean(KEY_DESKTOP_SITE_DEFAULT, false),
        closeTabOnBack = preferences.getBoolean(KEY_CLOSE_TAB_ON_BACK, false),
        smoothTransitions = preferences.getBoolean(KEY_SMOOTH_TRANSITIONS, true),
        blockBannerImages = preferences.getBoolean(KEY_BLOCK_BANNER_IMAGES, true),
        secureDns = preferences.getBoolean(KEY_SECURE_DNS, false),
        dnsProvider = preferences.getString(KEY_DNS_PROVIDER, DohProvider.CLOUDFLARE.id) ?: DohProvider.CLOUDFLARE.id,
        dnsCustomUrl = preferences.getString(KEY_DNS_CUSTOM_URL, "") ?: "",
        dnsFallbackToSystem = preferences.getBoolean(KEY_DNS_FALLBACK, true),
    )

    companion object {
        private const val KEY_SEARCH_ENGINE = "browser.searchEngine"
        private const val KEY_HTTPS_ONLY = "browser.httpsOnly"
        private const val KEY_TRACKING_LEVEL = "browser.trackingLevel"
        private const val KEY_THEME = "browser.theme"
        private const val KEY_DYNAMIC_COLOR = "browser.dynamicColor"
        private const val KEY_DO_NOT_TRACK = "browser.doNotTrack"
        private const val KEY_BLOCK_WEBRTC = "browser.blockWebRtc"
        private const val KEY_BLOCK_THIRD_PARTY_COOKIES = "browser.blockThirdPartyCookies"
        private const val KEY_JAVASCRIPT_ENABLED = "browser.javaScriptEnabled"
        private const val KEY_SAFE_BROWSING_ENABLED = "browser.safeBrowsingEnabled"
        private const val KEY_IMAGES_ENABLED = "browser.imagesEnabled"
        private const val KEY_PAGE_ZOOM = "browser.pageZoom"
        private const val KEY_DOM_STORAGE_ENABLED = "browser.domStorageEnabled"
        private const val KEY_MEDIA_GESTURE_REQUIRED = "browser.mediaGestureRequired"
        private const val KEY_DESKTOP_SITE_DEFAULT = "browser.desktopSiteDefault"
        private const val KEY_CLOSE_TAB_ON_BACK = "browser.closeTabOnBack"
        private const val KEY_SMOOTH_TRANSITIONS = "browser.smoothTransitions"
        private const val KEY_BLOCK_BANNER_IMAGES = "browser.blockBannerImages"
        private const val KEY_SECURE_DNS = "browser.secureDns"
        private const val KEY_DNS_PROVIDER = "browser.dnsProvider"
        private const val KEY_DNS_CUSTOM_URL = "browser.dnsCustomUrl"
        private const val KEY_DNS_FALLBACK = "browser.dnsFallbackToSystem"
    }
}

data class BrowserSettings(
    val searchEngine: String,
    val homepage: String,
    val httpsOnly: Boolean,
    val trackingLevel: String,
    val theme: String,
    val dynamicColor: Boolean,
    val doNotTrack: Boolean,
    val blockWebRtc: Boolean,
    val blockThirdPartyCookies: Boolean,
    val javaScriptEnabled: Boolean,
    val safeBrowsingEnabled: Boolean,
    val imagesEnabled: Boolean,
    val pageZoom: Int,
    val domStorageEnabled: Boolean,
    val mediaPlaybackRequiresUserGesture: Boolean = true,
    val desktopSiteByDefault: Boolean = false,
    val closeTabOnBack: Boolean = false,
    val smoothTransitions: Boolean = true,
    val blockBannerImages: Boolean = true,
    val secureDns: Boolean = false,
    val dnsProvider: String = "cloudflare",
    val dnsCustomUrl: String = "",
    val dnsFallbackToSystem: Boolean = true,
) {
    companion object {
        val DEFAULT = BrowserSettings("DuckDuckGo", StartPage.DEFAULT, true, "Strict", "System", true, true, true, true, true, true, true, 100, true)
    }
}
