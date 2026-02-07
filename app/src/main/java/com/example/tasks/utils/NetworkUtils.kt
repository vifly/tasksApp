package com.example.tasks.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI

object NetworkUtils {

    /**
     * Performs a fast TCP socket connection check.
     * Returns true if the host:port is reachable.
     */
    fun isServerReachable(url: String, timeoutMs: Int = 2000): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host ?: return false
            val port = if (uri.port != -1) {
                uri.port
            } else {
                if (uri.scheme == "https") 443 else 80
            }

            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets a summary of current network interfaces and IPs for debugging.
     */
    fun getNetworkSnapshot(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)

        val type = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "Unknown"
        }

        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val inter = interfaces.nextElement()
                if (inter.isUp && !inter.isLoopback) {
                    val addrs = inter.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                            ips.add("${inter.name}: ${addr.hostAddress}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }

        return "Type: $type, IPs: [${ips.joinToString(", ")}]"
    }
}
