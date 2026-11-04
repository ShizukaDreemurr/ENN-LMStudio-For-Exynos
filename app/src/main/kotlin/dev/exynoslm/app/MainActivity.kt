package dev.exynoslm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.exynoslm.app.ui.agent.AgentScreen
import dev.exynoslm.app.ui.bench.BenchScreen
import dev.exynoslm.app.ui.chat.ChatScreen
import dev.exynoslm.app.ui.hub.HubScreen
import dev.exynoslm.app.ui.models.MyModelsScreen
import dev.exynoslm.app.ui.settings.SettingsScreen
import dev.exynoslm.app.ui.settings.SettingsViewModel
import dev.exynoslm.app.ui.theme.AppTheme

enum class Dest(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Chat("chat", R.string.nav_chat, Icons.Default.Chat),
    Hub("hub", R.string.nav_hub, Icons.Default.Search),
    Models("models", R.string.nav_models, Icons.Default.Storage),
    Agent("agent", R.string.nav_agent, Icons.Default.SmartToy),
    Bench("bench", R.string.nav_bench, Icons.Default.Speed),
    Settings("settings", R.string.nav_settings, Icons.Default.Settings),
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val s by settingsVm.settings.collectAsStateWithLifecycle()
            AppTheme(s.theme) { Root() }
        }
    }
}

@Composable
private fun Root() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    Scaffold(bottomBar = {
        NavigationBar {
            Dest.entries.forEach { d ->
                NavigationBarItem(
                    selected = current?.destination?.route == d.route,
                    onClick = { nav.navigate(d.route) { popUpTo(Dest.Chat.route); launchSingleTop = true } },
                    icon = { Icon(d.icon, null) },
                    label = { Text(stringResource(d.label), maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }) { pad ->
        NavHost(nav, Dest.Chat.route, Modifier.padding(pad)) {
            composable(Dest.Chat.route) { ChatScreen() }
            composable(Dest.Hub.route) { HubScreen() }
            composable(Dest.Models.route) { MyModelsScreen(onLoaded = { nav.navigate(Dest.Chat.route) }) }
            composable(Dest.Agent.route) { AgentScreen() }
            composable(Dest.Bench.route) { BenchScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
