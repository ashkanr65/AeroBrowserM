package com.aero.browser

import android.app.Application
import android.webkit.WebView
import dagger.hilt.android.HiltAndroidApp

/** Android application startup and build-variant-specific WebView configuration. */
@HiltAndroidApp
class BrowserApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Keep remote WebView inspection available only in debuggable builds.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }
}
