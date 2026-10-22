package com.aero.browser.core.network.doh

import java.net.InetAddress

/** Resolves hostnames on behalf of the in-app proxy. */
interface HostResolver {
    fun resolve(host: String): List<InetAddress>
}

/** Plain platform resolver, used only as an explicit fallback or for literal addresses. */
object SystemHostResolver : HostResolver {
    override fun resolve(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()
}
