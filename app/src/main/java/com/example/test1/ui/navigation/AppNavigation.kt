package com.example.test1.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.test1.R
import com.example.test1.data.api.BarcodeResult
import com.example.test1.ui.chat.ChatScreen
import com.example.test1.ui.product.ProductFormScreen
import com.example.test1.ui.recipe.RecipeScreen
import com.example.test1.ui.scanner.BarcodeScannerScreen
import com.example.test1.ui.settings.SettingsScreen
import com.example.test1.ui.splash.SplashScreen
import com.example.test1.ui.summary.SummaryScreen
import com.example.test1.ui.theme.*
import kotlinx.coroutines.launch

private sealed class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Summary : Tab("summary", R.string.summary_title, Icons.Filled.BarChart)
    data object Chat    : Tab("chat",    R.string.chat_title,    Icons.AutoMirrored.Filled.Chat)
    data object Foods   : Tab("recipes", R.string.nav_foods,     Icons.Filled.Restaurant)
}

private val tabs      = listOf(Tab.Summary, Tab.Chat, Tab.Foods)
private val tabRoutes = tabs.map { it.route }.toSet()
private val languageOptions = listOf("es" to R.string.drawer_lang_es, "en" to R.string.drawer_lang_en)

@Composable
fun AppNavigation(
    isDark: Boolean = true,
    currentLang: String = "",
    onToggleTheme: () -> Unit = {},
    onToggleLanguage: (String) -> Unit = {}
) {
    val navController  = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route
    val haptics        = LocalHapticFeedback.current
    val drawerState    = rememberDrawerState(DrawerValue.Closed)
    val scope          = rememberCoroutineScope()
    var pendingBarcode by remember { mutableStateOf<BarcodeResult?>(null) }

    ModalNavigationDrawer(
        drawerState     = drawerState,
        gesturesEnabled = currentRoute == Tab.Summary.route,
        drawerContent   = {
            AppDrawerContent(
                isDark               = isDark,
                currentLang          = currentLang,
                onToggleTheme        = onToggleTheme,
                onToggleLanguage     = onToggleLanguage,
                onNavigateToSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigate("settings")
                }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                if (currentRoute in tabRoutes) {
                    Column {
                        HorizontalDivider(
                            color     = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.background,
                            tonalElevation = 0.dp,
                            modifier       = Modifier.height(72.dp)
                        ) {
                            tabs.forEach { tab ->
                                val selected = backStackEntry?.destination?.hierarchy
                                    ?.any { it.route == tab.route } == true
                                val label = stringResource(tab.labelRes)

                                if (selected) {
                                    Box(
                                        modifier         = Modifier.weight(1f).fillMaxHeight(),
                                        contentAlignment = Alignment.Center
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
                                                text  = label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                } else {
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
                                            contentDescription = label,
                                            modifier           = Modifier.size(22.dp),
                                            tint               = TextTertiary
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text  = label,
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
                    SummaryScreen(onOpenDrawer = { scope.launch { drawerState.open() } })
                }
                composable(Tab.Chat.route) {
                    ChatScreen(onNavigateToSettings = { navController.navigate("settings") })
                }
                composable(Tab.Foods.route) {
                    RecipeScreen(onScanBarcode = { navController.navigate("barcode_scanner") })
                }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDrawerContent(
    isDark: Boolean,
    currentLang: String,
    onToggleTheme: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
        Spacer(Modifier.height(Spacing.xl))

        // ── Header: app brand ─────────────────────────────────────────────────
        Row(
            modifier          = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .clip(AppShapeMd)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter     = painterResource(R.drawable.ic_app_mark),
                    contentDescription = null,
                    modifier    = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                text  = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider(
            modifier  = Modifier.padding(vertical = Spacing.md),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )

        // ── Goals ─────────────────────────────────────────────────────────────
        NavigationDrawerItem(
            icon    = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(20.dp)) },
            label   = { Text(stringResource(R.string.drawer_goals), style = MaterialTheme.typography.bodyMedium) },
            selected = false,
            onClick  = onNavigateToSettings,
            modifier = Modifier.padding(horizontal = Spacing.sm)
        )

        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )

        // ── Appearance ────────────────────────────────────────────────────────
        Text(
            text     = stringResource(R.string.drawer_appearance),
            style    = MaterialTheme.typography.labelSmall,
            color    = TextTertiary,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs)
        )

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text     = stringResource(if (isDark) R.string.drawer_dark_mode else R.string.drawer_light_mode),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked         = isDark,
                onCheckedChange = { onToggleTheme() }
            )
        }

        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )

        // ── Language ──────────────────────────────────────────────────────────
        Text(
            text     = stringResource(R.string.drawer_language),
            style    = MaterialTheme.typography.labelSmall,
            color    = TextTertiary,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs)
        )

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Filled.Language,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Spacing.md))
            LanguageMenu(
                currentLang      = currentLang.ifBlank { "en" },
                onToggleLanguage = onToggleLanguage,
                modifier         = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun LanguageMenu(
    currentLang: String,
    onToggleLanguage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = languageOptions.firstOrNull { it.first == currentLang } ?: languageOptions.last()

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapeMd)
                .clickable { expanded = true },
            shape = AppShapeMd,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(selected.second),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languageOptions.forEach { (code, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    leadingIcon = {
                        if (code == selected.first) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        if (code != selected.first) onToggleLanguage(code)
                    }
                )
            }
        }
    }
}
