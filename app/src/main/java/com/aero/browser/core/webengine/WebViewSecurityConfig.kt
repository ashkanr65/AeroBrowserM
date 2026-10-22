package com.aero.browser.core.webengine

import android.annotation.SuppressLint
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** Immutable strict security posture applied to every app-owned WebView. */
data class WebViewSecurityConfig(
    val javaScriptEnabled: Boolean = true,
    val allowFileAccess: Boolean = false,
    val allowContentAccess: Boolean = false,
    val allowFileAccessFromFileURLs: Boolean = false,
    val allowUniversalAccessFromFileURLs: Boolean = false,
    val mixedContentMode: Int = WebSettings.MIXED_CONTENT_NEVER_ALLOW,
    val safeBrowsingEnabled: Boolean = true,
    val supportMultipleWindows: Boolean = false,
    val javaScriptCanOpenWindowsAutomatically: Boolean = false,
    val mediaPlaybackRequiresUserGesture: Boolean = true,
    val saveFormData: Boolean = false,
    val savePassword: Boolean = false,
    val geolocationEnabled: Boolean = false,
    val databaseEnabled: Boolean = false,
    val domStorageEnabled: Boolean = true,
    val loadsImagesAutomatically: Boolean = true,
    val blockNetworkImages: Boolean = false,
    val loadWithOverviewMode: Boolean = true,
    val useWideViewPort: Boolean = true,
    val userAgentString: String? = null,
    val algorithmicDarkeningAllowed: Boolean = true,
    val textZoom: Int = 100,
) {
    companion object {
        val STRICT = WebViewSecurityConfig()
    }
}

@SuppressLint("SetJavaScriptEnabled")
object WebViewSettingsApplier {
    fun apply(settings: WebSettings, config: WebViewSecurityConfig) {
        settings.javaScriptEnabled = config.javaScriptEnabled
        settings.domStorageEnabled = config.domStorageEnabled
        settings.loadsImagesAutomatically = config.loadsImagesAutomatically
        settings.blockNetworkImage = config.blockNetworkImages
        settings.databaseEnabled = config.databaseEnabled
        settings.mediaPlaybackRequiresUserGesture = config.mediaPlaybackRequiresUserGesture
        settings.loadWithOverviewMode = config.loadWithOverviewMode
        settings.useWideViewPort = config.useWideViewPort
        settings.javaScriptCanOpenWindowsAutomatically = config.javaScriptCanOpenWindowsAutomatically
        settings.setSupportMultipleWindows(config.supportMultipleWindows)

        settings.allowFileAccess = config.allowFileAccess
        settings.allowContentAccess = config.allowContentAccess
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = config.allowFileAccessFromFileURLs
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = config.allowUniversalAccessFromFileURLs

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        @Suppress("DEPRECATION")
        settings.saveFormData = config.saveFormData
        @Suppress("DEPRECATION")
        settings.savePassword = config.savePassword

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, config.safeBrowsingEnabled)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, config.algorithmicDarkeningAllowed)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = config.mixedContentMode
        }
        settings.textZoom = config.textZoom
        config.userAgentString?.let { settings.userAgentString = it }
    }
}
