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

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.i(TAG, "AutoPortalAccessibilityService connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        // Check if event is from Captive Portal Login, system dialog, or browser/webview
        val isCaptiveLogin = packageName.contains("captiveportallogin", ignoreCase = true) ||
                packageName.contains("captiveportal", ignoreCase = true) ||
                packageName.contains("miui.portal", ignoreCase = true) ||
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

            // Step 2: Skip demographic / survey popups if present
            val skippedSurvey = findAndSkipSurvey(rootNode)
            if (skippedSurvey) {
                Log.d(TAG, "Skipped customer demographic survey.")
            }

            // Step 3: Find and click high-priority AWING resource IDs or action buttons
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

        val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        val lowerText = text.lowercase()

        val isAgreementText = WifiTargetConfig.AGREEMENT_KEYWORDS.any { lowerText.contains(it) }
        val isCheckable = node.isCheckable || node.className?.contains("CheckBox", ignoreCase = true) == true

        if ((isAgreementText || isCheckable) && node.isCheckable && !node.isChecked) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            didCheck = true
        }

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

    private fun findAndSkipSurvey(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()

        val isSkip = viewId.contains("skip") || viewId.contains("customer-skip") ||
                text.contains("bỏ qua") || text.contains("nhắc tôi sau") || text.contains("remind later") || text.contains("skip")

        if (isSkip && (node.isClickable || node.parent?.isClickable == true)) {
            val target = if (node.isClickable) node else node.parent
            if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val skipped = findAndSkipSurvey(child)
            try {
                child.recycle()
            } catch (_: Exception) {}
            if (skipped) return true
        }

        return false
    }

    private fun findAndClickActionNode(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val nodeText = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).trim().lowercase()

        // 1. High priority check by AWING known IDs
        val awingKnownIds = listOf(
            "acceptconnection",
            "connect-btn",
            "btnconnect",
            "btn-connect",
            "btnsubmit",
            "btnlogin",
            "banner"
        )

        val matchedId = awingKnownIds.firstOrNull { viewId.contains(it) }
        if (matchedId != null && node.isEnabled) {
            Log.d(TAG, "Matched AWING view ID: '$matchedId'")
            if (performClickOnNodeOrParent(node)) {
                showToast("Auto-Portal: Đã ấn nút kết nối AWING!")
                return true
            }
        }

        // 2. Check by keywords
        if (nodeText.isNotBlank()) {
            val matchedKeyword = WifiTargetConfig.CLICKABLE_KEYWORDS.firstOrNull { keyword ->
                nodeText.contains(keyword)
            }

            if (matchedKeyword != null && node.isEnabled) {
                Log.d(TAG, "Matched keyword: '$matchedKeyword' in element: '$nodeText'")
                if (performClickOnNodeOrParent(node)) {
                    showToast("Auto-Portal: Đã tự động ấn '$matchedKeyword'!")
                    return true
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

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var targetNode: AccessibilityNodeInfo? = node
        while (targetNode != null && !targetNode.isClickable) {
            targetNode = targetNode.parent
        }

        return if (targetNode != null && targetNode.isClickable) {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            false
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.i(TAG, "AutoPortalAccessibilityService destroyed.")
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoPortalAccessibilityService interrupted")
    }

    companion object {
        private const val TAG = "AutoPortalService"
        var isServiceRunning = false
    }
}
