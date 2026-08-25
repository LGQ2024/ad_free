package com.example.jingwang.core.rules

import java.net.IDN
import java.util.Locale

object DomainNames {
    private val asciiLabel = Regex("^[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?$")

    fun normalize(value: String): String? {
        val trimmed = value.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (trimmed.isEmpty() || trimmed.length > 253 || trimmed.any { it.isWhitespace() }) return null
        val ascii = try {
            IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (ascii.length > 253) return null
        val labels = ascii.split('.')
        if (labels.size < 2 || labels.any { it.length !in 1..63 || !asciiLabel.matches(it) }) return null
        return ascii
    }
}
