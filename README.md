# Auto Wi-Fi Portal (Tự động kết nối &amp; Xem quảng cáo Wi-Fi công cộng)

Ứng dụng Android chạy ngầm, tự động phát hiện và kết nối Wi-Fi tại các địa điểm công cộng phổ biến ở Việt Nam:
- **GS25** (`GS25 Free Wi-Fi`, `GS25`)
- **Circle K** (`Circle K Free Wifi`, `Circle K`)
- **Highlands Coffee** (`Highlands Coffee Free Wi-Fi`, `Highlands Coffee`)
- **Vincom** (`Vincom WiFi Free`, `Vincom Plaza`, `Vincom Center`)
- **Phúc Long, The Coffee House** và bất kỳ Wi-Fi tùy chỉnh nào bạn thêm vào.

---

## ⚡ Cơ chế hoạt động (Không hack, không bypass trái phép)
1. **Phát hiện Wi-Fi**: Dịch vụ chạy ngầm (`WifiMonitorService`) liên tục nhận diện khi máy bắt sóng hoặc kết nối vào Wi-Fi của các chuỗi cửa hàng trên.
2. **Kích hoạt giải mã Captive Portal**: Khi Wi-Fi yêu cầu đăng nhập/xem quảng cáo, ứng dụng kích hoạt trang chào.
3. **Tự động thao tác (`AccessibilityService`)**:
   - Tự động tích chọn checkbox *"Tôi đồng ý với điều khoản sử dụng"* nếu có.
   - Tự động tìm và bấm các nút: **"Xem quảng cáo"**, **"Kết nối ngay"**, **"Tiếp tục"**, **"Truy cập Internet"**, **"Connect"**, **"Skip"**...
   - Tự động đợi đếm ngược (nếu trang yêu cầu chờ 5s) và bấm hoàn tất ngay lập tức để cấp quyền truy cập mạng.

---

## 🚀 Hướng dẫn Build file APK siêu nhanh qua GitHub Actions (Chỉ mất 1 - 2 phút)

Bạn không cần cài đặt Android Studio hay máy tính cấu hình mạnh, GitHub sẽ tự compile ra file `.apk` cho bạn:

### Bước 1: Khởi tạo và đưa code lên GitHub
Mở terminal tại thư mục này và chạy các lệnh:
```bash
git init
git add .
git commit -m "feat: Auto Wi-Fi portal app with GitHub Actions"
git branch -M main
git remote add origin https://github.com/<tai-khoan-github-cua-ban>/autowifi-portal.git
git push -u origin main
```

### Bước 2: Nhận file APK từ GitHub
1. Vào repository GitHub của bạn trên trình duyệt.
2. Nhấn vào tab **Actions**.
3. Bạn sẽ thấy tiến trình **Build Android APK** đang tự động chạy (mất khoảng 1 - 2 phút).
4. Khi chạy xong (hiện dấu tích xanh ✅), bấm vào lần chạy đó:
   - Kéo xuống mục **Artifacts**.
   - Tải file **`AutoWifiPortal-Debug-APK`** về.
5. Giải nén ra sẽ có file `app-debug.apk`. Chuyển vào điện thoại **Redmi Note** (hoặc mở GitHub trên điện thoại tải trực tiếp) và bấm Cài đặt!

---

## 📱 Cài đặt quan trọng trên Redmi Note (MIUI / HyperOS)

Các dòng máy Xiaomi / Redmi có cơ chế tiết kiệm pin rất chặt chẽ. Để ứng dụng luôn chạy ngầm ổn định khi bạn đi đến quán:

1. **Bật quyền Trợ năng (Accessibility)**:
   - Mở app, nhấn nút **"Bật quyền Trợ năng"**.
   - Chọn *Ứng dụng đã tải xuống* -> Tìm **Auto Wi-Fi Portal** -> Bật gạt cho phép.
2. **Cấp quyền Tự khởi chạy (Autostart)**:
   - Trong app, nhấn nút **"1. Tự khởi chạy"** (hoặc vào *Bảo mật -> Quyền -> Tự khởi chạy* -> Bật cho app).
3. **Tắt hạn chế pin (No Restrictions)**:
   - Trong app, nhấn nút **"2. Không giới hạn pin"** (hoặc giữ icon app -> *Thông tin ứng dụng -> Tiết kiệm pin -> Chọn "Không giới hạn"*).

---

## 🛠 Cấu trúc mã nguồn
- `app/src/main/java/com/autowifi/portal/service/AutoPortalAccessibilityService.kt`: Xử lý tự động bấm nút, xem quảng cáo, tích đồng ý điều khoản.
- `app/src/main/java/com/autowifi/portal/service/WifiMonitorService.kt`: Dịch vụ Foreground chạy ngầm giám sát Wi-Fi.
- `app/src/main/java/com/autowifi/portal/service/HeadlessPortalSolver.kt`: Xử lý kiểm tra HTTP 204 và điều hướng trang portal.
- `app/src/main/java/com/autowifi/portal/data/WifiTargetConfig.kt`: Cấu hình danh sách tên Wi-Fi và từ khóa nút bấm.
- `app/src/main/java/com/autowifi/portal/util/XiaomiHelper.kt`: Tiện ích mở nhanh cài đặt HyperOS / MIUI.
- `.github/workflows/build-apk.yml`: Tự động build file APK mỗi khi push code lên GitHub.
