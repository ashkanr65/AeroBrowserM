package com.aero.browser.core.network.doh

import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live state of the browser-only secure DNS tunnel, shown in Settings. */
data class DnsStatus(
    val enabled: Boolean = false,
    val active: Boolean = false,
    val endpoint: String? = null,
    val port: Int = 0,
    val lookups: Int = 0,
    val fallbacks: Int = 0,
    val failures: Int = 0,
    val error: String? = null,
)

/**
 * Owns the app-scoped DNS-over-HTTPS setup.
 *
 * Android WebView has no per-WebView DNS hook, so Aero runs a loopback proxy that resolves names
 * over DoH and registers it with `ProxyController`. That override applies to this app's WebViews
 * only: the system resolver, other apps, and the system proxy/VPN settings are never changed.
 *
 * HTTPS is tunnelled (CONNECT) so certificates are still validated end to end by the browser.
 */
@Singleton
class BrowserDnsController @Inject constructor() {

    private data class Config(val enabled: Boolean, val endpoint: String?, val fallbackToSystem: Boolean)

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aero-doh-control").apply { isDaemon = true }
    }
    private val lock = Any()
    private val lookups = AtomicInteger()
    private val fallbacks = AtomicInteger()
    private val failures = AtomicInteger()

    private var applied: Config? = null
    private var generation = 0
    private var proxy: BrowserDnsProxy? = null
    private var resolver: DohHostResolver? = null

    private val _status = MutableStateFlow(DnsStatus())
    val status: StateFlow<DnsStatus> = _status.asStateFlow()

    /**
     * Idempotently applies the saved preference. Safe to call on every settings change; the
     * tunnel is only rebuilt when the configuration actually differs.
     */
    fun configure(enabled: Boolean, endpoint: String?, fallbackToSystem: Boolean) {
        val config = Config(enabled, endpoint?.takeIf { enabled }, fallbackToSystem)
        val token: Int
        synchronized(lock) {
            if (config == applied) return
            applied = config
            token = ++generation
        }
        if (!config.enabled || config.endpoint == null) {
            disable(token)
            return
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            _status.value = DnsStatus(
                enabled = true,
                endpoint = config.endpoint,
                error = "This WebView build cannot route only Aero through a proxy",
            )
            return
        }
        _status.value = _status.value.copy(enabled = true, active = false, endpoint = config.endpoint, error = null)
        worker.execute { start(config, token) }
    }

    /** Stops the tunnel and removes the WebView proxy override. */
    fun shutdown() {
        synchronized(lock) {
            applied = Config(false, null, false)
            generation++
        }
        releaseProxy()
        main.post {
            runCatching { clearOverride() }
            _status.value = DnsStatus()
        }
    }

    private fun start(config: Config, token: Int) {
        releaseProxy()
        val resolver = DohHostResolver(
            endpoint = requireNotNull(config.endpoint),
            fallbackToSystem = config.fallbackToSystem,
            onLookup = { lookup ->
                when {
                    lookup.failed -> failures.incrementAndGet()
                    lookup.usedFallback -> fallbacks.incrementAndGet()
                    lookup.fromCache -> Unit
                    else -> lookups.incrementAndGet()
                }
                publishCounters()
            },
        )
        val proxy = try {
            BrowserDnsProxy(resolver).also { it.start() }
        } catch (failure: Throwable) {
            runCatching { resolver.close() }
            _status.value = _status.value.copy(
                enabled = true,
                active = false,
                endpoint = config.endpoint,
                error = "Could not start the DNS proxy: ${failure.message}",
            )
            return
        }
        synchronized(lock) {
            if (token != generation) {
                proxy.close()
                resolver.close()
                return
            }
            this.proxy = proxy
            this.resolver = resolver
        }
        main.post {
            if (isStale(token)) {
                releaseProxy()
                return@post
            }
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule("127.0.0.1:${proxy.port}")
                .addBypassRule("127.0.0.1")
                .addBypassRule("localhost")
                .build()
            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                {
                    if (!isStale(token)) {
                        _status.value = _status.value.copy(active = true, port = proxy.port, error = null)
                    }
                },
                {
                    releaseProxy()
                    _status.value = DnsStatus(
                        enabled = true,
                        endpoint = config.endpoint,
                        error = "WebView rejected the app-only proxy override",
                    )
                },
            )
        }
    }

    private fun disable(token: Int) {
        main.post {
            if (!isStale(token)) runCatching { clearOverride() }
            releaseProxy()
            if (!isStale(token)) _status.value = DnsStatus()
        }
    }

    private fun clearOverride() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        ProxyController.getInstance().clearProxyOverride({ }, { })
    }

    private fun releaseProxy() {
        val currentProxy: BrowserDnsProxy?
        val currentResolver: DohHostResolver?
        synchronized(lock) {
            currentProxy = proxy
            currentResolver = resolver
            proxy = null
            resolver = null
        }
        runCatching { currentProxy?.close() }
        runCatching { currentResolver?.close() }
    }

    private fun isStale(token: Int): Boolean = synchronized(lock) { token != generation }

    private fun publishCounters() {
        _status.value = _status.value.copy(
            lookups = lookups.get(),
            fallbacks = fallbacks.get(),
            failures = failures.get(),
        )
    }
}
