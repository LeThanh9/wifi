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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WifiMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var awingSolver: AwingWebViewSolver
    private lateinit var headlessSolver: HeadlessPortalSolver
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val solveMutex = Mutex()
    private var sessionWatchdogJob: Job? = null
    private var currentConnectedNetwork: Network? = null
    private var currentSsid: String = ""

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        awingSolver = AwingWebViewSolver(this)
        headlessSolver = HeadlessPortalSolver(this)

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
                Log.d(TAG, "Wi-Fi network onAvailable: $network")
                handleWifiEvent(network)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, capabilities)
                val hasCaptive = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(TAG, "Wi-Fi capabilities changed: hasCaptive=$hasCaptive, isValidated=$isValidated")

                if (hasCaptive || !isValidated) {
                    handleWifiEvent(network, capabilities)
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "Wi-Fi network onLost: $network")
                if (network == currentConnectedNetwork) {
                    sessionWatchdogJob?.cancel()
                    sessionWatchdogJob = null
                    currentConnectedNetwork = null
                    currentSsid = ""
                    updateNotification("Đang chạy ngầm và quét Wi-Fi công cộng...")
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun handleWifiEvent(network: Network, capabilities: NetworkCapabilities? = null) {
        serviceScope.launch {
            if (!solveMutex.tryLock()) {
                Log.d(TAG, "Solve already in progress. Skipping duplicate event.")
                return@launch
            }

            try {
                val ssid = extractSsid(capabilities)
                currentSsid = ssid
                currentConnectedNetwork = network
                Log.d(TAG, "Connected Wi-Fi SSID: $ssid")

                val isTarget = WifiTargetConfig.isTargetMatching(applicationContext, ssid)
                val isAwing = WifiTargetConfig.isAwingSsid(ssid)

                if (!isTarget && !isAwing) {
                    Log.d(TAG, "SSID '$ssid' does not match target configs. Ignoring.")
                    return@launch
                }

                // Check if network already has internet access
                val alreadyConnected = awingSolver.verifyInternetAccess(network)
                if (alreadyConnected) {
                    Log.i(TAG, "Wi-Fi $ssid already has internet access (HTTP 204).")
                    updateNotification("Đã kết nối Wi-Fi: $ssid")
                    startSessionWatchdog(network, ssid)
                    return@launch
                }

                // Wi-Fi requires captive portal login: invoke AWING automated solver
                updateNotification("Đang tự động đăng nhập AWING Wi-Fi: $ssid...")
                Log.i(TAG, "Starting automated AWING solve for SSID: $ssid")

                val solved = awingSolver.solve(network)
                if (solved) {
                    Log.i(TAG, "AWING Wi-Fi auto-connected successfully for $ssid!")
                    updateNotification("Đã kết nối AWING Wi-Fi thành công: $ssid")
                    startSessionWatchdog(network, ssid)
                } else {
                    Log.w(TAG, "AWING WebView solver did not verify 204. Launching fallback portal solver...")
                    updateNotification("Đang mở trang đăng nhập cho $ssid...")
                    val fallbackSuccess = headlessSolver.trySolvePortal(network)
                    if (fallbackSuccess) {
                        Log.i(TAG, "Fallback captive portal window prompted.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling Wi-Fi event", e)
            } finally {
                solveMutex.unlock()
            }
        }
    }

    /**
     * Periodically verifies connection status and automatically re-authenticates if session expires.
     */
    private fun startSessionWatchdog(network: Network, ssid: String) {
        sessionWatchdogJob?.cancel()
        sessionWatchdogJob = serviceScope.launch {
            Log.i(TAG, "Started session keepalive watchdog for: $ssid")
            while (isActive) {
                delay(SESSION_WATCHDOG_INTERVAL_MS)

                if (currentConnectedNetwork != network) {
                    break
                }

                val hasInternet = awingSolver.verifyInternetAccess(network)
                if (!hasInternet) {
                    Log.w(TAG, "Session expired on $ssid! Automatically re-connecting...")
                    updateNotification("Phiên Wi-Fi $ssid hết hạn. Đang tự động kết nối lại...")

                    val reSolved = awingSolver.solve(network)
                    if (reSolved) {
                        Log.i(TAG, "Successfully renewed AWING session for $ssid!")
                        updateNotification("Đã tự động gia hạn Wi-Fi $ssid thành công!")
                    } else {
                        Log.w(TAG, "Re-solve failed, prompting portal window fallback...")
                        headlessSolver.trySolvePortal(network)
                    }
                } else {
                    Log.d(TAG, "Wi-Fi session healthy for: $ssid")
                }
            }
        }
    }

    private fun extractSsid(capabilities: NetworkCapabilities?): String {
        return try {
            // Android 10+ (Q+) extraction from NetworkCapabilities
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && capabilities != null) {
                val wifiInfo = capabilities.transportInfo as? WifiInfo
                val ssid = wifiInfo?.ssid?.replace("\"", "") ?: ""
                if (ssid.isNotBlank() && ssid != "<unknown ssid>") {
                    return ssid
                }
            }

            // Fallback via WifiManager
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: ""
            if (ssid.isNotBlank() && ssid != "<unknown ssid>") {
                ssid
            } else {
                "Wi-Fi công cộng"
            }
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
        sessionWatchdogJob?.cancel()
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
        private const val SESSION_WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

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
