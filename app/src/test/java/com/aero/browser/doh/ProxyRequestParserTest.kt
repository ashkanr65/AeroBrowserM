package com.aero.browser.doh

import com.aero.browser.core.network.doh.ConnectTarget
import com.aero.browser.core.network.doh.ForwardRequest
import com.aero.browser.core.network.doh.ProxyRequestParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRequestParserTest {

    @Test
    fun parsesHttpsTunnelRequests() {
        val parsed = ProxyRequestParser.parse("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n")
        assertEquals(ConnectTarget("example.com", 443), parsed)
    }

    @Test
    fun connectWithoutPortDefaultsToHttps() {
        assertEquals(ConnectTarget("example.com", 443), ProxyRequestParser.parse("CONNECT example.com HTTP/1.1\r\n\r\n"))
    }

    @Test
    fun parsesBracketedIpv6Authorities() {
        assertEquals(ConnectTarget("2606:4700::1111", 8443), ProxyRequestParser.parse("CONNECT [2606:4700::1111]:8443 HTTP/1.1\r\n\r\n"))
        assertNull(ProxyRequestParser.parse("CONNECT 2606:4700::1111:443 HTTP/1.1\r\n\r\n"))
    }

    @Test
    fun rejectsInvalidAuthorities() {
        assertNull(ProxyRequestParser.parse("CONNECT example.com:0 HTTP/1.1\r\n\r\n"))
        assertNull(ProxyRequestParser.parse("CONNECT example.com:70000 HTTP/1.1\r\n\r\n"))
        assertNull(ProxyRequestParser.parse("CONNECT user@example.com:443 HTTP/1.1\r\n\r\n"))
        assertNull(ProxyRequestParser.parse("CONNECT :443 HTTP/1.1\r\n\r\n"))
    }

    @Test
    fun parsesPlainHttpRequestsAndDropsHopByHopHeaders() {
        val parsed = ProxyRequestParser.parse(
            "GET http://example.com/a?b=c HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Proxy-Connection: keep-alive\r\n" +
                "Proxy-Authorization: Basic abc\r\n" +
                "Accept: */*\r\n\r\n",
        )
        assertTrue(parsed is ForwardRequest)
        parsed as ForwardRequest
        assertEquals("example.com", parsed.host)
        assertEquals(80, parsed.port)
        assertEquals("/a?b=c", parsed.path)
        assertEquals(listOf("Host" to "example.com", "Accept" to "*/*"), parsed.headers)
    }

    @Test
    fun plainHttpUrlWithoutPathUsesRoot() {
        val parsed = ProxyRequestParser.parse("GET http://example.com HTTP/1.1\r\n\r\n") as ForwardRequest
        assertEquals("/", parsed.path)
    }

    @Test
    fun rejectsMalformedOrUnsupportedRequests() {
        assertNull(ProxyRequestParser.parse(""))
        assertNull(ProxyRequestParser.parse("GET /relative HTTP/1.1\r\n\r\n"))
        assertNull(ProxyRequestParser.parse("GET https://example.com/ HTTP/1.1\r\n\r\n"))
        assertNull(ProxyRequestParser.parse("CONNECT example.com:443 FTP/1.1\r\n\r\n"))
        assertNull(ProxyRequestParser.parse("GET http://example.com/ HTTP/1.1 extra\r\n\r\n"))
    }

    @Test
    fun methodIsNormalisedToUpperCase() {
        val parsed = ProxyRequestParser.parse("get http://example.com/ HTTP/1.1\r\n\r\n") as ForwardRequest
        assertEquals("GET", parsed.method)
    }
}
