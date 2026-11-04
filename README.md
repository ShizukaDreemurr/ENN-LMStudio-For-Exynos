# Exynos LM Studio (Android)

App Android native kiểu “LM Studio trên điện thoại”, nhắm tới **Galaxy S26+ / Exynos 2600**:
tìm & tải model từ Hugging Face, chạy LLM **offline** với streaming, agent cục bộ có tool, chọn backend NPU/GPU/CPU,
và hiển thị hiệu năng (tok/s, TTFT, RAM, nhiệt, pin, backend, lý do fallback).

> **Trung thực về phạm vi hiện tại (MVP):**
> - Chạy LLM thật qua **MediaPipe LLM Inference** (`.task`) trên **CPU và GPU**.
> - **NPU**: `LiteRtNpuBridge` (LiteRT 2.3.2 + Samsung dispatch, model AOT `*.e9965.tflite` INT8) sau cờ `enn.enabled`; mặc định stub. **Chưa build/chạy thử trong repo này.** Xem [docs/ENN_INTEGRATION.md](docs/ENN_INTEGRATION.md).
> - GPU Xclipse (RDNA) của Exynos 2600 **chưa được Google xác nhận** cho đường GPU của MediaPipe; nếu load lỗi app tự fallback CPU.
> - Vision/ASR/embedding: có badge & bộ lọc trên Model Hub, chưa có pipeline chạy (xem Lộ trình).

## Kiến trúc

```
app ─────────────── Compose UI (Chat, Model Hub, My Models, Agent, Benchmark, Settings), Hilt, Room, DataStore, WorkManager
 ├─ core-model         (JVM)  ModelCard, ModelFile, LoadedModel, ModelFormat, Quantization, license risk
 ├─ core-runtime       (JVM)  InferenceEngine, GenerationEvent, BackendSelector, Compatibility, EngineManager (LRU), FakeEngine
 ├─ core-huggingface   (JVM)  HuggingFaceClient (search/pagination/429/gated), ModelDownloader (resume + sha256)
 ├─ runtime-agent      (JVM)  AgentRunner (ReAct), ToolRegistry, tool an toàn, dry-run, confirm, limits, trace
 ├─ runtime-cpu        (AAR)  MediaPipeLlmEngine, PromptFormatter (Gemma/ChatML/Llama3/Phi3), CpuTuning
 ├─ runtime-gpu        (AAR)  GPU LLM factory + LiteRT GPU delegate probe
 ├─ runtime-enn        (AAR)  EnnBridge (interface), Stub/Mock bridge, EnnEngine, BuildConfig.ENN_ENABLED
 ├─ core-storage       (AAR)  ModelStore: thư mục private, manifest, import SAF, dung lượng
 └─ core-telemetry     (AAR)  DeviceMonitor: RAM, PSS, thermal status/headroom, pin — local-only
```

Logic không phụ thuộc Android nằm ở module **JVM** để unit test chạy nhanh không cần SDK/thiết bị.

### Luồng load model
`My Models → InferenceHub.load()` → `BackendSelector.selectAndLoad()` thử theo thứ tự
**NPU → GPU → CPU** (hoặc backend người dùng ép trước, rồi fallback). Mỗi bước chạy compatibility check
(format, quantization, ước lượng RAM ≈ weights×1.25 + KV cache) và nếu `load()` lỗi runtime cũng fallback.
Lý do từng backend bị loại được hiển thị trong Chat/My Models. Khi máy nóng (thermal ≥ SEVERE) thứ tự thành NPU → CPU → GPU.
`EngineManager` giữ tối đa 1 model trong RAM (LRU) và `onTrimMemory` giải phóng model.

### Agent
`goal → model → <tool_call>{json}</tool_call> → tool → Observation → … → câu trả lời`.
Tool có sẵn: `calculator`, `datetime`, `list_files`, `read_file`, `search_notes`, `write_file`*, `clipboard`*, `fetch_url`* (*chỉ khi bật internet*).
- Mỗi tool: JSON schema, mô tả, `PermissionScope`, cờ `mutating`.
- Mặc định **dry-run**; tool ghi/mạng luôn hỏi xác nhận; file tool bị sandbox trong `filesDir/agent_workspace`.
- Giới hạn: max steps, token budget, timeout từng tool, danh sách chặn. Pause/Stop/Export trace JSON; trace lưu mã hoá trong Room.
- `contacts`/`calendar`: **chưa làm** — sẽ thêm như `Tool` riêng, xin quyền runtime đúng lúc, scope `CONTACTS`/`CALENDAR`.

### Bảo mật & riêng tư
Chat/trace mã hoá AES-GCM bằng khoá Android Keystore; `allowBackup=false`; release build strip `Log.v/d/i`;
mạng chỉ dùng cho Hugging Face và tool web khi người dùng bật; Settings có “Xoá toàn bộ dữ liệu cục bộ”.

## Build

```bash
# Unit test các module JVM (không cần Android SDK)
./gradlew -PjvmOnly=true test

# Build APK (cần Android SDK 35, JDK 17)
./gradlew assembleDebug
./gradlew installDebug
```

## Thêm model

1. **Model Hub**: lọc task “Chat/LLM”, tìm `litert-community` (ví dụ `litert-community/Gemma3-1B-IT`) → mở repo → chọn **đúng file**
   `.task` (ví dụ `*int4*.task`) → Download. Repo gated (Gemma, Llama) cần chấp nhận license trên web và dán token ở Settings.
2. **Import**: My Models → Import file (SAF) để nạp `.task` có sẵn trong máy.
3. Badge: `NPU-READY` (tflite lượng tử/ENN compiled), `GPU`, `CPU`, `NEEDS-CONVERSION` (GGUF/ONNX/safetensors), `TOO-LARGE`, `HEAVY-FOR-MOBILE`.

Gợi ý cho máy 12 GB: Gemma 3 1B INT4 (~0.5 GB), Qwen2.5 1.5B INT8, Phi-4-mini INT4 (~2–3 GB). Máy 8 GB: giữ ≤ ~2 GB weights.

## Giới hạn theo backend

| Backend | Chạy được hôm nay | Giới hạn |
|---|---|---|
| CPU (XNNPACK qua MediaPipe) | `.task` LLM | Chậm hơn GPU; chiếm CPU — agent nền nên ít thread (`CpuTuning`) |
| GPU (MediaPipe GPU) | `.task` LLM | Xclipse/RDNA chưa được xác nhận; fallback CPU nếu lỗi; theo dõi nhiệt |
| NPU (LiteRT + Samsung dispatch) | Graph INT8 AOT E9965 (khi `enn.enabled`) | Shape tĩnh; copy buffer tốn thời gian; **không chạy GGUF**; chưa có LLM trên NPU |
| GGUF | Không | Cần runtime llama.cpp (JNI) — xem Lộ trình |

Tham số chưa backend nào hỗ trợ sẽ báo `Warning` (ví dụ repetition penalty với MediaPipe).

## Benchmark

- **Trong app**: tab Benchmark chạy cùng prompt trên mọi backend tương thích, lưu load time, TTFT, prefill/decode tok/s, PSS đỉnh, % pin.
- **Instrumented** (đo 10 phút pin):
  ```bash
  adb push gemma3-1b-it-int4.task /data/local/tmp/bench.task
  ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=dev.exynoslm.app.LlmBenchmarkTest \
    -Pandroid.testInstrumentationRunnerArguments.minutes=10
  adb logcat -s BENCH
  ```
  Đo pin chính xác: rút sạc, tắt adaptive brightness, chạy `adb shell dumpsys batterystats --reset` trước.

## Test

| Phạm vi | File |
|---|---|
| HF search, pagination (Link header), 429 retry, gated, detail sha/size | `core-huggingface/.../HuggingFaceClientTest.kt` |
| Download resume (Range), checksum mismatch | như trên |
| BackendSelector/fallback, RAM, thermal, badge, LRU unload | `core-runtime/.../BackendSelectorTest.kt` |
| Agent loop với mock LLM, max steps, dry-run, confirm, sandbox, timeout, chặn tool | `runtime-agent/.../AgentRunnerTest.kt` |
| Hiệu năng trên thiết bị | `app/src/androidTest/.../LlmBenchmarkTest.kt` |

Chưa có: UI test Compose cho trạng thái offline/hết RAM/thermal (đang ở Lộ trình).

## Lộ trình

1. **MVP (repo này)** — HF search/download, LLM `.task` CPU/GPU, chat streaming, agent cơ bản, benchmark, ENN stub.
2. **Xác minh trên S26+** — chạy Benchmark thật, chỉnh `CpuTuning` theo topology Exynos 2600, kiểm tra GPU path.
3. **LiteRT-LM** — thêm engine `.litertlm` (hướng Google khuyến nghị thay MediaPipe LLM API) sau cùng `InferenceEngine`.
4. **GGUF** — engine llama.cpp qua JNI (CPU + Vulkan) để chạy GGUF từ HF.
5. **ENN thật** — theo `docs/ENN_INTEGRATION.md`: embedding/ASR/vision trên NPU trước, rồi hybrid (encoder NPU + LLM GPU).
6. **Mở rộng agent** — contacts/calendar có quyền rõ ràng, RAG trên notes bằng embedding NPU, function-calling native.
7. UI test Compose, lịch sử nhiều hội thoại, export/import cấu hình.
