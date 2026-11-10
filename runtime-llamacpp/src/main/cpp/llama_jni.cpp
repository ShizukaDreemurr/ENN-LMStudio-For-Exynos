// JNI bridge between Kotlin (dev.exynoslm.runtime.llamacpp.LlamaNative) and llama.cpp.
//
// Written against llama.cpp release tag b12257 (include/llama.h of that tag). Only these C API entry points are used:
//   llama_backend_init, llama_log_set, llama_model_default_params, llama_model_load_from_file, llama_model_free,
//   llama_context_default_params, llama_init_from_model, llama_free, llama_set_abort_callback, llama_n_ctx,
//   llama_get_memory, llama_memory_clear, llama_model_get_vocab, llama_model_chat_template, llama_chat_apply_template,
//   llama_tokenize, llama_vocab_bos, llama_vocab_n_tokens, llama_vocab_is_eog, llama_token_to_piece,
//   llama_batch_get_one + llama_decode, llama_sampler_chain_* / llama_sampler_init_{penalties,top_k,top_p,temp,dist,greedy},
//   llama_sampler_sample, llama_sampler_free.
// The tag's examples/simple-chat uses the newer llama_batch_ext/llama_process API; llama_batch_get_one + llama_decode is
// still declared in the same header and is used here because it is the long-stable path.
//
// Design: every generate() call is stateless (KV memory is cleared and the whole templated prompt is re-processed).
// All generate/load/free calls for one handle must come from ONE thread; only cancel() may be called concurrently.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <exception>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int32_t kBatchSize = 512;      // prompt is fed to llama_decode in chunks of at most this many tokens
constexpr int32_t kPenaltyLastN = 64;    // repetition penalty window

// generate() status codes (element 0 of the returned IntArray), mirrored in LlamaNative.kt
constexpr jint kStatusDone = 0;          // end-of-generation token or maxNewTokens reached
constexpr jint kStatusCancelled = 1;     // cancel() or the Kotlin callback asked to stop
constexpr jint kStatusContextFull = 2;   // KV context exhausted mid-generation

struct Handle {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int32_t n_ctx = 0;
    std::atomic<bool> cancel{false};
};

struct SamplerDeleter { void operator()(llama_sampler * s) const { if (s) llama_sampler_free(s); } };
using SamplerPtr = std::unique_ptr<llama_sampler, SamplerDeleter>;

void log_callback(enum ggml_log_level level, const char * text, void * /*user*/) {
    int prio = ANDROID_LOG_DEBUG;
    if (level == GGML_LOG_LEVEL_ERROR) prio = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_WARN) prio = ANDROID_LOG_WARN;
    else if (level == GGML_LOG_LEVEL_INFO) prio = ANDROID_LOG_INFO;
    __android_log_write(prio, LOG_TAG, text);
}

bool abort_callback(void * data) {
    return static_cast<Handle *>(data)->cancel.load(std::memory_order_relaxed);
}

void throw_java(JNIEnv * env, const char * cls, const std::string & msg) {
    if (env->ExceptionCheck()) return;
    jclass c = env->FindClass(cls);
    if (c) { env->ThrowNew(c, msg.c_str()); env->DeleteLocalRef(c); }
}
void throw_ise(JNIEnv * env, const std::string & msg) { throw_java(env, "java/lang/IllegalStateException", msg); }

std::string bytes_to_string(JNIEnv * env, jbyteArray arr) {
    if (!arr) return std::string();
    const jsize n = env->GetArrayLength(arr);
    std::string s(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte *>(&s[0]));
    return s;
}

// Decodes complete UTF-8 sequences from `s` into UTF-16 (appended to `out`) and returns the number of bytes consumed.
// Invalid bytes become U+FFFD. A truncated sequence at the very end is left unconsumed (so it can be completed by the next
// token piece) unless `flush` is set, in which case it becomes U+FFFD. Output is converted to UTF-16 and handed to
// NewString() instead of NewStringUTF(): JNI's NewStringUTF wants *modified* UTF-8, which rejects 4-byte sequences (emoji).
size_t decode_utf8(const std::string & s, std::u16string & out, bool flush) {
    size_t i = 0;
    const size_t n = s.size();
    while (i < n) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        uint32_t cp;
        size_t len;
        if (c < 0x80) { out.push_back(static_cast<char16_t>(c)); ++i; continue; }
        else if (c >= 0xC2 && c <= 0xDF) { len = 2; cp = c & 0x1F; }
        else if (c >= 0xE0 && c <= 0xEF) { len = 3; cp = c & 0x0F; }
        else if (c >= 0xF0 && c <= 0xF4) { len = 4; cp = c & 0x07; }
        else { out.push_back(0xFFFD); ++i; continue; }

        const size_t avail = n - i;
        size_t k = 1;
        bool bad = false;
        for (; k < len && k < avail; ++k) {
            const unsigned char cc = static_cast<unsigned char>(s[i + k]);
            if ((cc & 0xC0) != 0x80) { bad = true; break; }
            cp = (cp << 6) | (cc & 0x3F);
        }
        if (bad) { out.push_back(0xFFFD); ++i; continue; }
        if (k < len) {                      // truncated at end of buffer
            if (!flush) break;
            out.push_back(0xFFFD);
            i = n;
            break;
        }
        if ((len == 3 && cp < 0x800) || (len == 4 && (cp < 0x10000 || cp > 0x10FFFF)) || (cp >= 0xD800 && cp <= 0xDFFF)) {
            out.push_back(0xFFFD);          // overlong / out of range / surrogate
            ++i;
            continue;
        }
        if (cp >= 0x10000) {
            cp -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        } else {
            out.push_back(static_cast<char16_t>(cp));
        }
        i += len;
    }
    return i;
}

struct ChatMsg { std::string role; std::string content; };

// Applies the model's built-in chat template. Returns false (and leaves `out` untouched) if llama.cpp cannot render it.
// NOTE: llama_chat_apply_template does not run a jinja engine; it only recognises a fixed list of template families.
bool apply_template(const char * tmpl, const std::vector<ChatMsg> & msgs, std::string & out) {
    std::vector<llama_chat_message> chat;
    chat.reserve(msgs.size());
    size_t total = 0;
    for (const auto & m : msgs) {
        chat.push_back({m.role.c_str(), m.content.c_str()});
        total += m.role.size() + m.content.size();
    }
    std::vector<char> buf(std::max<size_t>(2 * total, 1024));
    int32_t n = llama_chat_apply_template(tmpl, chat.data(), chat.size(), /*add_ass*/ true, buf.data(), static_cast<int32_t>(buf.size()));
    if (n > static_cast<int32_t>(buf.size())) {
        buf.resize(static_cast<size_t>(n));
        n = llama_chat_apply_template(tmpl, chat.data(), chat.size(), true, buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (n < 0) return false;
    out.assign(buf.data(), static_cast<size_t>(n));
    return true;
}

std::once_flag g_backend_once;

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_exynoslm_runtime_llamacpp_LlamaNative_load(JNIEnv * env, jobject /*thiz*/, jstring jpath, jint nCtx, jint nThreads, jboolean useMmap) {
    try {
        std::call_once(g_backend_once, [] {
            llama_log_set(log_callback, nullptr);
            llama_backend_init();
        });
        const char * cpath = env->GetStringUTFChars(jpath, nullptr);
        if (!cpath) return 0;
        const std::string path(cpath);
        env->ReleaseStringUTFChars(jpath, cpath);

        llama_model_params mp = llama_model_default_params();
        mp.n_gpu_layers = 0;  // CPU only
        mp.load_mode = useMmap ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;

        llama_model * model = llama_model_load_from_file(path.c_str(), mp);
        if (!model) { throw_ise(env, "llama_model_load_from_file failed: " + path); return 0; }

        const int32_t threads = std::max<int32_t>(1, nThreads);
        llama_context_params cp = llama_context_default_params();
        cp.n_ctx = static_cast<uint32_t>(std::max<jint>(256, nCtx));
        cp.n_batch = std::min<uint32_t>(kBatchSize, cp.n_ctx);
        cp.n_ubatch = cp.n_batch;
        cp.n_threads = threads;
        cp.n_threads_batch = threads;

        llama_context * ctx = llama_init_from_model(model, cp);
        if (!ctx) {
            llama_model_free(model);
            throw_ise(env, "llama_init_from_model failed (not enough memory for the requested context?)");
            return 0;
        }

        auto * h = new Handle();
        h->model = model;
        h->ctx = ctx;
        h->vocab = llama_model_get_vocab(model);
        h->n_ctx = static_cast<int32_t>(llama_n_ctx(ctx));
        llama_set_abort_callback(ctx, abort_callback, h);
        LOGI("model loaded: n_ctx=%d threads=%d mmap=%d", h->n_ctx, threads, useMmap ? 1 : 0);
        return reinterpret_cast<jlong>(h);
    } catch (const std::exception & e) {
        throw_ise(env, std::string("native load failed: ") + e.what());
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_dev_exynoslm_runtime_llamacpp_LlamaNative_cancel(JNIEnv *, jobject, jlong handle) {
    if (handle != 0) reinterpret_cast<Handle *>(handle)->cancel.store(true);
}

JNIEXPORT void JNICALL
Java_dev_exynoslm_runtime_llamacpp_LlamaNative_free(JNIEnv *, jobject, jlong handle) {
    auto * h = reinterpret_cast<Handle *>(handle);
    if (!h) return;
    h->cancel.store(true);
    if (h->ctx) llama_free(h->ctx);
    if (h->model) llama_model_free(h->model);
    delete h;
}

JNIEXPORT jint JNICALL
Java_dev_exynoslm_runtime_llamacpp_LlamaNative_tokenCount(JNIEnv * env, jobject, jlong handle, jbyteArray jtext) {
    auto * h = reinterpret_cast<Handle *>(handle);
    if (!h) return -1;
    const std::string text = bytes_to_string(env, jtext);
    if (text.empty()) return 0;
    const int32_t r = llama_tokenize(h->vocab, text.c_str(), static_cast<int32_t>(text.size()), nullptr, 0, /*add_special*/ false, /*parse_special*/ true);
    if (r == INT32_MIN) return -1;
    return r < 0 ? -r : r;   // negative return = "would need -r tokens"
}

// Returns IntArray [status, promptTokens, completionTokens, templateFallback(0/1)].
// Throws IllegalStateException on hard errors (prompt does not fit, decode failure, template failure).
JNIEXPORT jintArray JNICALL
Java_dev_exynoslm_runtime_llamacpp_LlamaNative_generate(
        JNIEnv * env, jobject /*thiz*/, jlong handle,
        jobjectArray jroles, jobjectArray jcontents, jbyteArray jrawPrompt,
        jfloat temp, jint topK, jfloat topP, jfloat repeatPenalty, jlong seed, jint maxNewTokens,
        jobject callback) {
    auto * h = reinterpret_cast<Handle *>(handle);
    if (!h) { throw_ise(env, "model not loaded"); return nullptr; }
    h->cancel.store(false);

    try {
        // ---- 1. prompt text ----
        std::string prompt;
        jint templateFallback = 0;
        if (jrawPrompt) {
            prompt = bytes_to_string(env, jrawPrompt);   // caller already rendered the chat template
        } else {
            std::vector<ChatMsg> msgs;
            const jsize nm = jroles ? env->GetArrayLength(jroles) : 0;
            for (jsize i = 0; i < nm; ++i) {
                ChatMsg m;
                auto jr = static_cast<jstring>(env->GetObjectArrayElement(jroles, i));
                const char * r = jr ? env->GetStringUTFChars(jr, nullptr) : nullptr;
                m.role = r ? r : "user";
                if (r) env->ReleaseStringUTFChars(jr, r);
                if (jr) env->DeleteLocalRef(jr);
                auto jc = static_cast<jbyteArray>(env->GetObjectArrayElement(jcontents, i));
                m.content = bytes_to_string(env, jc);
                if (jc) env->DeleteLocalRef(jc);
                msgs.push_back(std::move(m));
            }
            const char * tmpl = llama_model_chat_template(h->model, /*name*/ nullptr);
            bool ok = tmpl && apply_template(tmpl, msgs, prompt);
            if (!ok) {
                // The model's own template is missing or not one of llama.cpp's built-in families: fall back to ChatML.
                LOGE("model chat template unavailable/unsupported; falling back to chatml");
                templateFallback = 1;
                ok = apply_template("chatml", msgs, prompt);
            }
            if (!ok) { throw_ise(env, "failed to apply chat template"); return nullptr; }
        }

        // ---- 2. tokenize ----
        int32_t need = llama_tokenize(h->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
        if (need == INT32_MIN) { throw_ise(env, "prompt too large to tokenize"); return nullptr; }
        if (need < 0) need = -need;
        if (need == 0) { throw_ise(env, "empty prompt"); return nullptr; }
        std::vector<llama_token> tokens(static_cast<size_t>(need));
        const int32_t got = llama_tokenize(h->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), need, true, true);
        if (got < 0) { throw_ise(env, "failed to tokenize the prompt"); return nullptr; }
        tokens.resize(static_cast<size_t>(got));
        // Chat templates often already contain the BOS text; tokenizing with add_special would then double it.
        const llama_token bos = llama_vocab_bos(h->vocab);
        if (bos >= 0 && tokens.size() > 1 && tokens[0] == bos && tokens[1] == bos) tokens.erase(tokens.begin());

        const int32_t promptTokens = static_cast<int32_t>(tokens.size());
        if (promptTokens + 1 > h->n_ctx) {
            throw_ise(env, "prompt (" + std::to_string(promptTokens) + " tokens) does not fit in the context of " + std::to_string(h->n_ctx) + " tokens");
            return nullptr;
        }

        // ---- 3. sampler chain: penalties -> top_k -> top_p -> temp -> dist (or greedy when temp <= 0) ----
        SamplerPtr smpl(llama_sampler_chain_init(llama_sampler_chain_default_params()));
        if (!smpl) { throw_ise(env, "failed to create sampler chain"); return nullptr; }
        if (repeatPenalty > 0.0f && repeatPenalty != 1.0f) {
            llama_sampler_chain_add(smpl.get(), llama_sampler_init_penalties(llama_vocab_n_tokens(h->vocab), kPenaltyLastN, repeatPenalty, 0.0f, 0.0f));
        }
        if (temp <= 0.0f) {
            llama_sampler_chain_add(smpl.get(), llama_sampler_init_greedy());
        } else {
            if (topK > 0) llama_sampler_chain_add(smpl.get(), llama_sampler_init_top_k(topK));
            if (topP > 0.0f && topP < 1.0f) llama_sampler_chain_add(smpl.get(), llama_sampler_init_top_p(topP, 1));
            llama_sampler_chain_add(smpl.get(), llama_sampler_init_temp(temp));
            llama_sampler_chain_add(smpl.get(), llama_sampler_init_dist(seed < 0 ? LLAMA_DEFAULT_SEED : static_cast<uint32_t>(seed)));
        }

        // ---- 4. Kotlin callback ----
        jclass cbClass = env->GetObjectClass(callback);
        jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(cbClass);
        if (!onToken) return nullptr;   // NoSuchMethodError already pending

        std::string pending;            // raw bytes not yet delivered (may end in an incomplete UTF-8 sequence)
        std::u16string utf16;
        // Delivers whatever complete text is pending. Returns false if the Kotlin side asked to stop or threw.
        auto deliver = [&](bool flush) -> bool {
            utf16.clear();
            const size_t used = decode_utf8(pending, utf16, flush);
            pending.erase(0, used);
            if (utf16.empty()) return true;
            jstring js = env->NewString(reinterpret_cast<const jchar *>(utf16.data()), static_cast<jsize>(utf16.size()));
            if (!js) return false;
            const jboolean cont = env->CallBooleanMethod(callback, onToken, js);
            env->DeleteLocalRef(js);
            if (env->ExceptionCheck()) return false;
            return cont == JNI_TRUE;
        };

        // ---- 5. prefill (stateless: wipe the KV memory, process the whole prompt in chunks) ----
        llama_memory_clear(llama_get_memory(h->ctx), true);
        for (size_t off = 0; off < tokens.size(); off += kBatchSize) {
            if (h->cancel.load()) { jint r[4] = {kStatusCancelled, promptTokens, 0, templateFallback}; jintArray a = env->NewIntArray(4); env->SetIntArrayRegion(a, 0, 4, r); return a; }
            const int32_t cnt = static_cast<int32_t>(std::min<size_t>(kBatchSize, tokens.size() - off));
            const int32_t rc = llama_decode(h->ctx, llama_batch_get_one(tokens.data() + off, cnt));
            if (rc == 2 || h->cancel.load()) { jint r[4] = {kStatusCancelled, promptTokens, 0, templateFallback}; jintArray a = env->NewIntArray(4); env->SetIntArrayRegion(a, 0, 4, r); return a; }
            if (rc != 0) { throw_ise(env, "llama_decode failed while processing the prompt, rc=" + std::to_string(rc)); return nullptr; }
        }

        // ---- 6. decode loop ----
        jint status = kStatusDone;
        int32_t produced = 0;
        char buf[256];
        while (produced < maxNewTokens) {
            if (h->cancel.load()) { status = kStatusCancelled; break; }
            llama_token tok = llama_sampler_sample(smpl.get(), h->ctx, -1);   // also accepts the token into the chain
            if (llama_vocab_is_eog(h->vocab, tok)) break;

            int32_t n = llama_token_to_piece(h->vocab, tok, buf, sizeof(buf), 0, /*special*/ false);
            if (n < 0) {
                std::string big(static_cast<size_t>(-n), '\0');
                n = llama_token_to_piece(h->vocab, tok, &big[0], static_cast<int32_t>(big.size()), 0, false);
                if (n > 0) pending.append(big.data(), static_cast<size_t>(n));
            } else if (n > 0) {
                pending.append(buf, static_cast<size_t>(n));
            }
            ++produced;
            if (!deliver(false)) { status = kStatusCancelled; break; }

            if (promptTokens + produced >= h->n_ctx) { status = kStatusContextFull; break; }
            const int32_t rc = llama_decode(h->ctx, llama_batch_get_one(&tok, 1));
            if (rc == 2 || h->cancel.load()) { status = kStatusCancelled; break; }
            if (rc != 0) { throw_ise(env, "llama_decode failed during generation, rc=" + std::to_string(rc)); return nullptr; }
        }
        if (!env->ExceptionCheck() && status != kStatusCancelled) deliver(true);   // flush a dangling partial sequence as U+FFFD
        if (env->ExceptionCheck()) return nullptr;   // exception thrown by the Kotlin callback: propagate

        jint r[4] = {status, promptTokens, produced, templateFallback};
        jintArray a = env->NewIntArray(4);
        env->SetIntArrayRegion(a, 0, 4, r);
        return a;
    } catch (const std::exception & e) {
        throw_ise(env, std::string("native generate failed: ") + e.what());
        return nullptr;
    }
}

} // extern "C"
