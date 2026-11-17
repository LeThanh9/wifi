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

    // Probe endpoints to detect captive portal redirection and test connectivity
    const val PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"
    const val FALLBACK_PROBE_URL = "http://clients3.google.com/generate_204"
    const val AWING_TRIGGER_URL = "http://awifi.vn"
    const val AWING_LANDING_URL = "http://connect.awing.vn"

    // Recognized AWING portal domains and host signatures
    val AWING_PORTAL_DOMAINS = listOf(
        "connect.awing.vn",
        "awifi.vn",
        "acm.awingconnect.vn",
        "wifi.awing.vn",
        "awingconnect.vn",
        "awing.vn"
    )

    val DEFAULT_TARGETS = listOf(
        WifiTarget(
            id = "awing",
            name = "AWING Free Wi-Fi",
            ssidPatterns = listOf("AWING", "AWING Free Wi-Fi", "Free Wi-Fi AWING", "awifi.vn", "Awing", "AWIFI")
        ),
        WifiTarget(
            id = "highlands",
            name = "Highlands Coffee",
            ssidPatterns = listOf("Highlands Coffee", "Highlands", "HIGHLANDS_WIFI", "Highlands Coffee Free Wi-Fi", "Highlands_Coffee_Free_WiFi")
        ),
        WifiTarget(
            id = "cgv",
            name = "CGV Cinemas",
            ssidPatterns = listOf("CGV Cinemas", "CGV", "CGV_Cinemas_Wifi", "CGV Free Wi-Fi", "CGV_Free_WiFi")
        ),
        WifiTarget(
            id = "trungnguyen",
            name = "Trung Nguyên Legend / Coffee",
            ssidPatterns = listOf("Trung Nguyen", "Trung Nguyên", "Trung Nguyen Legend", "Trung Nguyen Coffee", "TRUNG NGUYEN")
        ),
        WifiTarget(
            id = "goldengate",
            name = "Golden Gate Group (Gogi, Kichi, Manwah...)",
            ssidPatterns = listOf("Golden Gate", "Golden Gate Free Wi-Fi", "Gogi House", "Gogi", "Kichi Kichi", "Kichi", "Manwah", "Sumo BBQ", "Sumo", "Isushi", "iSushi", "Vuvuzela", "Daruma", "Ashima")
        ),
        WifiTarget(
            id = "vincom",
            name = "Vincom Retail / Plaza",
            ssidPatterns = listOf("Vincom", "Vincom WiFi Free", "Vincom Plaza", "Vincom Center", "VINCOM", "Vincom Free Wi-Fi", "Vincom_Free_Wi-Fi")
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

    // Common action keywords across AWING and Vietnam public captive portals
    val CLICKABLE_KEYWORDS = listOf(
        // Vietnamese keywords
        "kết nối internet",
        "kết nối ngay",
        "kết nối",
        "xem video để kết nối",
        "xem video",
        "xem quảng cáo",
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
        "đăng nhập",
        // English keywords
        "connect to internet",
        "connect now",
        "connect",
        "continue",
        "access internet",
        "free internet",
        "free wifi",
        "watch ad",
        "view ad",
        "watch video",
        "skip",
        "skip ad",
        "accept & connect",
        "agree and connect",
        "get online",
        "login"
    )

    // Agreement keywords (for checkboxes)
    val AGREEMENT_KEYWORDS = listOf(
        "tôi đồng ý",
        "đồng ý với điều khoản",
        "chấp nhận điều khoản",
        "chấp nhận",
        "điều khoản sử dụng",
        "quy định",
        "agree",
        "terms",
        "terms & conditions",
        "terms of service",
        "i agree",
        "accept"
    )

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAwingPortalUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return AWING_PORTAL_DOMAINS.any { domain -> lower.contains(domain) }
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

    fun isAwingSsid(currentSsid: String?): Boolean {
        if (currentSsid.isNullOrBlank()) return false
        val clean = currentSsid.replace("\"", "").trim()
        val awingBrands = listOf("awing", "awifi", "highlands", "cgv", "trung nguyen", "trung nguyên", "golden gate", "gogi", "kichi", "manwah", "sumo", "isushi", "vincom")
        return awingBrands.any { clean.contains(it, ignoreCase = true) }
    }
}
