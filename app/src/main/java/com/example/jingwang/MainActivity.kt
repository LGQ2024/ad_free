package com.example.jingwang

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jingwang.core.model.QueryLogEntry
import com.example.jingwang.core.model.VpnStatus
import com.example.jingwang.data.RuleState
import com.example.jingwang.vpn.AdBlockingVpnService
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IosLightColors = lightColorScheme(
    primary = Color(0xff007aff),
    onPrimary = Color.White,
    secondary = Color(0xff34c759),
    background = Color(0xfff2f2f7),
    onBackground = Color(0xff1c1c1e),
    surface = Color.White,
    onSurface = Color(0xff1c1c1e),
    surfaceVariant = Color(0xffe5e5ea),
    onSurfaceVariant = Color(0xff6e6e73),
    outline = Color(0xffc6c6c8),
    error = Color(0xffff3b30),
)

private val IosDarkColors = darkColorScheme(
    primary = Color(0xff0a84ff),
    onPrimary = Color.White,
    secondary = Color(0xff30d158),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xff1c1c1e),
    onSurface = Color.White,
    surfaceVariant = Color(0xff2c2c2e),
    onSurfaceVariant = Color(0xffaeaeb2),
    outline = Color(0xff48484a),
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
                    onWhitelist = { domain ->
                        container.applicationScope.launch { container.privacyRepository.addWhitelist(domain) }
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
                    ruleState = ruleState,
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
        shadowElevation = 10.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Screen.entries.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(index) }
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item.label,
                        color = if (selected == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal,
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
    val active = vpnState.status != VpnStatus.STOPPED && vpnState.status != VpnStatus.ERROR
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("净网", "本地 DNS 广告拦截") }
        item {
            StatusCard(
                title = vpnState.message,
                subtitle = "只处理发往虚拟 DNS 的查询，普通网络流量不会进入净网",
                active = vpnState.status == VpnStatus.ACTIVE,
            )
        }
        if (integrityWarning != null) item { WarningCard(integrityWarning) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("今日拦截", statistics.blockedToday.toString(), Modifier.weight(1f))
                MetricCard("今日放行", statistics.allowedToday.toString(), Modifier.weight(1f))
            }
        }
        item {
            Button(
                onClick = if (active) onStopVpn else onStartVpn,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) { Text(if (active) "停止保护并清空日志" else "开启保护") }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Text(
                    "正常查询仍由当前 Wi‑Fi 或运营商的系统 DNS 处理。净网不会上传查询日志。",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
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
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusCard(title: String, subtitle: String, active: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                color = if (active) {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(99.dp),
            ) {
                Text(
                    if (active) "保护中" else "未保护",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun LogsScreen(
    modifier: Modifier,
    logs: List<QueryLogEntry>,
    onWhitelist: (String) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filtered = remember(logs, search, filter) {
        logs.filter { entry ->
            (search.isBlank() || entry.domain.contains(search.trim(), ignoreCase = true)) &&
                (filter == 0 || (filter == 1 && entry.blocked) || (filter == 2 && !entry.blocked))
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("日志", "仅保存在内存中的最近查询")
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(253) },
            label = { Text("搜索域名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        IosSegmentedControl(
            options = listOf("全部", "已拦截", "已放行"),
            selected = filter,
            onSelected = { filter = it },
        )
        Text(
            "最多 500 条；停止 VPN 或进程退出后清空",
            modifier = Modifier.padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            "暂无匹配日志",
                            modifier = Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(filtered, key = { "${it.timestampEpochMillis}-${it.domain}" }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.domain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${if (entry.blocked) "已拦截" else "已放行"} · ${formatTime(entry.timestampEpochMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.blocked) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                            )
                        }
                        if (entry.blocked) {
                            TextButton(onClick = { onWhitelist(entry.domain) }) { Text("放行") }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
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
        ScreenHeader("应用", "选择不经过净网拦截的应用")
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(100) },
            label = { Text("搜索应用") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        IosSegmentedControl(
            options = listOf("桌面应用", "全部应用", "已绕过"),
            selected = appFilter,
            onSelected = { appFilter = it },
        )
        Text(
            "完整列表只保留在内存；仅加密保存已选择的包名${if (vpnActive) "，修改会重建 VPN" else ""}",
            modifier = Modifier.padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            "没有匹配的应用",
                            modifier = Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(visible, key = InstalledApp::packageName) { app ->
                    val isSelf = app.packageName == context.packageName
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    append(app.packageName)
                                    if (app.system) append(" · 系统")
                                    if (!app.hasLauncher) append(" · 无桌面图标")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = app.packageName in effectiveBypass,
                            enabled = !isSelf,
                            onCheckedChange = { onBypassChanged(app.packageName, it) },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
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
    ruleState: RuleState,
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
) {
    var domain by rememberSaveable { mutableStateOf("") }
    var updateMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var updating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("设置", "外观、规则和隐私控制") }
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
        item { SectionTitle("规则与更新") }
        if (ruleState.error != null) item { WarningCard(ruleState.error) }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("规则状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("来源：${ruleState.metadata.source}")
                    Text("版本：${ruleState.metadata.version}")
                    Text("条目：${ruleState.metadata.entryCount}")
                    Text(
                        "SHA-256：${ruleState.metadata.sha256.ifEmpty { "尚未加载" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        item { SectionTitle("白名单") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("白名单优先", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
        item { SectionTitle("隐私与日志") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "净网不读取网页正文，不上传 DNS 日志，不含账号、广告、统计、崩溃上报、远程配置或动态代码。系统 DNS 仍可看到正常查询；手动更新时 anti-AD 服务器可看到连接 IP 与时间。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = { container.queryLogRepository.clear() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("立即清空内存日志") }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
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
