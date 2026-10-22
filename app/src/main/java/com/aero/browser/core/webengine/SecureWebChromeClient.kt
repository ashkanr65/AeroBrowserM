package com.aero.browser.core.webengine

import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Default-deny WebChromeClient; optional site-permission prompts are wired by the browser shell. */
class SecureWebChromeClient(
    private val progressListener: (WebView, Int) -> Unit = { _, _ -> },
    private val sitePermissions: SitePermissionController? = null,
    private val onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit = { _, callback ->
        // No fullscreen host attached: refuse rather than leaving the page stuck in fullscreen.
        callback.onCustomViewHidden()
    },
    private val onExitFullscreen: () -> Unit = {},
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        progressListener(view, newProgress)
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean = false

    override fun onPermissionRequest(request: PermissionRequest) {
        if (sitePermissions == null) request.deny() else sitePermissions.handleWebRequest(request)
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) = Unit

    override fun onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: android.webkit.JsPromptResult): Boolean {
        result.cancel()
        return true
    }

    override fun onGeolocationPermissionsHidePrompt() = Unit

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: android.webkit.GeolocationPermissions.Callback) {
        if (sitePermissions == null) callback.invoke(origin, false, false)
        else sitePermissions.handleGeolocation(origin, callback)
    }

    /**
     * HTML5 fullscreen (`<video>` fullscreen button or `Element.requestFullscreen`).
     * The shell reparents [view] into a full-window host and hides the system bars.
     */
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onEnterFullscreen(view, callback)
    }

    override fun onHideCustomView() {
        onExitFullscreen()
    }
}
