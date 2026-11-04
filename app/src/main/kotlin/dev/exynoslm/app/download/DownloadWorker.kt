package dev.exynoslm.app.download

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.exynoslm.hf.DownloadState
import dev.exynoslm.hf.HuggingFaceClient
import dev.exynoslm.hf.ModelDownloader
import dev.exynoslm.storage.ModelStore
import java.io.File

/**
 * Background, resumable download. Pause = cancel work (the .part file stays); resume = enqueue again.
 * Retries with backoff on network errors. Only the selected file is downloaded, never the whole repo.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val hf: HuggingFaceClient,
    private val downloader: ModelDownloader,
    private val store: ModelStore,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = inputData.getString(K_REPO) ?: return Result.failure()
        val path = inputData.getString(K_PATH) ?: return Result.failure()
        val sha = inputData.getString(K_SHA)
        val size = inputData.getLong(K_SIZE, -1).takeIf { it > 0 }
        if (size != null && size > store.freeBytes()) return Result.failure(workDataOf(K_ERROR to "not enough free storage"))
        setForeground(foreground("$repo/$path", 0))
        val dest = File(store.dirFor(repo), path.substringAfterLast('/'))
        return try {
            downloader.download(hf.resolveUrl(repo, path), dest, sha, size).collect { st ->
                if (st is DownloadState.Progress) {
                    val pct = st.total?.let { (st.downloaded * 100 / it).toInt() } ?: -1
                    setProgress(workDataOf(K_DONE to st.downloaded, K_TOTAL to (st.total ?: -1L), K_PCT to pct))
                    setForeground(foreground("$repo/$path", pct))
                }
            }
            store.register(dest, repo)
            Result.success()
        } catch (e: dev.exynoslm.hf.ChecksumMismatch) {
            Result.failure(workDataOf(K_ERROR to e.message))
        } catch (e: java.io.IOException) {
            if (runAttemptCount < 5) Result.retry() else Result.failure(workDataOf(K_ERROR to e.message))
        }
    }

    private fun foreground(title: String, pct: Int): ForegroundInfo {
        val ch = "downloads"
        val nm = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        nm.createNotificationChannel(android.app.NotificationChannel(ch, "Model downloads", android.app.NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, ch).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setOngoing(true).setProgress(100, pct.coerceAtLeast(0), pct < 0).build()
        return ForegroundInfo(title.hashCode(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val K_REPO = "repo"; const val K_PATH = "path"; const val K_SHA = "sha"; const val K_SIZE = "size"
        const val K_DONE = "done"; const val K_TOTAL = "total"; const val K_PCT = "pct"; const val K_ERROR = "error"
        fun workName(repo: String, path: String) = "dl:$repo/$path"

        fun enqueue(ctx: Context, repo: String, path: String, sha: String?, size: Long?, wifiOnly: Boolean) {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().putString(K_REPO, repo).putString(K_PATH, path).putString(K_SHA, sha).putLong(K_SIZE, size ?: -1).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true).build())
                .addTag("download").addTag(workName(repo, path)).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(workName(repo, path), ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context, repo: String, path: String) = WorkManager.getInstance(ctx).cancelUniqueWork(workName(repo, path))
    }
}
