package com.aero.browser.core.network.doh

/**
 * Minimal parser for the proxy request head. Kept free of Android classes so the parsing rules
 * (which decide what the browser is allowed to tunnel) can be unit tested on the JVM.
 */
internal sealed interface ProxyTarget {
    val host: String
    val port: Int
}

internal data class ConnectTarget(
    override val host: String,
    override val port: Int,
) : ProxyTarget

internal data class ForwardRequest(
    override val host: String,
    override val port: Int,
    val method: String,
    val path: String,
    val version: String,
    val headers: List<Pair<String, String>>,
) : ProxyTarget

internal object ProxyRequestParser {
    const val MAX_HEADER_BYTES = 32 * 1024

    private val hopByHopHeaders = setOf(
        "connection",
        "proxy-connection",
        "proxy-authorization",
        "keep-alive",
    )

    /**
     * Parses a header block (already terminated by a blank line). Returns null for malformed or
     * unsupported requests, which the proxy answers with 400 instead of forwarding.
     */
    fun parse(head: String): ProxyTarget? {
        val lines = head.split("\r\n", "\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val requestLine = lines.first()
        val parts = requestLine.split(' ')
        if (parts.size != 3) return null
        val method = parts[0].uppercase()
        val target = parts[1]
        val version = parts[2]
        if (!version.startsWith("HTTP/")) return null
        val headers = lines.drop(1).mapNotNull(::parseHeader)
            .filterNot { (name, _) -> name.lowercase() in hopByHopHeaders }

        if (method == "CONNECT") {
            val authority = parseAuthority(target, defaultPort = 443) ?: return null
            return ConnectTarget(authority.first, authority.second)
        }
        // Only absolute-form plain-HTTP requests are supported; HTTPS always arrives as CONNECT.
        val absolute = parseAbsoluteHttpUrl(target) ?: return null
        return ForwardRequest(
            host = absolute.host,
            port = absolute.port,
            method = method,
            path = absolute.path,
            version = version,
            headers = headers,
        )
    }

    private data class AbsoluteUrl(val host: String, val port: Int, val path: String)

    private fun parseAbsoluteHttpUrl(target: String): AbsoluteUrl? {
        val prefix = "http://"
        if (!target.startsWith(prefix, ignoreCase = true)) return null
        val rest = target.substring(prefix.length)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let {
            if (it < 0) rest.length else it
        }
        val authority = rest.substring(0, authorityEnd)
        if (authority.contains('@')) return null
        val portAndHost = parseAuthority(authority, defaultPort = 80) ?: return null
        var path = rest.substring(authorityEnd)
        if (path.isEmpty() || path.startsWith("#")) path = "/$path"
        if (path.startsWith("?")) path = "/$path"
        return AbsoluteUrl(portAndHost.first, portAndHost.second, path)
    }

    /** Parses `host`, `host:port` or `[ipv6]:port`. */
    fun parseAuthority(raw: String, defaultPort: Int): Pair<String, Int>? {
        if (raw.isBlank()) return null
        if (raw.contains('@')) return null // never accept userinfo in a proxy authority
        if (raw.startsWith("[")) {
            val close = raw.indexOf(']')
            if (close <= 1) return null
            val host = raw.substring(1, close)
            val remainder = raw.substring(close + 1)
            val port = when {
                remainder.isEmpty() -> defaultPort
                remainder.startsWith(":") -> remainder.drop(1).toIntOrNull() ?: return null
                else -> return null
            }
            if (port !in 1..65535) return null
            return host to port
        }
        val colon = raw.lastIndexOf(':')
        if (colon < 0) return raw to defaultPort
        if (raw.indexOf(':') != colon) return null // unbracketed IPv6 with a port is ambiguous
        val host = raw.substring(0, colon)
        val port = raw.substring(colon + 1).toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535) return null
        return host to port
    }

    private fun parseHeader(line: String): Pair<String, String>? {
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        val name = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim()
        if (name.isEmpty() || name.any { it.isWhitespace() }) return null
        return name to value
    }
}
