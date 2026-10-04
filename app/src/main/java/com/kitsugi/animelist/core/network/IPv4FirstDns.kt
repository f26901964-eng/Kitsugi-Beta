package com.kitsugi.animelist.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Custom DNS that reorders resolved addresses to place IPv4 (Inet4Address)
 * before IPv6 (Inet6Address). This avoids 60s timeout delays on networks
 * with broken IPv6 routing.
 */
class IPv4FirstDns(private val delegate: Dns? = null) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val activeDelegate = delegate ?: DnsManager.getActiveDns()
        val addresses = try {
            activeDelegate.lookup(hostname)
        } catch (e: Exception) {
            // Fallback to system DNS if DoH query fails
            if (activeDelegate !== Dns.SYSTEM) {
                try {
                    Dns.SYSTEM.lookup(hostname)
                } catch (ex: Exception) {
                    fallbackLookup(hostname, e)
                }
            } else {
                fallbackLookup(hostname, e)
            }
        }
        return addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
    }

    private fun fallbackLookup(hostname: String, originalError: Exception): List<InetAddress> {
        if (hostname.equals("codeberg.org", ignoreCase = true)) {
            return runCatching { listOf(InetAddress.getByName("217.197.84.140")) }.getOrElse { throw originalError }
        }
        return runCatching {
            Dns.SYSTEM.lookup(hostname)
        }.getOrElse { throw originalError }
    }
}
