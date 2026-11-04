package dev.exynoslm.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.exynoslm.app.data.AppDatabase
import dev.exynoslm.app.data.SettingsRepository
import dev.exynoslm.hf.HuggingFaceClient
import dev.exynoslm.hf.ModelDownloader
import dev.exynoslm.model.Backend
import dev.exynoslm.runtime.BackendSelector
import dev.exynoslm.runtime.EngineManager
import dev.exynoslm.runtime.InferenceEngine
import dev.exynoslm.runtime.cpu.MediaPipeLlmEngine
import dev.exynoslm.runtime.enn.EnnEngine
import dev.exynoslm.runtime.enn.LiteRtNpuBridge
import dev.exynoslm.runtime.enn.StubEnnBridge
import dev.exynoslm.runtime.gpu.GpuEngines
import dev.exynoslm.storage.ModelStore
import dev.exynoslm.telemetry.DeviceMonitor
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun settings(@ApplicationContext c: Context) = SettingsRepository(c)

    @Provides @Singleton fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()

    // Token is read lazily per request; it's only sent to huggingface.co.
    @Provides @Singleton fun hf(http: OkHttpClient, s: SettingsRepository) =
        HuggingFaceClient(http, token = { runBlocking { s.current().hfToken }.ifBlank { null } })

    @Provides @Singleton fun downloader(http: OkHttpClient, s: SettingsRepository) =
        ModelDownloader(http, token = { runBlocking { s.current().hfToken }.ifBlank { null } })

    @Provides @Singleton fun store(@ApplicationContext c: Context) = ModelStore(c)
    @Provides @Singleton fun monitor(@ApplicationContext c: Context) = DeviceMonitor(c)
    @Provides @Singleton fun engineManager() = EngineManager(maxResident = 1)

    /**
     * Backend registry. Swap StubEnnBridge for the real bridge once the Samsung SDK is integrated
     * (docs/ENN_INTEGRATION.md). Everything else picks it up automatically.
     */
    @Provides @Singleton fun engines(@ApplicationContext c: Context): Map<Backend, @JvmSuppressWildcards InferenceEngine> = mapOf(
        Backend.NPU_ENN to EnnEngine(if (dev.exynoslm.runtime.enn.BuildConfig.ENN_ENABLED) LiteRtNpuBridge(c) else StubEnnBridge()),
        Backend.GPU to GpuEngines.llm(c),
        Backend.CPU to MediaPipeLlmEngine(c, Backend.CPU),
    )

    @Provides @Singleton fun selector(engines: Map<Backend, @JvmSuppressWildcards InferenceEngine>) = BackendSelector(engines)

    @Provides @Singleton fun db(@ApplicationContext c: Context) =
        Room.databaseBuilder(c, AppDatabase::class.java, "exynoslm.db").fallbackToDestructiveMigration().build()
}
