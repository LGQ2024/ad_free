package com.example.jingwang.core.rules

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class ParsedRuleList(
    val domains: Set<String>,
    val version: String,
    val sha256: String,
    val nonCommentLines: Int,
    val invalidLines: Int,
    val rawBytes: ByteArray,
)

class RuleListException(message: String) : Exception(message)

object RuleListParser {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_LINES = 300_000

    fun parse(input: InputStream, maxBytes: Int = MAX_BYTES): ParsedRuleList {
        val raw = readBounded(input, maxBytes)
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw))
                .toString()
        } catch (_: Exception) {
            throw RuleListException("规则文件不是有效的 UTF-8 文本")
        }

        val domains = LinkedHashSet<String>()
        var version = "未知"
        var nonCommentLines = 0
        var invalidLines = 0
        var lineCount = 0
        text.lineSequence().forEach { original ->
            lineCount++
            if (lineCount > MAX_LINES) throw RuleListException("规则文件行数超过上限")
            val line = original.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith('#')) {
                val candidate = extractVersion(line)
                if (candidate != null) version = candidate
                return@forEach
            }
            nonCommentLines++
            val value = when {
                line.startsWith("address=/") -> line.removePrefix("address=/").substringBefore('/')
                line.startsWith("0.0.0.0 ") -> line.substringAfter(' ').trim()
                line.startsWith("127.0.0.1 ") -> line.substringAfter(' ').trim()
                else -> line.substringBefore('#').trim()
            }
            val normalized = DomainNames.normalize(value)
            if (normalized == null) invalidLines++ else domains += normalized
        }
        return ParsedRuleList(
            domains = domains,
            version = version,
            sha256 = raw.sha256(),
            nonCommentLines = nonCommentLines,
            invalidLines = invalidLines,
            rawBytes = raw,
        )
    }

    fun validateRemote(parsed: ParsedRuleList, previousCount: Int) {
        if (parsed.domains.size < 10_000) throw RuleListException("有效规则少于 10,000 条")
        if (parsed.nonCommentLines == 0 || parsed.invalidLines * 100L > parsed.nonCommentLines * 5L) {
            throw RuleListException("规则中无效条目比例超过 5%")
        }
        if (previousCount >= 10_000 && parsed.domains.size < previousCount / 2) {
            throw RuleListException("规则条目较当前版本骤减超过 50%")
        }
    }

    private fun extractVersion(line: String): String? {
        val marker = listOf("#VER=", "#VERSION=", "#DATE=").firstOrNull { line.startsWith(it, true) }
            ?: return null
        return line.substring(marker.length).trim().take(80).ifEmpty { null }
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 256 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw RuleListException("规则文件超过 ${maxBytes / 1024 / 1024} MiB 上限")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
