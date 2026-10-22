package com.aero.browser

import com.aero.browser.core.storage.StartPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartPageTest {
    @Test
    fun normalizeAcceptsBareHostsAndAbsoluteUrls() {
        assertEquals("https://example.com", StartPage.normalize("example.com"))
        assertEquals("https://example.com/start", StartPage.normalize("  https://example.com/start  "))
        assertEquals("http://legacy.example/", StartPage.normalize("http://legacy.example/"))
    }

    @Test
    fun normalizeRejectsEmptyAndNonHttpSchemes() {
        assertEquals(StartPage.DEFAULT, StartPage.normalize(null))
        assertEquals(StartPage.DEFAULT, StartPage.normalize("   "))
        assertEquals(StartPage.DEFAULT, StartPage.normalize("javascript:alert(1)"))
        assertEquals(StartPage.DEFAULT, StartPage.normalize("file:///data/local/tmp/x.html"))
        assertEquals(StartPage.DEFAULT, StartPage.normalize("https://"))
    }

    @Test
    fun unchangedHomepageLeavesRestoredTabAlone() {
        assertNull(StartPage.restoredUrl("https://duckduckgo.com/", "https://duckduckgo.com/", "https://duckduckgo.com/"))
    }

    @Test
    fun changedHomepageReplacesTheTabLeftOnTheOldStartPage() {
        assertEquals(
            "https://example.com/",
            StartPage.restoredUrl("https://duckduckgo.com/", "https://duckduckgo.com/", "https://example.com/"),
        )
    }

    @Test
    fun changedHomepageKeepsPagesTheUserBrowsedTo() {
        assertNull(StartPage.restoredUrl("https://iana.org/", "https://duckduckgo.com/", "https://example.com/"))
    }

    @Test
    fun missingPreviousRecordAssumesTheOldBuiltInStartPage() {
        assertEquals(
            "https://example.com/",
            StartPage.restoredUrl(StartPage.DEFAULT, null, "https://example.com/"),
        )
        assertNull(StartPage.restoredUrl("https://iana.org/", null, "https://example.com/"))
    }
}
