package com.example.jingwang.vpn

import com.example.jingwang.core.dns.UdpDnsPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConnectionResolverTest {
    private val request = UdpDnsPacket(
        ipVersion = 4,
        sourceAddress = InetAddress.getByName("10.111.222.1"),
        destinationAddress = InetAddress.getByName("10.111.222.2"),
        sourcePort = 53000,
        destinationPort = 53,
        dnsPayload = byteArrayOf(1),
    )

    @Test
    fun usesPacketTupleAndReturnsSingleOwningApp() {
        var protocol = -1
        var local: InetSocketAddress? = null
        var remote: InetSocketAddress? = null
        val resolver = AppConnectionResolver(
            sdkInt = 29,
            ownerUidLookup = { value, localAddress, remoteAddress ->
                protocol = value
                local = localAddress
                remote = remoteAddress
                12345
            },
            packagesForUid = {
                listOf(InstalledPackageIdentity("com.example.video", "视频应用"))
            },
        )

        val source = resolver.resolve(request)

        assertEquals(17, protocol)
        assertEquals(InetSocketAddress(request.sourceAddress, 53000), local)
        assertEquals(InetSocketAddress(request.destinationAddress, 53), remote)
        assertEquals("视频应用", source?.label)
        assertEquals(listOf("com.example.video"), source?.packageNames)
        assertFalse(source?.sharedUid ?: true)
    }

    @Test
    fun marksMultiplePackagesAsSharedUidInsteadOfGuessing() {
        val resolver = AppConnectionResolver(
            sdkInt = 37,
            ownerUidLookup = { _, _, _ -> 12345 },
            packagesForUid = {
                listOf(
                    InstalledPackageIdentity("com.example.one", "应用一"),
                    InstalledPackageIdentity("com.example.two", "应用二"),
                )
            },
        )

        val source = resolver.resolve(request)

        assertTrue(source?.sharedUid == true)
        assertTrue(source?.label?.contains("应用一") == true)
        assertEquals(listOf("com.example.one", "com.example.two"), source?.packageNames)
    }

    @Test
    fun androidEightAndNineDoNotAttemptUnsupportedLookup() {
        var called = false
        val resolver = AppConnectionResolver(
            sdkInt = 28,
            ownerUidLookup = { _, _, _ ->
                called = true
                12345
            },
            packagesForUid = { emptyList() },
        )

        assertNull(resolver.resolve(request))
        assertFalse(called)
    }

    @Test
    fun invalidUidRemainsUnknown() {
        val resolver = AppConnectionResolver(
            sdkInt = 37,
            ownerUidLookup = { _, _, _ -> -1 },
            packagesForUid = { error("不应查询包名") },
        )

        assertNull(resolver.resolve(request))
    }
}
