package dev.exynoslm.hf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

sealed interface DownloadState {
    data class Progress(val downloaded: Long, val total: Long?) : DownloadState
    data object Verifying : DownloadState
    data class Done(val file: File) : DownloadState
}

class ChecksumMismatch(expected: String, actual: String) : IOException("sha256 mismatch: expected $expected got $actual")

/**
 * Resumable single-file downloader.
 * - Writes to `<dest>.part`; on restart sends `Range: bytes=<len>-` (HF resolve URLs support ranges).
 * - Cancel = cancel the collecting coroutine (the .part file is kept for resume). Pause = cancel + resume later.
 * - Verifies sha256 when known (HF exposes LFS sha256 via `?blobs=true` and `X-Linked-Etag`).
 * WorkManager wraps this in the app module for background/Wi-Fi-only downloads.
 */
class ModelDownloader(private val http: OkHttpClient, private val token: () -> String? = { null }) {

    fun download(url: String, dest: File, expectedSha256: String? = null, expectedSize: Long? = null): Flow<DownloadState> = flow {
        if (dest.exists() && (expectedSize == null || dest.length() == expectedSize)) { emit(DownloadState.Done(dest)); return@flow }
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        var offset = if (part.exists()) part.length() else 0L

        val req = Request.Builder().url(url).apply {
            if (offset > 0) header("Range", "bytes=$offset-")
            token()?.let { header("Authorization", "Bearer $it") }
        }.build()
        var serverSha: String? = null
        http.newCall(req).execute().use { r ->
            if (r.code == 416) { /* already complete */ }
            else {
                if (!r.isSuccessful) throw HubException.Http(r.code)
                if (r.code != 206) offset = 0 // server ignored Range -> restart
                serverSha = r.header("X-Linked-Etag")?.trim('"')?.takeIf { it.length == 64 }
                val total = r.body!!.contentLength().takeIf { it >= 0 }?.plus(offset) ?: expectedSize
                FileOutputStream(part, offset > 0).use { out ->
                    val src = r.body!!.byteStream()
                    val buf = ByteArray(256 * 1024)
                    var done = offset; var lastEmit = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = src.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (done - lastEmit > 1_000_000 || done == total) { emit(DownloadState.Progress(done, total)); lastEmit = done }
                    }
                }
            }
        }
        val want = expectedSha256 ?: serverSha
        if (want != null) {
            emit(DownloadState.Verifying)
            val got = sha256(part)
            if (!got.equals(want, ignoreCase = true)) { part.delete(); throw ChecksumMismatch(want, got) }
        }
        if (!part.renameTo(dest)) throw IOException("rename failed")
        emit(DownloadState.Done(dest))
    }.flowOn(Dispatchers.IO)

    companion object {
        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { s -> val b = ByteArray(1 shl 20); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
