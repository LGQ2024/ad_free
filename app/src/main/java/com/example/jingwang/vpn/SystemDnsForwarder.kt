package com.example.jingwang.vpn

import android.net.VpnService
import com.example.jingwang.core.dns.DnsFormatException
import com.example.jingwang.core.dns.DnsMessage
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

class SystemDnsForwarder(
    private val vpnService: VpnService,
    private val networkProvider: () -> UpstreamNetwork?,
) {
    fun forward(query: ByteArray): ByteArray {
        require(query.size in 12..DnsMessage.MAX_MESSAGE_SIZE)
        val snapshot = networkProvider() ?: throw DnsFormatException("当前系统网络没有可用 DNS")
        var lastError: Exception? = null
        repeat(RETRY_ROUNDS) {
            snapshot.dnsServers.forEach { server ->
                try {
                    val udpResponse = forwardUdp(snapshot, server.hostAddress ?: return@forEach, query)
                    return if (DnsMessage.isTruncated(udpResponse)) {
                        forwardTcp(snapshot, server.hostAddress ?: return@forEach, query)
                    } else {
                        udpResponse
                    }
                } catch (error: Exception) {
                    lastError = error
                }
            }
        }
        throw DnsFormatException("所有系统 DNS 均未响应（${lastError?.javaClass?.simpleName ?: "unknown"}）")
    }

    private fun forwardUdp(snapshot: UpstreamNetwork, host: String, query: ByteArray): ByteArray {
        DatagramSocket().use { socket ->
            check(vpnService.protect(socket)) { "无法保护 DNS UDP 套接字" }
            snapshot.network.bindSocket(socket)
            socket.soTimeout = UDP_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, DNS_PORT))
            socket.send(DatagramPacket(query, query.size))
            val buffer = ByteArray(DnsMessage.MAX_MESSAGE_SIZE)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            return validateResponse(query, buffer.copyOf(response.length))
        }
    }

    private fun forwardTcp(snapshot: UpstreamNetwork, host: String, query: ByteArray): ByteArray {
        Socket().use { socket ->
            check(vpnService.protect(socket)) { "无法保护 DNS TCP 套接字" }
            snapshot.network.bindSocket(socket)
            socket.connect(InetSocketAddress(host, DNS_PORT), TCP_CONNECT_TIMEOUT_MS)
            socket.soTimeout = TCP_READ_TIMEOUT_MS
            DataOutputStream(socket.getOutputStream()).use { output ->
                output.writeShort(query.size)
                output.write(query)
                output.flush()
                val input = DataInputStream(socket.getInputStream())
                val length = input.readUnsignedShort()
                if (length !in 12..DnsMessage.MAX_MESSAGE_SIZE) throw DnsFormatException("TCP DNS 响应长度无效")
                return validateResponse(query, ByteArray(length).also(input::readFully))
            }
        }
    }

    private fun validateResponse(query: ByteArray, response: ByteArray): ByteArray {
        if (response.size < 12 || response[0] != query[0] || response[1] != query[1]) {
            throw DnsFormatException("DNS 响应事务标识无效")
        }
        if (response[2].toInt() and 0x80 == 0) throw DnsFormatException("上游返回的不是 DNS 响应")
        return response
    }

    companion object {
        private const val DNS_PORT = 53
        private const val RETRY_ROUNDS = 2
        private const val UDP_TIMEOUT_MS = 2_000
        private const val TCP_CONNECT_TIMEOUT_MS = 3_000
        private const val TCP_READ_TIMEOUT_MS = 4_000
    }
}
