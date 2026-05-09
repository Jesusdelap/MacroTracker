package com.example.test1.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.test1.data.api.BarcodeResult
import com.example.test1.ui.chat.ChatScreen
import com.example.test1.ui.product.ProductFormScreen
import com.example.test1.ui.recipe.RecipeScreen
import com.example.test1.ui.scanner.BarcodeScannerScreen
import com.example.test1.ui.settings.SettingsScreen
import com.example.test1.ui.splash.SplashScreen
import com.example.test1.ui.summary.SummaryScreen
import com.example.test1.ui.theme.*

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Summary : Tab("summary", "Resumen",  Icons.Filled.BarChart)
    data object Chat    : Tab("chat",    "Registro", Icons.Filled.Chat)
    data object Recipes : Tab("recipes", "Recetas",  Icons.Filled.MenuBook)
}

private val tabs      = listOf(Tab.Summary, Tab.Chat, Tab.Recipes)
private val tabRoutes = tabs.map { it.route }.toSet()

@Composable
fun AppNavigation() {
    val navController  = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route
    val haptics        = LocalHapticFeedback.current
    var pendingBarcode by remember { mutableStateOf<BarcodeResult?>(null) }

    Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                Column {
                    HorizontalDivider(
                        color     = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                    // NavigationBar proporciona el padding de insets del sistema automáticamente
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                        modifier       = Modifier.height(72.dp)
                    ) {
                        tabs.forEach { tab ->
                            val selected = backStackEntry?.destination?.hierarchy
                                ?.any { it.route == tab.route } == true

                            if (selected) {
                                // Pestaña activa: pill horizontal con icono + label
                                Box(
                                    modifier          = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment  = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(AppShapeXl)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        Icon(
                                            imageVector        = tab.icon,
                                            contentDescription = null,
                                            modifier           = Modifier.size(22.dp),
                                            tint               = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text  = tab.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            } else {
                                // Pestaña inactiva: icono + label debajo
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(AppShapeMd)
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState    = true
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector        = tab.icon,
                                        contentDescription = tab.label,
                                        modifier           = Modifier.size(22.dp),
                                        tint               = TextTertiary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text  = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = "splash",
            modifier         = Modifier.padding(innerPadding)
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
            composable("barcode_scanner") {
                BarcodeScannerScreen(
                    onProductFound = { result ->
                        pendingBarcode = result
                        navController.navigate("product_form")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("product_form") {
                ProductFormScreen(
                    barcodeResult = pendingBarcode,
                    onBack        = { navController.popBackStack() },
                    onComplete    = {
                        pendingBarcode = null
                        navController.popBackStack("product_form", inclusive = true)
                    }
                )
            }
        }
    }
}
