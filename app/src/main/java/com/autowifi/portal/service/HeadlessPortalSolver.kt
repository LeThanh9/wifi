package com.autowifi.portal.service

import android.content.Context
import android.content.Intent
import android.net.Network
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class HeadlessPortalSolver(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun trySolvePortal(network: Network? = null): Boolean = withContext(Dispatchers.IO) {
        var httpConn: HttpURLConnection? = null
        try {
            Log.d(TAG, "Starting captive portal detection & headless solve...")

            // Probe Google generate_204 endpoint with Connection: close
            val testUrl = "http://connectivitycheck.gstatic.com/generate_204"
            val request = Request.Builder()
                .url(testUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .header("Connection", "close")
                .build()

            val response = if (network != null) {
                // Bind to the Wi-Fi network specifically
                httpConn = network.openConnection(URL(testUrl)) as? HttpURLConnection
                httpConn?.instanceFollowRedirects = true
                httpConn?.connectTimeout = 6000
                httpConn?.readTimeout = 6000
                httpConn?.setRequestProperty("Connection", "close")
                val responseCode = httpConn?.responseCode ?: 0
                val redirectedUrl = httpConn?.url?.toString() ?: ""
                Log.d(TAG, "Network probe response code: $responseCode, url: $redirectedUrl")

                if (responseCode == 204) {
                    Log.i(TAG, "Already connected to internet (HTTP 204). No portal needed.")
                    return@withContext true
                }
                redirectedUrl
            } else {
                client.newCall(request).execute().use { res ->
                    val code = res.code
                    val redirectedUrl = res.request.url.toString()
                    if (code == 204) {
                        Log.i(TAG, "Already connected to internet (HTTP 204).")
                        return@withContext true
                    }
                    redirectedUrl
                }
            }

            if (response.isBlank()) {
                return@withContext false
            }

            Log.i(TAG, "Captive portal detected: $response")

            // Attempt to trigger the portal login UI so Accessibility Service can auto-click
            withContext(Dispatchers.Main) {
                promptPortalWindow(response)
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "HeadlessPortalSolver error", e)
            return@withContext false
        } finally {
            try {
                httpConn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun promptPortalWindow(portalUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch captive portal intent", e)
        }
    }

    companion object {
        private const val TAG = "HeadlessPortalSolver"
    }
}
