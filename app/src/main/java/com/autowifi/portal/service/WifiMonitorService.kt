package com.autowifi.portal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autowifi.portal.MainActivity
import com.autowifi.portal.R
import com.autowifi.portal.data.WifiTargetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WifiMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var solver: HeadlessPortalSolver
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        solver = HeadlessPortalSolver(this)
        startForeground(NOTIFICATION_ID, createNotification("Đang chạy ngầm và quét Wi-Fi công cộng..."))
        registerNetworkCallback()
        Log.i(TAG, "WifiMonitorService started.")
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                handleWifiEvent(network)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, capabilities)
                val hasCaptive = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                if (hasCaptive) {
                    Log.i(TAG, "Network has captive portal capability.")
                    handleWifiEvent(network)
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun handleWifiEvent(network: Network) {
        serviceScope.launch {
            val ssid = getCurrentSsid()
            Log.d(TAG, "Connected Wi-Fi SSID: $ssid")

            val isTarget = WifiTargetConfig.isTargetMatching(applicationContext, ssid)
            if (isTarget) {
                updateNotification("Đang tự động xử lý quảng cáo cho $ssid...")
                val solved = solver.trySolvePortal(network)
                if (solved) {
                    updateNotification("Đã kết nối thành công: $ssid")
                }
            }
        }
    }

    private fun getCurrentSsid(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: ""
            if (ssid == "<unknown ssid>" || ssid.isBlank()) "Wi-Fi công cộng" else ssid
        } catch (e: Exception) {
            "Wi-Fi công cộng"
        }
    }

    private fun createNotification(content: String): Notification {
        val channelId = "wifi_monitor_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auto Wi-Fi Portal Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Theo dõi Wi-Fi công cộng và tự động kết nối"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Wi-Fi Portal")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        serviceScope.cancel()
        Log.i(TAG, "WifiMonitorService stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WifiMonitorService"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java)
            context.stopService(intent)
        }
    }
}
