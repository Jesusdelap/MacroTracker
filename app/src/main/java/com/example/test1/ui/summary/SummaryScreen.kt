package com.example.test1.ui.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test1.MacroApp
import com.example.test1.data.db.entity.FoodEntryEntity
import com.example.test1.ui.components.MacroPill
import com.example.test1.ui.components.MacroProgressRow
import com.example.test1.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SummaryScreen(onNavigateToSettings: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as MacroApp
    val vm: SummaryViewModel = viewModel {
        SummaryViewModel(app.foodRepository, app.goalRepository, app.selectedDate)
    }
    val summary by vm.summary.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(start = 20.dp, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Resumen",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Ajustes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
        ) {
            item {
                DateSelector(
                    dateDisplay  = selectedDate.toDisplayDate(),
                    canGoForward = true,
                    onPrevious   = vm::goToPreviousDay,
                    onNext       = vm::goToNextDay
                )
            }

            item {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter   = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 }
                ) {
                    CaloriesSummaryCard(
                        totalKcal = summary.totalKcal,
                        goalKcal  = summary.goal.kcal
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter   = fadeIn(tween(300, delayMillis = 60)) + slideInVertically(tween(300, delayMillis = 60)) { it / 4 }
                ) {
                    MacronutrientsCard(
                        totalProtein = summary.totalProtein,
                        goalProtein  = summary.goal.protein.toFloat(),
                        totalCarbs   = summary.totalCarbs,
                        goalCarbs    = summary.goal.carbs.toFloat(),
                        totalFat     = summary.totalFat,
                        goalFat      = summary.goal.fat.toFloat()
                    )
                }
            }

            item {
                FoodEntriesSection(
                    entries  = summary.entries,
                    onDelete = { vm.deleteEntry(it) }
                )
            }
        }
    }
}

// ── Date selector ─────────────────────────────────────────────────────────────

@Composable
private fun DateSelector(
    dateDisplay: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Día anterior")
        }
        Text(dateDisplay, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Día siguiente")
        }
    }
}

// ── Card hero: RESTANTES ──────────────────────────────────────────────────────

@Composable
private fun CaloriesSummaryCard(totalKcal: Int, goalKcal: Int) {
    val remaining = goalKcal - totalKcal
    val isOver    = remaining < 0
    val progress  = (totalKcal.toFloat() / goalKcal.coerceAtLeast(1)).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label         = "caloriesProgress"
    )

    val heroColor = if (isOver) MaterialTheme.colorScheme.error
                   else        MaterialTheme.colorScheme.primary

    val surfaceTop    = MaterialTheme.colorScheme.surfaceVariant   // SurfaceElevated
    val surfaceBottom = MaterialTheme.colorScheme.surface          // Surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapeXl)                                      // 24dp radius
            .background(Brush.verticalGradient(listOf(surfaceTop, surfaceBottom)))
            .padding(28.dp)
    ) {
        Column {
            // Línea 1: etiqueta
            Text(
                text  = "RESTANTES",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
            Spacer(Modifier.height(Spacing.xs))

            // Línea 2: número gigante + unidad alineados por baseline
            Row {
                Text(
                    text     = "${remaining.coerceAtLeast(0)}",
                    style    = MaterialTheme.typography.displayLarge.copy(
                        fontFeatureSettings = FontFeatureTnum
                    ),
                    color    = heroColor,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    text     = "kcal",
                    style    = MaterialTheme.typography.titleMedium,
                    color    = TextTertiary,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = Spacing.sm)
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            // Línea 3: barra de progreso 4dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(AppShapeXl)
                    .background(MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(heroColor)
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            // Línea 4: resumen textual
            Text(
                text  = "$totalKcal de $goalKcal kcal consumidas",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = FontFeatureTnum
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Card de macronutrientes ───────────────────────────────────────────────────

@Composable
private fun MacronutrientsCard(
    totalProtein: Float, goalProtein: Float,
    totalCarbs: Float,   goalCarbs: Float,
    totalFat: Float,     goalFat: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.large           // AppShapeLg = 16dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),   // 24dp
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text  = "MACRONUTRIENTES",
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
            MacroProgressRow(MacroType.PROTEIN, totalProtein, goalProtein)
            MacroProgressRow(MacroType.CARBS,   totalCarbs,   goalCarbs)
            MacroProgressRow(MacroType.FAT,     totalFat,     goalFat)
        }
    }
}

// ── Sección de comidas ────────────────────────────────────────────────────────

@Composable
private fun FoodEntriesSection(
    entries: List<FoodEntryEntity>,
    onDelete: (FoodEntryEntity) -> Unit
) {
    Column {
        Text(
            text     = "COMIDAS REGISTRADAS  ${entries.size}",
            style    = MaterialTheme.typography.labelMedium,
            color    = TextTertiary,
            modifier = Modifier.padding(bottom = Spacing.lg)
        )
        if (entries.isEmpty()) {
            FoodEntryEmptyState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                entries.forEach { entry ->
                    key(entry.id) {
                        FoodEntryRow(entry, onDelete = { onDelete(entry) })
                    }
                }
            }
        }
    }
}

// ── Food entry row con swipe-to-delete ───────────────────────────────────────

@Composable
private fun FoodEntryRow(entry: FoodEntryEntity, onDelete: () -> Unit) {
    val timeStr = remember(entry.timestamp) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(entry.timestamp))
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteConfirm = true
            }
            false
        },
        positionalThreshold = { it * 0.38f }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar registro?") },
            text  = { Text("Se eliminará \"${entry.name}\" de este día.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    SwipeToDismissBox(
        state                       = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val fraction = dismissState.progress
            val visible  = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(
                            alpha = if (visible) (fraction * 2f).coerceIn(0f, 1f) else 0f
                        )
                    )
                    .padding(end = Spacing.lg),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector        = Icons.Filled.Delete,
                    contentDescription = "Eliminar",
                    tint               = MaterialTheme.colorScheme.onErrorContainer,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = MaterialTheme.shapes.large   // AppShapeLg = 16dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Línea 1: hora + nombre alineados por baseline
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text     = timeStr,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = TextTertiary,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text     = entry.name,
                        style    = MaterialTheme.typography.headlineMedium,
                        color    = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .alignByBaseline()
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                // Línea 2: kcal destacadas
                Row {
                    Text(
                        text     = "${entry.kcal}",
                        style    = MaterialTheme.typography.titleMedium.copy(
                            fontFeatureSettings = FontFeatureTnum
                        ),
                        color    = MaterialTheme.macroColors.calories,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text     = " kcal",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = TextTertiary,
                        modifier = Modifier.alignByBaseline()
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                // Línea 3: proteína · carbs · grasas
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MacroPill(MacroType.PROTEIN, "${entry.protein.toInt()}g")
                    MacroPill(MacroType.CARBS,   "${entry.carbs.toInt()}g")
                    MacroPill(MacroType.FAT,      "${entry.fat.toInt()}g")
                }
            }
        }
    }
}

// ── Estado vacío ──────────────────────────────────────────────────────────────

@Composable
private fun FoodEntryEmptyState(onNavigateToChat: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Icon(
            imageVector        = Icons.Filled.RestaurantMenu,
            contentDescription = null,
            modifier           = Modifier.size(48.dp),
            tint               = TextTertiary.copy(alpha = 0.4f)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text  = "Sin comidas registradas hoy",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = "Empieza añadiendo tu primera comida desde Registro",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
        if (onNavigateToChat != null) {
            TextButton(onClick = onNavigateToChat) {
                Text(
                    text  = "Ir a Registro →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun String.toDisplayDate(): String {
    val today = LocalDate.now()
    val date  = LocalDate.parse(this)
    return when {
        date == today              -> "Hoy"
        date == today.minusDays(1) -> "Ayer"
        date == today.plusDays(1)  -> "Mañana"
        else -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
}
