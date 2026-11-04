package dev.exynoslm.app.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.exynoslm.agent.*
import dev.exynoslm.app.data.AgentRunEntity
import dev.exynoslm.app.data.AppDatabase
import dev.exynoslm.app.data.Crypto
import dev.exynoslm.app.data.InferenceHub
import dev.exynoslm.app.data.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

data class PendingConfirm(val tool: String, val description: String, val answer: CompletableDeferred<Boolean>)

data class AgentUi(
    val running: Boolean = false,
    val timeline: List<AgentEvent> = emptyList(),
    val live: String = "",
    val result: String? = null,
    val lastTrace: AgentTrace? = null,
    val confirm: PendingConfirm? = null,
    val enabledTools: Set<String> = emptySet(),
)

/** Android-only tool: clipboard (read-only by default; write requires confirmation). */
class ClipboardTool(private val ctx: Context) : Tool {
    override val spec = ToolSpec("clipboard", "Read the clipboard text, or write text to it when 'text' is given.",
        """{"type":"object","properties":{"text":{"type":"string"}}}""", PermissionScope.CLIPBOARD, mutating = true)
    override fun describe(args: JsonObject) = args["text"]?.jsonPrimitive?.contentOrNull?.let { "Copy \"${it.take(80)}\" to clipboard" } ?: "Read clipboard"
    override suspend fun execute(args: JsonObject): ToolOutcome {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        val text = args["text"]?.jsonPrimitive?.contentOrNull
        return if (text != null) { cm.setPrimaryClip(ClipData.newPlainText("agent", text)); ToolOutcome.Ok("copied") }
        else ToolOutcome.Ok(cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: "(empty)")
    }
}

@HiltViewModel
class AgentViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    val hub: InferenceHub,
    private val settings: SettingsRepository,
    private val db: AppDatabase,
    private val http: OkHttpClient,
) : ViewModel() {
    private val workspace = File(ctx.filesDir, "agent_workspace").apply { mkdirs() }
    private val baseTools = listOf(CalculatorTool(), DateTimeTool(), ListFilesTool(workspace), ReadFileTool(workspace),
        SearchNotesTool(workspace), WriteFileTool(workspace), ClipboardTool(ctx))
    private val netTool = FetchUrlTool { url ->
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty() }
        }
    }
    val allToolNames get() = (baseTools + netTool).map { it.spec.name }

    private val _ui = MutableStateFlow(AgentUi(enabledTools = baseTools.map { it.spec.name }.toSet() - "write_file" - "clipboard"))
    val ui = _ui.asStateFlow()
    private var job: Job? = null
    private var runner: AgentRunner? = null

    fun toggleTool(name: String) = _ui.update { it.copy(enabledTools = if (name in it.enabledTools) it.enabledTools - name else it.enabledTools + name) }

    fun run(goal: String, customSystem: String?) {
        val engine = hub.active.value.engine ?: run { _ui.update { it.copy(result = "Load a chat model first (My Models).") }; return }
        job = viewModelScope.launch {
            val s = settings.current()
            val tools = (baseTools + if (s.agentInternet) listOf(netTool) else emptyList()).filter { it.spec.name in _ui.value.enabledTools }
            val scopes = setOf(PermissionScope.READ_ONLY) // anything else always goes through the confirm dialog
            val r = AgentRunner(engine, ToolRegistry(tools), AgentLimits(maxSteps = s.agentMaxSteps), AgentPolicy(s.agentDryRun, scopes),
                confirmer = { tool, desc -> CompletableDeferred<Boolean>().also { d -> _ui.update { it.copy(confirm = PendingConfirm(tool, desc, d)) } }.await() })
            runner = r
            _ui.update { AgentUi(running = true, enabledTools = it.enabledTools) }
            try {
                r.run(goal, customSystem?.ifBlank { null }).collect { ev ->
                    when (ev) {
                        is AgentEvent.Thinking -> _ui.update { it.copy(live = ev.partial) }
                        is AgentEvent.Final -> { _ui.update { it.copy(result = ev.answer, lastTrace = ev.trace, timeline = it.timeline + ev) }; save(ev.trace) }
                        is AgentEvent.Stopped -> { _ui.update { it.copy(result = "Stopped: ${ev.reason}", lastTrace = ev.trace, timeline = it.timeline + ev) }; save(ev.trace) }
                        else -> _ui.update { it.copy(timeline = it.timeline + ev, live = "") }
                    }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(result = "Error: ${e.message}") }
            } finally { _ui.update { it.copy(running = false, confirm = null) } }
        }
    }

    fun answer(allow: Boolean) { _ui.value.confirm?.answer?.complete(allow); _ui.update { it.copy(confirm = null) } }
    fun pause(p: Boolean) { runner?.paused?.value = p }
    fun stop() { job?.cancel() }
    fun exportTrace(): String? = _ui.value.lastTrace?.let(AgentRunner::exportJson)

    private suspend fun save(t: AgentTrace) {
        db.agentRuns().insert(AgentRunEntity(goalEnc = Crypto.encrypt(t.goal), traceJsonEnc = Crypto.encrypt(AgentRunner.exportJson(t)), stopReason = t.stopReason))
    }
}
