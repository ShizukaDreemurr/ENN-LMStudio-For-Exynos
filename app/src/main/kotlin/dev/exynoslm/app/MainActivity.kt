package dev.exynoslm.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
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
import dev.exynoslm.app.ui.components.LxBottomBar
import dev.exynoslm.app.ui.components.LxTab
import dev.exynoslm.app.ui.hub.HubScreen
import dev.exynoslm.app.ui.models.MyModelsScreen
import dev.exynoslm.app.ui.settings.SettingsScreen
import dev.exynoslm.app.ui.settings.SettingsViewModel
import dev.exynoslm.app.ui.theme.AppTheme
import dev.exynoslm.app.ui.theme.LocalReducedMotion
import dev.exynoslm.app.ui.theme.LxMotion
import dev.exynoslm.app.ui.theme.lxTween

/** [label] is the full name (accessibility, headers); [short] is what the bottom bar shows under the selected tab. */
enum class Dest(val route: String, @StringRes val label: Int, @StringRes val short: Int, val icon: ImageVector) {
    Chat("chat", R.string.nav_chat, R.string.nav_chat_short, Icons.Default.Chat),
    Hub("hub", R.string.nav_hub, R.string.nav_hub_short, Icons.Default.Search),
    Models("models", R.string.nav_models, R.string.nav_models_short, Icons.Default.Storage),
    Agent("agent", R.string.nav_agent, R.string.nav_agent_short, Icons.Default.SmartToy),
    Bench("bench", R.string.nav_bench, R.string.nav_bench_short, Icons.Default.Speed),
    Settings("settings", R.string.nav_settings, R.string.nav_settings_short, Icons.Default.Settings),
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
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    // The bar slides away while the keyboard is up (the chat composer handles the IME inset itself).
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val tabs = Dest.entries.map { LxTab(stringResource(it.short), it.icon) }
    val selected = Dest.entries.indexOfFirst { it.route == current?.destination?.route }.coerceAtLeast(0)
    fun go(d: Dest) = nav.navigate(d.route) { popUpTo(Dest.Chat.route); launchSingleTop = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = !imeVisible,
                enter = slideInVertically(lxTween<IntOffset>(reduced, LxMotion.BaseMs, LxMotion.Ease)) { it } + fadeIn(lxTween(reduced, LxMotion.BaseMs, LxMotion.Ease)),
                exit = slideOutVertically(lxTween<IntOffset>(reduced, 200, LxMotion.Ease)) { it } + fadeOut(lxTween(reduced, 150, LxMotion.Ease)),
            ) {
                LxBottomBar(tabs, selected, onSelect = { go(Dest.entries[it]) })
            }
        },
    ) { pad ->
        NavHost(
            nav, Dest.Chat.route, Modifier.padding(pad),
            enterTransition = { fadeIn(lxTween(reduced, 300, LxMotion.Ease)) + slideInVertically(lxTween<IntOffset>(reduced, 400, LxMotion.Ease)) { it / 28 } },
            exitTransition = { fadeOut(lxTween(reduced, 150, LxMotion.Ease)) },
            popEnterTransition = { fadeIn(lxTween(reduced, 300, LxMotion.Ease)) + slideInVertically(lxTween<IntOffset>(reduced, 400, LxMotion.Ease)) { it / 28 } },
            popExitTransition = { fadeOut(lxTween(reduced, 150, LxMotion.Ease)) },
        ) {
            composable(Dest.Chat.route) { ChatScreen(onOpenModels = { go(Dest.Models) }) }
            composable(Dest.Hub.route) { HubScreen() }
            composable(Dest.Models.route) { MyModelsScreen(onLoaded = { nav.navigate(Dest.Chat.route) }) }
            composable(Dest.Agent.route) { AgentScreen() }
            composable(Dest.Bench.route) { BenchScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
