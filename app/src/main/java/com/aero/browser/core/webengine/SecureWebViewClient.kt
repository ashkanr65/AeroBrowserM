package com.aero.browser.core.webengine

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aero.browser.core.network.HttpsEnforcer
import com.aero.browser.core.network.TrackingProtection
import com.aero.browser.core.network.TrackingParamStripper
import java.net.URI
import java.net.URISyntaxException

/**
 * The single WebViewClient used by every WebView in the app.
 *
 * Security policy:
 *  1. **SSL errors are fatal.** `onReceivedSslError` always calls
 *     `handler.cancel()` and blocks the page; there is no "proceed anyway"
 *     path, matching the desktop app's fail-closed certificate handling.
 *
 *  2. Main-frame navigation goes through [TrackingParamStripper] before the
 *     WebView ever sees the URL; redirects to http:// are upgraded to
 *     https:// by [HttpsEnforcer] and the upgraded URL is re-loaded instead.
 *
 *  3. Every subresource passes [TrackingProtection] first: blocklisted hosts
 *     are killed with an empty 403 response so the page never renders their
 *     payload.
 */
class SecureWebViewClient(
    private val trackingProtection: TrackingProtection,
    private val httpsEnforcer: HttpsEnforcer,
    private val httpsOnly: () -> Boolean = { true },
    private val onPageFinished: (WebView, String, String?) -> Unit = { _, _, _ -> },
) : WebViewClient() {

    /** Pure decision helper used by [shouldOverrideUrlLoading]; unit tested. */
    internal sealed class NavDecision {
        /** Load this (possibly rewritten) URL in the same WebView. */
        data class Internal(val url: String) : NavDecision()

        /** Let the OS handle it (mailto:, tel:, or another app). */
        data object External : NavDecision()

        /** Refuse to load: not navigable content for this browser. */
        data object Blocked : NavDecision()
    }

    internal fun decideNavigation(url: String, isMainFrame: Boolean): NavDecision {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return NavDecision.Blocked
        }
        val scheme = uri.scheme?.lowercase() ?: return NavDecision.Blocked
        if (trackingProtection.isBlocked(url)) return NavDecision.Blocked

        return when (scheme) {
            // Internal schemes the WebView itself renders.
            "https" -> NavDecision.Internal(TrackingParamStripper.strip(url) ?: url)
            "http" -> {
                if (!httpsOnly()) NavDecision.Internal(TrackingParamStripper.strip(url) ?: url)
                else {
                    val upgraded = upgradeHttp(url)
                    if (isMainFrame && upgraded != null) NavDecision.Internal(upgraded) else NavDecision.Blocked
                }
            }
            "about" -> if (url.equals("about:blank", ignoreCase = true)) NavDecision.Internal(url) else NavDecision.Blocked
            "data" -> if (isMainFrame && uri.rawQuery == null && url.length <= MAX_INLINE_DATA_URL_LENGTH) NavDecision.Internal(url) else NavDecision.Blocked
            "blob" -> NavDecision.Blocked                         // blob: navigations are rare/abused
            "file" -> NavDecision.Blocked                         // file:// is always off
            "javascript" -> NavDecision.Blocked
            "mailto", "tel", "sms", "geo", "market" -> NavDecision.External
            else -> NavDecision.Blocked
        }
    }

    private fun upgradeHttp(url: String): String? =
        HttpsEnforcer.upgrade(url)?.takeIf { it != url }?.let { TrackingParamStripper.strip(it) ?: it }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return when (val decision = decideNavigation(request.url.toString(), request.isForMainFrame)) {
            is NavDecision.Internal -> {
                val clean = decision.url
                if (clean != request.url.toString() && request.isForMainFrame) {
                    view.post { view.loadUrl(clean) }
                    true // we've replaced the navigation
                } else {
                    false // let WebView continue normally
                }
            }
            NavDecision.External -> {
                try {
                    view.context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (_: android.content.ActivityNotFoundException) {
                    // No external handler; treat like any other referrer-less nav.
                }
                true
            }
            NavDecision.Blocked -> true
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // 1) Tracker hosts die here.
        trackingProtection.intercept(request)?.let { return it }

        // HTTP subresources cannot be redirected from this API safely; block
        // them rather than allowing the insecure URL through. Main-frame HTTP
        // navigation is upgraded in shouldOverrideUrlLoading.
        if (httpsOnly() && request.url.scheme.equals("http", ignoreCase = true)) {
            val upgraded = upgradeHttp(request.url.toString())
            if (request.isForMainFrame && upgraded != null) view.post { view.loadUrl(upgraded) }
            return BLOCKED_RESPONSE
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // Notify listeners (the shell layer) after every main-frame start.
        StateListenerRegistry.notifyPageStarted(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        // Document-start injection can be skipped by some WebView providers or restored documents;
        // re-assert the double-tap seek gesture once the page is live (the script is idempotent).
        SecureWebView.injectVideoGestureSeek(view)
        StateListenerRegistry.notifyPageFinished(view, url, view.title)
        onPageFinished(view, url, view.title)
    }

    private companion object {
        const val MAX_INLINE_DATA_URL_LENGTH = 64 * 1024
        val BLOCKED_RESPONSE = WebResourceResponse(
            "text/plain", "utf-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)),
        )
    }

    /** Fatal SSL handling: no bypass, ever. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Cancel the pending network transaction, then load a blank page so
        // no partial content from the failed origin is left on screen.
        handler.cancel()
        view.stopLoading()
        view.loadUrl("about:blank")
        StateListenerRegistry.notifySslBlocked(view, error.primaryError)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        // Surfaced to the shell layer so Phase 3's tab can show an error page.
        if (request.isForMainFrame) {
            StateListenerRegistry.notifyLoadError(view, request.url.toString(), error.description?.toString())
        }
    }

    // Basic auth is rejected outright: prompting for it would let a malicious
    // origin phish credentials in a native dialog. Desktop parity: none.
    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        view.stopLoading()
        StateListenerRegistry.notifyRenderProcessGone(view)
        return true
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
        handler.cancel()
    }
}

/**
 * Page lifecycle event fan-out. Phase 3's tab engine replaces this with a
 * per-tab listener; a process-wide registry is all Phase 2 needs for the
 * single-shell demo.
 */
object StateListenerRegistry {
    interface Listener {
        fun onPageStarted(view: WebView, url: String) {}
        fun onPageFinished(view: WebView, url: String, title: String?) {}
        fun onSslBlocked(view: WebView, primaryError: Int) {}
        fun onLoadError(view: WebView, url: String, description: String?) {}
        fun onRenderProcessGone(view: WebView) {}
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()

    fun addListener(listener: Listener) { listeners += listener }

    fun removeListener(listener: Listener) { listeners -= listener }

    internal fun notifyPageStarted(view: WebView, url: String) = listeners.forEach { it.onPageStarted(view, url) }
    internal fun notifyPageFinished(view: WebView, url: String, title: String?) = listeners.forEach { it.onPageFinished(view, url, title) }
    internal fun notifySslBlocked(view: WebView, error: Int) = listeners.forEach { it.onSslBlocked(view, error) }
    internal fun notifyLoadError(view: WebView, url: String, description: String?) = listeners.forEach { it.onLoadError(view, url, description) }
    internal fun notifyRenderProcessGone(view: WebView) = listeners.forEach { it.onRenderProcessGone(view) }
}
