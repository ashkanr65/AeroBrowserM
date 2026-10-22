package com.aero.browser.core.network

/**
 * Strips known tracking parameters from URLs before they are used for
 * navigation, matching standard privacy practice (Brave/Firefox-style).
 *
 * Pure functions so they are unit-testable without Android.
 */
object TrackingParamStripper {

    /** Prefix-matched tracker params (e.g. `utm_source`, `utm_medium`, all `utm_*`). */
    private val UTM_PREFIXES = listOf("utm_")

    /** Exact-name tracker params from common ad/analytics toolkits. */
    private val EXACT_PARAMS = setOf(
        "fbclid",        // Facebook
        "gclid",         // Google Ads
        "gclsrc",        // Google Ads 2
        "dclid",         // Display & Video 360
        "wbraid",        // Google legacy
        "gbraid",        // Google iOS-to-web
        "msclkid",       // Microsoft Advertising
        "twclid",        // Twitter/X
        "igshid",        // Instagram
        "li_fat_id",     // LinkedIn conversion
        "mc_eid",        // Mailchimp
        "mc_cid",
        "oly_anon_id",
        "oly_enc_id",
        "_hsenc",        // HubSpot
        "_hsmi",
        "vero_id",
        "wickedid",
        "yclid",         // Yandex Ads
        "ttclid",        // TikTok
        "spm",           // Alibaba/AliExpress tracking
        "scm",
        "ck_fid",
        "ref_src",       // not always tracker, but common cross-site attribution
        "ref_url",
    )

    /** Returns true if the parameter name is a tracking parameter. */
    fun isTrackingParam(name: String): Boolean {
        val lowered = name.lowercase()
        return UTM_PREFIXES.any { lowered.startsWith(it) } || lowered in EXACT_PARAMS
    }

    /**
     * Removes tracking params from a URL string. Preserves scheme, host, path
     * and fragment; strips bad query params. Returns the original string if
     * the parse fails or nothing needs changing.
     */
    fun strip(url: String?): String? {
        if (url.isNullOrBlank()) return url
        val questionIndex = url.indexOf('?')
        if (questionIndex < 0) return url
        val before = url.substring(0, questionIndex)
        val after = url.substring(questionIndex + 1)
        // Fragment stays untouched (after the first '#').
        val (query, fragment) = splitFragment(after)
        if (query.isEmpty()) return url

        val kept = query.split('&')
            .filter { it.isNotEmpty() }
            .filter { segment -> !isTrackingParam(segment.substringBefore('=')) }
        return when {
            kept.isEmpty() -> "$before$fragment"                          // drop empty "?"
            else -> {
                val cleaned = kept.joinToString("&")
                if (cleaned == query) url else "$before?$cleaned$fragment"
            }
        }
    }

    /** Splits "a=1&utm=x#frag" into ("a=1&utm=x", "#frag"). */
    private fun splitFragment(value: String): Pair<String, String> {
        val hashIndex = value.indexOf('#')
        return if (hashIndex < 0) value to "" else value.substring(0, hashIndex) to value.substring(hashIndex)
    }
}
