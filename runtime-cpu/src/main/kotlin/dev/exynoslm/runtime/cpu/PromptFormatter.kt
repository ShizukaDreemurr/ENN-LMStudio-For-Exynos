package dev.exynoslm.runtime.cpu

import dev.exynoslm.model.ChatTemplate
import dev.exynoslm.runtime.GenerationRequest
import dev.exynoslm.runtime.Role

/** Renders chat history into a single prompt string for engines that take raw text. */
object PromptFormatter {
    fun format(template: ChatTemplate, req: GenerationRequest): String {
        val msgs = buildList {
            req.systemPrompt?.let { add(Role.SYSTEM to it) }
            req.messages.forEach { add(it.role to it.content) }
        }
        return when (template) {
            ChatTemplate.GEMMA, ChatTemplate.ENGINE_DEFAULT -> buildString {
                // Gemma has no system role: fold system text into the first user turn.
                var pendingSystem = ""
                for ((r, c) in msgs) when (r) {
                    Role.SYSTEM -> pendingSystem += c + "\n\n"
                    Role.USER, Role.TOOL -> { append("<start_of_turn>user\n").append(pendingSystem).append(c).append("<end_of_turn>\n"); pendingSystem = "" }
                    Role.ASSISTANT -> append("<start_of_turn>model\n").append(c).append("<end_of_turn>\n")
                }
                append("<start_of_turn>model\n")
            }
            ChatTemplate.CHATML -> msgs.joinToString("") { (r, c) -> "<|im_start|>${chatmlRole(r)}\n$c<|im_end|>\n" } + "<|im_start|>assistant\n"
            ChatTemplate.LLAMA3 -> "<|begin_of_text|>" + msgs.joinToString("") { (r, c) ->
                "<|start_header_id|>${if (r == Role.TOOL) "ipython" else r.name.lowercase()}<|end_header_id|>\n\n$c<|eot_id|>" } +
                "<|start_header_id|>assistant<|end_header_id|>\n\n"
            ChatTemplate.PHI3 -> msgs.joinToString("") { (r, c) -> "<|${chatmlRole(r)}|>\n$c<|end|>\n" } + "<|assistant|>\n"
        }
    }
    private fun chatmlRole(r: Role) = when (r) { Role.SYSTEM -> "system"; Role.USER -> "user"; Role.ASSISTANT -> "assistant"; Role.TOOL -> "tool" }
}
