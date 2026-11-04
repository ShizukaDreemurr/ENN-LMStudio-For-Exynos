Keystore dùng chung cho bản dev (debug + release). Mật khẩu: `exynoslm`, alias `exynoslm`.
Mục đích: mọi APK build ở CI hay máy nào cũng **cùng chữ ký** → cài đè cập nhật được, không phải gỡ app.
KHÔNG dùng key này để phát hành lên Play Store (nó công khai trong repo).
