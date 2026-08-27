package com.example.jingwang.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.jingwang.JingwangApplication
import com.example.jingwang.MainActivity
import com.example.jingwang.R
import com.example.jingwang.core.dns.DnsMessage
import com.example.jingwang.core.dns.IpPacketCodec
import com.example.jingwang.core.dns.UdpDnsPacket
import com.example.jingwang.core.model.VpnRuntimeState
import com.example.jingwang.core.model.VpnStatus
import com.example.jingwang.core.rules.RuleMatcher
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

class AdBlockingVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outputLock = Any()
    private val packetSlots = Semaphore(MAX_CONCURRENT_QUERIES)
    private lateinit var systemDnsNetwork: SystemDnsNetwork
    private lateinit var appConnectionResolver: AppConnectionResolver
    private lateinit var forwarder: SystemDnsForwarder
    private var tunnel: ParcelFileDescriptor? = null
    private var tunnelJob: Job? = null
    @Volatile private var matcher = RuleMatcher(emptySet(), emptySet())

    private val container get() = (application as JingwangApplication).container

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        systemDnsNetwork = SystemDnsNetwork(connectivityManager)
        appConnectionResolver = AppConnectionResolver(this, connectivityManager)
        forwarder = SystemDnsForwarder(this) { systemDnsNetwork.current.value }
        serviceScope.launch {
            combine(container.privacyRepository.settings, container.ruleRepository.state) { settings, _ ->
                container.ruleRepository.matcher(settings.whitelist, settings.customBlockedDomains)
            }.collect { matcher = it }
        }
        serviceScope.launch {
            systemDnsNetwork.current.collect { network ->
                if (tunnel != null) {
                    setUnderlyingNetworks(network?.network?.let { arrayOf(it) })
                    updateState(
                        if (network == null) VpnStatus.WAITING_FOR_NETWORK else VpnStatus.ACTIVE,
                        if (network == null) "等待可用的系统 DNS" else "保护已开启",
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> stopVpnAndSelf()
            ACTION_RECONFIGURE -> {
                startForegroundSafely()
                serviceScope.launch { restartTunnel() }
            }
            else -> {
                startForegroundSafely()
                serviceScope.launch { startTunnel() }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpnAndSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        tunnelJob?.cancel()
        tunnel?.close()
        tunnel = null
        container.privacyRepository.flush()
        container.queryLogRepository.clear()
        systemDnsNetwork.close()
        serviceScope.cancel()
        updateState(VpnStatus.STOPPED, "已停止")
        super.onDestroy()
    }

    private suspend fun startTunnel() {
        if (tunnel != null) return
        updateState(VpnStatus.STARTING, "正在建立本地 DNS VPN")
        container.ruleRepository.ensureLoaded()
        if (!container.ruleRepository.state.value.ready) {
            updateState(VpnStatus.ERROR, "规则不可用，未启动 VPN")
            stopSelf()
            return
        }
        val descriptor = try {
            buildTunnel().establish()
        } catch (_: Exception) {
            null
        }
        if (descriptor == null) {
            updateState(VpnStatus.ERROR, "无法建立 VPN，请检查系统授权")
            stopSelf()
            return
        }
        tunnel = descriptor
        updateState(
            if (systemDnsNetwork.current.value == null) VpnStatus.WAITING_FOR_NETWORK else VpnStatus.ACTIVE,
            if (systemDnsNetwork.current.value == null) "等待可用的系统 DNS" else "保护已开启",
        )
        tunnelJob = serviceScope.launch { packetLoop(descriptor) }
    }

    private suspend fun restartTunnel() {
        tunnelJob?.cancel()
        tunnelJob = null
        tunnel?.close()
        tunnel = null
        startTunnel()
    }

    private fun buildTunnel(): Builder {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(TUN_MTU)
            .addAddress(IPV4_INTERFACE, 32)
            .addDnsServer(IPV4_DNS)
            .addRoute(IPV4_DNS, 32)
            .addAddress(IPV6_INTERFACE, 128)
            .addDnsServer(IPV6_DNS)
            .addRoute(IPV6_DNS, 128)
            .setBlocking(true)
            .setUnderlyingNetworks(systemDnsNetwork.preferredNetwork()?.let { arrayOf(it) })

        val excluded = container.privacyRepository.settings.value.bypassPackages + packageName
        excluded.forEach { packageName ->
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {
                // 已卸载或当前不可用的历史包名不会扩大 VPN 权限范围。
            }
        }
        return builder
    }

    private suspend fun packetLoop(descriptor: ParcelFileDescriptor) = withContext(Dispatchers.IO) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(65_535)
        try {
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                val request = try {
                    IpPacketCodec.parseUdp(buffer, length)
                } catch (_: Exception) {
                    continue
                }
                if (!isVirtualDnsRequest(request)) continue
                packetSlots.acquire()
                launch {
                    try {
                        val response = processDnsRequest(request) ?: return@launch
                        synchronized(outputLock) {
                            output.write(response)
                        }
                    } finally {
                        packetSlots.release()
                    }
                }
            }
        } catch (_: CancellationException) {
            throw CancellationException()
        } catch (_: Exception) {
            if (tunnel != null) updateState(VpnStatus.ERROR, "VPN 数据通道意外停止")
        }
    }

    private fun processDnsRequest(request: UdpDnsPacket): ByteArray? {
        val query = try {
            DnsMessage.parseSingleQuestionQuery(request.dnsPayload)
        } catch (_: Exception) {
            return null
        }
        val domain = query.question.name
        val sourceApp = appConnectionResolver.resolve(request)
        val blocked = matcher.shouldBlock(domain)
        val dnsResponse = if (blocked) {
            DnsMessage.nxdomain(query)
        } else {
            try {
                forwarder.forward(request.dnsPayload)
            } catch (_: Exception) {
                return null
            }
        }
        if (dnsResponse.size > MAX_DNS_RESPONSE) return null
        container.queryLogRepository.add(domain, blocked, sourceApp)
        container.privacyRepository.recordQuery(blocked)
        return try {
            IpPacketCodec.buildUdpResponse(request, dnsResponse)
        } catch (_: Exception) {
            null
        }
    }

    private fun isVirtualDnsRequest(request: UdpDnsPacket): Boolean {
        if (request.destinationPort != DNS_PORT) return false
        val expected = if (request.ipVersion == 4) IPV4_DNS else IPV6_DNS
        return request.destinationAddress == InetAddress.getByName(expected)
    }

    private fun startForegroundSafely() {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AdBlockingVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("净网正在保护 DNS")
            .setContentText("仅处理发往虚拟 DNS 地址的查询")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "停止", stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            "VPN 运行状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "只显示净网 DNS VPN 是否正在运行"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun stopVpnAndSelf() {
        tunnelJob?.cancel()
        tunnel?.close()
        tunnel = null
        container.privacyRepository.flush()
        container.queryLogRepository.clear()
        updateState(VpnStatus.STOPPED, "已停止")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateState(status: VpnStatus, message: String) {
        container.vpnStateRepository.update(VpnRuntimeState(status, message))
    }

    companion object {
        const val ACTION_START = "com.example.jingwang.action.START"
        const val ACTION_STOP = "com.example.jingwang.action.STOP"
        const val ACTION_RECONFIGURE = "com.example.jingwang.action.RECONFIGURE"
        const val IPV4_INTERFACE = "10.111.222.1"
        const val IPV4_DNS = "10.111.222.2"
        const val IPV6_INTERFACE = "fd66:6a69:6e67::1"
        const val IPV6_DNS = "fd66:6a69:6e67::2"
        private const val DNS_PORT = 53
        private const val TUN_MTU = 4096
        private const val MAX_DNS_RESPONSE = 65_487
        private const val MAX_CONCURRENT_QUERIES = 32
        private const val NOTIFICATION_CHANNEL = "vpn_status"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, action: String = ACTION_START) {
            val intent = Intent(context, AdBlockingVpnService::class.java).setAction(action)
            context.startForegroundService(intent)
        }
    }
}
