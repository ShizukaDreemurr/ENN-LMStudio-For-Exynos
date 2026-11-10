package dev.exynoslm.app.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.exynoslm.app.ui.components.LxButton
import dev.exynoslm.app.ui.components.LxButtonKind
import dev.exynoslm.app.ui.components.LxCard
import dev.exynoslm.app.ui.components.LxConfirmDialog
import dev.exynoslm.app.ui.components.LxNotice
import dev.exynoslm.app.ui.components.LxRollingNumber
import dev.exynoslm.app.ui.components.LxScreenHeader
import dev.exynoslm.app.ui.components.LxSectionTitle
import dev.exynoslm.app.ui.components.LxSegmented
import dev.exynoslm.app.ui.components.LxSwitchRow
import dev.exynoslm.app.ui.components.LxTextField
import dev.exynoslm.app.ui.components.LxTone
import dev.exynoslm.app.ui.theme.Lx
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
    val c = Lx.colors
    var confirmWipe by remember { mutableStateOf(false) }
    val currentLang = if (LocalConfiguration.current.locales[0].language == "vi") "vi" else "en"
    val themeLabels = mapOf(
        "system" to stringResource(R.string.theme_system),
        "light" to stringResource(R.string.theme_light),
        "dark" to stringResource(R.string.theme_dark),
    )
    val autoLabel = stringResource(R.string.settings_backend_auto)

    Column(Modifier.fillMaxSize()) {
        LxScreenHeader(title = stringResource(R.string.nav_settings), kicker = stringResource(R.string.app_kicker))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Appearance: language + theme as sliding-pill segmented controls (#039).
            LxCard(Modifier.fillMaxWidth()) {
                LxSectionTitle(stringResource(R.string.settings_appearance))
                Text(stringResource(R.string.language), Modifier.padding(top = 12.dp, bottom = 8.dp), color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight(700))
                LxSegmented(
                    options = listOf("en", "vi"),
                    selected = currentLang,
                    onSelect = { tag -> ctx.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(tag) },
                    label = { if (it == "vi") "Tiếng Việt" else "English" },
                )
                Text(stringResource(R.string.theme), Modifier.padding(top = 14.dp, bottom = 8.dp), color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight(700))
                LxSegmented(
                    options = listOf("system", "light", "dark"),
                    selected = s.theme,
                    onSelect = { t -> vm.update { it.copy(theme = t) } },
                    label = { themeLabels[it] ?: it },
                )
            }

            // Downloads and inference.
            LxCard(Modifier.fillMaxWidth()) {
                LxSectionTitle(stringResource(R.string.settings_inference))
                Text(stringResource(R.string.backend), Modifier.padding(top = 12.dp, bottom = 8.dp), color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight(700))
                LxSegmented(
                    options = BackendPreference.entries.toList(),
                    selected = s.backend,
                    onSelect = { b -> vm.update { it.copy(backend = b) } },
                    label = { if (it == BackendPreference.AUTO) autoLabel else it.name },
                )
                LxSwitchRow(stringResource(R.string.wifi_only), s.wifiOnly, { v -> vm.update { it.copy(wifiOnly = v) } }, Modifier.padding(top = 8.dp))
                LxSwitchRow(stringResource(R.string.battery_saver), s.batterySaver, { v -> vm.update { it.copy(batterySaver = v) } })
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.max_context), Modifier.weight(1f), color = c.ink, fontSize = 14.5.sp, fontWeight = FontWeight(700))
                    LxRollingNumber("${s.maxContext}", style = MaterialTheme.typography.titleSmall, color = c.accent)
                }
                Slider(
                    value = s.maxContext.toFloat(),
                    onValueChange = { v -> vm.update { it.copy(maxContext = (v / 512).toInt() * 512) } },
                    valueRange = 512f..16384f,
                    colors = SliderDefaults.colors(
                        thumbColor = c.accent,
                        activeTrackColor = c.accent,
                        inactiveTrackColor = c.surface3,
                    ),
                )
                LxTextField(
                    s.systemPrompt, { v -> vm.update { it.copy(systemPrompt = v) } },
                    stringResource(R.string.system_prompt), Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, maxLines = 5,
                )
            }

            // Agent.
            LxCard(Modifier.fillMaxWidth()) {
                LxSectionTitle(stringResource(R.string.settings_agent))
                LxSwitchRow(stringResource(R.string.agent_dry_run), s.agentDryRun, { v -> vm.update { it.copy(agentDryRun = v) } }, Modifier.padding(top = 6.dp))
                LxSwitchRow(stringResource(R.string.agent_internet), s.agentInternet, { v -> vm.update { it.copy(agentInternet = v) } })
            }

            // Privacy and data.
            LxCard(Modifier.fillMaxWidth()) {
                LxSectionTitle(stringResource(R.string.settings_privacy))
                LxTextField(
                    s.hfToken, { v -> vm.update { it.copy(hfToken = v.trim()) } },
                    stringResource(R.string.hf_token), Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                )
                LxNotice(stringResource(R.string.telemetry_note), Modifier.fillMaxWidth().padding(top = 12.dp), LxTone.Accent)
                LxButton(
                    stringResource(R.string.wipe), { confirmWipe = true }, Modifier.fillMaxWidth().padding(top = 14.dp),
                    kind = LxButtonKind.Danger, icon = Icons.Default.DeleteForever,
                )
            }
        }
    }
    if (confirmWipe) {
        LxConfirmDialog(
            title = stringResource(R.string.wipe),
            message = stringResource(R.string.wipe_message),
            confirmLabel = stringResource(R.string.delete),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = { vm.wipe(); confirmWipe = false },
            onDismiss = { confirmWipe = false },
            requireText = stringResource(R.string.wipe_confirm_word),
        )
    }
}
