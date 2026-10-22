package com.aero.browser.core.webengine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aero.browser.core.network.HttpsEnforcer
import com.aero.browser.core.network.TrackingProtection

/** Hardened WebView factory and Compose bridge. */
object SecureWebView {
    private const val NORMAL_PROFILE_NAME = "aero_normal"

    /** Choose the isolated profile before any URL, script, or cookie access. */
    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        trackingProtection: TrackingProtection,
        httpsEnforcer: HttpsEnforcer,
        privateMode: Boolean = false,
        privateProfileName: String = "aero_private",
        onProgressChanged: (WebView, Int) -> Unit = { _, _ -> },
        httpsOnly: () -> Boolean = { true },
        onDownload: (WebView, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
        onPageFinished: (WebView, String, String?) -> Unit = { _, _, _ -> },
        doNotTrackEnabled: () -> Boolean = { true },
        blockWebRtc: Boolean = true,
        settings: com.aero.browser.features.settings.BrowserSettings = com.aero.browser.features.settings.BrowserSettings.DEFAULT,
    ): WebView {
        val webView = WebView(context)
        try {
            val supportsProfiles = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
            if (supportsProfiles) {
                val profileName = if (privateMode) privateProfileName else NORMAL_PROFILE_NAME
                ProfileStore.getInstance().getOrCreateProfile(profileName)
                WebViewCompat.setProfile(webView, profileName)
            }
            webView.setTag(com.aero.browser.R.id.webview_private_mode, privateMode)
            configure(
                webView = webView,
                trackingProtection = trackingProtection,
                httpsEnforcer = httpsEnforcer,
                privateMode = privateMode,
                supportsProfiles = supportsProfiles,
                onProgressChanged = onProgressChanged,
                httpsOnly = httpsOnly,
                onPageFinished = onPageFinished,
                doNotTrackEnabled = doNotTrackEnabled,
                blockWebRtc = blockWebRtc,
                settings = settings,
            )
            webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                onDownload(webView, url, userAgent, contentDisposition, mimeType)
            }
            return webView
        } catch (failure: Throwable) {
            webView.destroy()
            throw failure
        }
    }

    /** Apply the canonical security configuration and attach hardened clients. */
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        webView: WebView,
        trackingProtection: TrackingProtection,
        httpsEnforcer: HttpsEnforcer,
        privateMode: Boolean = false,
        supportsProfiles: Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE),
        onProgressChanged: (WebView, Int) -> Unit = { _, _ -> },
        httpsOnly: () -> Boolean = { true },
        onPageFinished: (WebView, String, String?) -> Unit = { _, _, _ -> },
        doNotTrackEnabled: () -> Boolean = { true },
        blockWebRtc: Boolean = true,
        settings: com.aero.browser.features.settings.BrowserSettings = com.aero.browser.features.settings.BrowserSettings.DEFAULT,
    ) {
        applyBrowserSettings(webView, settings)
        if (privateMode && !supportsProfiles) {
            // Older WebView providers lack profile isolation. Fall back to a
            // deliberately storage-less session instead of crashing or
            // exposing normal-mode cookies/storage to private pages.
            webView.settings.domStorageEnabled = false
            webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            webView.clearCache(true)
        }
        webView.settings.userAgentString = UserAgent.aeroUserAgent(webView.settings.userAgentString)
        webView.settings.setSupportMultipleWindows(false)
        webView.webViewClient = SecureWebViewClient(trackingProtection, httpsEnforcer, httpsOnly, onPageFinished)
        updateDoNotTrack(webView, doNotTrackEnabled())
        updateVideoGestureSeek(webView)
        updateWebRtcRestriction(webView, enabled = blockWebRtc)
        webView.webChromeClient = SecureWebChromeClient(onProgressChanged)

        val cookieManager = if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.getProfile(webView).cookieManager
        } else {
            CookieManager.getInstance()
        }
        cookieManager.setAcceptCookie(!privateMode || supportsProfiles)
        cookieManager.setAcceptThirdPartyCookies(webView, !settings.blockThirdPartyCookies)
        webView.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
        webView.isFocusableInTouchMode = true
    }

    /** Apply user preferences without relaxing the browser's strict baseline. */
    fun applyBrowserSettings(webView: WebView, settings: com.aero.browser.features.settings.BrowserSettings) {
        val privateMode = webView.getTag(com.aero.browser.R.id.webview_private_mode) == true
        val supportsProfiles = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        WebViewSettingsApplier.apply(webView.settings, WebViewSecurityConfig.STRICT.copy(
            javaScriptEnabled = settings.javaScriptEnabled,
            safeBrowsingEnabled = settings.safeBrowsingEnabled,
            domStorageEnabled = settings.domStorageEnabled,
            loadsImagesAutomatically = settings.imagesEnabled,
            blockNetworkImages = !settings.imagesEnabled,
            textZoom = settings.pageZoom.coerceIn(50, 200),
            mediaPlaybackRequiresUserGesture = settings.mediaPlaybackRequiresUserGesture,
        ))
        val cookieManager = if (supportsProfiles) {
            runCatching { WebViewCompat.getProfile(webView).cookieManager }.getOrNull()
        } else CookieManager.getInstance()
        cookieManager?.setAcceptCookie(!privateMode || supportsProfiles)
        cookieManager?.setAcceptThirdPartyCookies(webView, !settings.blockThirdPartyCookies)
    }

    /** Gate page-script access to WebRTC APIs; this is not an engine/network-level IP guarantee. */
    fun updateWebRtcRestriction(webView: WebView, enabled: Boolean) {
        val handler = webView.getTag(com.aero.browser.R.id.webview_webrtc_script) as? ScriptHandler
        handler?.remove()
        webView.setTag(com.aero.browser.R.id.webview_webrtc_script, null)
        if (!enabled) {
            webView.evaluateJavascript("try { delete window.__aeroBlockWebRtc; } catch (_) {}", null)
            return
        }
        val script = """
            (() => {
              const denied = () => { throw new DOMException('WebRTC is disabled by browser privacy settings', 'NotAllowedError'); };
              const disabledPeerConnection = function() { denied(); };
              for (const key of ['RTCPeerConnection', 'webkitRTCPeerConnection']) {
                try { Object.defineProperty(window, key, { configurable: false, get: () => disabledPeerConnection, set: denied }); } catch (_) {}
              }
              try {
                if (navigator.mediaDevices) {
                  Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: false, value: () => Promise.reject(new DOMException('Media capture is disabled by browser privacy settings', 'NotAllowedError')) });
                }
              } catch (_) {}
              Object.defineProperty(window, '__aeroBlockWebRtc', { value: true, configurable: false });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            runCatching {
                WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://*", "http://*"))
            }.getOrNull()?.let { webView.setTag(com.aero.browser.R.id.webview_webrtc_script, it) }
        }
    }

    fun removeWebRtcRestriction(webView: WebView) {
        (webView.getTag(com.aero.browser.R.id.webview_webrtc_script) as? ScriptHandler)?.remove()
        webView.setTag(com.aero.browser.R.id.webview_webrtc_script, null)
    }

    /** Add a standards-shaped page signal; it is a preference, not a blocking guarantee. */
    fun updateDoNotTrack(webView: WebView, enabled: Boolean) {
        removeDoNotTrackScript(webView)
        val script = doNotTrackScript(enabled)
        webView.evaluateJavascript(script, null)
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://*", "http://*"))
        }.getOrNull()?.let { webView.setTag(com.aero.browser.R.id.webview_dnt_script, it) }
    }

    fun removeDoNotTrackScript(webView: WebView) {
        (webView.getTag(com.aero.browser.R.id.webview_dnt_script) as? ScriptHandler)?.remove()
        webView.setTag(com.aero.browser.R.id.webview_dnt_script, null)
    }

    /**
     * Double-tap on the left/right side of an HTML5 video seeks -10s/+10s, the gesture users
     * expect from mobile players. It never treats a double tap as played elsewhere on the page.
     */
    fun updateVideoGestureSeek(webView: WebView, enabled: Boolean = true) {
        removeVideoGestureSeek(webView)
        if (!enabled) return
        val script = videoGestureSeekScript()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            webView.evaluateJavascript(script, null)
            return
        }
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("https://*", "http://*"))
        }.getOrNull()?.let { webView.setTag(com.aero.browser.R.id.webview_seek_script, it) }
    }

    fun removeVideoGestureSeek(webView: WebView) {
        (webView.getTag(com.aero.browser.R.id.webview_seek_script) as? ScriptHandler)?.remove()
        webView.setTag(com.aero.browser.R.id.webview_seek_script, null)
    }

    /** Re-assert the gesture script for the current document; the script itself is idempotent. */
    fun injectVideoGestureSeek(webView: WebView) {
        webView.evaluateJavascript(videoGestureSeekScript(), null)
    }

    private fun videoGestureSeekScript(): String = """
        (() => {
          try {
            if (window.__aeroSeekGesture) return;
            Object.defineProperty(window, '__aeroSeekGesture', { value: true, configurable: false });
            const HOLD_MS = 280;
            const SEEK_SECONDS = 10;
            let lastTapAt = 0;
            let lastX = 0;
            let lastY = 0;

            const playerAt = (x, y) => {
              const hits = Array.prototype.slice.call(document.querySelectorAll('video')).filter((video) => {
                if (!video.offsetWidth || !video.offsetHeight || video.readyState < 1) return false;
                const rect = video.getBoundingClientRect();
                return rect.width >= 120 && rect.height >= 80 &&
                  x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
              });
              hits.sort((a, b) => (b.offsetWidth * b.offsetHeight) - (a.offsetWidth * a.offsetHeight));
              const playing = hits.find((video) => !video.paused && !video.ended);
              return playing || hits[0] || null;
            };

            const showHint = (video, side) => {
              try {
                const rect = video.getBoundingClientRect();
                const existing = document.querySelector('[data-aero-seek-hint]');
                if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
                const hint = document.createElement('div');
                hint.setAttribute('data-aero-seek-hint', '');
                hint.textContent = (side < 0 ? '-' : '+') + SEEK_SECONDS + 's';
                hint.style.cssText = 'position:fixed;z-index:2147483647;pointer-events:none;' +
                  'padding:8px 16px;border-radius:999px;background:rgba(12,12,16,0.72);color:#fff;' +
                  'font:600 15px/1.2 Roboto,sans-serif;opacity:1;transition:opacity .5s ease;' +
                  'top:' + Math.max(12, rect.top + rect.height / 2 - 20) + 'px;' +
                  (side < 0
                    ? 'left:' + Math.max(12, rect.left + rect.width * 0.1) + 'px;'
                    : 'right:' + Math.max(12, window.innerWidth - rect.right + rect.width * 0.1) + 'px;');
                (document.body || document.documentElement).appendChild(hint);
                setTimeout(() => { hint.style.opacity = '0'; }, 500);
                setTimeout(() => { if (hint.parentNode) hint.parentNode.removeChild(hint); }, 1100);
              } catch (_) {}
            };

            const seekAt = (x, y) => {
              const video = playerAt(x, y);
              if (!video) return;
              const rect = video.getBoundingClientRect();
              const side = x < rect.left + rect.width / 2 ? -1 : 1;
              const limit = isFinite(video.duration) ? video.duration : Infinity;
              const target = Math.max(0, Math.min(limit, video.currentTime + side * SEEK_SECONDS));
              try { video.currentTime = target; } catch (_) { return; }
              showHint(video, side);
            };

            const onEnd = (event) => {
              if (event.touches && event.touches.length) return;
              const point = (event.changedTouches && event.changedTouches[0]) || event;
              if (typeof point.clientX !== 'number') return;
              const now = Date.now();
              const isDoubleTap = now - lastTapAt <= HOLD_MS &&
                Math.abs(point.clientX - lastX) < 60 && Math.abs(point.clientY - lastY) < 60;
              lastTapAt = now;
              lastX = point.clientX;
              lastY = point.clientY;
              if (!isDoubleTap) return;
              lastTapAt = 0;
              seekAt(point.clientX, point.clientY);
            };

            document.addEventListener('touchend', onEnd, true);
            document.addEventListener('dblclick', (event) => seekAt(event.clientX, event.clientY), true);
          } catch (_) {}
        })();
    """.trimIndent()

    private fun doNotTrackScript(enabled: Boolean): String {
        val value = if (enabled) "'1'" else "null"
        return "try { Object.defineProperty(Navigator.prototype, 'doNotTrack', { configurable: true, get: function() { return $value; } }); } catch (_) {}"
    }
}

internal object UserAgent {
    fun aeroUserAgent(base: String?): String {
        if (base.isNullOrBlank()) return ""
        // Avoid adding a product/version token that needlessly distinguishes Aero users.
        return base.split(' ').filter { token ->
            !token.startsWith("Electron/") && !token.startsWith("Aero/") && !token.startsWith("aero/")
        }.joinToString(" ")
    }
}

/** Standalone Compose entry point for a single secure WebView. */
@Composable
fun SecureWebView(
    url: String,
    modifier: Modifier = Modifier,
    onStateChange: (WebViewUiState) -> Unit = {},
    privateMode: Boolean = false,
    privateProfileName: String = "aero_private",
) {
    val trackingProtection = remember { TrackingProtection.forApp() }
    val initialUrl = remember(url) { HttpsEnforcer.upgrade(url) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SecureWebView.create(context, trackingProtection, HttpsEnforcer, privateMode, privateProfileName).also {
                it.loadUrl(initialUrl ?: "about:blank")
            }
        },
        update = {},
    )
}

data class WebViewUiState(
    val url: String? = null,
    val title: String? = null,
    val isLoading: Boolean = false,
    val blockedRequests: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)
