package com.autowifi.portal.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.autowifi.portal.data.WifiTargetConfig

class AutoPortalAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastClickTimestamp: Long = 0
    private val clickDebounceMs = 800L
    private var isProcessing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AutoPortalAccessibilityService connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        // Check if event is from Captive Portal Login or a browser or system dialog
        val isCaptiveLogin = packageName.contains("captiveportal", ignoreCase = true) ||
                packageName.contains("browser", ignoreCase = true) ||
                packageName.contains("chrome", ignoreCase = true) ||
                packageName.contains("webview", ignoreCase = true)

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val now = System.currentTimeMillis()
            if (now - lastClickTimestamp < clickDebounceMs) {
                return
            }

            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                processActiveWindow(isCaptiveLogin)
            }, 250)
        }
    }

    private fun processActiveWindow(isLikelyCaptive: Boolean) {
        val rootNode = rootInActiveWindow ?: return

        try {
            // Step 1: Auto-check terms / agreement checkboxes
            val checkedAgreement = findAndCheckAgreement(rootNode)
            if (checkedAgreement) {
                Log.d(TAG, "Checked agreement checkbox.")
            }

            // Step 2: Find connect or watch-ad button
            val clicked = findAndClickActionNode(rootNode)
            if (clicked) {
                lastClickTimestamp = System.currentTimeMillis()
                Log.i(TAG, "Auto-clicked Wi-Fi captive portal action button successfully!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility window", e)
        } finally {
            try {
                rootNode.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun findAndCheckAgreement(node: AccessibilityNodeInfo): Boolean {
        var didCheck = false

        // Check if this node is a checkbox or agreement element
        val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        val lowerText = text.lowercase()

        val isAgreementText = WifiTargetConfig.AGREEMENT_KEYWORDS.any { lowerText.contains(it) }
        val isCheckable = node.isCheckable || node.className?.contains("CheckBox", ignoreCase = true) == true

        if (isAgreementText || isCheckable) {
            if (node.isCheckable && !node.isChecked) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                didCheck = true
            }
        }

        // Recursively inspect children
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndCheckAgreement(child)) {
                didCheck = true
            }
            try {
                child.recycle()
            } catch (_: Exception) {}
        }

        return didCheck
    }

    private fun findAndClickActionNode(node: AccessibilityNodeInfo): Boolean {
        val nodeText = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        val lowerText = nodeText.trim().lowercase()

        if (lowerText.isNotBlank()) {
            // Check if matches any keyword
            val matchedKeyword = WifiTargetConfig.CLICKABLE_KEYWORDS.firstOrNull { keyword ->
                lowerText.contains(keyword)
            }

            if (matchedKeyword != null && node.isEnabled) {
                Log.d(TAG, "Matched keyword: '$matchedKeyword' in element: '$lowerText'")

                // Try to click the node itself or traverse up to a clickable parent
                var targetNode: AccessibilityNodeInfo? = node
                while (targetNode != null && !targetNode.isClickable) {
                    targetNode = targetNode.parent
                }

                if (targetNode != null && targetNode.isClickable) {
                    val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) {
                        Toast.makeText(
                            applicationContext,
                            "Auto-Portal: Đã tự động ấn '$matchedKeyword'!",
                            Toast.LENGTH_SHORT
                        ).show()
                        return true
                    }
                }
            }
        }

        // Recursively check children
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val clicked = findAndClickActionNode(child)
            try {
                child.recycle()
            } catch (_: Exception) {}
            if (clicked) {
                return true
            }
        }

        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoPortalAccessibilityService interrupted")
    }

    companion object {
        private const val TAG = "AutoPortalService"
        var isServiceRunning = false
    }
}
