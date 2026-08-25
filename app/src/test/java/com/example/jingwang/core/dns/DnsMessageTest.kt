package com.example.jingwang.core.dns

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessageTest {
    @Test
    fun parsesOrdinaryQueryAndBuildsNxdomain() {
        val bytes = query("ads.example.com")
        val parsed = DnsMessage.parseSingleQuestionQuery(bytes)

        assertEquals("ads.example.com", parsed.question.name)
        assertEquals(1, parsed.question.type)
        val response = DnsMessage.nxdomain(parsed)
        assertTrue(response[2].toInt() and 0x80 != 0)
        assertEquals(3, response[3].toInt() and 0x0f)
        assertEquals(bytes.copyOfRange(12, bytes.size).toList(), response.copyOfRange(12, response.size).toList())
    }

    @Test
    fun rejectsCompressionPointerLoop() {
        val bytes = header() + byteArrayOf(0xc0.toByte(), 0x0c, 0, 1, 0, 1)
        assertThrows(DnsFormatException::class.java) { DnsMessage.parseSingleQuestionQuery(bytes) }
    }

    @Test
    fun rejectsTruncatedAndMalformedLabels() {
        assertThrows(DnsFormatException::class.java) { DnsMessage.parseSingleQuestionQuery(ByteArray(11)) }
        val malformed = header() + byteArrayOf(63, 'a'.code.toByte(), 0)
        assertThrows(DnsFormatException::class.java) { DnsMessage.parseSingleQuestionQuery(malformed) }
    }

    @Test
    fun randomInputIsOnlyAcceptedOrRejected() {
        val random = Random(20260824)
        repeat(10_000) {
            val bytes = random.nextBytes(random.nextInt(0, 512))
            try {
                DnsMessage.parseSingleQuestionQuery(bytes)
            } catch (_: DnsFormatException) {
                // 预期：畸形输入被有界拒绝。
            }
        }
    }

    private fun query(domain: String): ByteArray {
        val body = buildList<Byte> {
            domain.split('.').forEach { label ->
                add(label.length.toByte())
                label.encodeToByteArray().forEach { add(it) }
            }
            add(0)
            add(0); add(1)
            add(0); add(1)
        }.toByteArray()
        return header() + body
    }

    private fun header(): ByteArray = byteArrayOf(
        0x12, 0x34, 0x01, 0x00,
        0x00, 0x01, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
    )
}
