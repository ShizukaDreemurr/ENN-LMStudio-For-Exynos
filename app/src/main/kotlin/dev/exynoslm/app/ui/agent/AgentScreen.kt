package dev.exynoslm.app.ui.agent

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.exynoslm.agent.AgentEvent
import dev.exynoslm.agent.ToolOutcome
import dev.exynoslm.app.R
import dev.exynoslm.app.ui.components.LxButton
import dev.exynoslm.app.ui.components.LxButtonKind
import dev.exynoslm.app.ui.components.LxCard
import dev.exynoslm.app.ui.components.LxConfirmDialog
import dev.exynoslm.app.ui.components.LxEmptyState
import dev.exynoslm.app.ui.components.LxFilterChip
import dev.exynoslm.app.ui.components.LxFlow
import dev.exynoslm.app.ui.components.LxIconButton
import dev.exynoslm.app.ui.components.LxNotice
import dev.exynoslm.app.ui.components.LxProgressBar
import dev.exynoslm.app.ui.components.LxScreenHeader
import dev.exynoslm.app.ui.components.LxSectionTitle
import dev.exynoslm.app.ui.components.LxStatusPill
import dev.exynoslm.app.ui.components.LxTextField
import dev.exynoslm.app.ui.components.LxTimelineItem
import dev.exynoslm.app.ui.components.LxTone
import dev.exynoslm.app.ui.theme.Lx

private class EventRow(val title: String, val body: String, val tone: LxTone, val badge: String? = null, val mono: Boolean = true)

@Composable
fun AgentScreen(vm: AgentViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val active by vm.hub.active.collectAsStateWithLifecycle()
    var goal by rememberSaveable { mutableStateOf("") }
    var sys by rememberSaveable { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val c = Lx.colors
    LaunchedEffect(ui.running) { if (!ui.running) paused = false }

    ui.confirm?.let { cf ->
        LxConfirmDialog(
            title = stringResource(R.string.confirm_action),
            message = stringResource(R.string.agent_confirm_hint),
            chip = cf.tool,
            confirmLabel = stringResource(R.string.allow),
            dismissLabel = stringResource(R.string.deny),
            onConfirm = { vm.answer(true) },
            onDismiss = { vm.answer(false) },
            tone = LxTone.Warn,
            content = {
                Text(
                    cf.description, Modifier.padding(top = 10.dp), color = c.ink2, fontSize = 13.sp,
                    lineHeight = 19.sp, fontWeight = FontWeight(650),
                )
            },
        )
    }

    // Labels for the timeline are resolved here (composable scope) and handed to the row mapper below.
    val okBadge = stringResource(R.string.agent_badge_ok)
    val failedBadge = stringResource(R.string.agent_badge_failed)
    val dryBadge = stringResource(R.string.agent_badge_dry_run)
    val deniedBadge = stringResource(R.string.agent_badge_denied)
    val finalTitle = stringResource(R.string.agent_final)
    val stoppedTitle = stringResource(R.string.agent_stopped)
    val thinkingTitle = stringResource(R.string.agent_thinking)

    Column(Modifier.fillMaxSize()) {
        LxScreenHeader(
            title = stringResource(R.string.nav_agent),
            kicker = stringResource(R.string.app_kicker),
            trailing = {
                if (active.engine != null) LxStatusPill(active.backend?.name ?: "", c.mint, blink = ui.running)
            },
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (active.model == null) {
                item(key = "needModel") { LxNotice(stringResource(R.string.agent_need_model), Modifier.fillMaxWidth(), LxTone.Warn) }
            } else {
                item(key = "model") {
                    Text(
                        active.model?.displayName ?: "", color = c.ink3, fontSize = 12.5.sp, fontWeight = FontWeight(700), maxLines = 1,
                    )
                }
            }
            item(key = "goal") {
                LxTextField(goal, { goal = it }, stringResource(R.string.goal_hint), Modifier.fillMaxWidth(), minLines = 2)
            }
            item(key = "sys") {
                LxTextField(sys, { sys = it }, stringResource(R.string.system_prompt), Modifier.fillMaxWidth(), maxLines = 3)
            }
            item(key = "tools") {
                Column {
                    LxSectionTitle(stringResource(R.string.agent_tools))
                    LxFlow(Modifier.padding(top = 8.dp)) {
                        vm.allToolNames.forEach { t -> LxFilterChip(selected = t in ui.enabledTools, onClick = { vm.toggleTool(t) }, label = t) }
                    }
                }
            }
            item(key = "controls") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!ui.running) {
                        LxButton(
                            stringResource(R.string.run), { vm.run(goal, sys) }, Modifier.weight(1f),
                            icon = Icons.Default.PlayArrow, enabled = goal.isNotBlank(),
                        )
                    } else {
                        LxButton(
                            if (paused) stringResource(R.string.agent_resume) else stringResource(R.string.pause),
                            { paused = !paused; vm.pause(paused) }, Modifier.weight(1f),
                            kind = LxButtonKind.Secondary, icon = if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        )
                        LxButton(stringResource(R.string.stop), { vm.stop() }, Modifier.weight(1f), kind = LxButtonKind.Danger, icon = Icons.Default.Stop)
                    }
                    LxIconButton(
                        Icons.Default.Share, stringResource(R.string.export_trace),
                        {
                            vm.exportTrace()?.let { json ->
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, json), null))
                            }
                        },
                        enabled = ui.lastTrace != null, size = 48.dp,
                    )
                }
            }
            if (ui.running) item(key = "progress") { LxProgressBar(null) }

            if (ui.timeline.isEmpty() && ui.live.isEmpty() && ui.result == null && !ui.running) {
                item(key = "empty") { LxEmptyState(stringResource(R.string.agent_empty_title), stringResource(R.string.agent_empty_sub)) }
            }

            itemsIndexed(ui.timeline) { i, ev ->
                val row = when (ev) {
                    is AgentEvent.ToolCall -> EventRow("#${ev.step} → ${ev.name}", ev.args, LxTone.Accent)
                    is AgentEvent.Observation -> {
                        val (body, tone, badge) = when (val o = ev.outcome) {
                            is ToolOutcome.Ok -> Triple(o.output, LxTone.Success, okBadge)
                            is ToolOutcome.Failed -> Triple(o.error, LxTone.Danger, failedBadge)
                            is ToolOutcome.DryRun -> Triple(o.wouldDo, LxTone.Warn, dryBadge)
                            ToolOutcome.Denied -> Triple("", LxTone.Warn, deniedBadge)
                        }
                        EventRow("#${ev.step} ← ${ev.name}", body, tone, badge)
                    }
                    is AgentEvent.Final -> EventRow(finalTitle, ev.answer, LxTone.Success, mono = false)
                    is AgentEvent.Stopped -> EventRow(stoppedTitle, ev.reason, LxTone.Danger)
                    is AgentEvent.Thinking -> EventRow(thinkingTitle, ev.partial, LxTone.Neutral)
                }
                val lastIdx = i == ui.timeline.lastIndex
                LxTimelineItem(
                    title = row.title,
                    body = row.body.take(1500),
                    tone = row.tone,
                    badge = row.badge,
                    running = lastIdx && ui.running && ev is AgentEvent.ToolCall,
                    newest = lastIdx && ui.live.isEmpty(),
                    last = lastIdx && ui.live.isEmpty(),
                    mono = row.mono,
                )
            }
            if (ui.live.isNotEmpty()) {
                item(key = "live") {
                    LxTimelineItem(title = thinkingTitle, body = ui.live.takeLast(1200), tone = LxTone.Neutral, running = true, newest = true, last = true)
                }
            }
            ui.result?.let { r ->
                item(key = "result") {
                    LxCard(Modifier.fillMaxWidth(), borderColor = c.accent) {
                        Text(stringResource(R.string.agent_result), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight(800))
                        SelectionContainer {
                            Text(r, Modifier.padding(top = 6.dp), color = c.ink, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight(600))
                        }
                    }
                }
            }
        }
    }
}
