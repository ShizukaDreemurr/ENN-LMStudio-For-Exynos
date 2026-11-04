# Tích hợp Samsung ENN / Exynos NPU

> **Trạng thái:** `runtime-enn` hiện chỉ có **adapter + stub + mock**. Không có dòng code nào gọi API Samsung thật,
> vì SDK không được phân phối công khai theo cách có thể kiểm chứng từ repo này. Mọi tên API Samsung ở dưới đều là
> *việc cần xác minh*, không phải sự thật.

## 1. Kiến trúc boundary

```
UI / Agent ──► InferenceEngine (core-runtime)
                    ▲
             EnnEngine (runtime-enn)  ──►  EnnBridge (interface trung lập)
                                               ├─ StubEnnBridge   (mặc định, luôn "unavailable")
                                               ├─ MockEnnBridge   (test/demo, copy input→output)
                                               └─ RealEnnBridge   (BẠN viết, bọc AAR/.so của Samsung)
```

Phần còn lại của app **không** import bất kỳ class nào của Samsung. Thay backend = thay 1 dòng trong
`app/src/main/kotlin/dev/exynoslm/app/di/AppModule.kt` (`EnnEngine(StubEnnBridge())`).

## 2. Các bước bật ENN thật

1. **Lấy SDK hợp lệ.** Kiểm tra Samsung Developers / Exynos AI Studio / chương trình đối tác Samsung System LSI
   tại thời điểm bạn làm. Samsung đã thay đổi chính sách phân phối Neural SDK nhiều lần; có thể cần NDA hoặc tài khoản đối tác.
   Ghi lại: tên artifact, phiên bản, giấy phép, danh sách SoC hỗ trợ (phải có Exynos 2600).
2. Đặt artifact vào `runtime-enn/libs/` (KHÔNG commit nếu giấy phép cấm phân phối lại — thêm vào `.gitignore`).
3. Bỏ comment dependency trong `runtime-enn/build.gradle.kts` và chạy với `-Penn.enabled=true`
   (hoặc đặt `enn.enabled=true` trong `gradle.properties`). Cờ này sinh `BuildConfig.ENN_ENABLED`.
4. Viết `RealEnnBridge : EnnBridge` theo **tài liệu chính thức**. Cần xác minh:
   - Cách khởi tạo context/runtime, threading (có cần 1 thread cố định không?).
   - Định dạng model đã compile (đuôi file — `ModelFormat.ENN_COMPILED` hiện giả định `.nnc`, *chưa kiểm chứng*).
   - Yêu cầu buffer (direct ByteBuffer? ION/AHardwareBuffer? alignment?) để tránh copy giữa NPU/GPU.
   - Tên thư viện native cần khai báo bằng `<uses-native-library>` trong `AndroidManifest.xml` (đã để sẵn comment).
5. Đổi `AppModule` sang `EnnEngine(if (BuildConfig.ENN_ENABLED) RealEnnBridge(ctx) else StubEnnBridge())`.
6. Chạy màn **Benchmark** để so sánh NPU / GPU / CPU.

## 3. Chuyển đổi model cho NPU

- Nguồn: TFLite/LiteRT **đã lượng tử hoá** (INT8, hoặc INT4/FP16 nếu toolchain hỗ trợ), hoặc ONNX qua converter của Samsung.
- Shape tĩnh. Operator không được hỗ trợ sẽ bị fallback (về CPU) hoặc compile lỗi — luôn đọc báo cáo coverage của toolchain.
- **GGUF không chạy trực tiếp trên NPU.** GGUF là định dạng của llama.cpp; muốn lên NPU phải export lại từ checkpoint gốc.
- **LLM trên NPU** cần: graph prefill + graph decode với KV-cache là I/O, tokenizer riêng, sampling trên CPU.
  `EnnEngine.generate()` hiện trả `Error` một cách có chủ đích → `BackendSelector` tự fallback sang GPU/CPU.

## 4. Việc nên đưa lên NPU trước (thực tế hơn LLM)

| Tác vụ | Lý do |
|---|---|
| Embedding (MiniLM, bge-small INT8) | Graph nhỏ, shape cố định |
| ASR encoder (Whisper tiny/base encoder) | Phần nặng nhất của ASR, shape cố định |
| Image classification / detection / OCR | Use-case kinh điển của NPU |
| Image encoder cho VLM | Chạy NPU, LLM decode trên GPU/CPU (hybrid) |

## 5. Checklist xác minh trước khi merge RealEnnBridge

- [ ] Link tài liệu chính thức + phiên bản SDK ghi trong KDoc của `RealEnnBridge`.
- [ ] `isAvailable()` trả false an toàn trên máy không phải Exynos (Snapdragon S26 variant!).
- [ ] Không crash khi thiếu `.so` (bắt `UnsatisfiedLinkError`).
- [ ] Benchmark trên Galaxy S26+ thật được lưu vào bảng `benchmarks`.
