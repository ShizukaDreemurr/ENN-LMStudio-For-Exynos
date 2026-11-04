package dev.exynoslm.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.exynoslm.app.R
import dev.exynoslm.app.ui.PerfBar
import dev.exynoslm.runtime.Role

@Composable
fun ChatScreen(vm: ChatViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val active by vm.hub.active.collectAsStateWithLifecycle()
    val snap by vm.device.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(ui.messages.size, ui.streaming?.length) { if (ui.messages.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(active.model?.displayName ?: stringResource(R.string.no_model), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = vm::regenerate, enabled = !ui.generating && ui.messages.isNotEmpty()) { Icon(Icons.Default.Refresh, stringResource(R.string.regenerate)) }
            IconButton(onClick = vm::clear) { Icon(Icons.Default.DeleteSweep, stringResource(R.string.clear)) }
        }
        PerfBar(active.backend?.name, ui.tokPerSec, ui.ttftMs, snap)
        (active.fallbackNotes + ui.warnings + listOfNotNull(active.error)).forEach {
            Text("⚠ $it", Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ui.messages) { Bubble(it.role == Role.USER, it.content) }
            ui.streaming?.let { item { Bubble(false, "$it▌") } }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp).imePadding(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.message_hint)) }, maxLines = 5)
            if (ui.generating) IconButton(onClick = vm::stop) { Icon(Icons.Default.Stop, stringResource(R.string.stop)) }
            else IconButton(onClick = { vm.send(input); input = "" }, enabled = active.engine != null) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send)) }
        }
    }
}

@Composable
private fun Bubble(user: Boolean, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium, modifier = Modifier.widthIn(max = 320.dp),
        ) { Text(text, Modifier.padding(10.dp)) }
    }
}
