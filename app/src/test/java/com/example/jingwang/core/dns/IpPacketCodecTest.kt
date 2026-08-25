package com.example.jingwang.core.dns

import java.net.InetAddress
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IpPacketCodecTest {
    @Test
    fun buildsValidIpv4HeaderAndUdpChecksums() {
        val request = UdpDnsPacket(
            ipVersion = 4,
            sourceAddress = InetAddress.getByName("10.111.222.1"),
            destinationAddress = InetAddress.getByName("10.111.222.2"),
            sourcePort = 53000,
            destinationPort = 53,
            dnsPayload = byteArrayOf(1, 2, 3),
            ipv4Identification = 77,
        )
        val packet = IpPacketCodec.buildUdpResponse(request, byteArrayOf(5, 6, 7, 8))

        assertEquals(0, IpPacketCodec.internetChecksum(packet, 0, 20))
        assertEquals(0, ipv4UdpChecksum(packet))
        val parsed = IpPacketCodec.parseUdp(packet)
        assertEquals(53, parsed.sourcePort)
        assertEquals(53000, parsed.destinationPort)
    }

    @Test
    fun buildsValidIpv6UdpChecksum() {
        val request = UdpDnsPacket(
            ipVersion = 6,
            sourceAddress = InetAddress.getByName("fd66:6a69:6e67::1"),
            destinationAddress = InetAddress.getByName("fd66:6a69:6e67::2"),
            sourcePort = 53001,
            destinationPort = 53,
            dnsPayload = byteArrayOf(1),
        )
        val packet = IpPacketCodec.buildUdpResponse(request, byteArrayOf(9, 8, 7))

        assertEquals(0, ipv6UdpChecksum(packet))
        assertEquals(6, IpPacketCodec.parseUdp(packet).ipVersion)
    }

    @Test
    fun rejectsInvalidLengthsAndFragments() {
        assertThrows(DnsFormatException::class.java) { IpPacketCodec.parseUdp(byteArrayOf(0x45)) }
        val request = UdpDnsPacket(
            4,
            InetAddress.getByName("10.0.0.1"),
            InetAddress.getByName("10.0.0.2"),
            1234,
            53,
            byteArrayOf(1),
        )
        val packet = IpPacketCodec.buildUdpResponse(request, byteArrayOf(1))
        packet[6] = 0x20
        assertThrows(DnsFormatException::class.java) { IpPacketCodec.parseUdp(packet) }
    }

    @Test
    fun randomPacketInputDoesNotEscapeBoundedParser() {
        val random = Random(42)
        repeat(10_000) {
            val bytes = random.nextBytes(random.nextInt(1, 1024))
            try {
                IpPacketCodec.parseUdp(bytes)
            } catch (_: DnsFormatException) {
                // 预期拒绝。
            } catch (_: java.net.UnknownHostException) {
                // InetAddress 对不可能的地址长度进行防御性拒绝。
            }
        }
    }

    private fun ipv4UdpChecksum(packet: ByteArray): Int {
        val udpLength = packet.size - 20
        val pseudo = ByteArray(12 + udpLength)
        packet.copyInto(pseudo, 0, 12, 20)
        pseudo[9] = 17
        pseudo[10] = (udpLength ushr 8).toByte()
        pseudo[11] = udpLength.toByte()
        packet.copyInto(pseudo, 12, 20)
        return IpPacketCodec.internetChecksum(pseudo)
    }

    private fun ipv6UdpChecksum(packet: ByteArray): Int {
        val udpLength = packet.size - 40
        val pseudo = ByteArray(40 + udpLength)
        packet.copyInto(pseudo, 0, 8, 40)
        pseudo[34] = (udpLength ushr 8).toByte()
        pseudo[35] = udpLength.toByte()
        pseudo[39] = 17
        packet.copyInto(pseudo, 40, 40)
        return IpPacketCodec.internetChecksum(pseudo)
    }
}
