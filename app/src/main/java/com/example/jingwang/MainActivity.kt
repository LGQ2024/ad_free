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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
            MaterialTheme {
                JingwangApp(
                    container = container,
                    onStartVpn = ::beginVpnPermissionFlow,
                    onStopVpn = ::stopVpn,
                    onReconfigureVpn = ::reconfigureVpn,
                )
            }
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

private enum class Screen(val label: String, val glyph: String) {
    HOME("首页", "盾"),
    LOGS("日志", "记"),
    APPS("应用", "应"),
    SETTINGS("设置", "设"),
}

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("净网 · ${screen.label}") }) },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Text(item.glyph, fontWeight = FontWeight.Bold) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                vpnState = vpnState,
                ruleState = ruleState,
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
            )
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    vpnState: com.example.jingwang.core.model.VpnRuntimeState,
    ruleState: RuleState,
    statistics: com.example.jingwang.core.model.TrafficStatistics,
    integrityWarning: String?,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
) {
    val active = vpnState.status != VpnStatus.STOPPED && vpnState.status != VpnStatus.ERROR
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            StatusCard(
                title = vpnState.message,
                subtitle = "只拦截发往虚拟 DNS 地址的查询；其他流量不进入净网",
                active = vpnState.status == VpnStatus.ACTIVE,
            )
        }
        if (integrityWarning != null) item { WarningCard(integrityWarning) }
        if (ruleState.error != null) item { WarningCard(ruleState.error) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("今日拦截", statistics.blockedToday.toString(), Modifier.weight(1f))
                MetricCard("今日放行", statistics.allowedToday.toString(), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("规则状态", style = MaterialTheme.typography.titleMedium)
                    Text("来源：${ruleState.metadata.source}")
                    Text("版本：${ruleState.metadata.version}")
                    Text("条目：${ruleState.metadata.entryCount}")
                    Text(
                        "SHA-256：${ruleState.metadata.sha256.ifEmpty { "尚未加载" }}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Button(
                onClick = if (active) onStopVpn else onStartVpn,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (active) "停止保护并清空日志" else "开启保护") }
        }
        item {
            Text(
                "正常查询仍由当前 Wi‑Fi 或运营商提供的系统 DNS 处理。净网不会上传查询日志。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun StatusCard(title: String, subtitle: String, active: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
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
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(253) },
            label = { Text("仅搜索当前内存日志") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部", "已拦截", "已放行").forEachIndexed { index, title ->
                FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(title) })
            }
        }
        Text("最多 500 条；停止 VPN 或进程退出后清空", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { "${it.timestampEpochMillis}-${it.domain}" }) { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.domain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${if (entry.blocked) "已拦截" else "已放行"} · ${formatTime(entry.timestampEpochMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.blocked) MaterialTheme.colorScheme.error else Color(0xff2e7d32),
                        )
                    }
                    if (entry.blocked) TextButton(onClick = { onWhitelist(entry.domain) }) { Text("放行") }
                }
                HorizontalDivider()
            }
        }
    }
}

private data class InstalledApp(val label: String, val packageName: String, val system: Boolean)

@Composable
private fun AppsScreen(
    modifier: Modifier,
    context: Context,
    bypassPackages: Set<String>,
    vpnActive: Boolean,
    onBypassChanged: (String, Boolean) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val apps by produceState<List<InstalledApp>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }
    val visible = remember(apps, search) {
        val term = search.trim()
        if (term.isEmpty()) apps else apps.filter {
            it.label.contains(term, true) || it.packageName.contains(term, true)
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(100) },
            label = { Text("搜索已安装应用") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "列表仅保留在内存；只加密保存您选择绕过的包名${if (vpnActive) "，修改会重建 VPN" else ""}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = InstalledApp::packageName) { app ->
                val isSelf = app.packageName == context.packageName
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${app.packageName}${if (app.system) " · 系统" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = isSelf || app.packageName in bypassPackages,
                        enabled = !isSelf,
                        onCheckedChange = { onBypassChanged(app.packageName, it) },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    container: AppContainer,
    whitelist: Set<String>,
    ruleState: RuleState,
) {
    var domain by rememberSaveable { mutableStateOf("") }
    var updateMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var updating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("手动规则更新", style = MaterialTheme.typography.titleMedium)
                    Text("固定地址：https://anti-ad.net/domains.txt", style = MaterialTheme.typography.bodySmall)
                    Text("不会后台更新、启动检查或跟随重定向", style = MaterialTheme.typography.bodySmall)
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
                    ) { Text(if (updating) "正在验证…" else "立即更新并验证") }
                    if (updateMessage != null) Text(updateMessage!!, style = MaterialTheme.typography.bodySmall)
                    if (ruleState.metadata.updatedAtEpochMillis > 0) {
                        Text("上次更新：${formatTime(ruleState.metadata.updatedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("白名单优先", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it.take(253) },
                        label = { Text("例如 example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        val candidate = domain
                        scope.launch(Dispatchers.IO) {
                            if (container.privacyRepository.addWhitelist(candidate)) {
                                withContext(Dispatchers.Main) { domain = "" }
                            }
                        }
                    }) { Text("添加白名单") }
                    whitelist.sorted().forEach { item ->
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
        item {
            OutlinedButton(
                onClick = { container.queryLogRepository.clear() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("立即清空内存日志") }
        }
        item {
            Text("隐私边界", style = MaterialTheme.typography.titleMedium)
            Text(
                "净网不读取网页正文，不上传 DNS 日志，不含账号、广告、统计、崩溃上报、远程配置或动态代码。系统 DNS 仍可看到正常查询；手动更新时 anti-AD 服务器可看到连接 IP 与时间。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun loadInstalledApps(context: Context): List<InstalledApp> {
    val packageManager = context.packageManager
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
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledApp::label).thenBy(InstalledApp::packageName))
}

private fun formatTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(epochMillis))
