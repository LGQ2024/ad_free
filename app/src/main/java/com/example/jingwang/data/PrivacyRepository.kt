package com.example.jingwang.data

import com.example.jingwang.core.model.PersistedSettings
import com.example.jingwang.core.model.RuleMetadata
import com.example.jingwang.core.model.TrafficStatistics
import com.example.jingwang.core.rules.DomainNames
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrivacyRepository(private val store: SecureSettingsStore) {
    private val lock = Any()
    private var pendingStatistics = 0
    private val initialResult = store.read()
    private val mutableSettings = MutableStateFlow(initialResult.getOrDefault(PersistedSettings()).withCurrentDay())

    val settings: StateFlow<PersistedSettings> = mutableSettings.asStateFlow()
    val integrityWarning: String? = initialResult.exceptionOrNull()?.let {
        "加密设置无法读取，已使用安全默认值；原文件未上传或输出。"
    }

    fun addWhitelist(value: String): Boolean {
        val domain = DomainNames.normalize(value) ?: return false
        mutateAndPersist { it.copy(whitelist = it.whitelist + domain) }
        return true
    }

    fun removeWhitelist(domain: String) = mutateAndPersist { it.copy(whitelist = it.whitelist - domain) }

    fun setPackageBypassed(packageName: String, bypassed: Boolean) {
        if (packageName.length !in 1..255) return
        mutateAndPersist {
            it.copy(bypassPackages = if (bypassed) it.bypassPackages + packageName else it.bypassPackages - packageName)
        }
    }

    fun setRuleMetadata(metadata: RuleMetadata) = mutateAndPersist { it.copy(ruleMetadata = metadata) }

    fun recordQuery(blocked: Boolean) {
        synchronized(lock) {
            val current = mutableSettings.value.withCurrentDay()
            val stats = current.statistics
            mutableSettings.value = current.copy(
                statistics = if (blocked) {
                    stats.copy(blockedToday = stats.blockedToday + 1, blockedTotal = stats.blockedTotal + 1)
                } else {
                    stats.copy(allowedToday = stats.allowedToday + 1, allowedTotal = stats.allowedTotal + 1)
                },
            )
            pendingStatistics++
            if (pendingStatistics >= 50) persistLocked()
        }
    }

    fun flush() = synchronized(lock) { persistLocked() }

    private fun mutateAndPersist(transform: (PersistedSettings) -> PersistedSettings) {
        synchronized(lock) {
            mutableSettings.value = transform(mutableSettings.value.withCurrentDay())
            persistLocked()
        }
    }

    private fun persistLocked() {
        store.write(mutableSettings.value.withCurrentDay())
        pendingStatistics = 0
    }

    private fun PersistedSettings.withCurrentDay(): PersistedSettings {
        val today = LocalDate.now().toEpochDay()
        return if (statistics.dayEpoch == today) this else copy(
            statistics = statistics.copy(dayEpoch = today, blockedToday = 0, allowedToday = 0),
        )
    }
}
