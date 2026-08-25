package com.example.jingwang.crash

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrashReportUnitTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("jingwang-crash-test-").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun reportContainsFullExceptionAndEnvironment() {
        val error = IllegalStateException("duplicate key example.com", IllegalArgumentException("cause"))
        val report = CrashReportRepository.formatCrashReport(
            timestampEpochMillis = 0L,
            appVersion = "1.0.0 (1)",
            androidVersion = "16 / API 36",
            threadName = "main",
            throwable = error,
        )

        assertTrue(report.contains("1970-01-01T00:00:00Z"))
        assertTrue(report.contains("IllegalStateException: duplicate key example.com"))
        assertTrue(report.contains("Caused by: java.lang.IllegalArgumentException: cause"))
        assertTrue(report.contains("应用版本：1.0.0 (1)"))
    }

    @Test
    fun storeKeepsOnlyLatestReportAndCanBeCleared() {
        val store = CrashReportStore(directory)
        store.write("first")
        store.write("second")

        assertEquals("second", store.read())
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun oversizedReportIsStoredWithinLimitAsValidUtf8() {
        val store = CrashReportStore(directory)
        store.write("域".repeat(CrashReportStore.MAX_REPORT_BYTES))

        val report = store.read()
        assertNotNull(report)
        assertFalse(report!!.contains('\uFFFD'))
        assertTrue(report.contains("已截断"))
        assertTrue(
            report.toByteArray(StandardCharsets.UTF_8).size <= CrashReportStore.MAX_REPORT_BYTES,
        )
    }
}
