package dev.exynoslm.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

class CalculatorTool : Tool {
    override val spec = ToolSpec("calculator", "Evaluate an arithmetic expression (+ - * / ^ parentheses).",
        """{"type":"object","properties":{"expression":{"type":"string"}},"required":["expression"]}""", PermissionScope.READ_ONLY)
    override suspend fun execute(args: JsonObject): ToolOutcome {
        val expr = args.str("expression") ?: return ToolOutcome.Failed("missing 'expression'")
        return runCatching { ToolOutcome.Ok(Calc(expr).parse().let { if (it % 1.0 == 0.0 && kotlin.math.abs(it) < 1e15) it.toLong().toString() else it.toString() }) }
            .getOrElse { ToolOutcome.Failed("invalid expression: ${it.message}") }
    }

    /** Minimal recursive-descent parser. No eval of arbitrary code. */
    private class Calc(private val s: String) {
        private var i = 0
        fun parse(): Double { val v = expr(); skip(); require(i == s.length) { "unexpected '${s[i]}'" }; return v }
        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun eat(c: Char): Boolean { skip(); return if (i < s.length && s[i] == c) { i++; true } else false }
        private fun expr(): Double { var v = term(); while (true) v = when { eat('+') -> v + term(); eat('-') -> v - term(); else -> return v } }
        private fun term(): Double { var v = factor(); while (true) v = when { eat('*') -> v * factor(); eat('/') -> v / factor(); eat('%') -> v % factor(); else -> return v } }
        private fun factor(): Double { val b = unary(); return if (eat('^')) Math.pow(b, factor()) else b }
        private fun unary(): Double = when { eat('-') -> -unary(); eat('+') -> unary(); else -> atom() }
        private fun atom(): Double {
            if (eat('(')) { val v = expr(); require(eat(')')) { "missing )" }; return v }
            skip(); val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > st) { "number expected at $st" }
            return s.substring(st, i).toDouble()
        }
    }
}

class DateTimeTool(private val now: () -> ZonedDateTime = { ZonedDateTime.now() }) : Tool {
    override val spec = ToolSpec("datetime", "Get the current local date, time and time zone.",
        """{"type":"object","properties":{}}""", PermissionScope.READ_ONLY)
    override suspend fun execute(args: JsonObject) = ToolOutcome.Ok(now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + " " + now().zone)
}

/** All file tools are sandboxed to [root] (the app's agent workspace). Path traversal is rejected. */
abstract class SandboxedFileTool(protected val root: File) : Tool {
    protected fun resolve(rel: String?): File? {
        val f = File(root, rel ?: "").canonicalFile
        return if (f.path == root.canonicalPath || f.path.startsWith(root.canonicalPath + File.separator)) f else null
    }
}

class ListFilesTool(root: File) : SandboxedFileTool(root) {
    override val spec = ToolSpec("list_files", "List files in the agent workspace directory.",
        """{"type":"object","properties":{"path":{"type":"string","description":"relative dir, default root"}}}""", PermissionScope.READ_ONLY)
    override suspend fun execute(args: JsonObject): ToolOutcome {
        val dir = resolve(args.str("path")) ?: return ToolOutcome.Failed("path outside workspace")
        if (!dir.isDirectory) return ToolOutcome.Failed("not a directory")
        return ToolOutcome.Ok(dir.listFiles().orEmpty().sortedBy { it.name }.joinToString("\n") { (if (it.isDirectory) "[d] " else "") + it.name })
    }
}

class ReadFileTool(root: File, private val maxChars: Int = 8000) : SandboxedFileTool(root) {
    override val spec = ToolSpec("read_file", "Read a UTF-8 text file from the agent workspace.",
        """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""", PermissionScope.READ_ONLY)
    override suspend fun execute(args: JsonObject): ToolOutcome {
        val f = resolve(args.str("path")) ?: return ToolOutcome.Failed("path outside workspace")
        if (!f.isFile) return ToolOutcome.Failed("file not found")
        return ToolOutcome.Ok(f.readText().take(maxChars))
    }
}

class WriteFileTool(root: File) : SandboxedFileTool(root) {
    override val spec = ToolSpec("write_file", "Write (overwrite) a UTF-8 text file in the agent workspace.",
        """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}""",
        PermissionScope.WRITE_LOCAL, mutating = true)
    override fun describe(args: JsonObject) = "Write ${args.str("content")?.length ?: 0} chars to '${args.str("path")}'"
    override suspend fun execute(args: JsonObject): ToolOutcome {
        val f = resolve(args.str("path")) ?: return ToolOutcome.Failed("path outside workspace")
        val content = args.str("content") ?: return ToolOutcome.Failed("missing 'content'")
        f.parentFile?.mkdirs(); f.writeText(content)
        return ToolOutcome.Ok("wrote ${content.length} chars to ${f.relativeTo(root.canonicalFile)}")
    }
}

/** Naive full-text search over .md/.txt notes in the workspace. Replace with embeddings once an embedding model is wired. */
class SearchNotesTool(root: File) : SandboxedFileTool(root) {
    override val spec = ToolSpec("search_notes", "Search the user's local notes for a keyword. Returns matching lines.",
        """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""", PermissionScope.READ_ONLY)
    override suspend fun execute(args: JsonObject): ToolOutcome {
        val q = args.str("query")?.lowercase() ?: return ToolOutcome.Failed("missing 'query'")
        val hits = root.walkTopDown().filter { it.isFile && it.extension in setOf("md", "txt") }.flatMap { f ->
            f.readLines().withIndex().filter { q in it.value.lowercase() }.map { "${f.relativeTo(root)}:${it.index + 1}: ${it.value.trim()}" }
        }.take(20).toList()
        return ToolOutcome.Ok(if (hits.isEmpty()) "no matches" else hits.joinToString("\n"))
    }
}

/** Network tool. Only registered when the user enables internet for the agent; always needs confirmation. */
class FetchUrlTool(private val fetch: suspend (String) -> String) : Tool {
    override val spec = ToolSpec("fetch_url", "Fetch the text content of an https URL.",
        """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""", PermissionScope.NETWORK, mutating = true)
    override fun describe(args: JsonObject) = "Send a network request to ${args.str("url")}"
    override suspend fun execute(args: JsonObject): ToolOutcome {
        val url = args.str("url") ?: return ToolOutcome.Failed("missing 'url'")
        if (!url.startsWith("https://")) return ToolOutcome.Failed("only https URLs are allowed")
        return runCatching { ToolOutcome.Ok(fetch(url).take(8000)) }.getOrElse { ToolOutcome.Failed(it.message ?: "fetch failed") }
    }
}
