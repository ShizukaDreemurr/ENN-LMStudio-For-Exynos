package dev.exynoslm.app.ui.hub

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.exynoslm.app.data.SettingsRepository
import dev.exynoslm.app.download.DownloadWorker
import dev.exynoslm.hf.HubException
import dev.exynoslm.hf.HuggingFaceClient
import dev.exynoslm.hf.SearchQuery
import dev.exynoslm.model.CompatBadge
import dev.exynoslm.model.LicenseRisk
import dev.exynoslm.model.Licenses
import dev.exynoslm.model.ModelCard
import dev.exynoslm.model.ModelFile
import dev.exynoslm.runtime.Compatibility
import dev.exynoslm.telemetry.DeviceMonitor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TaskFilter(val pipelineTag: String?, val label: String) {
    ALL(null, "All"), CHAT("text-generation", "Chat/LLM"), EMBED("feature-extraction", "Embedding"),
    ASR("automatic-speech-recognition", "Speech"), CLS("image-classification", "Image cls"),
    DET("object-detection", "Detection"), OCR("image-to-text", "OCR"), IMGGEN("text-to-image", "Image gen"),
}

enum class LibFilter(val library: String?) { ANY(null), LITERT("tflite"), GGUF("gguf"), ONNX("onnx"), MEDIAPIPE("mediapipe") }

data class HubUi(
    val query: String = "",
    val task: TaskFilter = TaskFilter.CHAT,
    val lib: LibFilter = LibFilter.ANY,
    val results: List<ModelCard> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val detail: ModelCard? = null,
)

@HiltViewModel
class HubViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val hf: HuggingFaceClient,
    private val settings: SettingsRepository,
    private val monitor: DeviceMonitor,
) : ViewModel() {
    private val _ui = MutableStateFlow(HubUi())
    val ui = _ui.asStateFlow()
    private val totalRam by lazy { monitor.snapshot().totalRamBytes }

    /** Live progress of all model downloads, keyed by unique work name. */
    val downloads: StateFlow<Map<String, WorkInfo>> = WorkManager.getInstance(ctx).getWorkInfosByTagFlow("download")
        .map { list -> list.associateBy { wi -> wi.tags.firstOrNull { it.startsWith("dl:") } ?: wi.id.toString() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init { search() }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    fun setTask(t: TaskFilter) { _ui.update { it.copy(task = t) }; search() }
    fun setLib(l: LibFilter) { _ui.update { it.copy(lib = l) }; search() }

    fun search() = load {
        val s = _ui.value
        val page = hf.search(SearchQuery(search = s.query.ifBlank { null }, pipelineTag = s.task.pipelineTag, library = s.lib.library))
        _ui.update { it.copy(results = page.models, next = page.nextUrl) }
    }

    fun loadMore() {
        val url = _ui.value.next ?: return
        load { val p = hf.fetchPage(url); _ui.update { it.copy(results = it.results + p.models, next = p.nextUrl) } }
    }

    fun open(card: ModelCard) = load { _ui.update { it.copy(detail = hf.model(card.repoId)) } }
    fun closeDetail() = _ui.update { it.copy(detail = null) }

    fun badges(card: ModelCard, f: ModelFile): Set<CompatBadge> = Compatibility.badges(f, card.task, totalRam)
    fun licenseRisk(card: ModelCard): LicenseRisk = Licenses.classify(card.license)

    fun download(card: ModelCard, f: ModelFile) = viewModelScope.launch {
        DownloadWorker.enqueue(ctx, card.repoId, f.path, f.sha256, f.sizeBytes, settings.current().wifiOnly)
    }
    fun cancel(card: ModelCard, f: ModelFile) = DownloadWorker.cancel(ctx, card.repoId, f.path)

    private fun load(block: suspend () -> Unit) = viewModelScope.launch {
        _ui.update { it.copy(loading = true, error = null) }
        try { block() } catch (e: HubException.RateLimited) {
            _ui.update { it.copy(error = "Rate limited by Hugging Face, retry in ${e.retryAfterSec ?: 60}s") }
        } catch (e: java.io.IOException) {
            _ui.update { it.copy(error = "Offline or network error: ${e.message}") }
        } finally { _ui.update { it.copy(loading = false) } }
    }
}
