package dev.exynoslm.agent

import dev.exynoslm.runtime.ChatMessage
import dev.exynoslm.runtime.GenerationEvent
import dev.exynoslm.runtime.GenerationRequest
import dev.exynoslm.runtime.InferenceEngine
import dev.exynoslm.runtime.Role
import dev.exynoslm.runtime.SamplingParams
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AgentLimits(
    val maxSteps: Int = 8,
    val maxTotalTokens: Int = 8192,
    val toolTimeoutMs: Long = 10_000,
    val blockedTools: Set<String> = emptySet(),
)

data class AgentPolicy(
    /** Default ON: mutating tools only describe what they would do. */
    val dryRun: Boolean = true,
    val grantedScopes: Set<PermissionScope> = setOf(PermissionScope.READ_ONLY),
)

/** UI hook: ask the user to approve a side-effecting action. */
fun interface Confirmer { suspend fun confirm(toolName: String, description: String): Boolean }

@Serializable
data class TraceStep(
    val index: Int,
    val modelOutput: String,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val error: String? = null,
    val completionTokens: Int = 0,
    val durationMs: Long = 0,
)

@Serializable
data class AgentTrace(val goal: String, val systemPrompt: String, val steps: List<TraceStep>, val finalAnswer: String?, val stopReason: String)

sealed interface AgentEvent {
    data class Thinking(val step: Int, val partial: String) : AgentEvent
    data class ToolCall(val step: Int, val name: String, val args: String) : AgentEvent
    data class Observation(val step: Int, val name: String, val outcome: ToolOutcome) : AgentEvent
    data class Final(val answer: String, val trace: AgentTrace) : AgentEvent
    data class Stopped(val reason: String, val trace: AgentTrace) : AgentEvent
}

/**
 * ReAct-style loop with a simple, model-agnostic protocol:
 * the model either emits `<tool_call>{"name":"...","arguments":{...}}</tool_call>` or a plain final answer.
 * Works with any instruct model; models with native function calling can be adapted in [parseToolCall].
 */
class AgentRunner(
    private val engine: InferenceEngine,
    private val tools: ToolRegistry,
    private val limits: AgentLimits = AgentLimits(),
    private val policy: AgentPolicy = AgentPolicy(),
    private val confirmer: Confirmer = Confirmer { _, _ -> false },
    private val sampling: SamplingParams = SamplingParams(temperature = 0.2f, maxNewTokens = 512),
) {
    /** Set true to pause between steps (the current generation finishes first). */
    val paused = MutableStateFlow(false)

    fun systemPrompt(custom: String?): String = (custom ?: DEFAULT_SYSTEM) + "\n\nAvailable tools:\n" +
        tools.promptBlock(tools.all().map { it.spec.name }.filter { it !in limits.blockedTools }.toSet()) +
        "\n\nTo call a tool reply with ONLY: <tool_call>{\"name\":\"tool_name\",\"arguments\":{...}}</tool_call>\n" +
        "After you receive the observation, continue. When done, reply with the final answer as plain text."

    fun run(goal: String, customSystemPrompt: String? = null): Flow<AgentEvent> = flow {
        val sys = systemPrompt(customSystemPrompt)
        val messages = mutableListOf(ChatMessage(Role.SYSTEM, sys), ChatMessage(Role.USER, goal))
        val steps = mutableListOf<TraceStep>()
        var tokens = 0
        fun trace(final: String?, reason: String) = AgentTrace(goal, sys, steps.toList(), final, reason)

        for (step in 0 until limits.maxSteps) {
            paused.first { !it }
            val t0 = System.currentTimeMillis()
            var output = ""; var usedTokens = 0
            engine.generate(GenerationRequest(messages.toList(), sampling)).collect { ev ->
                when (ev) {
                    is GenerationEvent.PartialText -> { output = ev.textSoFar; emit(AgentEvent.Thinking(step, output)) }
                    is GenerationEvent.Completed -> output = ev.fullText
                    is GenerationEvent.Usage -> usedTokens = ev.usage.completionTokens
                    is GenerationEvent.Error -> throw IllegalStateException(ev.message, ev.cause)
                    else -> Unit
                }
            }
            tokens += usedTokens
            val call = parseToolCall(output)
            if (call == null) {
                steps += TraceStep(step, output, completionTokens = usedTokens, durationMs = System.currentTimeMillis() - t0)
                emit(AgentEvent.Final(output.trim(), trace(output.trim(), "completed"))); return@flow
            }
            emit(AgentEvent.ToolCall(step, call.first, call.second.toString()))
            val outcome = invoke(call.first, call.second)
            emit(AgentEvent.Observation(step, call.first, outcome))
            val obs = when (outcome) {
                is ToolOutcome.Ok -> outcome.output
                is ToolOutcome.Failed -> "ERROR: ${outcome.error}"
                is ToolOutcome.DryRun -> "DRY-RUN (not executed): ${outcome.wouldDo}"
                ToolOutcome.Denied -> "DENIED by user or policy"
            }
            steps += TraceStep(step, output, call.first, call.second.toString(), obs,
                error = (outcome as? ToolOutcome.Failed)?.error, completionTokens = usedTokens, durationMs = System.currentTimeMillis() - t0)
            messages += ChatMessage(Role.ASSISTANT, output)
            messages += ChatMessage(Role.TOOL, "Observation from ${call.first}: $obs", toolName = call.first)
            if (tokens >= limits.maxTotalTokens) { emit(AgentEvent.Stopped("token budget exhausted", trace(null, "max_tokens"))); return@flow }
        }
        emit(AgentEvent.Stopped("max steps (${limits.maxSteps}) reached", trace(null, "max_steps")))
    }

    internal suspend fun invoke(name: String, args: JsonObject): ToolOutcome {
        val tool = tools[name] ?: return ToolOutcome.Failed("unknown tool '$name'")
        if (name in limits.blockedTools) return ToolOutcome.Denied
        if (tool.spec.scope !in policy.grantedScopes && tool.spec.scope != PermissionScope.READ_ONLY) {
            if (policy.dryRun) return ToolOutcome.DryRun(tool.describe(args))
            if (!confirmer.confirm(name, tool.describe(args))) return ToolOutcome.Denied
        } else if (tool.spec.mutating) {
            if (policy.dryRun) return ToolOutcome.DryRun(tool.describe(args))
            if (!confirmer.confirm(name, tool.describe(args))) return ToolOutcome.Denied
        }
        return try { withTimeout(limits.toolTimeoutMs) { tool.execute(args) } }
        catch (e: TimeoutCancellationException) { ToolOutcome.Failed("timed out after ${limits.toolTimeoutMs} ms") }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val TAG = Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL)

        fun parseToolCall(text: String): Pair<String, JsonObject>? {
            val body = TAG.find(text)?.groupValues?.get(1)?.trim() ?: return null
            return runCatching {
                val obj = json.parseToJsonElement(body).jsonObject
                val name = obj["name"]!!.jsonPrimitive.contentOrNull!!
                name to (obj["arguments"]?.jsonObject ?: JsonObject(emptyMap()))
            }.getOrNull()
        }

        const val DEFAULT_SYSTEM = "You are a helpful on-device assistant running fully offline. " +
            "Think step by step, use tools when they help, and never invent tool results."

        fun exportJson(trace: AgentTrace): String = Json { prettyPrint = true }.encodeToString(AgentTrace.serializer(), trace)
    }
}
