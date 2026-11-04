package dev.exynoslm.app.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.exynoslm.app.R
import dev.exynoslm.app.data.AppDatabase
import dev.exynoslm.app.data.AppSettings
import dev.exynoslm.app.data.Crypto
import dev.exynoslm.app.data.InferenceHub
import dev.exynoslm.app.data.SettingsRepository
import dev.exynoslm.model.BackendPreference
import dev.exynoslm.storage.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: SettingsRepository,
    private val db: AppDatabase,
    private val store: ModelStore,
    private val hub: InferenceHub,
) : ViewModel() {
    val settings = repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    fun update(f: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(f) }

    /** "Delete all local data": models, chats, traces, benchmarks, settings, encryption key. */
    fun wipe() = viewModelScope.launch(Dispatchers.IO) {
        hub.unloadAll(); store.wipe(); db.clearAllTables(); repo.clear(); Crypto.deleteKey()
        File(ctx.filesDir, "agent_workspace").deleteRecursively()
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirmWipe by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("en" to "English", "vi" to "Tiếng Việt").forEach { (tag, label) ->
                OutlinedButton({ ctx.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(tag) }) { Text(label) }
            }
        }
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("system", "light", "dark").forEach { t -> FilterChip(s.theme == t, { vm.update { it.copy(theme = t) } }, { Text(t) }) }
        }
        Text(stringResource(R.string.backend), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BackendPreference.entries.forEach { b -> FilterChip(s.backend == b, { vm.update { it.copy(backend = b) } }, { Text(b.name) }) }
        }
        Toggle(stringResource(R.string.wifi_only), s.wifiOnly) { v -> vm.update { it.copy(wifiOnly = v) } }
        Toggle(stringResource(R.string.battery_saver), s.batterySaver) { v -> vm.update { it.copy(batterySaver = v) } }
        Text("${stringResource(R.string.max_context)}: ${s.maxContext}")
        Slider(s.maxContext.toFloat(), { v -> vm.update { it.copy(maxContext = (v / 512).toInt() * 512) } }, valueRange = 512f..16384f)
        OutlinedTextField(s.systemPrompt, { v -> vm.update { it.copy(systemPrompt = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.system_prompt)) })
        Toggle(stringResource(R.string.agent_dry_run), s.agentDryRun) { v -> vm.update { it.copy(agentDryRun = v) } }
        Toggle(stringResource(R.string.agent_internet), s.agentInternet) { v -> vm.update { it.copy(agentInternet = v) } }
        OutlinedTextField(s.hfToken, { v -> vm.update { it.copy(hfToken = v.trim()) } }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.hf_token)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Text("Telemetry is local-only: metrics are shown on-device and never uploaded.", style = MaterialTheme.typography.bodySmall)
        Button({ confirmWipe = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.wipe)) }
    }
    if (confirmWipe) AlertDialog(onDismissRequest = { confirmWipe = false }, title = { Text(stringResource(R.string.wipe)) },
        confirmButton = { Button({ vm.wipe(); confirmWipe = false }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton({ confirmWipe = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, onChange)
    }
}
