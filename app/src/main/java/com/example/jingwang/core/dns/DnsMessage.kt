package com.example.jingwang.core.dns

data class DnsQuestion(
    val name: String,
    val type: Int,
    val queryClass: Int,
)

data class ParsedDnsQuery(
    val id: Int,
    val flags: Int,
    val question: DnsQuestion,
    val questionEnd: Int,
    val original: ByteArray,
)

class DnsFormatException(message: String) : Exception(message)

object DnsMessage {
    const val MAX_MESSAGE_SIZE = 65_535
    private const val HEADER_SIZE = 12
    private const val MAX_POINTER_JUMPS = 32

    fun parseSingleQuestionQuery(packet: ByteArray): ParsedDnsQuery {
        if (packet.size !in HEADER_SIZE..MAX_MESSAGE_SIZE) throw DnsFormatException("DNS 长度无效")
        val id = u16(packet, 0)
        val flags = u16(packet, 2)
        if (flags and 0x8000 != 0) throw DnsFormatException("不是 DNS 查询")
        if (u16(packet, 4) != 1) throw DnsFormatException("仅接受一个问题的 DNS 查询")
        val decoded = readName(packet, HEADER_SIZE)
        if (decoded.nextOffset + 4 > packet.size) throw DnsFormatException("DNS 问题被截断")
        val type = u16(packet, decoded.nextOffset)
        val queryClass = u16(packet, decoded.nextOffset + 2)
        return ParsedDnsQuery(
            id = id,
            flags = flags,
            question = DnsQuestion(decoded.name, type, queryClass),
            questionEnd = decoded.nextOffset + 4,
            original = packet.copyOf(),
        )
    }

    fun nxdomain(query: ParsedDnsQuery): ByteArray {
        val response = query.original.copyOf(query.questionEnd)
        putU16(response, 2, 0x8000 or (query.flags and 0x7900) or 0x0080 or 0x0003)
        putU16(response, 4, 1)
        putU16(response, 6, 0)
        putU16(response, 8, 0)
        putU16(response, 10, 0)
        return response
    }

    fun isTruncated(response: ByteArray): Boolean = response.size >= 4 && u16(response, 2) and 0x0200 != 0

    private data class DecodedName(val name: String, val nextOffset: Int)

    private fun readName(packet: ByteArray, start: Int): DecodedName {
        var position = start
        var nextOffset = -1
        var jumps = 0
        val labels = ArrayList<String>(8)
        val visited = HashSet<Int>()
        var wireLength = 1

        while (true) {
            if (position !in packet.indices) throw DnsFormatException("域名越界")
            if (!visited.add(position)) throw DnsFormatException("DNS 压缩指针循环")
            val length = packet[position].toInt() and 0xff
            when {
                length == 0 -> {
                    if (nextOffset < 0) nextOffset = position + 1
                    break
                }
                length and 0xc0 == 0xc0 -> {
                    if (position + 1 >= packet.size) throw DnsFormatException("压缩指针被截断")
                    val pointer = ((length and 0x3f) shl 8) or (packet[position + 1].toInt() and 0xff)
                    if (pointer >= packet.size) throw DnsFormatException("压缩指针越界")
                    if (nextOffset < 0) nextOffset = position + 2
                    jumps++
                    if (jumps > MAX_POINTER_JUMPS) throw DnsFormatException("压缩指针层级过深")
                    position = pointer
                }
                length and 0xc0 != 0 -> throw DnsFormatException("DNS 标签类型无效")
                length > 63 -> throw DnsFormatException("DNS 标签过长")
                else -> {
                    val end = position + 1 + length
                    if (end > packet.size) throw DnsFormatException("DNS 标签被截断")
                    val label = packet.copyOfRange(position + 1, end)
                    if (label.any { (it.toInt() and 0xff) !in 0x21..0x7e }) {
                        throw DnsFormatException("DNS 标签包含非法字符")
                    }
                    labels += label.toString(Charsets.US_ASCII)
                    wireLength += length + 1
                    if (wireLength > 255) throw DnsFormatException("DNS 域名过长")
                    position = end
                }
            }
        }
        if (labels.isEmpty()) throw DnsFormatException("不接受根域查询")
        return DecodedName(labels.joinToString(".").lowercase(), nextOffset)
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) throw DnsFormatException("字段被截断")
        return ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}
