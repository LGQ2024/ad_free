package com.example.jingwang.core.dns

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

data class UdpDnsPacket(
    val ipVersion: Int,
    val sourceAddress: InetAddress,
    val destinationAddress: InetAddress,
    val sourcePort: Int,
    val destinationPort: Int,
    val dnsPayload: ByteArray,
    val ipv4Identification: Int = 0,
)

object IpPacketCodec {
    fun parseUdp(packet: ByteArray, length: Int = packet.size): UdpDnsPacket {
        if (length <= 0 || length > packet.size) throw DnsFormatException("IP 数据包长度无效")
        return when ((packet[0].toInt() ushr 4) and 0x0f) {
            4 -> parseIpv4(packet, length)
            6 -> parseIpv6(packet, length)
            else -> throw DnsFormatException("IP 版本无效")
        }
    }

    fun buildUdpResponse(request: UdpDnsPacket, dnsResponse: ByteArray): ByteArray = when (request.ipVersion) {
        4 -> buildIpv4(request, dnsResponse)
        6 -> buildIpv6(request, dnsResponse)
        else -> throw IllegalArgumentException("Unsupported IP version")
    }

    fun internetChecksum(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        var sum = 0L
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += ((bytes[index].toInt() and 0xff) shl 8) or (bytes[index + 1].toInt() and 0xff)
            index += 2
        }
        if (index < end) sum += (bytes[index].toInt() and 0xff) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun parseIpv4(packet: ByteArray, length: Int): UdpDnsPacket {
        if (length < 28) throw DnsFormatException("IPv4/UDP 数据包过短")
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        if (headerLength < 20 || headerLength + 8 > length) throw DnsFormatException("IPv4 首部长度无效")
        val totalLength = u16(packet, 2)
        if (totalLength < headerLength + 8 || totalLength > length) throw DnsFormatException("IPv4 总长度无效")
        val fragment = u16(packet, 6)
        if (fragment and 0x3fff != 0) throw DnsFormatException("不接受分片 DNS 数据包")
        if ((packet[9].toInt() and 0xff) != 17) throw DnsFormatException("不是 UDP 数据包")
        val udpOffset = headerLength
        val udpLength = u16(packet, udpOffset + 4)
        if (udpLength < 8 || udpOffset + udpLength > totalLength) throw DnsFormatException("UDP 长度无效")
        return UdpDnsPacket(
            ipVersion = 4,
            sourceAddress = InetAddress.getByAddress(packet.copyOfRange(12, 16)) as Inet4Address,
            destinationAddress = InetAddress.getByAddress(packet.copyOfRange(16, 20)) as Inet4Address,
            sourcePort = u16(packet, udpOffset),
            destinationPort = u16(packet, udpOffset + 2),
            dnsPayload = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength),
            ipv4Identification = u16(packet, 4),
        )
    }

    private fun parseIpv6(packet: ByteArray, length: Int): UdpDnsPacket {
        if (length < 48) throw DnsFormatException("IPv6/UDP 数据包过短")
        val payloadLength = u16(packet, 4)
        if (40 + payloadLength > length || payloadLength < 8) throw DnsFormatException("IPv6 负载长度无效")
        if ((packet[6].toInt() and 0xff) != 17) throw DnsFormatException("不支持 IPv6 扩展首部或非 UDP 数据包")
        val udpLength = u16(packet, 44)
        if (udpLength < 8 || udpLength > payloadLength) throw DnsFormatException("UDP 长度无效")
        return UdpDnsPacket(
            ipVersion = 6,
            sourceAddress = InetAddress.getByAddress(packet.copyOfRange(8, 24)) as Inet6Address,
            destinationAddress = InetAddress.getByAddress(packet.copyOfRange(24, 40)) as Inet6Address,
            sourcePort = u16(packet, 40),
            destinationPort = u16(packet, 42),
            dnsPayload = packet.copyOfRange(48, 40 + udpLength),
        )
    }

    private fun buildIpv4(request: UdpDnsPacket, dns: ByteArray): ByteArray {
        require(request.sourceAddress is Inet4Address && request.destinationAddress is Inet4Address)
        val udpLength = 8 + dns.size
        val result = ByteArray(20 + udpLength)
        result[0] = 0x45
        putU16(result, 2, result.size)
        putU16(result, 4, request.ipv4Identification)
        putU16(result, 6, 0x4000)
        result[8] = 64
        result[9] = 17
        request.destinationAddress.address.copyInto(result, 12)
        request.sourceAddress.address.copyInto(result, 16)
        putU16(result, 10, internetChecksum(result, 0, 20))
        writeUdp(result, 20, request.destinationPort, request.sourcePort, dns)
        putU16(result, 26, udpChecksumIpv4(result))
        return result
    }

    private fun buildIpv6(request: UdpDnsPacket, dns: ByteArray): ByteArray {
        require(request.sourceAddress is Inet6Address && request.destinationAddress is Inet6Address)
        val udpLength = 8 + dns.size
        val result = ByteArray(40 + udpLength)
        result[0] = 0x60
        putU16(result, 4, udpLength)
        result[6] = 17
        result[7] = 64
        request.destinationAddress.address.copyInto(result, 8)
        request.sourceAddress.address.copyInto(result, 24)
        writeUdp(result, 40, request.destinationPort, request.sourcePort, dns)
        putU16(result, 46, udpChecksumIpv6(result))
        return result
    }

    private fun writeUdp(target: ByteArray, offset: Int, sourcePort: Int, destinationPort: Int, dns: ByteArray) {
        putU16(target, offset, sourcePort)
        putU16(target, offset + 2, destinationPort)
        putU16(target, offset + 4, 8 + dns.size)
        putU16(target, offset + 6, 0)
        dns.copyInto(target, offset + 8)
    }

    private fun udpChecksumIpv4(packet: ByteArray): Int {
        val udpLength = packet.size - 20
        val pseudo = ByteArray(12 + udpLength)
        packet.copyInto(pseudo, 0, 12, 20)
        pseudo[9] = 17
        putU16(pseudo, 10, udpLength)
        packet.copyInto(pseudo, 12, 20)
        return internetChecksum(pseudo).let { if (it == 0) 0xffff else it }
    }

    private fun udpChecksumIpv6(packet: ByteArray): Int {
        val udpLength = packet.size - 40
        val pseudo = ByteArray(40 + udpLength)
        packet.copyInto(pseudo, 0, 8, 40)
        putU32(pseudo, 32, udpLength)
        pseudo[39] = 17
        packet.copyInto(pseudo, 40, 40)
        return internetChecksum(pseudo).let { if (it == 0) 0xffff else it }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
