package com.autowifi.portal.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object XiaomiHelper {
    private const val TAG = "XiaomiHelper"

    fun isXiaomi(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                brand.contains("xiaomi") || brand.contains("redmi") ||
                brand.contains("poco")
    }

    /**
     * Opens MIUI / HyperOS Autostart settings screen
     */
    fun openAutostartSettings(context: Context): Boolean {
        val intents = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.PowerSettings"
                )
            )
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed intent: ${intent.component?.className}", e)
            }
        }

        // Fallback to application details
        return openAppDetails(context)
    }

    /**
     * Request battery optimization ignore (No restrictions on Redmi/HyperOS)
     */
    fun openBatteryOptimization(context: Context): Boolean {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS", e)
        }

        // Xiaomi hidden battery config intent
        try {
            val intent = Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY").apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", "Auto Wi-Fi Portal")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed Xiaomi HIDDEN_APPS_CONFIG_ACTIVITY", e)
        }

        return openAppDetails(context)
    }

    /**
     * Opens system Accessibility Settings screen
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open accessibility settings", e)
        }
    }

    fun openAppDetails(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
