package com.example.jingwang.core.model

data class RuleMetadata(
    val source: String = "内置 anti-AD 快照",
    val version: String = "未加载",
    val updatedAtEpochMillis: Long = 0L,
    val entryCount: Int = 0,
    val sha256: String = "",
)

data class TrafficStatistics(
    val dayEpoch: Long = 0L,
    val blockedToday: Long = 0L,
    val allowedToday: Long = 0L,
    val blockedTotal: Long = 0L,
    val allowedTotal: Long = 0L,
)

data class PersistedSettings(
    val whitelist: Set<String> = emptySet(),
    val customBlockedDomains: Set<String> = emptySet(),
    val bypassPackages: Set<String> = emptySet(),
    val ruleMetadata: RuleMetadata = RuleMetadata(),
    val statistics: TrafficStatistics = TrafficStatistics(),
    val darkMode: Boolean = false,
)

data class QuerySourceApp(
    val label: String,
    val packageNames: List<String>,
    val sharedUid: Boolean,
)

data class QueryLogEntry(
    val id: Long,
    val timestampEpochMillis: Long,
    val domain: String,
    val blocked: Boolean,
    val sourceApp: QuerySourceApp?,
)

enum class VpnStatus {
    STOPPED,
    STARTING,
    ACTIVE,
    WAITING_FOR_NETWORK,
    ERROR,
}

data class VpnRuntimeState(
    val status: VpnStatus = VpnStatus.STOPPED,
    val message: String = "已停止",
)
