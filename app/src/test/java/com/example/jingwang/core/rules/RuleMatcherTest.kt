package com.example.jingwang.core.rules

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {
    @Test
    fun whitelistWinsAtEverySuffix() {
        val matcher = RuleMatcher(
            blockedDomains = setOf("ads.example.com", "tracker.test"),
            whitelist = setOf("example.com"),
        )

        assertFalse(matcher.shouldBlock("ads.example.com"))
        assertFalse(matcher.shouldBlock("deep.ads.example.com"))
        assertTrue(matcher.shouldBlock("a.tracker.test"))
        assertFalse(matcher.shouldBlock("unrelated.test"))
    }

    @Test
    fun parserExtractsVersionAndNormalizes() {
        val text = "#VER=202608240001\nExample.COM\naddress=/ads.test/\ninvalid line here\n"
        val parsed = RuleListParser.parse(ByteArrayInputStream(text.encodeToByteArray()))

        assertEquals("202608240001", parsed.version)
        assertEquals(setOf("example.com", "ads.test"), parsed.domains)
        assertEquals(1, parsed.invalidLines)
        assertEquals(64, parsed.sha256.length)
    }

    @Test
    fun parserRejectsOversizeInput() {
        val bytes = ByteArray(1025) { 'a'.code.toByte() }
        assertThrows(RuleListException::class.java) {
            RuleListParser.parse(ByteArrayInputStream(bytes), maxBytes = 1024)
        }
    }
}
