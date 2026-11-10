package dev.exynoslm.runtime.llamacpp

/** Receives decoded text pieces from the native generation loop (called on the native/inference thread). */
interface LlamaTokenCallback {
    /** @return true to keep generating, false to stop as soon as possible. */
    fun onToken(piece: String): Boolean
}

/**
 * Raw JNI surface of libllama_jni.so (see src/main/cpp/llama_jni.cpp, built against llama.cpp tag b12257).
 * All load/generate/free calls for one handle must come from the same thread; only [cancel] may be called concurrently.
 * Kept public and non-mangled: the C++ symbols are named after this class.
 */
object LlamaNative {
    /** False if the native library is missing for this ABI (anything but arm64-v8a). */
    val isAvailable: Boolean = try {
        System.loadLibrary("llama_jni"); true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    const val STATUS_DONE = 0
    const val STATUS_CANCELLED = 1
    const val STATUS_CONTEXT_FULL = 2

    /** @return native handle (never 0); throws IllegalStateException if the model or context cannot be created. */
    external fun load(path: String, nCtx: Int, nThreads: Int, useMmap: Boolean): Long

    /**
     * Streams pieces to [callback]; contents are UTF-8 bytes so supplementary characters survive JNI.
     * If [rawPrompt] is non-null it is used verbatim (already templated) and roles/contents are ignored.
     * @param seed negative = random.
     * @return [status, promptTokens, completionTokens, templateFallback(0/1)]. Throws IllegalStateException on hard errors.
     */
    external fun generate(
        handle: Long,
        roles: Array<String>,
        contents: Array<ByteArray>,
        rawPrompt: ByteArray?,
        temp: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float,
        seed: Long,
        maxNewTokens: Int,
        callback: LlamaTokenCallback,
    ): IntArray

    external fun cancel(handle: Long)
    external fun tokenCount(handle: Long, text: ByteArray): Int
    external fun free(handle: Long)
}
