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
import java.util.concurrent.TimeUnit

class HeadlessPortalSolver(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun trySolvePortal(network: Network? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting captive portal detection & headless solve...")

            // Probe Google generate_204 endpoint
            val testUrl = "http://connectivitycheck.gstatic.com/generate_204"
            val request = Request.Builder()
                .url(testUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                .build()

            val response = if (network != null) {
                // Bind to the Wi-Fi network specifically
                val urlConnection = network.openConnection(java.net.URL(testUrl))
                urlConnection.connectTimeout = 8000
                urlConnection.readTimeout = 8000
                val responseCode = (urlConnection as? java.net.HttpURLConnection)?.responseCode ?: 0
                val redirectedUrl = urlConnection.url.toString()
                Log.d(TAG, "Network probe response code: $responseCode, url: $redirectedUrl")

                if (responseCode == 204) {
                    Log.i(TAG, "Already connected to internet (HTTP 204). No portal needed.")
                    return@withContext true
                }
                redirectedUrl
            } else {
                val call = client.newCall(request)
                val res = call.execute()
                val code = res.code
                val redirectedUrl = res.request.url.toString()
                res.close()

                if (code == 204) {
                    Log.i(TAG, "Already connected to internet (HTTP 204).")
                    return@withContext true
                }
                redirectedUrl
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
