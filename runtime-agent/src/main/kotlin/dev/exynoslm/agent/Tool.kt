package dev.exynoslm.agent

import kotlinx.serialization.json.JsonObject

/** What a tool is allowed to touch. Anything beyond READ_ONLY requires user confirmation unless dry-run. */
enum class PermissionScope { READ_ONLY, WRITE_LOCAL, NETWORK, CONTACTS, CALENDAR, CLIPBOARD }

data class ToolSpec(
    val name: String,
    val description: String,
    /** JSON-schema (as a JSON string) describing the arguments object. */
    val parametersSchema: String,
    val scope: PermissionScope,
    val mutating: Boolean = false,
)

sealed interface ToolOutcome {
    data class Ok(val output: String) : ToolOutcome
    data class Failed(val error: String) : ToolOutcome
    data class DryRun(val wouldDo: String) : ToolOutcome
    data object Denied : ToolOutcome
}

interface Tool {
    val spec: ToolSpec
    /** Human-readable summary of the side effect, shown in the confirmation dialog and in dry-run results. */
    fun describe(args: JsonObject): String = "${spec.name}($args)"
    suspend fun execute(args: JsonObject): ToolOutcome
}

class ToolRegistry(tools: List<Tool> = emptyList()) {
    private val map = LinkedHashMap<String, Tool>().apply { tools.forEach { put(it.spec.name, it) } }
    fun register(tool: Tool) { map[tool.spec.name] = tool }
    fun unregister(name: String) { map.remove(name) }
    operator fun get(name: String): Tool? = map[name]
    fun all(): List<Tool> = map.values.toList()

    /** Tool list rendered for the system prompt. */
    fun promptBlock(enabled: Set<String>? = null): String = all()
        .filter { enabled == null || it.spec.name in enabled }
        .joinToString("\n") { "- ${it.spec.name}: ${it.spec.description} Arguments schema: ${it.spec.parametersSchema}" }
}
