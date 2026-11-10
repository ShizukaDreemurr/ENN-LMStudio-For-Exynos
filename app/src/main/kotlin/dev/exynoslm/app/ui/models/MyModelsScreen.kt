package dev.exynoslm.app.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.exynoslm.app.R
import dev.exynoslm.app.data.InferenceHub
import dev.exynoslm.app.ui.gb
import dev.exynoslm.app.ui.components.LxBadge
import dev.exynoslm.app.ui.components.LxButton
import dev.exynoslm.app.ui.components.LxButtonKind
import dev.exynoslm.app.ui.components.LxCard
import dev.exynoslm.app.ui.components.LxConfirmDialog
import dev.exynoslm.app.ui.components.LxEmptyState
import dev.exynoslm.app.ui.components.LxFlow
import dev.exynoslm.app.ui.components.LxIconButton
import dev.exynoslm.app.ui.components.LxNotice
import dev.exynoslm.app.ui.components.LxProgressBar
import dev.exynoslm.app.ui.components.LxScreenHeader
import dev.exynoslm.app.ui.components.LxStatTile
import dev.exynoslm.app.ui.components.LxStatusPill
import dev.exynoslm.app.ui.components.LxToastHost
import dev.exynoslm.app.ui.components.LxTone
import dev.exynoslm.app.ui.mb
import dev.exynoslm.app.ui.theme.Lx
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
    fun import(uris: List<Uri>) = viewModelScope.launch {
        uris.forEach { u -> runCatching { store.import(u) }.onFailure { status.value = it.message } }
        status.value = "Imported ${uris.size} file(s). NPU models need <name>.e9965.tflite + <name>.e9965.io.json."
        refresh()
    }
    fun verify(m: LoadedModel) = viewModelScope.launch {
        status.value = "Hashing ${m.displayName}…"
        val sha = withContext(Dispatchers.IO) { ModelDownloader.sha256(File(m.localPath)) }
        status.value = "sha256 ${sha.take(16)}… (${File(m.localPath).length().mb()})"
    }
}

@Composable
fun MyModelsScreen(onLoaded: () -> Unit, onOpenHub: () -> Unit = {}, vm: MyModelsViewModel = hiltViewModel()) {
    val models by vm.models.collectAsStateWithLifecycle()
    val active by vm.hub.active.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.import(it) }
    var pendingDelete by remember { mutableStateOf<LoadedModel?>(null) }
    val c = Lx.colors
    LaunchedEffect(Unit) { vm.refresh() }

    pendingDelete?.let { m ->
        LxConfirmDialog(
            title = stringResource(R.string.models_delete_title),
            message = stringResource(R.string.models_delete_msg, m.displayName),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = { vm.delete(m); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LxScreenHeader(title = stringResource(R.string.nav_models), kicker = stringResource(R.string.app_kicker))
            // Storage header: used / free on the left, import on the right.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LxStatTile(stringResource(R.string.models_storage), vm.usage, Modifier.weight(1f))
                LxButton(stringResource(R.string.import_file), { picker.launch(arrayOf("*/*")) }, icon = Icons.Default.Add, height = 52.dp)
            }
            if (active.loading) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.models_loading), color = c.ink2, fontSize = 12.5.sp, fontWeight = FontWeight(750))
                    LxProgressBar(null)
                }
            }
            if (models.isEmpty() && !active.loading && active.error == null) {
                LxEmptyState(
                    stringResource(R.string.models_empty_title), stringResource(R.string.models_empty_sub),
                    Modifier.weight(1f), actionLabel = stringResource(R.string.nav_hub), onAction = onOpenHub,
                )
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    active.error?.let { e -> item(key = "error") { LxNotice(e, Modifier.fillMaxWidth(), LxTone.Danger, stringResource(R.string.models_load_failed)) } }
                    items(active.fallbackNotes) { n -> LxNotice(n, Modifier.fillMaxWidth(), LxTone.Accent, stringResource(R.string.models_fallback)) }
                    items(models, key = { it.id }) { m ->
                        val isActive = active.model?.id == m.id && active.engine != null
                        val date = remember(m.localPath) { DateFormat.getDateInstance().format(Date(File(m.localPath).lastModified())) }
                        LxCard(Modifier.fillMaxWidth(), borderColor = if (isActive) c.accent else c.line) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    m.displayName, Modifier.weight(1f), color = c.ink, fontSize = 15.sp, lineHeight = 20.sp,
                                    fontWeight = FontWeight(800), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                if (isActive) LxStatusPill(stringResource(R.string.models_loaded, active.backend?.name ?: ""), c.mint)
                            }
                            LxFlow(Modifier.padding(top = 8.dp), gap = 5.dp) {
                                LxBadge(m.format.name, tone = LxTone.Accent)
                                LxBadge(m.quantization.toString(), tone = LxTone.Neutral)
                                LxBadge(m.sizeBytes.mb(), tone = LxTone.Neutral)
                                LxBadge(m.sourceRepo ?: stringResource(R.string.models_local), tone = LxTone.Neutral)
                            }
                            Text(date, Modifier.padding(top = 6.dp), color = c.ink3, fontSize = 11.5.sp, fontWeight = FontWeight(650))
                            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LxButton(
                                    stringResource(R.string.load), { vm.load(m, onLoaded) }, Modifier.weight(1f),
                                    icon = Icons.Default.PlayArrow, enabled = !active.loading, height = 44.dp,
                                )
                                LxButton(
                                    stringResource(R.string.verify), { vm.verify(m) }, Modifier.weight(1f),
                                    kind = LxButtonKind.Secondary, icon = Icons.Default.VerifiedUser, height = 44.dp,
                                )
                                LxIconButton(
                                    Icons.Default.Delete, stringResource(R.string.delete), { pendingDelete = m },
                                    tint = c.danger, size = 44.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
        LxToastHost(status, onDismiss = { vm.status.value = null }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
