package com.yourname.nearness.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yourname.nearness.ui.archive.ArchiveScreen
import com.yourname.nearness.ui.signals.SignalsScreen
import com.yourname.nearness.ui.today.TodayScreen
import com.yourname.nearness.ui.whiteboard.WhiteboardScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MainScreen(userId: String, partnerId: String) {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab(Routes.TODAY, "Today", Icons.Outlined.WbSunny),
        Tab(Routes.WHITEBOARD, "Whiteboard", Icons.Outlined.Dashboard),
        Tab(Routes.SIGNALS, "Signals", Icons.Outlined.Favorite),
    )

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination?.route
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(Routes.TODAY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TODAY) { TodayScreen(userId = userId, partnerId = partnerId) }
            composable(Routes.WHITEBOARD) {
                WhiteboardScreen(
                    userId = userId,
                    partnerId = partnerId,
                    onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                )
            }
            composable(Routes.SIGNALS) {
                SignalsScreen(userId = userId, partnerId = partnerId)
            }
            composable(Routes.ARCHIVE) {
                ArchiveScreen(
                    userId = userId,
                    partnerId = partnerId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
