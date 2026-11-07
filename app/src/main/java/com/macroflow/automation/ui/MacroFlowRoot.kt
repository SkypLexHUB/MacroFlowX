package com.macroflow.automation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.macroflow.automation.R
import com.macroflow.automation.engine.PlayerError
import com.macroflow.automation.ui.editor.EditorScreen
import com.macroflow.automation.ui.home.HomeScreen
import com.macroflow.automation.ui.macros.MacrosScreen
import com.macroflow.automation.ui.navigation.Destination
import com.macroflow.automation.ui.navigation.Routes
import com.macroflow.automation.ui.player.PlayerBar
import com.macroflow.automation.ui.player.PlayerViewModel
import com.macroflow.automation.ui.settings.SettingsScreen

@Composable
fun MacroFlowRoot(playerViewModel: PlayerViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showTabs = Destination.values().any { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }

    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessages = PlayerError.values().associateWith { stringResource(errorRes(it)) }

    LaunchedEffect(playerState.error) {
        val error = playerState.error
        if (error != null) {
            snackbarHostState.showSnackbar(errorMessages.getValue(error))
            playerViewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                PlayerBar(
                    state = playerState,
                    onPause = playerViewModel::pause,
                    onResume = playerViewModel::resume,
                    onStop = playerViewModel::stop,
                )
                if (showTabs) {
                    NavigationBar {
                        Destination.values().forEach { destination ->
                            NavigationBarItem(
                                selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                                onClick = { navController.navigateToTab(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Home.route) {
                HomeScreen(onManageMacros = { navController.navigateToTab(Destination.Macros.route) })
            }
            composable(Destination.Macros.route) {
                MacrosScreen(onOpenEditor = { id -> navController.navigate(Routes.editor(id)) })
            }
            composable(Destination.Settings.route) { SettingsScreen() }
            composable(
                route = Routes.EDITOR_PATTERN,
                arguments = listOf(navArgument("macroId") { type = NavType.LongType }),
            ) {
                EditorScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun errorRes(error: PlayerError): Int = when (error) {
    PlayerError.NO_ACCESSIBILITY -> R.string.err_no_acc
    PlayerError.MACRO_NOT_FOUND -> R.string.err_not_found
    PlayerError.EMPTY_MACRO -> R.string.err_empty
    PlayerError.INVALID_ACTION -> R.string.err_invalid
    PlayerError.GESTURE_FAILED -> R.string.err_gesture
    PlayerError.TIME_LIMIT -> R.string.err_time_limit
    PlayerError.ALREADY_RUNNING -> R.string.err_busy
}

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
