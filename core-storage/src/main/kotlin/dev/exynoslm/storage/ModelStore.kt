package dev.exynoslm.storage

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.model.Quantization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Models live in app-private storage: <filesDir>/models/<repo__id>/<file> plus a manifest.json per model.
 * App-private storage keeps models out of the media scanner and other apps; mmap-friendly (no copies).
 */
class ModelStore(private val context: Context, private val root: File = File(context.filesDir, "models")) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun dirFor(repoId: String): File = File(root, repoId.replace('/', '_').replace("..", "_")).apply { mkdirs() }
    fun freeBytes(): Long = StatFs(root.apply { mkdirs() }.path).availableBytes
    fun usedBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    suspend fun list(): List<LoadedModel> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().mapNotNull { dir ->
            runCatching { json.decodeFromString(LoadedModel.serializer(), File(dir, MANIFEST).readText()) }.getOrNull()
                ?.takeIf { File(it.localPath).exists() }
        }.sortedBy { it.displayName }
    }

    suspend fun register(file: File, repoId: String?): LoadedModel = withContext(Dispatchers.IO) {
        val m = LoadedModel(
            id = (repoId ?: "local") + ":" + file.name,
            displayName = file.nameWithoutExtension,
            localPath = file.absolutePath,
            format = ModelFormat.fromFileName(file.name),
            quantization = Quantization.guess(file.name),
            sizeBytes = file.length(),
            sourceRepo = repoId,
        )
        File(file.parentFile, MANIFEST).writeText(json.encodeToString(LoadedModel.serializer(), m))
        m
    }

    suspend fun delete(model: LoadedModel) = withContext(Dispatchers.IO) { File(model.localPath).parentFile?.deleteRecursively() }

    /** Import from SAF (Files app / USB). Copies once into private storage. */
    suspend fun import(uri: Uri): LoadedModel = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "imported.bin"
        val dest = File(dirFor("local/" + name.substringBeforeLast('.')), name)
        context.contentResolver.openInputStream(uri)!!.use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 20) } }
        register(dest, null)
    }

    fun wipe() { root.deleteRecursively() }

    companion object { const val MANIFEST = "manifest.json" }
}
