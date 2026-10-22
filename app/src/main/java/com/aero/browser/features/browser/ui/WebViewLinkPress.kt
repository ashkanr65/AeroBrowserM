package com.aero.browser.features.browser.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/**
 * Intercepts long presses on links and images so the browser can show its own menu instead of
 * WebView's built-in one. Presses on plain text return false, leaving text selection to WebView.
 */
@SuppressLint("ClickableViewAccessibility")
fun installLinkLongPress(
    webView: WebView,
    pageUrl: () -> String?,
    onTarget: (LinkTarget) -> Unit,
) {
    webView.setOnLongClickListener { view ->
        val web = view as? WebView ?: return@setOnLongClickListener false
        val base = pageUrl() ?: web.url
        val hit = web.hitTestResult
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val hitImage = if (hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) hit.extra else null
                // The anchor href is not exposed by hit test results, so ask the focused node.
                web.requestFocusNodeHref(
                    Handler(Looper.getMainLooper()) { message ->
                        val data = message.data
                        val target = linkTargetFor(data?.getString("url"), data?.getString("src") ?: hitImage, base)
                            ?: linkTargetFor(null, hitImage, base)
                        if (target != null) onTarget(target)
                        true
                    }.obtainMessage(),
                )
                true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                val target = linkTargetFor(null, hit.extra, base) ?: return@setOnLongClickListener false
                onTarget(target)
                true
            }
            else -> false
        }
    }
}
