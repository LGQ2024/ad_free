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
        assertEquals(InsightConfidence.HIGH, insight.confidence)
    }

    @Test
    fun doesNotMatchLookalikeDomainWithoutSuffixBoundary() {
        val insight = DomainInsightResolver.resolve("notdoubleclick.net", blocked = true)

        assertEquals("notdoubleclick.net · 已拦截域名", insight.displayName)
        assertEquals(InsightConfidence.LOW, insight.confidence)
    }

    @Test
    fun explainsBlockedAdvertisingKeyword() {
        val insight = DomainInsightResolver.resolve("cdn.ads.example.test", blocked = true)

        assertEquals("example.test · 广告服务", insight.displayName)
        assertEquals("广告投放", insight.category)
        assertEquals(InsightConfidence.MEDIUM, insight.confidence)
    }

    @Test
    fun keepsUnknownBlockedDomainHonest() {
        val insight = DomainInsightResolver.resolve("opaque.example.test", blocked = true)

        assertEquals("example.test", insight.provider)
        assertTrue(insight.purpose.contains("不能可靠判断"))
        assertEquals(InsightConfidence.LOW, insight.confidence)
    }

    @Test
    fun explainsAllowedApiKeyword() {
        val insight = DomainInsightResolver.resolve("api.example.test", blocked = false)

        assertEquals("example.test · 接口服务", insight.displayName)
        assertEquals("应用接口通信", insight.category)
        assertEquals(InsightConfidence.MEDIUM, insight.confidence)
    }

    @Test
    fun explainsCommonCrashPushAndCdnDomainsOffline() {
        val crash = DomainInsightResolver.resolve("sessions.crashlytics.com", blocked = true)
        val push = DomainInsightResolver.resolve("fcm.googleapis.com", blocked = false)
        val cdn = DomainInsightResolver.resolve("d123.cloudfront.net", blocked = false)

        assertEquals("Firebase Crashlytics 崩溃收集", crash.displayName)
        assertEquals("崩溃诊断", crash.category)
        assertEquals(InsightConfidence.HIGH, crash.confidence)
        assertEquals("Firebase 云消息", push.displayName)
        assertEquals("消息推送", push.category)
        assertEquals(InsightConfidence.HIGH, push.confidence)
        assertEquals("Amazon CloudFront 内容分发", cdn.displayName)
        assertEquals("CDN 静态资源", cdn.category)
    }

    @Test
    fun unknownAllowedDomainUsesBaseDomainWithoutInventingPurpose() {
        val insight = DomainInsightResolver.resolve("opaque.example.test", blocked = false)

        assertEquals("example.test · 网络连接", insight.displayName)
        assertEquals("未命中拦截规则（用途未知）", insight.category)
        assertEquals(InsightConfidence.LOW, insight.confidence)
    }

    @Test
    fun preservesCommonMultiLevelPublicSuffixInBaseDomain() {
        val insight = DomainInsightResolver.resolve("api.example.com.cn", blocked = false)

        assertEquals("example.com.cn · 接口服务", insight.displayName)
    }
}
