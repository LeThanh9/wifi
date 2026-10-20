package com.autowifi.portal.data

import android.content.Context
import android.content.SharedPreferences

data class WifiTarget(
    val id: String,
    val name: String,
    val ssidPatterns: List<String>,
    var isEnabled: Boolean = true
)

object WifiTargetConfig {
    private const val PREFS_NAME = "wifi_portal_prefs"
    private const val KEY_CUSTOM_SSIDS = "custom_ssids"

    val DEFAULT_TARGETS = listOf(
        WifiTarget(
            id = "highlands",
            name = "Highlands Coffee",
            ssidPatterns = listOf("Highlands Coffee", "Highlands", "HIGHLANDS_WIFI", "Highlands Coffee Free Wi-Fi")
        ),
        WifiTarget(
            id = "vincom",
            name = "Vincom Retail / Plaza",
            ssidPatterns = listOf("Vincom", "Vincom WiFi Free", "Vincom Plaza", "Vincom Center", "VINCOM")
        ),
        WifiTarget(
            id = "gs25",
            name = "GS25 Convenience Store",
            ssidPatterns = listOf("GS25", "GS25 Free Wi-Fi", "GS25 Free WiFi", "GS25_WIFI")
        ),
        WifiTarget(
            id = "circlek",
            name = "Circle K Vietnam",
            ssidPatterns = listOf("Circle K", "Circle K Free Wifi", "CircleK", "Circle K Free")
        ),
        WifiTarget(
            id = "phuclong",
            name = "Phúc Long Coffee & Tea",
            ssidPatterns = listOf("Phuc Long", "Phúc Long", "Phuc Long Tea", "Phuc Long Free Wi-Fi")
        ),
        WifiTarget(
            id = "thecoffeehouse",
            name = "The Coffee House",
            ssidPatterns = listOf("The Coffee House", "THE COFFEE HOUSE", "TCH Free Wi-Fi")
        )
    )

    // Common action keywords in Vietnam public captive portals
    val CLICKABLE_KEYWORDS = listOf(
        // Vietnamese keywords
        "xem quảng cáo",
        "kết nối ngay",
        "kết nối",
        "tiếp tục",
        "truy cập internet",
        "truy cập",
        "sử dụng internet",
        "dùng wifi",
        "dùng miễn phí",
        "bắt đầu",
        "xác nhận",
        "hoàn tất",
        "đồng ý",
        "bỏ qua",
        "miễn phí",
        "lướt web ngay",
        "vào mạng",
        // English keywords
        "connect",
        "continue",
        "access internet",
        "free internet",
        "free wifi",
        "watch ad",
        "view ad",
        "skip",
        "skip ad",
        "accept & connect",
        "agree and connect",
        "get online"
    )

    // Agreement keywords (for checkboxes)
    val AGREEMENT_KEYWORDS = listOf(
        "tôi đồng ý",
        "đồng ý với điều khoản",
        "chấp nhận điều khoản",
        "chấp nhận",
        "agree",
        "terms",
        "terms & conditions",
        "i agree",
        "accept"
    )

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isTargetMatching(context: Context, currentSsid: String?): Boolean {
        if (currentSsid.isNullOrBlank()) return true // Fallback to handle any captive portal if unknown
        val cleanSsid = currentSsid.replace("\"", "").trim()
        val prefs = getPrefs(context)

        // Check default targets
        for (target in DEFAULT_TARGETS) {
            val enabled = prefs.getBoolean("target_${target.id}", true)
            if (enabled) {
                for (pattern in target.ssidPatterns) {
                    if (cleanSsid.contains(pattern, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }

        // Check custom SSIDs
        val customSet = prefs.getStringSet(KEY_CUSTOM_SSIDS, emptySet()) ?: emptySet()
        for (custom in customSet) {
            if (cleanSsid.contains(custom, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
