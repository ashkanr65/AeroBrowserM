package com.aero.browser.core.network.doh

import java.io.Closeable
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/**
 * Resolves hostnames with DNS-over-HTTPS and never touches the system resolver unless the user
 * explicitly allows the fallback (some networks block public DoH endpoints entirely).
 *
 * The DoH endpoint hostname itself is resolved by the platform resolver: there is no answer to
 * bootstrap from before the secure channel exists. Only that one hostname leaves through the
 * system resolver.
 */
class DohHostResolver(
    endpoint: String,
    private val fallbackToSystem: Boolean,
    private val cache: DohAnswerCache = DohAnswerCache(),
    client: OkHttpClient = defaultClient(),
    private val onLookup: (Lookup) -> Unit = {},
) : HostResolver, Closeable {

    data class Lookup(
        val host: String,
        val addressCount: Int,
        val usedFallback: Boolean,
        val failed: Boolean,
        val fromCache: Boolean = false,
    )

    private val doh = DnsOverHttps.Builder()
        .client(client)
        .url(endpoint.toHttpUrl())
        .post(true)
        .includeIPv6(true)
        .build()

    override fun resolve(host: String): List<InetAddress> {
        if (host.isLiteralAddress()) return listOf(InetAddress.getByName(host))
        when (val cached = cache.get(host)) {
            is DohAnswerCache.Entry.Resolved -> {
                onLookup(Lookup(host, cached.addresses.size, usedFallback = false, failed = false, fromCache = true))
                return cached.addresses
            }
            DohAnswerCache.Entry.Failed -> {
                onLookup(Lookup(host, 0, usedFallback = false, failed = true))
                throw UnknownHostException("Secure DNS recently failed for $host")
            }
            null -> Unit
        }
        val secure = runCatching { doh.lookup(host) }.getOrElse { failure ->
            if (failure !is IOException) throw failure
            return resolveWithFallback(host)
        }
        if (secure.isEmpty()) return resolveWithFallback(host)
        val ordered = secure.sortedBy { if (it is Inet4Address) 0 else 1 }
        cache.put(host, ordered)
        onLookup(Lookup(host, ordered.size, usedFallback = false, failed = false))
        return ordered
    }

    private fun resolveWithFallback(host: String): List<InetAddress> {
        if (!fallbackToSystem) {
            cache.putFailure(host)
            onLookup(Lookup(host, 0, usedFallback = false, failed = true))
            throw UnknownHostException("Secure DNS could not resolve $host")
        }
        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (failure: IOException) {
            cache.putFailure(host)
            onLookup(Lookup(host, 0, usedFallback = true, failed = true))
            throw UnknownHostException("Could not resolve $host").apply { initCause(failure) }
        }
        cache.put(host, addresses)
        onLookup(Lookup(host, addresses.size, usedFallback = true, failed = false))
        return addresses
    }

    override fun close() {
        cache.clear()
    }

    companion object {
        /**
         * Bootstrap client for the DoH conversation itself. Timeouts are deliberately short so an
         * unreachable resolver fails over quickly instead of hanging page loads.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private fun String.isLiteralAddress(): Boolean =
            matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$""")) || contains(':')
    }
}
