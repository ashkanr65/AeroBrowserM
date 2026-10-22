package com.aero.browser

import com.aero.browser.core.network.HttpsEnforcer
import com.aero.browser.core.network.TrackingParamStripper
import com.aero.browser.core.webengine.SecureWebViewClient
import com.aero.browser.core.network.TrackingProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-level tests for the Phase 2 security filters. These do not need an
 * Android runtime — pure string/URI logic only (SecureWebViewClient.decideNavigation
 * uses `android.net.Uri`, so it is covered by emulator tests later; here we
 * cover the pure helpers it calls).
 */
class URLSecurityTest {

    // ── TrackingProtection ────────────────────────────────────────────────

    private val protection = TrackingProtection.forApp()

    @Test
    fun `blocks doubleclick hosts`() {
        assertTrue(protection.isBlocked("https://ad.doubleclick.net/ddm/adj/N1234"))
        assertTrue(protection.isBlocked("https://doubleclick.net/anything"))
    }

    @Test
    fun `strict protection includes a broad ad and tracker host set`() {
        assertTrue(protection.blockedHostCount > 80)
        assertTrue(protection.isBlocked("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(protection.isBlocked("https://pubads.g.doubleclick.net/gampad/ads"))
        assertTrue(protection.isBlocked("https://www.googletagservices.com/tag/js/gpt.js"))
        assertTrue(protection.isBlocked("https://track.adnxs.com/"))
        assertTrue(protection.isBlocked("https://www.clarity.ms/tag/"))
    }

    @Test
    fun `strict protection covers current adblock tester third party checks`() {
        listOf(
            "https://ymatuhin.ru/ads/ads.js",
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://an.yandex.ru/system/context.js",
            "https://www.googletagmanager.com/gtag/js?id=G-TEST",
            "https://static.hotjar.com/c/hotjar.js",
            "https://mc.yandex.ru/metrika/tag.js",
            "https://js.sentry-cdn.com/test.js",
            "https://d2wy8f7a9ursnm.cloudfront.net/v4/bugsnag.min.js",
        ).forEach { url -> assertTrue("Should block $url", protection.isBlocked(url)) }
    }

    @Test
    fun `balanced protection remains narrower and off disables blocking`() {
        val balanced = TrackingProtection.forApp("Balanced")
        assertTrue(balanced.isBlocked("https://ad.doubleclick.net/ad.js"))
        assertFalse(balanced.isBlocked("https://track.adnxs.com/ad.js"))

        protection.updateLevel("Off")
        assertFalse(protection.isBlocked("https://ad.doubleclick.net/ad.js"))
        protection.updateLevel("Strict")
        assertTrue(protection.isBlocked("https://pagead2.googlesyndication.com/ad.js"))
    }

    @Test
    fun `banner image filter blocks only image URLs with ad-like path tokens`() {
        val protectionWithBannerImages = TrackingProtection.forApp("Off", blockBannerImages = true)
        val protectionWithoutBannerImages = TrackingProtection.forApp("Off", blockBannerImages = false)

        assertTrue(protectionWithBannerImages.isBlocked("https://adblock-tester.com/banners/pr_advertising_ads_banner.gif"))
        assertTrue(protectionWithBannerImages.isBlocked("https://adblock-tester.com/banners/pr_advertising_ads_banner.png"))
        assertTrue(protectionWithBannerImages.isBlocked("https://adblock-tester.com/banners/pr_advertising_ads_banner.swf?v=1"))
        assertTrue(protectionWithBannerImages.isBlocked("https://ads.example.test/assets/leaderboard.webp"))
        assertFalse(protectionWithBannerImages.isBlocked("https://example.com/images/banner-cat.png"))
        assertFalse(protectionWithBannerImages.isBlocked("https://adblock-tester.com/banners/pr_advertising_ads_banner.html"))
        assertFalse(protectionWithoutBannerImages.isBlocked("https://adblock-tester.com/banners/pr_advertising_ads_banner.gif"))
    }

    @Test
    fun `host parsing handles ipv6 and ignores credentials in authority`() {
        assertFalse(protection.isBlocked("https://user@notadoubleclick.net:443/script.js"))
        assertFalse(protection.isBlocked("https://[2001:db8::1]/"))
    }

    @Test
    fun `does not block normal sites`() {
        assertFalse(protection.isBlocked("https://en.wikipedia.org/wiki/Privacy"))
        assertFalse(protection.isBlocked("https://example.com/"))
    }

    @Test
    fun `does not block subdomain lookalikes`() {
        // notadoubleclick.net must not match doubleclick.net
        assertFalse(protection.isBlocked("https://notadoubleclick.net/ad.js"))
    }

    // ── HttpsEnforcer ─────────────────────────────────────────────────────

    @Test
    fun `upgrades http to https`() {
        assertEquals("https://example.com/path?q=1", HttpsEnforcer.upgrade("http://example.com/path?q=1"))
    }

    @Test
    fun `leaves https unchanged`() {
        assertEquals("https://secure.example.com/", HttpsEnforcer.upgrade("https://secure.example.com/"))
    }

    @Test
    fun `upgrades loopback with other http hosts`() {
        assertEquals("https://localhost:5173/", HttpsEnforcer.upgrade("http://localhost:5173/"))
        assertEquals("https://127.0.0.1/x", HttpsEnforcer.upgrade("http://127.0.0.1/x"))
    }

    @Test
    fun `returns null for empty input`() {
        assertEquals(null, HttpsEnforcer.upgrade(""))
        assertEquals(null, HttpsEnforcer.upgrade(null))
    }

    // ── TrackingParamStripper ────────────────────────────────────────────

    @Test
    fun `strips utm parameters`() {
        assertEquals(
            "https://example.com/article",
            TrackingParamStripper.strip("https://example.com/article?utm_source=newsletter&utm_medium=email"),
        )
    }

    @Test
    fun `strips fbclid and gclid`() {
        assertEquals(
            "https://example.com/page",
            TrackingParamStripper.strip("https://example.com/page?gclid=Cj0KC&fbclid=IwAR9x"),
        )
    }

    @Test
    fun `preserves fragment and non-tracking params`() {
        assertEquals(
            "https://example.com/search?q=privacy#results",
            TrackingParamStripper.strip("https://example.com/search?q=privacy&utm_campaign=x#results"),
        )
    }

    @Test
    fun `returns original url when no tracking params`() {
        val url = "https://example.com/a?b=1&c=2"
        assertEquals(url, TrackingParamStripper.strip(url))
    }

    @Test
    fun `isTrackingParam case insensitive`() {
        assertTrue(TrackingParamStripper.isTrackingParam("UTM_Source"))
        assertTrue(TrackingParamStripper.isTrackingParam("FbClId"))
        assertFalse(TrackingParamStripper.isTrackingParam("query"))
    }
}
