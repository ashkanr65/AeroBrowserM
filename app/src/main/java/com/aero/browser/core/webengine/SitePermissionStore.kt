package com.aero.browser.core.webengine

import android.net.Uri
import com.aero.browser.core.storage.SecurePreferences

/** Per-site WebView permissions. ASK means the browser must prompt again. */
enum class SitePermission { ASK, ALLOW, BLOCK }

class SitePermissionStore(private val preferences: SecurePreferences) {
    fun get(origin: String, permission: String): SitePermission {
        val key = key(origin, permission) ?: return SitePermission.ASK
        return runCatching {
            SitePermission.valueOf(preferences.getString(key, SitePermission.ASK.name) ?: SitePermission.ASK.name)
        }.getOrDefault(SitePermission.ASK)
    }

    fun set(origin: String, permission: String, decision: SitePermission) {
        val normalizedOrigin = normalizeOrigin(origin) ?: return
        val key = key(normalizedOrigin, permission) ?: return
        if (decision == SitePermission.ASK) preferences.remove(key)
        else preferences.putString(key, decision.name)
    }

    fun all(): Map<String, Map<String, SitePermission>> {
        val decisions = mutableMapOf<String, MutableMap<String, SitePermission>>()
        preferences.getStringEntries(KEY_PREFIX).forEach { (key, value) ->
            val encoded = key.removePrefix(KEY_PREFIX)
            val separator = encoded.lastIndexOf('|')
            if (separator <= 0) return@forEach
            val origin = Uri.decode(encoded.substring(0, separator))
            val permission = encoded.substring(separator + 1)
            val decision = runCatching { SitePermission.valueOf(value) }.getOrNull() ?: return@forEach
            decisions.getOrPut(origin, ::mutableMapOf)[permission] = decision
        }
        return decisions.mapValues { it.value.toMap() }.toSortedMap()
    }

    fun removeSite(origin: String) {
        val encodedOrigin = Uri.encode(normalizeOrigin(origin) ?: return)
        preferences.getStringEntries(KEY_PREFIX + encodedOrigin + "|").keys.forEach(preferences::remove)
    }

    fun clear() {
        preferences.getStringEntries(KEY_PREFIX).keys.forEach(preferences::remove)
    }

    private fun key(origin: String, permission: String): String? {
        val normalizedOrigin = normalizeOrigin(origin) ?: return null
        if (permission !in PERMISSIONS) return null
        return KEY_PREFIX + Uri.encode(normalizedOrigin) + "|" + permission
    }

    private fun normalizeOrigin(value: String): String? = runCatching {
        val uri = Uri.parse(value)
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "https" || it == "http" } ?: return null
        val host = uri.host?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        val port = uri.port.takeIf { it >= 0 && it != defaultPort(scheme) }?.let { ":$it" }.orEmpty()
        "$scheme://$host$port"
    }.getOrNull()

    private fun defaultPort(scheme: String) = if (scheme == "https") 443 else 80

    private companion object {
        const val KEY_PREFIX = "browser.sitePermission."
        val PERMISSIONS = setOf("Location", "Camera", "Microphone")
    }
}
