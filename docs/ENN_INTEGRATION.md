# Exynos NPU qua LiteRT + Samsung dispatch

> Nguồn: thực nghiệm trên **SM-S947B (Galaxy S26+)** và mã nguồn `google-ai-edge/litert` @ `b6b64443` (2026-11-01),
> `litert/vendors/samsung/dispatch/*`, `litert/vendors/samsung/soc_model.cc`. Mục nào chưa kiểm chứng ghi **CHƯA XÁC MINH**.

## 1. Đường chạy thật (không gọi ENN trực tiếp)

```
EnnEngine ─► LiteRtNpuBridge ─► LiteRT 2.3.2 CompiledModel(Accelerator.NPU)
                                   └► libLiteRtDispatch_Samsung.so   (tự build từ tag v2.3.2, đóng gói trong APK)
                                        └► libenn_public_api_cpp.so  (lib hệ thống, KHÔNG đóng gói, dispatch dlopen)
```
Trình tự ENN bên trong dispatch: `EnnInitialize → EnnOpenModel* → EnnGetBuffersInfo (chỉ trả số lượng) → EnnSetBufferByIndex
→ EnnBufferCommit → EnnExecuteModel (đồng bộ) → … → EnnCloseModel → EnnDeinitialize`. Chữ ký C: CHƯA XÁC MINH.

## 2. Bật NPU

1. Build `libLiteRt.so` + `libLiteRtDispatch_Samsung.so` từ LiteRT tag **v2.3.2** (bản Maven ẩn export abseil mà dispatch cần).
   Đặt vào `runtime-enn/jniLibs/arm64-v8a/`. **Không** chép `libenn_public_api_cpp.so`.
2. Build với `-Penn.enabled=true` → thêm `com.google.ai.edge.litert:litert:2.3.2`, `BuildConfig.ENN_ENABLED=true`,
   `AppModule` dùng `LiteRtNpuBridge`. Lưu ý: LiteRT bị nâng lên 2.x toàn app → kiểm tra lại `GpuEngines.liteRtDelegateSupported()`.
3. Phát hiện NPU: `Environment.getAvailableAccelerators()` chứa `NPU`. Trên Snapdragon: CHƯA XÁC MINH (bridge trả `false` an toàn).
4. Manifest đã có `<uses-native-library android:name="libenn_public_api_cpp.so" android:required="false"/>` —
   có thực sự cần hay không: CHƯA XÁC MINH; đối chiếu 4 dòng 30–33 trong manifest app tham chiếu.

## 3. Chuẩn bị model (AOT trên CI, không trên máy)

- Đầu vào: **TFLite full-INT8, shape tĩnh**. pip `ai-edge-litert==2.3.0` + `ai-edge-litert-sdk-samsung-nightly`, `Target(SocModel.E9965)`.
- Đầu ra AOT vẫn là `.tflite` ⇒ app dùng quy ước tên: **`<name>.e9965.tflite`** (→ `ModelFormat.ENN_COMPILED`).
- **Sidecar bắt buộc** `<name>.e9965.io.json` (vì ENN chỉ trả số lượng tensor, không trả shape/dtype):
  ```json
  {"inputs":[{"name":"in","shape":[1,540,1170,3],"dtype":"int8"}],
   "outputs":[{"name":"out","shape":[1,1080,2340,3],"dtype":"int8"}]}
  ```
  Nên sinh file này ngay trong script compile từ metadata TFLite.
- Op đã biết không lên NPU: `DEPTH_TO_SPACE` (thay bằng pixel-shuffle residual 12 kênh). Báo cáo coverage op: CHƯA XÁC MINH.
- E9965 = Exynos 2600 là **suy ra** từ việc chạy được trên S947B. Chạy trên SoC/firmware khác: CHƯA XÁC MINH.

## 4. Giới hạn hiện tại của bridge

| Hạng mục | Trạng thái |
|---|---|
| Dữ liệu | Copy `ByteBuffer → TensorBuffer.writeInt8/readInt8` (chỉ INT8). Copy chiếm phần lớn thời gian NPU. |
| Zero-copy | Dispatch chỉ nhận DMA-BUF (`EnnCreateBufferFromFdWithOffset`). Đường fd/AHardwareBuffer: chưa làm, CHƯA XÁC MINH. |
| Thread | Khoá toàn cục `LiteRtNpuBridge.NPU_LOCK` quanh mọi lần chạy (ràng buộc thật của ENN: CHƯA XÁC MINH). |
| API LiteRT | Gọi bằng reflection; tên hàm cần đối chiếu `SrBackend.kt`/`NpuEnv.kt`. Sai tên ⇒ `isAvailable()=false`, không crash. |
| LLM | Không. Chỉ có dấu hiệu `ai_litecore_manager.h::SetGenAiPerfConfig`. `EnnEngine.generate()` trả lỗi ⇒ fallback GPU/CPU. |
| Giấy phép | Đóng gói `libLiteRtDispatch_Samsung.so` / gói pip Samsung vào APK: CHƯA XÁC MINH. |

## 5. Việc tiếp theo
- [ ] Đối chiếu tên API LiteRT trong `LiteRtNpuBridge` với `SrBackend.kt`; khi chắc chắn thì bỏ reflection.
- [ ] Đường zero-copy DMA-BUF.
- [ ] Đưa embedding / ASR encoder / vision INT8 lên NPU (fixed-shape), LLM giữ ở GPU/CPU.
- [ ] Ghi số ms/frame thật vào bảng `benchmarks`.
