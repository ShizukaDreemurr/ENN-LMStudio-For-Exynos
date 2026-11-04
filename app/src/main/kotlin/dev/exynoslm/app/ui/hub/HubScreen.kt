package dev.exynoslm.app.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import dev.exynoslm.app.R
import dev.exynoslm.app.download.DownloadWorker
import dev.exynoslm.app.ui.mb
import dev.exynoslm.model.LicenseRisk
import dev.exynoslm.model.ModelCard
import dev.exynoslm.model.ModelFormat

@Composable
fun HubScreen(vm: HubViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    ui.detail?.let { Detail(it, vm, downloads); return }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        OutlinedTextField(ui.query, vm::setQuery, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.search() }))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TaskFilter.entries.forEach { FilterChip(ui.task == it, { vm.setTask(it) }, { Text(it.label) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LibFilter.entries.forEach { FilterChip(ui.lib == it, { vm.setLib(it) }, { Text(it.name.lowercase()) }) }
        }
        if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ui.results, key = { it.repoId }) { m ->
                Card(Modifier.fillMaxWidth().clickable { vm.open(m) }) {
                    Column(Modifier.padding(10.dp)) {
                        Text(m.repoId, style = MaterialTheme.typography.titleSmall)
                        Text("${m.pipelineTag ?: "?"} · ${m.libraryName ?: "?"} · ⬇${m.downloads} · ♥${m.likes} · ${m.license ?: "no license"}",
                            style = MaterialTheme.typography.bodySmall)
                        if (m.task == dev.exynoslm.model.ModelTask.IMAGE_GENERATION) AssistChip({}, { Text("Heavy / may not suit mobile") })
                    }
                }
            }
            if (ui.next != null) item { TextButton(onClick = vm::loadMore) { Text("Load more") } }
        }
    }
}

@Composable
private fun Detail(card: ModelCard, vm: HubViewModel, downloads: Map<String, WorkInfo>) {
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        TextButton(onClick = vm::closeDetail) { Text("←") }
        Text(card.repoId, style = MaterialTheme.typography.titleMedium)
        Text("author ${card.author} · license ${card.license} · updated ${card.lastModified?.take(10)} · ⬇${card.downloads}")
        if (card.gated) Text("Gated model: accept the license on huggingface.co and set a token in Settings.", color = MaterialTheme.colorScheme.error)
        if (vm.licenseRisk(card) != LicenseRisk.PERMISSIVE) Text("⚠ " + stringResource(R.string.license_warning), color = MaterialTheme.colorScheme.tertiary)
        Text("Tags: " + card.tags.take(12).joinToString(), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        // Only model weight files are offered — we never pull the whole repo.
        val files = card.files.filter { it.format != ModelFormat.UNKNOWN }
        if (files.isEmpty()) Text("No runnable model files (.task/.litertlm/.tflite/.gguf/.onnx) in this repo.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(files, key = { it.path }) { f ->
                val wi = downloads[DownloadWorker.workName(card.repoId, f.path)]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(f.path, style = MaterialTheme.typography.bodyMedium)
                        Text("${f.sizeBytes?.mb() ?: "? MB"} · ${f.format} · ${f.quantization}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            vm.badges(card, f).forEach { AssistChip({}, { Text(it.name.replace('_', '-')) }) }
                        }
                        when (wi?.state) {
                            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                                val pct = wi.progress.getInt(DownloadWorker.K_PCT, -1)
                                if (pct >= 0) LinearProgressIndicator({ pct / 100f }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                                Row {
                                    TextButton(onClick = { vm.cancel(card, f) }) { Text(stringResource(R.string.pause) + " / " + stringResource(R.string.cancel)) }
                                    if (wi.state == WorkInfo.State.ENQUEUED) Text("Waiting for network (Wi-Fi only?)", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            WorkInfo.State.SUCCEEDED -> Text("✓ Downloaded")
                            WorkInfo.State.FAILED -> { Text("Failed: ${wi.outputData.getString(DownloadWorker.K_ERROR)}"); Button({ vm.download(card, f) }) { Text("Retry") } }
                            else -> Button({ vm.download(card, f) }) { Text(stringResource(R.string.download)) }
                        }
                    }
                }
            }
        }
    }
}
