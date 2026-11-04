package dev.exynoslm.app.ui.agent

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.exynoslm.agent.AgentEvent
import dev.exynoslm.agent.ToolOutcome
import dev.exynoslm.app.R

@Composable
fun AgentScreen(vm: AgentViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val active by vm.hub.active.collectAsStateWithLifecycle()
    var goal by rememberSaveable { mutableStateOf("") }
    var sys by rememberSaveable { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    ui.confirm?.let { c ->
        AlertDialog(onDismissRequest = { vm.answer(false) }, title = { Text(stringResource(R.string.confirm_action)) },
            text = { Text("${c.tool}: ${c.description}") },
            confirmButton = { Button({ vm.answer(true) }) { Text(stringResource(R.string.allow)) } },
            dismissButton = { TextButton({ vm.answer(false) }) { Text(stringResource(R.string.deny)) } })
    }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Model: ${active.model?.displayName ?: "—"} (${active.backend ?: "not loaded"})", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(goal, { goal = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.goal_hint)) }, minLines = 2)
        OutlinedTextField(sys, { sys = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.system_prompt)) }, maxLines = 3)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            vm.allToolNames.forEach { t -> FilterChip(t in ui.enabledTools, { vm.toggleTool(t) }, { Text(t) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!ui.running) Button({ vm.run(goal, sys) }, enabled = goal.isNotBlank()) { Text(stringResource(R.string.run)) }
            else {
                OutlinedButton({ paused = !paused; vm.pause(paused) }) { Text(if (paused) "Resume" else stringResource(R.string.pause)) }
                Button({ vm.stop() }) { Text(stringResource(R.string.stop)) }
            }
            OutlinedButton({
                vm.exportTrace()?.let { json ->
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, json), null))
                }
            }, enabled = ui.lastTrace != null) { Text(stringResource(R.string.export_trace)) }
        }
        if (ui.running) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(ui.timeline) { ev ->
                val (title, body) = when (ev) {
                    is AgentEvent.ToolCall -> "#${ev.step} → ${ev.name}" to ev.args
                    is AgentEvent.Observation -> "#${ev.step} ← ${ev.name}" to when (val o = ev.outcome) {
                        is ToolOutcome.Ok -> o.output; is ToolOutcome.Failed -> "ERROR ${o.error}"
                        is ToolOutcome.DryRun -> "DRY-RUN: ${o.wouldDo}"; ToolOutcome.Denied -> "DENIED"
                    }
                    is AgentEvent.Final -> "✓ Final" to ev.answer
                    is AgentEvent.Stopped -> "■ Stopped" to ev.reason
                    is AgentEvent.Thinking -> "…" to ev.partial
                }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(8.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge); Text(body.take(1500), style = MaterialTheme.typography.bodySmall)
                } }
            }
            if (ui.live.isNotEmpty()) item { Text(ui.live, style = MaterialTheme.typography.bodySmall) }
            ui.result?.let { item { Text(it, style = MaterialTheme.typography.bodyLarge) } }
        }
    }
}
