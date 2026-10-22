package com.aero.browser

import com.aero.browser.features.browser.ui.LinkTarget
import com.aero.browser.features.browser.ui.LinkTargetKind
import com.aero.browser.features.browser.ui.linkTargetFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkContextMenuTest {

    @Test
    fun anchorLongPressOffersLinkActions() {
        val target = linkTargetFor("https://example.com/docs?q=1", null)
        assertEquals(LinkTarget(LinkTargetKind.LINK, "https://example.com/docs?q=1"), target)
        assertEquals("https://example.com/docs?q=1", target?.primaryUrl)
    }

    @Test
    fun imageLongPressOffersImageActions() {
        assertEquals(
            LinkTarget(LinkTargetKind.IMAGE, imageUrl = "https://cdn.example.com/pixel.png"),
            linkTargetFor(null, "https://cdn.example.com/pixel.png"),
        )
    }

    @Test
    fun imageInsideLinkKeepsBothTargets() {
        assertEquals(
            LinkTarget(
                LinkTargetKind.IMAGE_LINK,
                linkUrl = "https://example.com/article",
                imageUrl = "https://example.com/hero.jpg",
            ),
            linkTargetFor("https://example.com/article", "https://example.com/hero.jpg"),
        )
    }

    @Test
    fun relativeReferencesResolveAgainstThePage() {
        assertEquals(
            LinkTarget(LinkTargetKind.LINK, "https://example.com/section/page?a=b"),
            linkTargetFor("../section/page?a=b", null, "https://example.com/deep/current"),
        )
        assertEquals(
            LinkTarget(LinkTargetKind.IMAGE, imageUrl = "https://example.com/img/logo.png"),
            linkTargetFor(null, "img/logo.png", "https://example.com/index.html"),
        )
    }

    @Test
    fun nonWebSchemesAreNotOfferedAsLinks() {
        assertNull(linkTargetFor("mailto:help@example.com", null))
        assertNull(linkTargetFor("tel:+123456", null))
        assertNull(linkTargetFor("javascript:alert(1)", null))
        assertNull(linkTargetFor("intent://scan/#Intent;scheme=zxing;end", null))
    }

    @Test
    fun emptyOrUnresolvableTargetsLeaveThePressToWebView() {
        assertNull(linkTargetFor(null, null))
        assertNull(linkTargetFor("   ", ""))
        assertNull(linkTargetFor("relative/path", null))
        assertNull(linkTargetFor("relative/path", null, "about:blank"))
    }
}
