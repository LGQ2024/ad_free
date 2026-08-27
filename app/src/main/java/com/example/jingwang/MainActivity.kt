package com.example.jingwang

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PersistableBundle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jingwang.core.log.DomainInsightResolver
import com.example.jingwang.core.model.QueryLogEntry
import com.example.jingwang.core.model.VpnStatus
import com.example.jingwang.core.rules.RuleMatcher
import com.example.jingwang.data.RuleState
import com.example.jingwang.vpn.AdBlockingVpnService
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IosLightColors = lightColorScheme(
    primary = Color(0xff08785f),
    onPrimary = Color.White,
    secondary = Color(0xff24966d),
    background = Color(0xfff5f7f6),
    onBackground = Color(0xff1c1c1e),
    surface = Color.White,
    onSurface = Color(0xff1c1c1e),
    surfaceVariant = Color(0xffedf1ef),
    onSurfaceVariant = Color(0xff5f6964),
    outline = Color(0xffd6ddda),
    error = Color(0xffff3b30),
)

private val IosDarkColors = darkColorScheme(
    primary = Color(0xff65d6b0),
    onPrimary = Color(0xff06251d),
    secondary = Color(0xff54c997),
    background = Color(0xff0e1110),
    onBackground = Color.White,
    surface = Color(0xff181c1a),
    onSurface = Color.White,
    surfaceVariant = Color(0xff242a27),
    onSurfaceVariant = Color(0xffabb5b0),
    outline = Color(0xff39413d),
    error = Color(0xffff453a),
)

private val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

class MainActivity : ComponentActivity() {
    private val container get() = (application as JingwangApplication).container

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) AdBlockingVpnService.start(this)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { requestVpnPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JingwangApp(
                container = container,
                onStartVpn = ::beginVpnPermissionFlow,
                onStopVpn = ::stopVpn,
                onReconfigureVpn = ::reconfigureVpn,
            )
        }
    }

    private fun beginVpnPermissionFlow() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent == null) AdBlockingVpnService.start(this) else vpnPermissionLauncher.launch(permissionIntent)
    }

    private fun stopVpn() {
        startService(Intent(this, AdBlockingVpnService::class.java).setAction(AdBlockingVpnService.ACTION_STOP))
    }

    private fun reconfigureVpn() {
        startService(Intent(this, AdBlockingVpnService::class.java).setAction(AdBlockingVpnService.ACTION_RECONFIGURE))
    }
}

private enum class Screen(val label: String) {
    HOME("首页"),
    LOGS("日志"),
    APPS("应用"),
    SETTINGS("设置"),
}

@Composable
private fun JingwangApp(
    container: AppContainer,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onReconfigureVpn: () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val settings by container.privacyRepository.settings.collectAsStateWithLifecycle()
    val ruleState by container.ruleRepository.state.collectAsStateWithLifecycle()
    val vpnState by container.vpnStateRepository.state.collectAsStateWithLifecycle()
    val logs by container.queryLogRepository.logs.collectAsStateWithLifecycle()
    val crashReport by container.crashReportRepository.report.collectAsStateWithLifecycle()
    val screen = Screen.entries[selected]

    MaterialTheme(
        colorScheme = if (settings.darkMode) IosDarkColors else IosLightColors,
        shapes = IosShapes,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { IosTabBar(selected = selected, onSelected = { selected = it }) },
        ) { padding ->
            when (screen) {
                Screen.HOME -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    vpnState = vpnState,
                    statistics = settings.statistics,
                    integrityWarning = container.privacyRepository.integrityWarning,
                    onStartVpn = onStartVpn,
                    onStopVpn = onStopVpn,
                )
                Screen.LOGS -> LogsScreen(
                    modifier = Modifier.padding(padding),
                    logs = logs,
                    whitelist = settings.whitelist,
                    customBlockedDomains = settings.customBlockedDomains,
                    onWhitelist = { domain ->
                        container.applicationScope.launch { container.privacyRepository.addWhitelist(domain) }
                    },
                    onBlock = { domain ->
                        container.applicationScope.launch { container.privacyRepository.addCustomBlock(domain) }
                    },
                )
                Screen.APPS -> AppsScreen(
                    modifier = Modifier.padding(padding),
                    context = context,
                    bypassPackages = settings.bypassPackages,
                    vpnActive = vpnState.status != VpnStatus.STOPPED && vpnState.status != VpnStatus.ERROR,
                    onBypassChanged = { packageName, bypassed ->
                        container.applicationScope.launch {
                            container.privacyRepository.setPackageBypassed(packageName, bypassed)
                            if (vpnState.status != VpnStatus.STOPPED && vpnState.status != VpnStatus.ERROR) {
                                withContext(Dispatchers.Main) { onReconfigureVpn() }
                            }
                        }
                    },
                )
                Screen.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    container = container,
                    whitelist = settings.whitelist,
                    customBlockedDomains = settings.customBlockedDomains,
                    ruleState = ruleState,
                    crashReport = crashReport,
                    darkMode = settings.darkMode,
                    onDarkModeChanged = { enabled ->
                        container.applicationScope.launch {
                            container.privacyRepository.setDarkMode(enabled)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun IosTabBar(selected: Int, onSelected: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 7.dp)
                .heightIn(min = 46.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Screen.entries.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected == index) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSelected(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item.label,
                        color = if (selected == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    vpnState: com.example.jingwang.core.model.VpnRuntimeState,
    statistics: com.example.jingwang.core.model.TrafficStatistics,
    integrityWarning: String?,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
) {
    val running = vpnState.status != VpnStatus.STOPPED && vpnState.status != VpnStatus.ERROR
    val protected = vpnState.status == VpnStatus.ACTIVE
    val statusTitle = when (vpnState.status) {
        VpnStatus.ACTIVE -> "保护正在运行"
        VpnStatus.STARTING -> "正在开启保护"
        VpnStatus.WAITING_FOR_NETWORK -> "等待可用网络"
        VpnStatus.ERROR -> "保护出现问题"
        VpnStatus.STOPPED -> "保护尚未开启"
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeader("净网", "隐私优先的本地 DNS 保护") }
        if (integrityWarning != null) item { WarningCard(integrityWarning) }
        item {
            ProtectionCard(
                title = statusTitle,
                message = vpnState.message,
                running = running,
                protected = protected,
                problem = vpnState.status == VpnStatus.ERROR,
                onStartVpn = onStartVpn,
                onStopVpn = onStopVpn,
            )
        }
        item {
            TrafficSummaryCard(
                blocked = statistics.blockedToday,
                allowed = statistics.allowedToday,
            )
        }
        item {
            TrustCard(
                title = "隐私边界",
                items = listOf(
                    "仅处理 DNS" to "不读取网页正文，也不接管普通应用流量",
                    "日志仅在内存" to "最多 500 条，停止保护后立即清空",
                    "无遥测上传" to "不含统计、广告或联网崩溃上报",
                ),
            )
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ProtectionCard(
    title: String,
    message: String,
    running: Boolean,
    protected: Boolean,
    problem: Boolean,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
) {
    val statusColor = when {
        problem -> MaterialTheme.colorScheme.error
        protected -> MaterialTheme.colorScheme.secondary
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .size(58.dp)
                        .background(statusColor.copy(alpha = 0.13f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(18.dp).background(statusColor, CircleShape))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        contentColor = statusColor,
                        shape = RoundedCornerShape(99.dp),
                    ) {
                        Text(
                            when {
                                problem -> "需要处理"
                                protected -> "DNS 保护已生效"
                                running -> "保护准备中"
                                else -> "当前未保护"
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (running) {
                OutlinedButton(
                    onClick = onStopVpn,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("停止保护") }
            } else {
                Button(
                    onClick = onStartVpn,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (problem) "重新开启保护" else "开启保护") }
            }
            Text(
                if (running) {
                    "停止保护会同时清空内存中的 DNS 日志。"
                } else {
                    "首次开启时，Android 会显示系统 VPN 授权确认。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrafficSummaryCard(blocked: Long, allowed: Long) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("今日 DNS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrafficMetric("已拦截", blocked.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                )
                TrafficMetric("已放行", allowed.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrafficMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrustCard(title: String, items: List<Pair<String, String>>) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            items.forEach { item ->
                TrustRow(item.first, item.second)
            }
        }
    }
}

@Composable
private fun TrustRow(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
    ) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun IosSegmentedControl(
    options: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.padding(2.dp)) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected == index) MaterialTheme.colorScheme.surface else Color.Transparent,
                        )
                        .clickable { onSelected(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoStrip(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.width(64.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LogsScreen(
    modifier: Modifier,
    logs: List<QueryLogEntry>,
    whitelist: Set<String>,
    customBlockedDomains: Set<String>,
    onWhitelist: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var selectedEntry by remember { mutableStateOf<QueryLogEntry?>(null) }
    val manualRuleMatcher = remember(whitelist, customBlockedDomains) {
        RuleMatcher(emptySet(), whitelist, customBlockedDomains)
    }
    val filtered = remember(logs, search, filter) {
        val term = search.trim()
        logs.filter { entry ->
            val matchesSearch = if (term.isEmpty()) {
                true
            } else {
                val insight = DomainInsightResolver.resolve(entry.domain, entry.blocked)
                entry.domain.contains(term, ignoreCase = true) ||
                    insight.displayName.contains(term, ignoreCase = true) ||
                    insight.provider.contains(term, ignoreCase = true) ||
                    insight.category.contains(term, ignoreCase = true) ||
                    entry.sourceApp?.label?.contains(term, ignoreCase = true) == true ||
                    entry.sourceApp?.packageNames?.any { it.contains(term, ignoreCase = true) } == true
            }
            matchesSearch &&
                (filter == 0 || (filter == 1 && entry.blocked) || (filter == 2 && !entry.blocked))
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("日志", logs.size.toString() + " 条记录仅保存在内存")
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(253) },
            label = { Text("搜索应用、用途或域名") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        IosSegmentedControl(
            options = listOf("全部", "已拦截", "已放行"),
            selected = filter,
            onSelected = { filter = it },
        )
        Spacer(Modifier.height(10.dp))
        InfoStrip(
            title = filtered.size.toString() + " 条结果",
            detail = "最多 500 条 · 停止保护即清空",
        )
        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (filtered.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("暂无匹配日志", fontWeight = FontWeight.SemiBold)
                            Text(
                                "开启保护并产生 DNS 查询后，记录会显示在这里。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(filtered, key = { it.id }) { entry ->
                    val insight = remember(entry.domain, entry.blocked) {
                        DomainInsightResolver.resolve(entry.domain, entry.blocked)
                    }
                    val customBlocked = manualRuleMatcher.isCustomBlocked(entry.domain)
                    val whitelisted = manualRuleMatcher.isWhitelisted(entry.domain) && !customBlocked
                    val sourceName = entry.sourceApp?.let { source ->
                        "来源 " + source.label + if (source.sharedUid) "（共享 UID）" else ""
                    } ?: insight.provider
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedEntry = entry }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                insight.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                sourceName + " · " + insight.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                entry.domain,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                (if (entry.blocked) "已拦截" else "已放行") +
                                    " · " + formatTime(entry.timestampEpochMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.blocked) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (entry.blocked) {
                            TextButton(
                                onClick = { onWhitelist(entry.domain) },
                                enabled = !whitelisted,
                            ) { Text(if (whitelisted) "已放行" else "放行") }
                        } else {
                            TextButton(
                                onClick = { onBlock(entry.domain) },
                                enabled = !customBlocked,
                            ) { Text(if (customBlocked) "已设置" else "拦截") }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    selectedEntry?.let { entry ->
        LogDetailDialog(entry = entry, onDismiss = { selectedEntry = null })
    }
}

@Composable
private fun LogDetailDialog(entry: QueryLogEntry, onDismiss: () -> Unit) {
    val insight = remember(entry.domain, entry.blocked) {
        DomainInsightResolver.resolve(entry.domain, entry.blocked)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(insight.displayName) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { DetailRow("服务平台", insight.provider) }
                item { DetailRow("用途分类", insight.category) }
                item { DetailRow("可能用途", insight.purpose) }
                item { DetailRow("识别可信度", "${insight.confidence.label}：${insight.confidence.explanation}") }
                item {
                    DetailRow(
                        "来源应用",
                        entry.sourceApp?.let { source ->
                            if (source.sharedUid) {
                                "${source.label}。这些应用共享同一 UID，无法可靠区分具体是哪一个。"
                            } else {
                                "${source.label}。由 Android 系统连接归属 API 识别。"
                            }
                        } ?: if (Build.VERSION.SDK_INT < 29) {
                            "当前为 Android 8/9，系统尚未提供 VPN 连接所属 UID 查询 API。"
                        } else "Android 未返回该连接的所属 UID，净网不会用猜测结果代替。",
                    )
                }
                entry.sourceApp?.let { source ->
                    item { DetailRow("应用包名", source.packageNames.joinToString("\n")) }
                }
                item { DetailRow("原始域名", entry.domain) }
                item {
                    DetailRow(
                        "处理结果",
                        if (entry.blocked) {
                            "已拦截：返回 NXDOMAIN，未向上游系统 DNS 转发。"
                        } else {
                            "已放行：查询已转发到当前网络提供的系统 DNS。"
                        },
                    )
                }
                item { DetailRow("发生时间", formatTime(entry.timestampEpochMillis)) }
                item { DetailRow("识别依据", insight.recognitionBasis) }
                item {
                    DetailRow(
                        "数据保存",
                        "该日志仅保存在内存中，停止 VPN、进程退出或设备重启后清空。",
                    )
                }
                item {
                    Text(
                        "平台与用途来自 APK 内置离线映射或域名关键词；来源 APP 来自 Android 本机连接归属。结果可能不完整，净网不会为识别这些信息联网、写入磁盘或上传数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xff6e6e73),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        containerColor = Color.White,
        titleContentColor = Color(0xff1c1c1e),
        textContentColor = Color(0xff1c1c1e),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xff6e6e73),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private data class InstalledApp(
    val label: String,
    val packageName: String,
    val system: Boolean,
    val hasLauncher: Boolean,
)

@Composable
private fun AppsScreen(
    modifier: Modifier,
    context: Context,
    bypassPackages: Set<String>,
    vpnActive: Boolean,
    onBypassChanged: (String, Boolean) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var appFilter by remember { mutableIntStateOf(0) }
    val apps by produceState<List<InstalledApp>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }
    val effectiveBypass = remember(bypassPackages, context.packageName) {
        bypassPackages + context.packageName
    }
    val visible = remember(apps, effectiveBypass, search, appFilter) {
        val term = search.trim()
        apps.filter { app ->
            val matchesFilter = when (appFilter) {
                0 -> app.hasLauncher || app.packageName in effectiveBypass
                1 -> true
                else -> app.packageName in effectiveBypass
            }
            matchesFilter &&
                (term.isEmpty() || app.label.contains(term, true) || app.packageName.contains(term, true))
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("应用", bypassPackages.size.toString() + " 个应用已绕过拦截")
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(100) },
            label = { Text("搜索名称或包名") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        IosSegmentedControl(
            options = listOf("桌面应用", "全部应用", "已绕过"),
            selected = appFilter,
            onSelected = { appFilter = it },
        )
        Spacer(Modifier.height(10.dp))
        InfoStrip(
            title = visible.size.toString() + " 个结果",
            detail = if (vpnActive) "更改后自动重建保护" else "完整应用列表仅在内存",
        )
        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (visible.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("没有匹配的应用", fontWeight = FontWeight.SemiBold)
                            Text(
                                "可切换筛选范围或尝试搜索包名。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(visible, key = InstalledApp::packageName) { app ->
                    val isSelf = app.packageName == context.packageName
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                app.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (app.system || !app.hasLauncher) {
                                Text(
                                    buildString {
                                        if (app.system) append("系统应用")
                                        if (app.system && !app.hasLauncher) append(" · ")
                                        if (!app.hasLauncher) append("无桌面图标")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = app.packageName in effectiveBypass,
                            enabled = !isSelf,
                            onCheckedChange = { onBypassChanged(app.packageName, it) },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    container: AppContainer,
    whitelist: Set<String>,
    customBlockedDomains: Set<String>,
    ruleState: RuleState,
    crashReport: String?,
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
) {
    var domain by rememberSaveable { mutableStateOf("") }
    var updateMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var updating by rememberSaveable { mutableStateOf(false) }
    var showCrashReport by rememberSaveable { mutableStateOf(false) }
    var crashReportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("设置", "安全、规则与本地数据") }
        item {
            TrustCard(
                title = "安全状态",
                items = listOf(
                    "DNS 日志" to "仅保存在内存，停止保护后清空",
                    "规则更新" to "仅在你主动点击时访问固定 HTTPS 地址",
                    "系统备份" to "云备份和设备迁移备份均已关闭",
                ),
            )
        }
        item { SectionTitle("规则与更新") }
        if (ruleState.error != null) item { WarningCard(ruleState.error) }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SettingsValueRow("来源", ruleState.metadata.source)
                    SettingsValueRow("版本", ruleState.metadata.version)
                    SettingsValueRow("条目", ruleState.metadata.entryCount.toString())
                    SettingsValueRow(
                        "SHA-256",
                        ruleState.metadata.sha256.ifEmpty { "尚未加载" },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    )
                    Text("手动更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "固定地址：https://anti-ad.net/domains.txt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "不会后台更新、启动检查或跟随重定向",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            updating = true
                            updateMessage = null
                            scope.launch {
                                val result = container.ruleRepository.updateManually()
                                updateMessage = result.fold(
                                    onSuccess = { "更新成功：${it.entryCount} 条，SHA-256 ${it.sha256}" },
                                    onFailure = { container.ruleRepository.state.value.error ?: "更新失败，旧规则已保留" },
                                )
                                updating = false
                            }
                        },
                        enabled = !updating,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    ) { Text(if (updating) "正在验证…" else "立即更新并验证") }
                    if (updateMessage != null) {
                        Text(updateMessage!!, style = MaterialTheme.typography.bodySmall)
                    }
                    if (ruleState.metadata.updatedAtEpochMillis > 0) {
                        Text(
                            "上次更新：${formatTime(ruleState.metadata.updatedAtEpochMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { SectionTitle("始终拦截") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("自定义拦截", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "在日志页点击“拦截”后，该域名及其子域名会优先被拦截。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (customBlockedDomains.isEmpty()) {
                        Text(
                            "尚未设置自定义拦截",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    customBlockedDomains.sorted().forEach { item ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = {
                                container.applicationScope.launch {
                                    container.privacyRepository.removeCustomBlock(item)
                                }
                            }) { Text("移除") }
                        }
                    }
                }
            }
        }
        item { SectionTitle("始终放行") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("白名单规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "加入后，该域名及其子域名不会被公共规则拦截；自定义拦截仍然优先。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it.take(253) },
                        label = { Text("例如 example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val candidate = domain
                            scope.launch(Dispatchers.IO) {
                                if (container.privacyRepository.addWhitelist(candidate)) {
                                    withContext(Dispatchers.Main) { domain = "" }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("添加白名单") }
                    if (whitelist.isEmpty()) {
                        Text(
                            "尚未添加白名单",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    whitelist.sorted().forEach { item ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = {
                                container.applicationScope.launch { container.privacyRepository.removeWhitelist(item) }
                            }) { Text("移除") }
                        }
                    }
                }
            }
        }
        item { SectionTitle("本地数据") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("内存日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "最多保留 500 条 DNS 记录和尽力识别的来源应用信息，不写入磁盘，也不会上传。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { container.queryLogRepository.clear() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("立即清空内存日志") }
                    Text(
                        "被放行的正常查询仍由当前网络提供的系统 DNS 处理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SectionTitle("崩溃诊断") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "最近一次闪退",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (crashReport == null) {
                            "没有保存的崩溃报告。"
                        } else {
                            "报告包含完整异常消息和堆栈，可能带有当时的运行数据；仅保存在应用私有且禁止备份的目录中，新报告会覆盖旧报告。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    crashReport?.let { report ->
                        Button(
                            onClick = { showCrashReport = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("查看崩溃报告") }
                        OutlinedButton(
                            onClick = {
                                copyCrashReport(context, report)
                                crashReportMessage = "已复制到系统剪贴板；请只在准备发送分析时复制，并在发送后清除剪贴板。"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("复制崩溃报告") }
                        TextButton(
                            onClick = {
                                container.crashReportRepository.clear()
                                showCrashReport = false
                                crashReportMessage = "崩溃报告已删除。"
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("删除崩溃报告") }
                    }
                    crashReportMessage?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { SectionTitle("外观") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("深色模式", fontWeight = FontWeight.SemiBold)
                        Text(
                            "关闭时使用浅色界面",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = darkMode, onCheckedChange = onDarkModeChanged)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (showCrashReport) {
        crashReport?.let { report ->
            AlertDialog(
                onDismissRequest = { showCrashReport = false },
                title = { Text("最近一次崩溃报告") },
                text = {
                    SelectionContainer {
                        LazyColumn(Modifier.heightIn(max = 520.dp)) {
                            item {
                                Text(
                                    report,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        copyCrashReport(context, report)
                        crashReportMessage = "已复制到系统剪贴板；发送后请清除剪贴板。"
                    }) { Text("复制") }
                },
                dismissButton = {
                    TextButton(onClick = { showCrashReport = false }) { Text("关闭") }
                },
            )
        }
    }
}

private fun copyCrashReport(context: Context, report: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("净网崩溃报告", report)
    clip.description.extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= 33) {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launcherPackages = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcherIntent, 0)
    }.mapTo(hashSetOf()) { it.activityInfo.packageName }
    val applications = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getInstalledApplications(0)
    }
    return applications.map { info ->
        InstalledApp(
            label = packageManager.getApplicationLabel(info).toString(),
            packageName = info.packageName,
            system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            hasLauncher = info.packageName in launcherPackages,
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledApp::label).thenBy(InstalledApp::packageName))
}

private fun formatTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(epochMillis))
