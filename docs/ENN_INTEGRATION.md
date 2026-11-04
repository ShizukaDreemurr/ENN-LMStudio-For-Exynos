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
2. Toàn dự án dùng `litert:2.3.2` + `litert-gpu:2.3.2` (như app tham chiếu). Build với `-Penn.enabled=true` để
   `AppModule` dùng `LiteRtNpuBridge` thay cho stub.
3. `Environment.create(context, mapOf(Option.DispatchLibraryDir to nativeLibraryDir))`, thử thêm `CompilerPluginLibraryDir`,
   cuối cùng `Environment.create(context)`. Vì vậy app bật `useLegacyPackaging = true` (lib phải được giải nén ra `nativeLibraryDir`).
4. `pickFirsts += "**/libLiteRt.so"`. Bản nào thắng (tự build hay Maven): CHƯA XÁC MINH — kiểm bằng `unzip -l app-debug.apk`.
5. Phát hiện NPU: `getAvailableAccelerators().joinToString()` chứa `"NPU"`. Snapdragon: CHƯA XÁC MINH (bridge trả `false`).
6. Manifest khai báo đủ 4 lib hệ thống như app tham chiếu: `libenn_public_api_cpp.so`, `libenn_public_api_cpp_lib.so`,
   `libenn_user.samsung_slsi.so`, `libenn_user_lib.so` (đều `required=false`).

## 3. Chuẩn bị model (AOT trên CI, không trên máy)

- Đầu vào: **TFLite full-INT8, shape tĩnh**. pip `ai-edge-litert==2.3.0` + `ai-edge-litert-sdk-samsung-nightly`, `Target(SocModel.E9965)`.
- Đầu ra AOT vẫn là `.tflite` ⇒ app dùng quy ước tên: **`<name>.e9965.tflite`** (→ `ModelFormat.ENN_COMPILED`).
- **Sidecar bắt buộc** `<name>.e9965.io.json` (vì ENN chỉ trả số lượng tensor, không trả shape/dtype):
  ```json
  {"inputs":[{"name":"serving_default_input:0","shape":[1,3,540,1170],"dtype":"int8","scale":0.003921568859368563,"zero_point":-128}],
   "outputs":[{"name":"StatefulPartitionedCall_1:0","shape":[1,12,540,1170],"dtype":"int8","scale":0.0032997210510075092,"zero_point":1}]}
  ```
  (kết quả thật của `io_sidecar.py` trên sr_x2 *raw*; chạy trên bản AOT và việc tên tensor có đổi hay không: CHƯA XÁC MINH).
  Thêm vào cuối `compile_sr_trained.py`: `sidecar(raw, f"out_sr_trained/c_{name}/{name}.e9965.io.json")`, và đổi bước `cp`
  trong `aot-model.yml:69` thành `<name>.e9965.tflite`.
- **Layout là NCHW** và model nhận/trả **int8 thô**: app tự quantize `q = round(x/scale) + zp` và dequantize `x = scale·(q − zp)`
  bằng `dev.exynoslm.runtime.Quant` (có `hwcToChw` cho ảnh RGB). Output sr_x2 là 12 kênh: pixel-shuffle residual, app tự ghép.
- Op đã biết không lên NPU: `DEPTH_TO_SPACE` (thay bằng pixel-shuffle residual 12 kênh). Báo cáo coverage op: CHƯA XÁC MINH.
- E9965 = Exynos 2600 là **suy ra** từ việc chạy được trên S947B. Chạy trên SoC/firmware khác: CHƯA XÁC MINH.

## 4. Giới hạn hiện tại của bridge

| Hạng mục | Trạng thái |
|---|---|
| Dữ liệu | Copy `ByteBuffer → TensorBuffer.writeInt8/readInt8` (chỉ INT8). Copy chiếm phần lớn thời gian NPU. |
| Zero-copy | Dispatch chỉ nhận DMA-BUF (`EnnCreateBufferFromFdWithOffset`). Đường fd/AHardwareBuffer: chưa làm, CHƯA XÁC MINH. |
| Thread | Khoá toàn cục `LiteRtNpuBridge.NPU_LOCK` quanh mọi lần chạy (ràng buộc thật của ENN: CHƯA XÁC MINH). |
| API LiteRT | Gọi trực tiếp như `NpuEnv.kt`/`SrBackend.kt`; chỉ `writeInt8`/`readInt8` còn reflection (dò lúc `openModel`). |
| LLM | Không. Chỉ có dấu hiệu `ai_litecore_manager.h::SetGenAiPerfConfig`. `EnnEngine.generate()` trả lỗi ⇒ fallback GPU/CPU. |
| Giấy phép | Đóng gói `libLiteRtDispatch_Samsung.so` / gói pip Samsung vào APK: CHƯA XÁC MINH. |

## 5. Việc tiếp theo
- [ ] Lấy danh sách method thật của `TensorBuffer` (màn chẩn đoán "API TensorBuffer") để bỏ nốt reflection.
- [ ] Đo copy vào / chạy / copy ra của sr_x2 (`NpuEnv.srBench`).
- [ ] Đường zero-copy DMA-BUF.
- [ ] Đưa embedding / ASR encoder / vision INT8 lên NPU (fixed-shape), LLM giữ ở GPU/CPU.
- [ ] Ghi số ms/frame thật vào bảng `benchmarks`.
