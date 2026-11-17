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

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.i(TAG, "AutoPortalAccessibilityService connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        // CRITICAL: Fast early check: ignore immediately if not a captive portal or browser.
        // Avoids waking Handler, posting runnables, or inspecting rootInActiveWindow on unrelated apps.
        if (!isCaptiveOrBrowserPackage(packageName)) {
            return
        }

        val eventType = event.eventType
        // Restrict event handling: ignore all non-window change events.
        // TYPE_WINDOW_CONTENT_CHANGED is only processed when package is confirmed captive portal or browser.
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp < CLICK_DEBOUNCE_MS) {
            return
        }

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (System.currentTimeMillis() - lastClickTimestamp >= CLICK_DEBOUNCE_MS) {
                processActiveWindow()
            }
        }, 250)
    }

    private fun isCaptiveOrBrowserPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase()
        return lower.contains("captiveportallogin") ||
                lower.contains("captiveportal") ||
                lower.contains("miui.portal") ||
                lower.contains("browser") ||
                lower.contains("chrome") ||
                lower.contains("webview") ||
                lower.contains("firefox") ||
                lower.contains("sbrowser") ||
                lower.contains("opera")
    }

    private fun processActiveWindow() {
        if (System.currentTimeMillis() - lastClickTimestamp < CLICK_DEBOUNCE_MS) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        try {
            // Step 1: Auto-check terms / agreement checkboxes
            val checkedAgreement = findAndCheckAgreement(rootNode, 0)
            if (checkedAgreement) {
                Log.d(TAG, "Checked agreement checkbox.")
            }

            // Step 2: Skip demographic / survey popups if present
            val skippedSurvey = findAndSkipSurvey(rootNode, 0)
            if (skippedSurvey) {
                Log.d(TAG, "Skipped customer demographic survey.")
            }

            // Step 3: Find and click high-priority AWING resource IDs or action buttons
            val clicked = findAndClickActionNode(rootNode, 0)
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

    private fun findAndCheckAgreement(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > MAX_SEARCH_DEPTH) return false

        val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        val lowerText = text.lowercase()

        val isAgreementText = WifiTargetConfig.AGREEMENT_KEYWORDS.any { lowerText.contains(it) }
        val isCheckable = node.isCheckable || node.className?.contains("CheckBox", ignoreCase = true) == true

        if ((isAgreementText || isCheckable) && node.isCheckable && !node.isChecked) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                return true // Abort immediately upon finding a match
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val found = try {
                findAndCheckAgreement(child, depth + 1)
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {}
            }
            if (found) {
                return true // Abort immediately upon finding a match
            }
        }

        return false
    }

    private fun findAndSkipSurvey(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > MAX_SEARCH_DEPTH) return false

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()

        val isSkip = viewId.contains("skip") || viewId.contains("customer-skip") ||
                text.contains("bỏ qua") || text.contains("nhắc tôi sau") || text.contains("remind later") || text.contains("skip")

        if (isSkip) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            val parent = node.parent
            if (parent != null) {
                val parentClicked = try {
                    if (parent.isClickable) parent.performAction(AccessibilityNodeInfo.ACTION_CLICK) else false
                } finally {
                    try { parent.recycle() } catch (_: Exception) {}
                }
                if (parentClicked) return true
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val skipped = try {
                findAndSkipSurvey(child, depth + 1)
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {}
            }
            if (skipped) return true // Abort immediately upon finding a match
        }

        return false
    }

    private fun findAndClickActionNode(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (depth > MAX_SEARCH_DEPTH) return false

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
                return true // Abort immediately upon finding a match
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
                    return true // Abort immediately upon finding a match
                }
            }
        }

        // Recursively check children up to MAX_SEARCH_DEPTH
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val clicked = try {
                findAndClickActionNode(child, depth + 1)
            } finally {
                try {
                    child.recycle()
                } catch (_: Exception) {}
            }
            if (clicked) {
                return true // Abort immediately upon finding a match
            }
        }

        return false
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var current: AccessibilityNodeInfo? = node.parent
        while (current != null) {
            val parentToRecycle = current
            try {
                if (current.isClickable) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                current = current.parent
            } finally {
                try {
                    parentToRecycle.recycle()
                } catch (_: Exception) {}
            }
        }
        return false
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "AutoPortalAccessibilityService destroyed.")
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoPortalAccessibilityService interrupted")
    }

    companion object {
        private const val TAG = "AutoPortalService"
        private const val CLICK_DEBOUNCE_MS = 1200L
        private const val MAX_SEARCH_DEPTH = 6
        var isServiceRunning = false
    }
}
