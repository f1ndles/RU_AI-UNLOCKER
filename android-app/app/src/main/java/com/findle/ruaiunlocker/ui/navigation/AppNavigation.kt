package com.findle.ruaiunlocker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.findle.ruaiunlocker.R
import com.findle.ruaiunlocker.ui.screens.home.HomeScreen
import com.findle.ruaiunlocker.ui.screens.hosts.HostsScreen
import com.findle.ruaiunlocker.ui.screens.logs.LogsScreen
import com.findle.ruaiunlocker.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    data object Home : Screen("home", R.string.tab_home, Icons.Filled.Home)
    data object Hosts : Screen("hosts", R.string.tab_hosts, Icons.Filled.Description)
    data object Logs : Screen("logs", R.string.tab_logs, Icons.Filled.BugReport)
    data object Settings : Screen("settings", R.string.tab_settings, Icons.Filled.Settings)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Home, Screen.Hosts, Screen.Logs, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.titleRes)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen(viewModel = hiltViewModel()) }
            composable(Screen.Hosts.route) { HostsScreen(viewModel = hiltViewModel()) }
            composable(Screen.Logs.route) { LogsScreen(viewModel = hiltViewModel()) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel = hiltViewModel()) }
        }
    }
}
