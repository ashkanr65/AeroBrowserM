package com.aero.browser.doh

import com.aero.browser.core.network.doh.DohProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DohProvidersTest {

    @Test
    fun knownProvidersUseTheirHttpsEndpoints() {
        assertEquals("https://cloudflare-dns.com/dns-query", DohProvider.endpointFor("cloudflare", null))
        assertEquals("https://dns.google/dns-query", DohProvider.endpointFor("google", null))
        assertEquals("https://dns.quad9.net/dns-query", DohProvider.endpointFor("quad9", null))
        assertEquals("https://cloudflare-dns.com/dns-query", DohProvider.endpointFor("unknown-id", null))
    }

    @Test
    fun customEndpointsMustBeAbsoluteHttpsUrls() {
        assertEquals(
            "https://dns.example.org/dns-query",
            DohProvider.endpointFor("custom", " https://dns.example.org/dns-query "),
        )
        assertNull(DohProvider.endpointFor("custom", "http://dns.example.org/dns-query"))
        assertNull(DohProvider.endpointFor("custom", "dns.example.org/dns-query"))
        assertNull(DohProvider.endpointFor("custom", "https://user:pass@dns.example.org/q"))
        assertNull(DohProvider.endpointFor("custom", ""))
    }

    @Test
    fun normalizationRejectsNonHttpSchemes() {
        assertNull(DohProvider.normalizeEndpoint("ftp://dns.example.org/q"))
        assertNull(DohProvider.normalizeEndpoint("javascript:alert(1)"))
        assertEquals("https://dns.example.org:8443/q", DohProvider.normalizeEndpoint("https://dns.example.org:8443/q"))
    }
}
