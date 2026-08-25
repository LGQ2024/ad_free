package com.example.jingwang.crash

import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class CrashReportStore(private val directory: File) {
    private val reportFile = File(directory, FILE_NAME)
    private val temporaryFile = File(directory, TEMPORARY_FILE_NAME)

    @Synchronized
    fun read(): String? {
        if (!reportFile.isFile || reportFile.length() !in 1..MAX_REPORT_BYTES.toLong()) return null
        return runCatching { reportFile.readText(StandardCharsets.UTF_8) }.getOrNull()
    }

    @Synchronized
    fun write(report: String) {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建崩溃报告目录" }
        FileOutputStream(temporaryFile).use { output ->
            output.write(report.toBoundedUtf8())
            output.fd.sync()
        }
        try {
            Files.move(
                temporaryFile.toPath(),
                reportFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                reportFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    @Synchronized
    fun clear() {
        reportFile.delete()
        temporaryFile.delete()
    }

    private fun String.toBoundedUtf8(): ByteArray {
        val bytes = toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= MAX_REPORT_BYTES) return bytes
        val suffix = TRUNCATED_SUFFIX.toByteArray(StandardCharsets.UTF_8)
        var end = MAX_REPORT_BYTES - suffix.size
        while (end > 0 && bytes[end].toInt() and UTF8_CONTINUATION_MASK == UTF8_CONTINUATION_PREFIX) {
            end--
        }
        return bytes.copyOfRange(0, end) + suffix
    }

    companion object {
        internal const val MAX_REPORT_BYTES = 128 * 1024
        private const val FILE_NAME = "last_crash_report.txt"
        private const val TEMPORARY_FILE_NAME = "$FILE_NAME.tmp"
        private const val TRUNCATED_SUFFIX = "\n\n[报告内容超过容量限制，已截断]\n"
        private const val UTF8_CONTINUATION_MASK = 0xc0
        private const val UTF8_CONTINUATION_PREFIX = 0x80
    }
}
