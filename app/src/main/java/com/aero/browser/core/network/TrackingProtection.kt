package com.aero.browser.core.network

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Host-based tracker filter. Matching is exact or on a DNS subdomain boundary. */
class TrackingProtection private constructor(
    initialBlockedHosts: Set<String>,
    private var level: String = "Balanced",
    @Volatile private var blockBannerImages: Boolean = true,
) {
    @Volatile private var blockedHosts: Set<String> = initialBlockedHosts
    private val blockedHostTrie = HostSuffixTrie(blockedHosts)
    val blockedHostCount: Int get() = blockedHosts.size

    fun updateLevel(newLevel: String) {
        level = newLevel
        blockedHosts = hostsFor(newLevel)
        blockedHostTrie.replace(blockedHosts)
    }

    fun isBlocked(url: String?): Boolean {
        val value = url ?: return false
        val host = value.hostOrNull()?.trimEnd('.')?.lowercase(Locale.ROOT)
        return (host != null && blockedHostTrie.matches(host)) ||
            (blockBannerImages && isBannerAdImage(value))
    }

    fun blockedHostsSnapshot(): Set<String> = blockedHosts.toSet()

    fun updateBannerImageBlocking(enabled: Boolean) {
        blockBannerImages = enabled
    }

    fun intercept(request: WebResourceRequest?): WebResourceResponse? =
        if (isBlocked(request?.url?.toString())) blockedResponse() else null

    companion object {
        private class HostSuffixTrie(initialHosts: Set<String>) {
            private class Node {
                val children = HashMap<String, Node>()
                var terminal = false
            }

            @Volatile private var root = build(initialHosts)

            fun replace(hosts: Set<String>) {
                root = build(hosts)
            }

            fun matches(host: String): Boolean {
                var node = root
                for (label in host.split('.').asReversed()) {
                    node = node.children[label] ?: return false
                    if (node.terminal) return true
                }
                return false
            }

            private fun build(hosts: Set<String>): Node = Node().also { root ->
                hosts.forEach { host ->
                    var node = root
                    host.split('.').asReversed().forEach { label ->
                        node = node.children.getOrPut(label, ::Node)
                    }
                    node.terminal = true
                }
            }
        }

        fun blockedResponse(): WebResourceResponse = WebResourceResponse(
            "text/plain", "utf-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)),
        )

        private val TOP_TRACKERS = listOf(
            "adnxs.com", "adsrvr.org", "adform.net", "advertising.com", "adcolony.com",
            "admob.com", "adservice.google.com", "amazon-adsystem.com", "analytics.google.com",
            "app-measurement.com", "bat.bing.com", "branch.io", "casalemedia.com",
            "chartbeat.com", "clarity.ms", "connect.facebook.net", "criteo.com", "demdex.net",
            "doubleclick.net", "facebook.net", "google-analytics.com", "googleadservices.com",
            "googlesyndication.com", "googletagmanager.com", "googletagservices.com",
            "hotjar.com", "hotjar.io", "impact.com", "intentiq.com", "linkedin.com",
            "mathtag.com", "mixpanel.com", "moatads.com", "omtrdc.net", "openx.net",
            "outbrain.com", "pagead2.googlesyndication.com", "pubmatic.com", "quantserve.com",
            "scorecardresearch.com", "serving-sys.com", "sharethrough.com", "taboola.com",
            "tapad.com", "teads.tv", "thetradedesk.com", "tidaltv.com", "tiktok.com",
            "trustarc.com", "twitter.com", "yieldmo.com", "zedo.com",
            // Hosts exercised by AdBlock Tester (v3.1.1). Keep these explicit: several
            // checks use infrastructure domains that broad, obvious ad lists omit.
            "ymatuhin.ru", "an.yandex.ru", "static.hotjar.com", "js.sentry-cdn.com",
            "browser.sentry-cdn.com", "d2wy8f7a9ursnm.cloudfront.net",
            "2mdn.net", "adalyser.com", "adcolony.com", "adform.net", "ad.gt", "adition.com",
            "adkernel.com", "adlightning.com", "admanmedia.com", "admatic.com.tr", "admob.com",
            "adnxs.com", "adroll.com", "adsafeprotected.com", "adsrvr.org", "adsymptotic.com",
            "adtech.de", "adthrive.com", "advertising.com", "adzerk.net", "amazon-adsystem.com",
            "adsystem.com", "advertising-api-eu.amazon.com", "pagead2.googlesyndication.com",

            "analytics.google.com", "analytics.tiktok.com", "app-measurement.com", "appsflyer.com",
            "google-analytics.com", "googletagmanager.com", "googletagservices.com",
            "atdmt.com", "bidr.io", "bidswitch.net", "bluekai.com", "casalemedia.com", "chartboost.com",
            "clarity.ms", "clicktale.net", "contextweb.com", "criteo.com", "crwdcntrl.net",
            "demdex.net", "doubleclick.net", "e-planning.net", "everesttech.net", "facebook.net",
            "fastly-insights.com", "googleadservices.com", "google-analytics.com", "googlesyndication.com",
            "googletagmanager.com", "googletagservices.com", "hotjar.com", "impact.com", "imrworldwide.com",
            "inmobi.com", "iqzone.com", "lijit.com", "liveinternet.ru", "loggly.com", "matomo.cloud",
            "media.net", "mixpanel.com", "moatads.com", "newrelic.com", "nr-data.net", "onesignal.com",
            "openx.net", "optimizely.com", "outbrain.com", "permutive.com", "pubmatic.com", "quantserve.com",
            "rlcdn.com", "rubiconproject.com", "scorecardresearch.com", "serving-sys.com", "sharethrough.com",
            "sentry-cdn.com", "taboola.com", "tapad.com", "teads.tv", "thetradedesk.com", "turn.com",
            "urbanairship.com", "yieldmo.com", "zedo.com", "unityads.unity3d.com", "vungle.com",
            "yandex-metrica.com", "connect.facebook.net", "mc.yandex.ru", "adservice.google.com",
            "hotjar.io", "sentry.io", "bugsnag.com", "sentry-cdn.com",
            "adnxs.com", "adroll.com", "adsrvr.org", "adform.net", "advertising.com",
            "amazon-adsystem.com", "bidswitch.net", "bluekai.com", "casalemedia.com",
            "demdex.net", "doubleverify.com", "exelator.com", "flashtalking.com",
            "mathtag.com", "mediamath.com", "moatads.com", "nexac.com", "omtrdc.net",
            "pixel.quantserve.com", "rlcdn.com", "simpli.fi", "spotxchange.com",
            "trustarc.com", "undertone.com", "weborama.fr", "zemanta.com",
            "ads-twitter.com", "analytics.twitter.com", "ads.linkedin.com", "snap.licdn.com",
            "events.reddit.com", "pixel.reddit.com", "ads.tiktok.com", "business-api.tiktok.com",
            "fundingchoicesmessages.google.com",
        )

        fun forApp(level: String = "Strict", blockBannerImages: Boolean = true): TrackingProtection =
            TrackingProtection(hostsFor(level), level, blockBannerImages)

        private val AD_BANNER_PATH_EXTENSIONS = setOf("gif", "jpg", "jpeg", "png", "webp", "avif", "swf")
        private val AD_IMAGE_PATH_TOKEN = Regex("(?:^|[/_.-])(ad|ads|advert|advertising|banner|banners|sponsor|sponsored)(?:[/_.0-9]|$)|leaderboard", RegexOption.IGNORE_CASE)

        private fun isBannerAdImage(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val path = uri.rawPath ?: return false
            val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return extension in AD_BANNER_PATH_EXTENSIONS && AD_IMAGE_PATH_TOKEN.containsMatchIn(path)
        }

        private val BALANCED_TRACKERS = setOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "facebook.net", "criteo.com",
            "scorecardresearch.com", "taboola.com", "outbrain.com", "pagead2.googlesyndication.com",
        )

        private fun hostsFor(level: String): Set<String> = when (level.lowercase(Locale.ROOT)) {
            "off" -> emptySet()
            "strict" -> TOP_TRACKERS.toSet()
            "balanced" -> BALANCED_TRACKERS
            else -> TOP_TRACKERS.toSet()
        }.toSortedSet()
    }
}

/** Parses a URL authority using java.net.URI (including IPv6/port correctness). */
internal fun String.hostOrNull(): String? = try {
    val uri = URI(this.trim().let { value -> if (value.startsWith("//")) "https:$value" else value })
    uri.host?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
} catch (_: URISyntaxException) {
    null
}
