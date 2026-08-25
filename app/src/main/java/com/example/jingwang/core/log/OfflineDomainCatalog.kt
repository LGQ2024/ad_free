package com.example.jingwang.core.log

internal object OfflineDomainCatalog {
    private data class Service(
        val displayName: String,
        val provider: String,
        val category: String,
        val purpose: String,
        val domains: Set<String>,
    )

    private data class FunctionHint(
        val tokens: Set<String>,
        val displayName: String,
        val category: String,
        val purpose: String,
    )

    private fun service(
        displayName: String,
        provider: String,
        category: String,
        purpose: String,
        vararg domains: String,
    ) = Service(displayName, provider, category, purpose, domains.toSet())

    private val services = listOf(
        service(
            "Firebase Crashlytics 崩溃收集",
            "Google Firebase",
            "崩溃诊断",
            "用于接收应用崩溃堆栈、异常和稳定性诊断信息。",
            "crashlytics.com",
            "firebasecrashlytics.googleapis.com",
            "crashlyticsreports-pa.googleapis.com",
        ),
        service(
            "腾讯 Bugly 崩溃收集",
            "腾讯 Bugly",
            "崩溃诊断",
            "用于上报应用崩溃、异常和稳定性数据。",
            "bugly.qq.com",
            "bugly.qcloud.com",
        ),
        service(
            "Sentry 错误监控",
            "Sentry",
            "错误与性能监控",
            "用于收集应用错误、崩溃和性能诊断信息。",
            "sentry.io",
        ),
        service(
            "GrowingIO 数据分析",
            "GrowingIO",
            "使用统计与分析",
            "用于统计页面、事件、用户行为和渠道效果。",
            "growingio.com",
        ),
        service(
            "神策数据分析",
            "神策数据",
            "使用统计与分析",
            "用于应用事件、用户行为和运营效果分析。",
            "sensorsdata.cn",
        ),
        service(
            "TalkingData 数据分析",
            "TalkingData",
            "使用统计与分析",
            "用于应用使用情况、设备和推广效果统计。",
            "talkingdata.net",
        ),
        service(
            "Mixpanel 产品分析",
            "Mixpanel",
            "使用统计与分析",
            "用于分析应用事件、功能使用和用户留存。",
            "mixpanel.com",
        ),
        service(
            "Segment 数据分发",
            "Twilio Segment",
            "统计数据分发",
            "用于把应用事件转发到分析、营销或数据平台。",
            "segment.io",
            "segment.com",
        ),
        service(
            "New Relic 性能监控",
            "New Relic",
            "性能与错误监控",
            "用于收集应用性能、错误和网络耗时诊断信息。",
            "newrelic.com",
            "nr-data.net",
        ),
        service(
            "Datadog 监控",
            "Datadog",
            "性能与日志监控",
            "用于应用性能、错误、日志和运行状态监控。",
            "datadoghq.com",
        ),
        service(
            "Firebase 云消息",
            "Google Firebase Cloud Messaging",
            "消息推送",
            "用于接收应用通知、后台消息和推送令牌。",
            "fcm.googleapis.com",
            "mtalk.google.com",
        ),
        service(
            "极光推送",
            "极光 JPush",
            "消息推送",
            "用于应用通知、消息推送和设备推送注册。",
            "jpush.cn",
            "jpush.io",
        ),
        service(
            "小米推送",
            "小米",
            "消息推送",
            "用于小米设备上的应用通知、消息和推送注册。",
            "xmpush.xiaomi.com",
            "msg.xiaomi.net",
        ),
        service(
            "华为推送",
            "华为 Push Kit",
            "消息推送",
            "用于华为设备上的应用通知、消息和推送令牌。",
            "push-api.cloud.huawei.com",
            "push.hicloud.com",
        ),
        service(
            "OPPO 推送",
            "OPPO / HeyTap",
            "消息推送",
            "用于 OPPO 设备上的应用通知和消息推送。",
            "push.heytapmobi.com",
            "push.oppomobile.com",
        ),
        service(
            "vivo 推送",
            "vivo",
            "消息推送",
            "用于 vivo 设备上的应用通知和消息推送。",
            "push.vivo.com.cn",
        ),
        service(
            "Amazon CloudFront 内容分发",
            "Amazon Web Services",
            "CDN 静态资源",
            "用于就近加载图片、视频、安装包或其他静态资源。",
            "cloudfront.net",
        ),
        service(
            "Akamai 内容分发",
            "Akamai",
            "CDN 静态资源",
            "用于就近加载图片、视频、安装包或其他静态资源。",
            "akamaized.net",
            "akamaihd.net",
            "edgesuite.net",
        ),
        service(
            "Fastly 内容分发",
            "Fastly",
            "CDN 静态资源",
            "用于加速网站、接口和静态资源访问。",
            "fastly.net",
            "fastlylb.net",
        ),
        service(
            "阿里云内容分发",
            "阿里云 CDN",
            "CDN 静态资源",
            "用于加速图片、视频、下载文件和应用资源。",
            "alicdn.com",
            "kunlunaq.com",
            "kunlunca.com",
        ),
        service(
            "腾讯云内容分发",
            "腾讯云 CDN",
            "CDN 静态资源",
            "用于加速图片、视频、下载文件和应用资源。",
            "qcloudcdn.com",
            "qcloudcjgj.com",
        ),
        service(
            "百度静态资源",
            "百度",
            "CDN 静态资源",
            "用于加载百度相关图片、脚本和其他静态资源。",
            "bdstatic.com",
        ),
        service(
            "字节跳动静态资源",
            "字节跳动",
            "CDN 与对象存储",
            "用于加载图片、视频和应用静态资源。",
            "byteimg.com",
            "ibytedtos.com",
        ),
        service(
            "哔哩哔哩内容分发",
            "哔哩哔哩",
            "视频与静态资源",
            "用于加载哔哩哔哩视频、图片和其他内容资源。",
            "hdslb.com",
        ),
        service(
            "GitHub 资源服务",
            "GitHub",
            "代码与静态资源",
            "用于加载 GitHub 托管的代码、图片和网页静态资源。",
            "githubusercontent.com",
            "githubassets.com",
        ),
        service(
            "Google 账号登录",
            "Google",
            "登录与身份验证",
            "用于 Google 账号登录、OAuth 授权和身份验证。",
            "accounts.google.com",
            "oauth2.googleapis.com",
        ),
        service(
            "Apple 账号登录",
            "Apple",
            "登录与身份验证",
            "用于 Apple ID 登录和授权。",
            "appleid.apple.com",
        ),
        service(
            "微信开放平台",
            "腾讯微信",
            "登录与开放能力",
            "用于微信登录、授权、分享或开放平台接口。",
            "open.weixin.qq.com",
            "api.weixin.qq.com",
        ),
        service(
            "QQ 开放平台",
            "腾讯 QQ",
            "登录与开放能力",
            "用于 QQ 登录、授权、分享或开放平台接口。",
            "graph.qq.com",
            "openmobile.qq.com",
        ),
        service(
            "Microsoft 账号登录",
            "Microsoft",
            "登录与身份验证",
            "用于 Microsoft 账号、企业账号或 OAuth 登录。",
            "login.microsoftonline.com",
            "login.live.com",
        ),
        service(
            "支付宝服务",
            "支付宝",
            "支付与账户服务",
            "用于支付宝支付、账户验证或支付相关资源。",
            "mobilegw.alipay.com",
            "mclient.alipay.com",
            "alipayobjects.com",
        ),
        service(
            "银联支付服务",
            "中国银联",
            "支付服务",
            "用于银联支付、支付验证或相关接口。",
            "gateway.95516.com",
            "unionpay.com",
        ),
        service(
            "Firebase 安装与远程配置",
            "Google Firebase",
            "应用配置与实例注册",
            "用于生成应用实例标识、获取 Firebase 配置或功能参数。",
            "firebaseinstallations.googleapis.com",
            "firebaseremoteconfig.googleapis.com",
        ),
        service(
            "Android 网络连通性检测",
            "Android / Google",
            "系统网络检测",
            "用于判断网络是否可访问互联网以及是否存在认证门户。",
            "connectivitycheck.gstatic.com",
            "connectivitycheck.android.com",
            "clients3.google.com",
        ),
        service(
            "Google Play 服务",
            "Google",
            "应用商店与系统服务",
            "用于 Google Play 应用信息、许可、更新或系统服务接口。",
            "play.googleapis.com",
            "android.clients.google.com",
        ),
        service(
            "网络时间同步",
            "系统时间服务",
            "时间同步",
            "用于校准设备时间；准确时间也用于证书和安全校验。",
            "time.android.com",
            "time.google.com",
            "pool.ntp.org",
        ),
        service(
            "Amazon 广告",
            "Amazon Ads",
            "广告投放与归因",
            "用于广告请求、素材、展示和效果统计。",
            "amazon-adsystem.com",
        ),
        service(
            "Tapjoy 广告",
            "Tapjoy",
            "激励与移动广告",
            "用于激励广告、广告素材和转化效果统计。",
            "tapjoy.com",
        ),
        service(
            "Fyber 广告",
            "Digital Turbine / Fyber",
            "广告聚合与投放",
            "用于移动广告竞价、聚合、素材和效果统计。",
            "fyber.com",
            "inner-active.mobi",
        ),
    )

    private val functionHints = listOf(
        FunctionHint(
            setOf("crash", "crashes", "exception", "errorreport", "bugreport"),
            "崩溃或错误诊断",
            "稳定性诊断",
            "域名关键词显示它可能用于发送崩溃、异常或错误诊断信息。",
        ),
        FunctionHint(
            setOf("push", "notification", "notifications", "notify"),
            "消息推送",
            "通知与后台消息",
            "域名关键词显示它可能用于推送通知、后台消息或设备注册。",
        ),
        FunctionHint(
            setOf("auth", "login", "oauth", "oauth2", "sso", "account", "accounts"),
            "登录与身份验证",
            "账号与授权",
            "域名关键词显示它可能用于登录、账号验证或授权。",
        ),
        FunctionHint(
            setOf("pay", "payment", "payments", "billing", "checkout"),
            "支付服务",
            "支付与账单",
            "域名关键词显示它可能用于付款、账单或订单结算。",
        ),
        FunctionHint(
            setOf("cdn", "static", "assets", "asset"),
            "静态资源服务",
            "CDN 与资源加载",
            "域名关键词显示它可能用于加载图片、脚本、视频或其他静态文件。",
        ),
        FunctionHint(
            setOf("img", "image", "images", "media", "video"),
            "图片或媒体资源",
            "媒体内容加载",
            "域名关键词显示它可能用于加载图片、音视频或其他媒体内容。",
        ),
        FunctionHint(
            setOf("api", "gateway", "graphql"),
            "接口服务",
            "应用接口通信",
            "域名关键词显示它可能是应用后端接口或服务网关。",
        ),
        FunctionHint(
            setOf("config", "configuration", "settings", "remoteconfig"),
            "配置服务",
            "应用配置",
            "域名关键词显示它可能用于获取应用参数、功能开关或配置。",
        ),
        FunctionHint(
            setOf("update", "updates", "upgrade", "download", "downloads"),
            "更新或下载服务",
            "软件更新与文件下载",
            "域名关键词显示它可能用于检查更新、下载安装包或其他文件。",
        ),
        FunctionHint(
            setOf("upload", "uploads"),
            "上传服务",
            "内容上传",
            "域名关键词显示它可能用于上传文件、图片或用户提交内容。",
        ),
        FunctionHint(
            setOf("sync", "backup"),
            "同步或备份服务",
            "云同步与备份",
            "域名关键词显示它可能用于同步或备份应用数据。",
        ),
        FunctionHint(
            setOf("map", "maps", "geo", "geocode", "location"),
            "地图或位置服务",
            "地图与位置能力",
            "域名关键词显示它可能用于地图、地理编码或位置相关接口。",
        ),
        FunctionHint(
            setOf("time", "ntp"),
            "时间同步服务",
            "系统时间校准",
            "域名关键词显示它可能用于获取或校准网络时间。",
        ),
    )

    fun resolve(domain: String): DomainInsight? {
        services.forEach { service ->
            val matched = service.domains.firstOrNull { domain.matchesDomain(it) } ?: return@forEach
            return DomainInsight(
                displayName = service.displayName,
                provider = service.provider,
                category = service.category,
                purpose = service.purpose,
                recognitionBasis = "APK 内置离线常用域名映射：$matched",
                confidence = InsightConfidence.HIGH,
            )
        }
        return null
    }

    fun resolveFunction(domain: String, blocked: Boolean): DomainInsight? {
        val tokens = domain.split(Regex("[._-]+"))
        functionHints.forEach { hint ->
            val matched = tokens.firstOrNull { it in hint.tokens } ?: return@forEach
            val base = baseDomain(domain)
            return DomainInsight(
                displayName = "$base · ${hint.displayName}",
                provider = base,
                category = hint.category,
                purpose = hint.purpose,
                recognitionBasis =
                    "${if (blocked) "anti-AD 规则命中；" else "当前规则未拦截；"}域名关键词：$matched",
                confidence = InsightConfidence.MEDIUM,
            )
        }
        return null
    }

    fun baseDomain(domain: String): String {
        val labels = domain.split('.').filter(String::isNotBlank)
        if (labels.size < 2) return domain
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in commonMultiLevelSuffixes && labels.size >= 3) {
            labels.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    private val commonMultiLevelSuffixes = setOf(
        "com.cn", "net.cn", "org.cn", "gov.cn",
        "com.hk", "com.tw", "com.au", "com.br",
        "co.uk", "co.jp", "co.kr", "co.in", "co.nz",
    )

    private fun String.matchesDomain(suffix: String): Boolean = this == suffix || endsWith(".$suffix")
}
