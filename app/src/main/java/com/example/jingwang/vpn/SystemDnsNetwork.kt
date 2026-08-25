package com.example.jingwang.vpn

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.InetAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UpstreamNetwork(
    val network: Network,
    val dnsServers: List<InetAddress>,
)

class SystemDnsNetwork(private val connectivityManager: ConnectivityManager) : AutoCloseable {
    private val lock = Any()
    private val candidates = LinkedHashMap<Network, List<InetAddress>>()
    private val mutableCurrent = MutableStateFlow<UpstreamNetwork?>(null)
    val current: StateFlow<UpstreamNetwork?> = mutableCurrent.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh(network, connectivityManager.getLinkProperties(network))

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            refresh(network, linkProperties)

        override fun onLost(network: Network) = synchronized(lock) {
            candidates.remove(network)
            publishLocked()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
    }

    fun preferredNetwork(): Network? = current.value?.network

    private fun refresh(network: Network, properties: LinkProperties?) = synchronized(lock) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val eligible = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        val servers = properties?.dnsServers.orEmpty()
            .filterNot { it.hostAddress == AdBlockingVpnService.IPV4_DNS || it.hostAddress == AdBlockingVpnService.IPV6_DNS }
            .distinct()
        if (!eligible || servers.isEmpty()) candidates.remove(network) else candidates[network] = servers
        publishLocked()
    }

    private fun publishLocked() {
        val currentNetwork = mutableCurrent.value?.network
        val selected = if (currentNetwork != null && candidates.containsKey(currentNetwork)) {
            currentNetwork
        } else {
            candidates.keys.lastOrNull()
        }
        mutableCurrent.value = selected?.let { UpstreamNetwork(it, candidates.getValue(it)) }
    }

    override fun close() {
        connectivityManager.unregisterNetworkCallback(callback)
        synchronized(lock) {
            candidates.clear()
            mutableCurrent.value = null
        }
    }
}
