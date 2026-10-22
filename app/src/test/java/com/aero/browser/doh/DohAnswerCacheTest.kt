package com.aero.browser.doh

import com.aero.browser.core.network.doh.DohAnswerCache
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DohAnswerCacheTest {

    private var now = 1_000L
    private val cache = DohAnswerCache(ttlMillis = 100, negativeTtlMillis = 25, clock = { now })
    private val address = InetAddress.getByName("93.184.216.34")

    @Test
    fun resolvedAnswersHitUntilTheTtlExpires() {
        cache.put("example.com", listOf(address))
        val hit = cache.get("example.com") as DohAnswerCache.Entry.Resolved
        assertEquals(listOf(address), hit.addresses)

        now += 99
        assertTrue(cache.get("example.com") is DohAnswerCache.Entry.Resolved)
        now += 2
        assertNull(cache.get("example.com"))
    }

    @Test
    fun failuresAreCachedForTheShorterNegativeTtl() {
        cache.putFailure("broken.example")
        assertTrue(cache.get("broken.example") is DohAnswerCache.Entry.Failed)
        now += 25
        assertNull(cache.get("broken.example"))
    }

    @Test
    fun entriesAreBoundedSoBrowsingCannotGrowMemoryForever() {
        val bounded = DohAnswerCache(ttlMillis = 1_000, maxEntries = 2, clock = { now })
        bounded.put("a.example", listOf(address))
        bounded.put("b.example", listOf(address))
        bounded.put("c.example", listOf(address))
        assertEquals(2, bounded.size())
    }

    @Test
    fun clearDropsEverything() {
        cache.put("example.com", listOf(address))
        cache.clear()
        assertNull(cache.get("example.com"))
    }
}
