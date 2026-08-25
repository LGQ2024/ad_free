package com.example.jingwang.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainInsightResolverTest {
    @Test
    fun recognizesKnownPlatformByDomainSuffix() {
        val insight = DomainInsightResolver.resolve("pagead2.googlesyndication.com", blocked = true)

        assertEquals("Google 广告", insight.displayName)
        assertEquals("Google Ads / AdMob", insight.provider)
        assertTrue(insight.recognitionBasis.contains("googlesyndication.com"))
    }

    @Test
    fun doesNotMatchLookalikeDomainWithoutSuffixBoundary() {
        val insight = DomainInsightResolver.resolve("notdoubleclick.net", blocked = true)

        assertEquals("未知广告或追踪域名", insight.displayName)
    }

    @Test
    fun explainsBlockedAdvertisingKeyword() {
        val insight = DomainInsightResolver.resolve("cdn.ads.example.test", blocked = true)

        assertEquals("广告服务", insight.displayName)
        assertEquals("广告投放", insight.category)
    }

    @Test
    fun keepsUnknownBlockedDomainHonest() {
        val insight = DomainInsightResolver.resolve("opaque.example.test", blocked = true)

        assertEquals("无法离线识别", insight.provider)
        assertTrue(insight.purpose.contains("无法仅凭域名"))
    }

    @Test
    fun describesUnknownAllowedDomainAsOrdinaryQuery() {
        val insight = DomainInsightResolver.resolve("api.example.test", blocked = false)

        assertEquals("普通 DNS 查询", insight.displayName)
        assertEquals("未命中拦截规则", insight.category)
    }
}
