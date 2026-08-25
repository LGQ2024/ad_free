package com.example.jingwang.core.log

data class DomainInsight(
    val displayName: String,
    val provider: String,
    val category: String,
    val purpose: String,
    val recognitionBasis: String,
)

object DomainInsightResolver {
    private data class KnownService(
        val domains: Set<String>,
        val displayName: String,
        val provider: String,
        val category: String,
        val purpose: String,
    )

    private val knownServices = listOf(
        KnownService(
            setOf("pangolin-sdk-toutiao.com", "pglstatp-toutiao.com", "pangle.io", "pangle.com"),
            "穿山甲广告",
            "字节跳动穿山甲",
            "广告投放与归因",
            "用于广告素材请求、展示、点击及转化归因。",
        ),
        KnownService(
            setOf("gdt.qq.com", "e.qq.com"),
            "腾讯优量汇广告",
            "腾讯广告",
            "广告投放与归因",
            "用于应用内广告请求、展示、点击及效果统计。",
        ),
        KnownService(
            setOf("mobads.baidu.com", "cpro.baidu.com", "union.baidu.com"),
            "百度联盟广告",
            "百度营销",
            "广告投放",
            "用于百度广告联盟的广告请求、素材和展示统计。",
        ),
        KnownService(
            setOf("tanx.com", "alimama.com"),
            "阿里妈妈广告",
            "阿里妈妈 / Tanx",
            "广告投放与营销",
            "用于广告竞价、营销素材及广告效果统计。",
        ),
        KnownService(
            setOf("doubleclick.net", "googlesyndication.com", "googleadservices.com", "admob.com"),
            "Google 广告",
            "Google Ads / AdMob",
            "广告投放与归因",
            "用于应用或网页广告请求、素材下发、点击和转化统计。",
        ),
        KnownService(
            setOf("unityads.unity3d.com", "unityads.com"),
            "Unity Ads 广告",
            "Unity Ads",
            "游戏广告",
            "常用于游戏中的激励视频、插屏广告及展示统计。",
        ),
        KnownService(
            setOf("applovin.com", "applvn.com"),
            "AppLovin 广告",
            "AppLovin",
            "移动广告",
            "用于移动广告请求、竞价、素材下载和效果统计。",
        ),
        KnownService(
            setOf("ironsrc.com", "supersonicads.com"),
            "ironSource 广告",
            "ironSource / Unity LevelPlay",
            "广告聚合",
            "用于多个广告平台之间的竞价、聚合和展示统计。",
        ),
        KnownService(
            setOf("mintegral.com", "mbridge.com", "mbridge.net"),
            "Mintegral 广告",
            "Mintegral / MBridge",
            "移动广告",
            "用于移动广告请求、素材下发和广告效果归因。",
        ),
        KnownService(
            setOf("vungle.com"),
            "Vungle 广告",
            "Vungle / Liftoff",
            "视频广告",
            "常用于应用内视频、激励广告和广告效果统计。",
        ),
        KnownService(
            setOf("inmobi.com"),
            "InMobi 广告",
            "InMobi",
            "移动广告",
            "用于移动广告投放、受众匹配和效果统计。",
        ),
        KnownService(
            setOf("adcolony.com"),
            "AdColony 广告",
            "AdColony",
            "视频广告",
            "常用于应用内视频广告、素材请求和展示统计。",
        ),
        KnownService(
            setOf("chartboost.com"),
            "Chartboost 广告",
            "Chartboost",
            "游戏广告",
            "常用于游戏插屏、激励广告和安装归因。",
        ),
        KnownService(
            setOf("umeng.com", "umengcloud.com", "umtrack.com"),
            "友盟+统计",
            "友盟+",
            "统计与设备分析",
            "用于应用使用情况、事件、设备和渠道效果统计。",
        ),
        KnownService(
            setOf("appsflyer.com"),
            "AppsFlyer 归因",
            "AppsFlyer",
            "安装与营销归因",
            "用于判断应用安装、打开或付费来自哪个推广渠道。",
        ),
        KnownService(
            setOf("adjust.com"),
            "Adjust 归因",
            "Adjust",
            "安装与营销归因",
            "用于安装来源、广告转化和营销活动效果统计。",
        ),
        KnownService(
            setOf("app-measurement.com", "google-analytics.com"),
            "Google 数据分析",
            "Google Analytics / Firebase",
            "使用统计与分析",
            "用于应用事件、使用情况和广告转化统计。",
        ),
        KnownService(
            setOf("an.facebook.com", "connect.facebook.net"),
            "Meta 数据分析",
            "Meta / Facebook",
            "统计与社交 SDK",
            "用于应用事件、广告归因或社交 SDK 资源请求。",
        ),
        KnownService(
            setOf("ads.xiaomi.com", "ad.xiaomi.com", "tracking.miui.com", "data.mistat.xiaomi.com"),
            "小米广告与统计",
            "小米",
            "系统广告与统计",
            "用于小米广告、设备统计或广告效果追踪。",
        ),
        KnownService(
            setOf("ads.huawei.com", "metrics.data.hicloud.com"),
            "华为广告与统计",
            "华为",
            "系统广告与统计",
            "用于华为广告服务、设备指标或效果统计。",
        ),
        KnownService(
            setOf("adukwai.com", "e.kuaishou.com"),
            "快手广告",
            "快手磁力引擎",
            "广告投放与归因",
            "用于快手广告请求、营销素材及广告效果统计。",
        ),
    )

    private val advertisingTokens = setOf(
        "ad",
        "ads",
        "adservice",
        "adserver",
        "advert",
        "advertising",
        "sponsor",
        "sponsored",
    )
    private val trackingTokens = setOf(
        "analytics",
        "beacon",
        "metric",
        "metrics",
        "stat",
        "stats",
        "telemetry",
        "track",
        "tracker",
        "tracking",
    )
    private val attributionTokens = setOf("attribution", "conversion", "install", "installreferrer")

    fun resolve(domain: String, blocked: Boolean): DomainInsight {
        val normalized = domain.trim().trimEnd('.').lowercase()
        knownServices.forEach { service ->
            val matchedDomain = service.domains.firstOrNull { normalized.matchesDomain(it) }
            if (matchedDomain != null) {
                return DomainInsight(
                    displayName = service.displayName,
                    provider = service.provider,
                    category = service.category,
                    purpose = service.purpose,
                    recognitionBasis = "内置离线平台映射：$matchedDomain",
                )
            }
        }

        if (blocked) {
            val tokens = normalized.split(Regex("[._-]+"))
            tokens.firstOrNull { it in advertisingTokens }?.let { keyword ->
                return DomainInsight(
                    displayName = "广告服务",
                    provider = "未知广告平台",
                    category = "广告投放",
                    purpose = "域名名称显示它可能用于广告请求、素材或展示；无法仅凭域名确认具体平台。",
                    recognitionBasis = "拦截规则命中；域名关键词：$keyword",
                )
            }
            tokens.firstOrNull { it in trackingTokens }?.let { keyword ->
                return DomainInsight(
                    displayName = "统计或追踪服务",
                    provider = "未知统计平台",
                    category = "统计与追踪",
                    purpose = "域名名称显示它可能用于事件、设备、使用情况或营销效果统计。",
                    recognitionBasis = "拦截规则命中；域名关键词：$keyword",
                )
            }
            tokens.firstOrNull { it in attributionTokens }?.let { keyword ->
                return DomainInsight(
                    displayName = "安装或转化归因",
                    provider = "未知归因平台",
                    category = "营销归因",
                    purpose = "域名名称显示它可能用于安装来源、广告点击或转化效果统计。",
                    recognitionBasis = "拦截规则命中；域名关键词：$keyword",
                )
            }
            return DomainInsight(
                displayName = "未知广告或追踪域名",
                provider = "无法离线识别",
                category = "规则列表命中",
                purpose = "当前离线规则将该域名列为广告或追踪相关，但无法仅凭域名可靠判断具体业务。",
                recognitionBasis = "anti-AD 规则命中；内置平台映射未识别",
            )
        }

        return DomainInsight(
            displayName = "普通 DNS 查询",
            provider = "无法离线识别",
            category = "未命中拦截规则",
            purpose = "该域名未被当前规则拦截；仅凭 DNS 域名无法确定它在应用中的具体用途。",
            recognitionBasis = "当前规则未命中；内置平台映射未识别",
        )
    }

    private fun String.matchesDomain(suffix: String): Boolean = this == suffix || endsWith(".$suffix")
}
