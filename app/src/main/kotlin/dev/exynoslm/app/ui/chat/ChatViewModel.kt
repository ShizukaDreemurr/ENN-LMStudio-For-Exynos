package dev.exynoslm.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.exynoslm.app.data.AppDatabase
import dev.exynoslm.app.data.ChatMessageEntity
import dev.exynoslm.app.data.Crypto
import dev.exynoslm.app.data.InferenceHub
import dev.exynoslm.app.data.SettingsRepository
import dev.exynoslm.runtime.ChatMessage
import dev.exynoslm.runtime.GenerationEvent
import dev.exynoslm.runtime.GenerationRequest
import dev.exynoslm.runtime.Role
import dev.exynoslm.runtime.SamplingParams
import dev.exynoslm.telemetry.DeviceMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUi(
    val messages: List<ChatMessage> = emptyList(),
    val streaming: String? = null,
    val generating: Boolean = false,
    val tokPerSec: Double? = null,
    val ttftMs: Long? = null,
    val warnings: List<String> = emptyList(),
)

@HiltViewModel
class ChatViewModel @javax.inject.Inject constructor(
    val hub: InferenceHub,
    private val settings: SettingsRepository,
    private val db: AppDatabase,
    monitor: DeviceMonitor,
) : ViewModel() {
    private val conversationId = 1L
    private val _ui = MutableStateFlow(ChatUi())
    val ui = _ui.asStateFlow()
    val device = monitor.stream(2000).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    var sampling = MutableStateFlow(SamplingParams())
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val restored = db.chat().messages(conversationId).mapNotNull { e ->
                runCatching { ChatMessage(Role.valueOf(e.role), Crypto.decrypt(e.contentEnc)) }.getOrNull()
            }
            _ui.update { it.copy(messages = restored) }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || _ui.value.generating) return
        val msg = ChatMessage(Role.USER, text.trim())
        _ui.update { it.copy(messages = it.messages + msg) }
        persist(msg)
        generate()
    }

    fun regenerate() {
        if (_ui.value.generating) return
        _ui.update { s -> s.copy(messages = s.messages.dropLastWhile { it.role == Role.ASSISTANT }) }
        generate()
    }

    fun stop() { job?.cancel() }

    fun clear() {
        stop()
        _ui.value = ChatUi()
        viewModelScope.launch { db.chat().clear(conversationId) }
    }

    private fun generate() {
        val engine = hub.active.value.engine ?: run {
            _ui.update { it.copy(warnings = listOf("No model loaded")) }; return
        }
        job = viewModelScope.launch {
            val sys = settings.current().systemPrompt
            _ui.update { it.copy(generating = true, streaming = "", warnings = emptyList()) }
            var final: String? = null
            try {
                engine.generate(GenerationRequest(_ui.value.messages, sampling.value, sys)).collect { ev ->
                    when (ev) {
                        is GenerationEvent.PartialText -> _ui.update { it.copy(streaming = ev.textSoFar) }
                        is GenerationEvent.Usage -> _ui.update { it.copy(tokPerSec = ev.usage.tokensPerSecond, ttftMs = ev.usage.timeToFirstTokenMs) }
                        is GenerationEvent.Warning -> _ui.update { it.copy(warnings = it.warnings + ev.message) }
                        is GenerationEvent.Error -> _ui.update { it.copy(warnings = it.warnings + ev.message) }
                        is GenerationEvent.Completed -> final = ev.fullText
                        else -> Unit
                    }
                }
            } finally {
                // On Stop we keep the partial answer.
                val text = final ?: _ui.value.streaming.orEmpty()
                if (text.isNotEmpty()) {
                    val m = ChatMessage(Role.ASSISTANT, text)
                    _ui.update { it.copy(messages = it.messages + m) }
                    persist(m)
                }
                _ui.update { it.copy(generating = false, streaming = null) }
            }
        }
    }

    private fun persist(m: ChatMessage) = viewModelScope.launch {
        db.chat().insert(ChatMessageEntity(conversationId = conversationId, role = m.role.name, contentEnc = Crypto.encrypt(m.content)))
    }
}
