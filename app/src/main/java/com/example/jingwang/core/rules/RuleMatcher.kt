package com.example.jingwang.core.rules

class RuleMatcher(
    blockedDomains: Set<String>,
    whitelist: Set<String>,
) {
    private val blocked = blockedDomains
    private val allowed = whitelist.mapNotNull(DomainNames::normalize).toHashSet()

    fun shouldBlock(domain: String): Boolean {
        val normalized = DomainNames.normalize(domain) ?: return false
        if (matchesSuffix(normalized, allowed)) return false
        return matchesSuffix(normalized, blocked)
    }

    private fun matchesSuffix(domain: String, candidates: Set<String>): Boolean {
        var offset = 0
        while (true) {
            if (domain.substring(offset) in candidates) return true
            val next = domain.indexOf('.', offset)
            if (next < 0) return false
            offset = next + 1
        }
    }
}
