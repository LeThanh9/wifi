package com.autowifi.portal

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.autowifi.portal.data.WifiTargetConfig
import com.autowifi.portal.databinding.ActivityMainBinding
import com.autowifi.portal.service.AutoPortalAccessibilityService
import com.autowifi.portal.service.HeadlessPortalSolver
import com.autowifi.portal.service.WifiMonitorService
import com.autowifi.portal.util.XiaomiHelper
import com.google.android.material.chip.Chip
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pulseAnimator: ObjectAnimator? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocation) {
            WifiMonitorService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPulseAnimation()
        setupPermissions()
        setupBrandSwitches()
        setupCustomSsid()
        setupButtons()
    }

    private fun setupPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.pulseView,
            PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.35f, 1.0f),
            PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.35f, 1.0f),
            PropertyValuesHolder.ofFloat("alpha", 0.4f, 0.05f, 0.4f)
        ).apply {
            duration = 2400
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusUi()
    }

    private fun setupPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            WifiMonitorService.start(this)
        }
    }

    private fun updateStatusUi() {
        val isA11yEnabled = isAccessibilityServiceEnabled()
        if (isA11yEnabled) {
            binding.tvA11yBadge.text = "Đã bật"
            binding.tvA11yBadge.setBackgroundResource(R.drawable.bg_badge_active)
            binding.tvA11yBadge.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
            binding.tvAccessibilityStatus.text = "Sẵn sàng tự động nhận diện & bấm nút xem quảng cáo."
            binding.btnEnableAccessibility.text = "Cài đặt lại quyền Trợ năng"
        } else {
            binding.tvA11yBadge.text = "Chưa bật"
            binding.tvA11yBadge.setBackgroundResource(R.drawable.bg_badge_warning)
            binding.tvA11yBadge.setTextColor(ContextCompat.getColor(this, R.color.neon_orange))
            binding.tvAccessibilityStatus.text = "Cần cấp quyền Trợ năng để app tự ấn nút xem quảng cáo trên màn hình."
            binding.btnEnableAccessibility.text = "Bật Quyền Trợ Năng (Accessibility)"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/${AutoPortalAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(serviceName) ||
                enabledServices.contains(AutoPortalAccessibilityService::class.java.simpleName)
    }

    private fun setupBrandSwitches() {
        val prefs = WifiTargetConfig.getPrefs(this)
        binding.llBrandsContainer.removeAllViews()

        for (target in WifiTargetConfig.DEFAULT_TARGETS) {
            val switchView = SwitchMaterial(this).apply {
                text = "${target.name}  [${target.ssidPatterns.first()}]"
                setTextColor(ContextCompat.getColor(context, R.color.text_body))
                isChecked = prefs.getBoolean("target_${target.id}", true)
                setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("target_${target.id}", isChecked).apply()
                }
            }
            binding.llBrandsContainer.addView(switchView)
        }
    }

    private fun setupCustomSsid() {
        val prefs = WifiTargetConfig.getPrefs(this)
        refreshChips()

        binding.btnAddCustomSsid.setOnClickListener {
            val input = binding.etCustomSsid.text?.toString()?.trim() ?: ""
            if (input.isNotEmpty()) {
                val currentSet = prefs.getStringSet("custom_ssids", emptySet())?.toMutableSet() ?: mutableSetOf()
                currentSet.add(input)
                prefs.edit().putStringSet("custom_ssids", currentSet).apply()
                binding.etCustomSsid.setText("")
                refreshChips()
                Toast.makeText(this, "Đã thêm SSID: $input", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshChips() {
        val prefs = WifiTargetConfig.getPrefs(this)
        val customSet = prefs.getStringSet("custom_ssids", emptySet()) ?: emptySet()
        binding.chipGroupCustom.removeAllViews()

        for (ssid in customSet) {
            val chip = Chip(this).apply {
                text = ssid
                isCloseIconVisible = true
                setTextColor(ContextCompat.getColor(context, R.color.text_title))
                setChipBackgroundColorResource(R.color.card_surface_elevated)
                setOnCloseIconClickListener {
                    val updated = prefs.getStringSet("custom_ssids", emptySet())?.toMutableSet() ?: mutableSetOf()
                    updated.remove(ssid)
                    prefs.edit().putStringSet("custom_ssids", updated).apply()
                    refreshChips()
                }
            }
            binding.chipGroupCustom.addView(chip)
        }
    }

    private fun setupButtons() {
        binding.switchBackgroundService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                WifiMonitorService.start(this)
                binding.tvServiceStatus.text = "Dịch vụ giám sát đang chạy"
                binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
                binding.tvLiveStatus.text = "● Đang quét Wi-Fi công cộng"
                binding.tvLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
            } else {
                WifiMonitorService.stop(this)
                binding.tvServiceStatus.text = "Dịch vụ đã tạm dừng"
                binding.tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.text_dim))
                binding.tvLiveStatus.text = "○ Đang tạm dừng quét"
                binding.tvLiveStatus.setTextColor(ContextCompat.getColor(this, R.color.text_dim))
            }
        }

        binding.btnEnableAccessibility.setOnClickListener {
            XiaomiHelper.openAccessibilitySettings(this)
            Toast.makeText(this, "Chọn 'Auto Wi-Fi Portal' và gạt Bật", Toast.LENGTH_LONG).show()
        }

        binding.btnXiaomiAutostart.setOnClickListener {
            val opened = XiaomiHelper.openAutostartSettings(this)
            if (opened) {
                Toast.makeText(this, "Bật 'Tự khởi chạy' cho Auto Wi-Fi Portal", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnXiaomiBattery.setOnClickListener {
            val opened = XiaomiHelper.openBatteryOptimization(this)
            if (opened) {
                Toast.makeText(this, "Chọn 'Không giới hạn' (No restrictions)", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnTestPortal.setOnClickListener {
            Toast.makeText(this, "Đang kiểm tra kết nối Wi-Fi & Captive Portal...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                val solver = HeadlessPortalSolver(this@MainActivity)
                val solved = solver.trySolvePortal()
                if (solved) {
                    Toast.makeText(this@MainActivity, "Kiểm tra xong! Nếu có portal thì cửa sổ đã được mở để tự bấm.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Không phát hiện trang quảng cáo hoặc mạng đã vào được bình thường.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pulseAnimator?.cancel()
    }
}
