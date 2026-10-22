package com.aero.browser

import com.aero.browser.core.network.HttpsEnforcer
import com.aero.browser.core.network.TrackingProtection
import com.aero.browser.core.webengine.SecureWebViewClient
import com.aero.browser.features.browser.ui.shouldExitBrowserOnBack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewNavigationPolicyTest {
    private val client = SecureWebViewClient(TrackingProtection.forApp(), HttpsEnforcer)

    @Test
    fun doubleBackExitWindowWorksEvenAfterTheFirstBackNavigatesHistory() {
        assertFalse(shouldExitBrowserOnBack(lastBackPressAt = 0L, now = 10_000L))
        assertTrue(shouldExitBrowserOnBack(lastBackPressAt = 10_000L, now = 11_000L))
        assertTrue(shouldExitBrowserOnBack(lastBackPressAt = 10_000L, now = 12_000L))
        assertFalse(shouldExitBrowserOnBack(lastBackPressAt = 10_000L, now = 12_001L))
        assertFalse(shouldExitBrowserOnBack(lastBackPressAt = 11_000L, now = 11_000L))
    }

    @Test
    fun upgradesHttpForAllHostsBeforeLoading() {
        assertEquals(
            SecureWebViewClient.NavDecision.Internal("https://example.com/a"),
            client.decideNavigation("http://example.com/a", true),
        )
        assertEquals(
            SecureWebViewClient.NavDecision.Internal("https://localhost:8080/health"),
            client.decideNavigation("http://localhost:8080/health", true),
        )
    }

    @Test
    fun blocksHttpSubresourcesAndFileBasedSchemes() {
        assertEquals(SecureWebViewClient.NavDecision.Blocked, client.decideNavigation("http://example.com/pixel", false))
        assertEquals(SecureWebViewClient.NavDecision.Blocked, client.decideNavigation("http://127.0.0.1:1234/private", false))
        assertEquals(SecureWebViewClient.NavDecision.Blocked, client.decideNavigation("file:///sdcard/secret", true))
        assertEquals(SecureWebViewClient.NavDecision.Blocked, client.decideNavigation("javascript:alert(1)", true))
    }

    @Test
    fun blocksPopupAndKnownAdDomainNavigation() {
        assertEquals(SecureWebViewClient.NavDecision.Blocked, client.decideNavigation("intent://popup#Intent;scheme=https;end", true))
        assertEquals(SecureWebViewClient.NavDecision.Blocked, client.decideNavigation("https://pagead2.googlesyndication.com/pagead/ads", true))
        assertEquals(SecureWebViewClient.NavDecision.Internal("https://example.com/"), client.decideNavigation("https://example.com/", true))
    }

    @Test
    fun cleansTrackingParametersAndAllowsOnlyKnownExternalSchemes() {
        assertEquals(
            SecureWebViewClient.NavDecision.Internal("https://example.com/?q=a"),
            client.decideNavigation("https://example.com/?q=a&utm_source=mail", true),
        )
        assertEquals(SecureWebViewClient.NavDecision.External, client.decideNavigation("mailto:help@example.com", true))
        assertEquals(SecureWebViewClient.NavDecision.Blocked, client.decideNavigation("intent://attacker", true))
    }
}
