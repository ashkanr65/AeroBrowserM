package com.aero.browser.core.storage

/**
 * The page Aero opens on its own: a cold start, a new tab, or a replacement tab.
 *
 * Kept in one place so the Settings homepage, the tab manager and the saved
 * session state can never disagree about which URL that is.
 */
object StartPage {
    /** Used only when nothing usable is configured. */
    const val DEFAULT = "https://duckduckgo.com/"

    const val PREFERENCES_KEY = "browser.homepage"

    /**
     * Accepts a bare host or an http(s) URL and returns it unchanged, otherwise [DEFAULT].
     * Anything else (`javascript:`, `file:`, garbage) is rejected so a bad setting can never
     * become the page Aero loads at startup.
     */
    fun normalize(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return DEFAULT
        // A scheme we do not handle (javascript:, file:, intent:, ...) must never be coerced
        // into an https URL by prefixing it.
        val hasOtherScheme = EXPLICIT_SCHEME.containsMatchIn(value) &&
            !value.startsWith("https://", ignoreCase = true) &&
            !value.startsWith("http://", ignoreCase = true)
        if (hasOtherScheme) return DEFAULT
        val candidate = if (
            value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)
        ) {
            value
        } else {
            "https://$value"
        }
        val uri = runCatching { java.net.URI(candidate) }.getOrNull() ?: return DEFAULT
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        return if ((scheme == "https" || scheme == "http") && !host.isNullOrBlank()) candidate else DEFAULT
    }

    private val EXPLICIT_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun read(preferences: SecurePreferences): String =
        normalize(preferences.getString(PREFERENCES_KEY, DEFAULT))

    /**
     * URL a tab restored from the previous launch should be retargeted to, or null to leave it alone.
     *
     * Only a tab still sitting on the page Aero opened by itself last time follows a changed
     * homepage; a page the user actually browsed to is preserved. When no previous start page was
     * recorded (an install from before this was tracked) [DEFAULT] is assumed, since that was the
     * hard-coded start page.
     */
    fun restoredUrl(restoredUrl: String, previousStartPage: String?, currentStartPage: String): String? {
        val previous = normalize(previousStartPage)
        val current = normalize(currentStartPage)
        return current.takeIf { previous != current && restoredUrl == previous }
    }
}

/** Supplies the configured homepage to components that build tabs before any UI exists. */
fun interface HomepageSource {
    fun homepage(): String
}

class PreferencesHomepageSource(private val preferences: SecurePreferences) : HomepageSource {
    override fun homepage(): String = StartPage.read(preferences)
}
