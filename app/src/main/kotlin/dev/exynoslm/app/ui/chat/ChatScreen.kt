package dev.exynoslm.app.ui.chat

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.exynoslm.app.R
import dev.exynoslm.app.ui.PerfBar
import dev.exynoslm.app.ui.components.LxAssistantMessage
import dev.exynoslm.app.ui.components.LxComposer
import dev.exynoslm.app.ui.components.LxEmptyState
import dev.exynoslm.app.ui.components.LxIconButton
import dev.exynoslm.app.ui.components.LxMessageText
import dev.exynoslm.app.ui.components.LxNotice
import dev.exynoslm.app.ui.components.LxScreenHeader
import dev.exynoslm.app.ui.components.LxStatusPill
import dev.exynoslm.app.ui.components.LxTone
import dev.exynoslm.app.ui.components.LxTypingDots
import dev.exynoslm.app.ui.components.LxUserBubble
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.runtime.Role

@Composable
fun ChatScreen(onOpenModels: () -> Unit = {}, vm: ChatViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val active by vm.hub.active.collectAsStateWithLifecycle()
    val snap by vm.device.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val c = Lx.colors
    LaunchedEffect(ui.messages.size, ui.streaming?.length) {
        if (ui.messages.isNotEmpty() || ui.streaming != null) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    val notes = active.fallbackNotes
    val warnings = ui.warnings
    val loadError = active.error

    Column(Modifier.fillMaxSize()) {
        LxScreenHeader(
            title = active.model?.displayName ?: stringResource(R.string.nav_chat),
            kicker = stringResource(R.string.app_kicker),
            trailing = {
                if (active.engine != null) LxStatusPill(active.backend?.name ?: "", c.mint, blink = ui.generating)
                LxIconButton(Icons.Default.Refresh, stringResource(R.string.regenerate), vm::regenerate, enabled = !ui.generating && ui.messages.isNotEmpty(), size = 42.dp)
                LxIconButton(Icons.Default.DeleteSweep, stringResource(R.string.clear), vm::clear, size = 42.dp)
            },
        )
        PerfBar(active.backend?.name, ui.tokPerSec, ui.ttftMs, snap)

        val empty = ui.messages.isEmpty() && ui.streaming == null
        if (empty && active.engine == null) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                LxEmptyState(
                    title = stringResource(R.string.chat_empty_title),
                    message = stringResource(R.string.chat_empty_sub),
                    actionLabel = stringResource(R.string.open_models),
                    onAction = onOpenModels,
                )
                notes.forEach { LxNotice(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), LxTone.Accent, stringResource(R.string.fallback_title)) }
                loadError?.let { LxNotice(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), LxTone.Danger, stringResource(R.string.models_load_failed)) }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Fallback reasons, warnings and load errors stay visible at the top of the conversation.
                itemsIndexed(notes) { _, n -> LxNotice(n, Modifier.fillMaxWidth(), LxTone.Accent, stringResource(R.string.fallback_title)) }
                itemsIndexed(warnings) { _, w -> LxNotice(w, Modifier.fillMaxWidth(), LxTone.Warn) }
                loadError?.let { e -> item { LxNotice(e, Modifier.fillMaxWidth(), LxTone.Danger, stringResource(R.string.models_load_failed)) } }
                itemsIndexed(ui.messages) { i, m ->
                    val newest = i == ui.messages.lastIndex && ui.streaming == null
                    if (m.role == Role.USER) LxUserBubble(m.content, animate = newest)
                    else LxAssistantMessage(animate = newest) { LxMessageText(m.content) }
                }
                ui.streaming?.let { s ->
                    item {
                        LxAssistantMessage(glow = true, animate = true) {
                            if (s.isEmpty()) LxTypingDots() else LxMessageText(s, caret = true)
                        }
                    }
                }
            }
        }

        LxComposer(
            value = input,
            onValueChange = { input = it },
            placeholder = stringResource(R.string.message_hint),
            generating = ui.generating,
            canSend = active.engine != null && input.isNotBlank(),
            onSend = { vm.send(input); input = "" },
            onStop = vm::stop,
            sendLabel = stringResource(R.string.send),
            stopLabel = stringResource(R.string.stop),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).imePadding(),
        )
    }
}
