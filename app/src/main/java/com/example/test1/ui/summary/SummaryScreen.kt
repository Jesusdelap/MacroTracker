package com.example.test1.ui.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import android.content.Context
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test1.MacroApp
import com.example.test1.R
import com.example.test1.data.db.entity.FoodEntryEntity
import com.example.test1.ui.components.MacroItemCard
import com.example.test1.ui.components.MacroPill
import com.example.test1.ui.components.MacroProgressRow
import com.example.test1.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(onOpenDrawer: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as MacroApp
    val vm: SummaryViewModel = viewModel {
        SummaryViewModel(app.foodRepository, app.goalRepository, app.selectedDate)
    }
    val summary      by vm.summary.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    val entryDeletedFmt   = stringResource(R.string.summary_entry_deleted)
    val undoLabel         = stringResource(R.string.action_undo)
    val context           = LocalContext.current
    var showDatePicker    by remember { mutableStateOf(false) }
    var totalDrag         by remember { mutableFloatStateOf(0f) }

    val dateDisplay = selectedDate.toDisplayDate(context)

    if (showDatePicker) {
        val initialMillis = remember(selectedDate) {
            LocalDate.parse(selectedDate).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        vm.goToDate(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        .height(52.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd    = { totalDrag = 0f },
                                onDragCancel = { totalDrag = 0f },
                                onHorizontalDrag = { _, amount ->
                                    totalDrag += amount
                                    if (totalDrag > 80f)       { totalDrag = 0f; vm.goToPreviousDay() }
                                    else if (totalDrag < -80f) { totalDrag = 0f; vm.goToNextDay() }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: hamburger menu
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.drawer_menu_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Center: date navigation
                    Row(
                        modifier              = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = vm::goToPreviousDay) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.summary_prev_day_cd),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Row(
                            modifier              = Modifier.clickable { showDatePicker = true },
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(dateDisplay, style = MaterialTheme.typography.titleSmall)
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = stringResource(R.string.summary_select_date_cd),
                                modifier = Modifier.size(13.dp),
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = vm::goToNextDay) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.summary_next_day_cd),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Right: balance spacer (same width as hamburger)
                    Box(modifier = Modifier.size(48.dp))
                }
                HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
        ) {
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
                    onDelete = { entry ->
                        vm.deleteEntry(entry)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message     = String.format(entryDeletedFmt, entry.name),
                                actionLabel = undoLabel,
                                duration    = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                vm.restoreEntry(entry)
                            }
                        }
                    }
                )
            }
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

    val surfaceTop    = MaterialTheme.colorScheme.surfaceVariant
    val surfaceBottom = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapeXl)
            .background(Brush.verticalGradient(listOf(surfaceTop, surfaceBottom)))
            .padding(28.dp)
    ) {
        Column {
            Text(
                text  = stringResource(R.string.summary_remaining),
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary
            )
            Spacer(Modifier.height(Spacing.xs))

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

            Text(
                text  = stringResource(R.string.summary_kcal_consumed, totalKcal, goalKcal),
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
        shape    = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text  = stringResource(R.string.summary_macronutrients),
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
            text     = stringResource(R.string.summary_meals_title, entries.size),
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
    MacroItemCard(
        title         = entry.name,
        kcal          = entry.kcal,
        protein       = entry.protein,
        carbs         = entry.carbs,
        fat           = entry.fat,
        leadingLabel  = timeStr,
        onSwipeDelete = onDelete
    )
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
                text  = stringResource(R.string.summary_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = stringResource(R.string.summary_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
        if (onNavigateToChat != null) {
            TextButton(onClick = onNavigateToChat) {
                Text(
                    text  = stringResource(R.string.summary_go_to_log),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun String.toDisplayDate(context: Context): String {
    val today = LocalDate.now()
    val date  = LocalDate.parse(this)
    return when {
        date == today              -> context.getString(R.string.date_today)
        date == today.minusDays(1) -> context.getString(R.string.date_yesterday)
        date == today.plusDays(1)  -> context.getString(R.string.date_tomorrow)
        else -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
}
