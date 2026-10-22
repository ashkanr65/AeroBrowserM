package com.aero.browser.core.webengine

import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Safe permission policy layer. Site consent is necessary but not sufficient: Android consent is also required. */
class SitePermissionController(
    private val store: SitePermissionStore,
    private val requestDecision: (origin: String, permissions: Set<String>, decide: (Map<String, SitePermission>) -> Unit) -> Unit,
    private val onDecisionChanged: (origin: String, permission: String, decision: SitePermission) -> Unit = { _, _, _ -> },
    private val requestPlatformPermissions: (Set<String>, (Set<String>) -> Unit) -> Unit = { _, callback -> callback(emptySet()) },
) {
    fun handleWebRequest(request: PermissionRequest) {
        val origin = request.origin.toString()
        val resources = request.resources.mapNotNull { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> resource to "Camera"
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> resource to "Microphone"
                else -> null
            }
        }
        if (resources.isEmpty()) {
            request.deny()
            return
        }
        val decisions = resources.associate { (_, type) -> type to store.get(origin, type) }
        val undecided = decisions.filterValues { it == SitePermission.ASK }.keys
        val finish: (Map<String, SitePermission>) -> Unit = { selected ->
            selected.filterValues { it != SitePermission.ASK }.forEach { (type, decision) ->
                store.set(origin, type, decision)
                onDecisionChanged(origin, type, decision)
            }
            val approvedTypes = resources.map { it.second }.distinct().filter { (selected[it] ?: decisions[it]) == SitePermission.ALLOW }.toSet()
            if (approvedTypes.isEmpty()) request.deny()
            else requestPlatformPermissions(approvedTypes) { platformGranted ->
                val grantedResources = resources.filter { it.second in platformGranted }.map { it.first }
                if (grantedResources.isEmpty()) request.deny() else request.grant(grantedResources.toTypedArray())
            }
        }
        if (undecided.isEmpty()) finish(emptyMap())
        else requestDecision(origin, undecided, finish)
    }

    fun handleGeolocation(origin: String, callback: GeolocationPermissions.Callback) {
        val decision = store.get(origin, "Location")
        val finish: (SitePermission) -> Unit = { selected ->
            if (selected != SitePermission.ASK) {
                store.set(origin, "Location", selected)
                onDecisionChanged(origin, "Location", selected)
            }
            if (selected != SitePermission.ALLOW) callback.invoke(origin, false, false)
            else requestPlatformPermissions(setOf("Location")) { granted ->
                callback.invoke(origin, "Location" in granted, false)
            }
        }
        when (decision) {
            SitePermission.ALLOW, SitePermission.BLOCK -> finish(decision)
            SitePermission.ASK -> requestDecision(origin, setOf("Location")) { choices ->
                finish(choices["Location"] ?: SitePermission.ASK)
            }
        }
    }
}

