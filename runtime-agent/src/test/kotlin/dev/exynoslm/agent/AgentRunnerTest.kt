package dev.exynoslm.agent

import dev.exynoslm.runtime.FakeEngine
import dev.exynoslm.runtime.Role
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentRunnerTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Mock LLM: first turn asks for calculator, second turn answers using the observation. */
    private fun scripted() = FakeEngine { msgs ->
        val obs = msgs.lastOrNull { it.role == Role.TOOL }
        if (obs == null) """<tool_call>{"name":"calculator","arguments":{"expression":"(2+3)*4^2"}}</tool_call>"""
        else "The answer is " + obs.content.substringAfterLast(": ")
    }

    @Test fun toolLoopReachesFinalAnswer() = runTest {
        val events = AgentRunner(scripted(), ToolRegistry(listOf(CalculatorTool()))).run("compute").toList()
        val final = events.last() as AgentEvent.Final
        assertEquals("The answer is 80", final.answer)
        assertEquals(2, final.trace.steps.size)
        assertTrue(AgentRunner.exportJson(final.trace).contains("calculator"))
    }

    @Test fun maxStepsStopsLoop() = runTest {
        val loop = FakeEngine { """<tool_call>{"name":"datetime","arguments":{}}</tool_call>""" }
        val last = AgentRunner(loop, ToolRegistry(listOf(DateTimeTool())), AgentLimits(maxSteps = 3)).run("x").toList().last()
        assertTrue(last is AgentEvent.Stopped)
        assertEquals(3, (last as AgentEvent.Stopped).trace.steps.size)
    }

    @Test fun writeIsDryRunByDefault() = runTest {
        val runner = AgentRunner(FakeEngine(), ToolRegistry(listOf(WriteFileTool(tmp.root))))
        val out = runner.invoke("write_file", obj("path" to "a.txt", "content" to "hi"))
        assertTrue(out is ToolOutcome.DryRun)
        assertFalse(tmp.root.resolve("a.txt").exists())
    }

    @Test fun writeRequiresConfirmation() = runTest {
        val reg = ToolRegistry(listOf(WriteFileTool(tmp.root)))
        val policy = AgentPolicy(dryRun = false, grantedScopes = setOf(PermissionScope.READ_ONLY, PermissionScope.WRITE_LOCAL))
        assertEquals(ToolOutcome.Denied, AgentRunner(FakeEngine(), reg, policy = policy, confirmer = { _, _ -> false })
            .invoke("write_file", obj("path" to "a.txt", "content" to "hi")))
        assertTrue(AgentRunner(FakeEngine(), reg, policy = policy, confirmer = { _, _ -> true })
            .invoke("write_file", obj("path" to "a.txt", "content" to "hi")) is ToolOutcome.Ok)
        assertEquals("hi", tmp.root.resolve("a.txt").readText())
    }

    @Test fun sandboxRejectsTraversal() = runTest {
        val out = ReadFileTool(tmp.root).execute(obj("path" to "../../etc/passwd"))
        assertEquals(ToolOutcome.Failed("path outside workspace"), out)
    }

    @Test fun blockedAndTimeout() = runTest {
        val slow = object : Tool {
            override val spec = ToolSpec("slow", "", "{}", PermissionScope.READ_ONLY)
            override suspend fun execute(args: JsonObject): ToolOutcome { delay(60_000); return ToolOutcome.Ok("") }
        }
        val reg = ToolRegistry(listOf(slow, CalculatorTool()))
        val r = AgentRunner(FakeEngine(), reg, AgentLimits(toolTimeoutMs = 100, blockedTools = setOf("calculator")))
        assertEquals(ToolOutcome.Denied, r.invoke("calculator", obj("expression" to "1")))
        assertTrue((r.invoke("slow", JsonObject(emptyMap())) as ToolOutcome.Failed).error.contains("timed out"))
    }

    @Test fun searchNotes() = runTest {
        tmp.root.resolve("notes.md").writeText("buy milk\nExynos benchmark ideas\n")
        val out = SearchNotesTool(tmp.root).execute(obj("query" to "exynos")) as ToolOutcome.Ok
        assertEquals("notes.md:2: Exynos benchmark ideas", out.output)
    }

    @Test fun parseToolCall() {
        assertNull(AgentRunner.parseToolCall("plain answer"))
        assertEquals("datetime", AgentRunner.parseToolCall("ok <tool_call> {\"name\":\"datetime\"} </tool_call>")!!.first)
    }

    private fun obj(vararg p: Pair<String, String>) =
        JsonObject(p.associate { it.first to kotlinx.serialization.json.JsonPrimitive(it.second) })
}
