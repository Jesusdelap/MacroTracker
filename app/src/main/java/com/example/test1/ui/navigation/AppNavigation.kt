package com.example.test1.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.test1.ui.chat.ChatScreen
import com.example.test1.ui.recipe.RecipeScreen
import com.example.test1.ui.settings.SettingsScreen
import com.example.test1.ui.splash.SplashScreen
import com.example.test1.ui.summary.SummaryScreen

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Summary : Tab("summary", "Resumen", Icons.Filled.BarChart)
    data object Chat    : Tab("chat",    "Registro", Icons.Filled.Chat)
    data object Recipes : Tab("recipes", "Recetas",  Icons.Filled.MenuBook)
}

private val tabs = listOf(Tab.Summary, Tab.Chat, Tab.Recipes)
private val tabRoutes = tabs.map { it.route }.toSet()

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                selected = backStackEntry?.destination?.hierarchy
                                    ?.any { it.route == tab.route } == true,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor      = MaterialTheme.colorScheme.primary,
                                    selectedIconColor   = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor   = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") {
                SplashScreen(onFinished = {
                    navController.navigate(Tab.Summary.route) {
                        popUpTo("splash") { inclusive = true }
                    }
                })
            }
            composable(Tab.Summary.route) {
                SummaryScreen(onNavigateToSettings = { navController.navigate("settings") })
            }
            composable(Tab.Chat.route) {
                ChatScreen(onNavigateToSettings = { navController.navigate("settings") })
            }
            composable(Tab.Recipes.route) { RecipeScreen() }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
