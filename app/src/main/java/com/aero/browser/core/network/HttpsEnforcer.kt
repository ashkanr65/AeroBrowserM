package com.aero.browser.core.network

/**
 * HTTP→HTTPS upgrade, mirroring the desktop app’s privacy engine
 * (`upgradeHttps`): every insecure main-frame or subresource request is
 * redirected to its https:// equivalent. Subresource requests stay upgraded
 * even when the site would break, because the desktop behaviour is the same
 * and the WebView's `MIXED_CONTENT_NEVER_ALLOW` policy backs this up for
 * HTTPS pages anyway.
 */
object HttpsEnforcer {

    /** Pure URL transformer, unit tested. Returns null when the URL cannot be safely upgraded. */
    fun upgrade(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val parsed = parse(url) ?: return null
        parsed.host ?: return null
        return if (parsed.protocol.equals("http", ignoreCase = true)) {
            url.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        } else {
            url
        }
    }

    /**
     * WebResourceRequest hook. Returns the upgraded URL when this request
     * should be redirected from http to https, else null.
     */
    fun redirectFor(request: android.webkit.WebResourceRequest): String? {
        val url = request.url.toString()
        return if (url.startsWith("http://", ignoreCase = true)) upgrade(url) else null
    }

    /** java.net.URL parser seam, so unit tests exercise real URL logic. */
    internal fun parse(url: String): java.net.URL? = try {
        java.net.URL(url)
    } catch (_: java.net.MalformedURLException) {
        null
    }
}
