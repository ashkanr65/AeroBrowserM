package com.aero.browser.core.network.doh

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** DNS-over-HTTPS endpoints offered in Settings. */
enum class DohProvider(val id: String, val label: String, val endpoint: String) {
    CLOUDFLARE("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query"),
    GOOGLE("google", "Google", "https://dns.google/dns-query"),
    QUAD9("quad9", "Quad9", "https://dns.quad9.net/dns-query"),
    CUSTOM("custom", "Custom server", "");

    companion object {
        fun fromId(id: String?): DohProvider = entries.firstOrNull { it.id == id } ?: CLOUDFLARE

        /**
         * Endpoint to use for the saved selection, or null when a custom endpoint is blank/invalid.
         * Only HTTPS endpoints are accepted: a plain-HTTP resolver could be rewritten in transit.
         */
        fun endpointFor(providerId: String?, customUrl: String?): String? {
            val provider = fromId(providerId)
            return if (provider == CUSTOM) normalizeEndpoint(customUrl.orEmpty()) else provider.endpoint
        }

        /** Normalises a user-supplied DoH URL, keeping only safe absolute HTTPS URLs. */
        fun normalizeEndpoint(raw: String): String? {
            val url = raw.trim().toHttpUrlOrNull() ?: return null
            if (!url.isHttps) return null
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
            if (url.host.isBlank()) return null
            return url.toString()
        }
    }
}
