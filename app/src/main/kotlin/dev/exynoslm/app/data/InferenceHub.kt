package dev.exynoslm.app.data

import dev.exynoslm.model.Backend
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.runtime.BackendSelector
import dev.exynoslm.runtime.EngineManager
import dev.exynoslm.runtime.InferenceConfig
import dev.exynoslm.runtime.InferenceEngine
import dev.exynoslm.runtime.LoadResult
import dev.exynoslm.telemetry.DeviceMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveModel(
    val model: LoadedModel? = null,
    val engine: InferenceEngine? = null,
    val backend: Backend? = null,
    val loading: Boolean = false,
    val loadMs: Long = 0,
    val fallbackNotes: List<String> = emptyList(),
    val error: String? = null,
)

/** App-wide owner of the currently loaded model. Screens observe [active]; only this class loads/unloads. */
@Singleton
class InferenceHub @Inject constructor(
    private val selector: BackendSelector,
    private val manager: EngineManager,
    private val monitor: DeviceMonitor,
    private val settings: SettingsRepository,
) {
    private val _active = MutableStateFlow(ActiveModel())
    val active: StateFlow<ActiveModel> = _active.asStateFlow()

    suspend fun load(model: LoadedModel) {
        _active.value = ActiveModel(model = model, loading = true)
        val s = settings.current()
        val device = monitor.snapshot().toDeviceState()
        _active.value.engine?.unload()
        val (sel, res) = selector.selectAndLoad(model, s.backend, device, InferenceConfig(maxContextTokens = s.effectiveContext))
        _active.value = when (res) {
            is LoadResult.Success -> {
                manager.touch(model.id + "@" + sel.chosen, sel.engine!!)
                ActiveModel(model, sel.engine, sel.chosen, false, res.loadTimeMs, sel.fallbackNotes)
            }
            is LoadResult.Failure -> ActiveModel(model, null, null, false, 0, sel.fallbackNotes, res.reason)
        }
    }

    suspend fun unloadAll() { manager.trimAll(); _active.value = ActiveModel() }
}
