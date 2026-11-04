package dev.exynoslm.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.exynoslm.model.BackendPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val backend: BackendPreference = BackendPreference.AUTO,
    val theme: String = "system",            // system | light | dark
    val wifiOnly: Boolean = true,
    val batterySaver: Boolean = false,
    val maxContext: Int = 4096,
    val systemPrompt: String = "You are a helpful assistant running fully on-device.",
    val agentInternet: Boolean = false,
    val agentDryRun: Boolean = true,
    val hfToken: String = "",
) {
    /** Battery saver: smaller context, fewer agent steps, prefer NPU. */
    val effectiveContext get() = if (batterySaver) minOf(maxContext, 2048) else maxContext
    val agentMaxSteps get() = if (batterySaver) 4 else 8
}

class SettingsRepository(private val context: Context) {
    private object K {
        val backend = stringPreferencesKey("backend"); val theme = stringPreferencesKey("theme")
        val wifi = booleanPreferencesKey("wifi_only"); val saver = booleanPreferencesKey("battery_saver")
        val ctx = intPreferencesKey("max_ctx"); val sys = stringPreferencesKey("system_prompt")
        val net = booleanPreferencesKey("agent_net"); val dry = booleanPreferencesKey("agent_dry")
        val token = stringPreferencesKey("hf_token")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }
    suspend fun current() = flow.first()

    private fun Preferences.toSettings() = AppSettings(
        backend = this[K.backend]?.let { runCatching { BackendPreference.valueOf(it) }.getOrNull() } ?: BackendPreference.AUTO,
        theme = this[K.theme] ?: "system", wifiOnly = this[K.wifi] ?: true, batterySaver = this[K.saver] ?: false,
        maxContext = this[K.ctx] ?: 4096, systemPrompt = this[K.sys] ?: AppSettings().systemPrompt,
        agentInternet = this[K.net] ?: false, agentDryRun = this[K.dry] ?: true, hfToken = this[K.token] ?: "",
    )

    suspend fun update(f: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val s = f(p.toSettings())
            p[K.backend] = s.backend.name; p[K.theme] = s.theme; p[K.wifi] = s.wifiOnly; p[K.saver] = s.batterySaver
            p[K.ctx] = s.maxContext; p[K.sys] = s.systemPrompt; p[K.net] = s.agentInternet; p[K.dry] = s.agentDryRun; p[K.token] = s.hfToken
        }
    }

    suspend fun clear() { context.dataStore.edit { it.clear() } }
}
