package com.example.jingwang.data

import com.example.jingwang.core.model.PersistedSettings
import com.example.jingwang.core.model.RuleMetadata
import com.example.jingwang.core.model.TrafficStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryUnitTest {
    @Test
    fun versionedCodecRoundTripsWithoutPlainJson() {
        val settings = PersistedSettings(
            whitelist = setOf("example.com"),
            bypassPackages = setOf("com.example.app"),
            ruleMetadata = RuleMetadata("test", "1", 123, 10, "a".repeat(64)),
            statistics = TrafficStatistics(1, 2, 3, 4, 5),
        )
        assertEquals(settings, SecureSettingsStore.decode(SecureSettingsStore.encode(settings)))
    }

    @Test
    fun memoryLogNeverExceedsFiveHundredAndCanBeCleared() {
        val repository = QueryLogRepository()
        repeat(650) { repository.add("d$it.example", it % 2 == 0) }

        assertEquals(QueryLogRepository.MAX_ENTRIES, repository.logs.value.size)
        assertTrue(repository.logs.value.none { it.domain == "d0.example" })
        repository.clear()
        assertTrue(repository.logs.value.isEmpty())
    }
}
