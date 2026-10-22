package com.aero.browser.core.network.doh

import java.net.InetAddress

/**
 * Small in-memory answer cache for the DoH resolver. Browsing a single page issues dozens of
 * lookups for the same handful of hosts, and `DnsOverHttps` does not cache on its own.
 */
class DohAnswerCache(
    private val ttlMillis: Long = 120_000,
    private val negativeTtlMillis: Long = 30_000,
    private val maxEntries: Int = 512,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Entry {
        data class Resolved(val addresses: List<InetAddress>) : Entry
        data object Failed : Entry
    }

    private data class Stored(val entry: Entry, val expiresAt: Long)

    private val entries = object : LinkedHashMap<String, Stored>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Stored>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(host: String): Entry? {
        val stored = entries[host] ?: return null
        if (stored.expiresAt <= clock()) {
            entries.remove(host)
            return null
        }
        return stored.entry
    }

    /** Remembers a successful answer. */
    @Synchronized
    fun put(host: String, addresses: List<InetAddress>) {
        entries[host] = Stored(Entry.Resolved(addresses), clock() + ttlMillis)
    }

    /** Remembers a failed lookup so a broken host does not stall every subresource. */
    @Synchronized
    fun putFailure(host: String) {
        entries[host] = Stored(Entry.Failed, clock() + negativeTtlMillis)
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size
}
