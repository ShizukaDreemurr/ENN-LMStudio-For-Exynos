package dev.exynoslm.app.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.exynoslm.app.R
import dev.exynoslm.app.data.InferenceHub
import dev.exynoslm.app.ui.gb
import dev.exynoslm.app.ui.mb
import dev.exynoslm.hf.ModelDownloader
import dev.exynoslm.model.LoadedModel
import dev.exynoslm.storage.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class MyModelsViewModel @Inject constructor(private val store: ModelStore, val hub: InferenceHub) : ViewModel() {
    private val _models = MutableStateFlow<List<LoadedModel>>(emptyList())
    val models = _models.asStateFlow()
    val status = MutableStateFlow<String?>(null)
    val usage get() = "Used ${store.usedBytes().gb()} · free ${store.freeBytes().gb()}"

    init { refresh() }
    fun refresh() = viewModelScope.launch { _models.value = store.list() }
    fun load(m: LoadedModel, then: () -> Unit) = viewModelScope.launch { hub.load(m); if (hub.active.value.engine != null) then() }
    fun delete(m: LoadedModel) = viewModelScope.launch {
        if (hub.active.value.model?.id == m.id) hub.unloadAll()
        store.delete(m); refresh()
    }
    fun import(uri: Uri) = viewModelScope.launch { runCatching { store.import(uri) }.onFailure { status.value = it.message }; refresh() }
    fun verify(m: LoadedModel) = viewModelScope.launch {
        status.value = "Hashing ${m.displayName}…"
        val sha = withContext(Dispatchers.IO) { ModelDownloader.sha256(File(m.localPath)) }
        status.value = "sha256 ${sha.take(16)}… (${File(m.localPath).length().mb()})"
    }
}

@Composable
fun MyModelsScreen(onLoaded: () -> Unit, vm: MyModelsViewModel = hiltViewModel()) {
    val models by vm.models.collectAsStateWithLifecycle()
    val active by vm.hub.active.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::import) }
    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row { Text(vm.usage, Modifier.weight(1f)); Button({ picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_file)) } }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (active.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        active.error?.let { Text("Load failed: $it", color = MaterialTheme.colorScheme.error) }
        active.fallbackNotes.forEach { Text("↪ $it", style = MaterialTheme.typography.bodySmall) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(models, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(m.displayName + if (active.model?.id == m.id && active.engine != null) "  ● ${active.backend}" else "", style = MaterialTheme.typography.titleSmall)
                        Text("${m.format} · ${m.quantization} · ${m.sizeBytes.mb()} · ${m.sourceRepo ?: "local"} · " +
                            DateFormat.getDateInstance().format(Date(File(m.localPath).lastModified())), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button({ vm.load(m, onLoaded) }, enabled = !active.loading) { Text(stringResource(R.string.load)) }
                            OutlinedButton({ vm.verify(m) }) { Text(stringResource(R.string.verify)) }
                            OutlinedButton({ vm.delete(m) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }
    }
}
