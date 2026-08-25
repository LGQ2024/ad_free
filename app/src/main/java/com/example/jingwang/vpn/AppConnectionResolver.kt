package com.example.jingwang.vpn

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import com.example.jingwang.core.dns.UdpDnsPacket
import com.example.jingwang.core.model.QuerySourceApp
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

internal data class InstalledPackageIdentity(val packageName: String, val label: String)

internal class AppConnectionResolver internal constructor(
    private val sdkInt: Int,
    private val ownerUidLookup: (Int, InetSocketAddress, InetSocketAddress) -> Int,
    private val packagesForUid: (Int) -> List<InstalledPackageIdentity>,
) {
    private val cache = ConcurrentHashMap<Int, QuerySourceApp>()

    constructor(context: Context, connectivityManager: ConnectivityManager) : this(
        sdkInt = Build.VERSION.SDK_INT,
        ownerUidLookup = { protocol, local, remote ->
            if (Build.VERSION.SDK_INT >= 29) {
                connectivityManager.getConnectionOwnerUid(protocol, local, remote)
            } else {
                INVALID_UID
            }
        },
        packagesForUid = packageLookup(context.packageManager),
    )

    fun resolve(request: UdpDnsPacket): QuerySourceApp? {
        if (sdkInt < 29) return null
        val local = InetSocketAddress(request.sourceAddress, request.sourcePort)
        val remote = InetSocketAddress(request.destinationAddress, request.destinationPort)
        val uid = try {
            ownerUidLookup(IP_PROTOCOL_UDP, local, remote)
        } catch (_: Exception) {
            INVALID_UID
        }
        if (uid < 0) return null
        cache[uid]?.let { return it }
        val packages = try {
            packagesForUid(uid)
        } catch (_: Exception) {
            emptyList()
        }
        if (packages.isEmpty()) return null
        val packageNames = packages.map { it.packageName }.distinct().sorted().take(MAX_SHARED_PACKAGES)
        val labels = packages.map { it.label.sanitizeLabel() }.filter(String::isNotBlank).distinct()
        val shared = packageNames.size > 1
        val displayLabel = if (shared) {
            labels.take(MAX_SHARED_LABELS).joinToString(" / ").let { joined ->
                if (labels.size > MAX_SHARED_LABELS) "$joined 等 ${labels.size} 个应用" else joined
            }
        } else {
            labels.firstOrNull() ?: packageNames.first()
        }
        return QuerySourceApp(displayLabel, packageNames, shared).also { cache[uid] = it }
    }

    companion object {
        private const val INVALID_UID = -1
        private const val IP_PROTOCOL_UDP = 17
        private const val MAX_SHARED_PACKAGES = 8
        private const val MAX_SHARED_LABELS = 3

        private fun packageLookup(
            packageManager: PackageManager,
        ): (Int) -> List<InstalledPackageIdentity> = { uid ->
            packageManager.getPackagesForUid(uid).orEmpty().map { packageName ->
                val label = runCatching {
                    val applicationInfo = if (Build.VERSION.SDK_INT >= 33) {
                        packageManager.getApplicationInfo(
                            packageName,
                            PackageManager.ApplicationInfoFlags.of(0L),
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getApplicationInfo(packageName, 0)
                    }
                    packageManager.getApplicationLabel(applicationInfo).toString()
                }.getOrDefault(packageName)
                InstalledPackageIdentity(packageName, label)
            }
        }

        private fun String.sanitizeLabel(): String =
            replace('\r', ' ').replace('\n', ' ').trim().take(80)
    }
}
