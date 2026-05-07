package com.example.test1.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test1.MacroApp
import com.example.test1.ui.theme.*

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as MacroApp
    val vm: SettingsViewModel = viewModel { SettingsViewModel(app.goalRepository) }
    val goal by vm.goal.collectAsState()

    var kcal by remember(goal.kcal) { mutableStateOf(goal.kcal.toString()) }
    var protein by remember(goal.protein) { mutableStateOf(goal.protein.toString()) }
    var carbs by remember(goal.carbs) { mutableStateOf(goal.carbs.toString()) }
    var fat by remember(goal.fat) { mutableStateOf(goal.fat.toString()) }

    var showSavedSnackbar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (showSavedSnackbar) {
        LaunchedEffect(Unit) {
            snackbarHostState.showSnackbar("Objetivos guardados")
            showSavedSnackbar = false
        }
    }

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
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        "Objetivos diarios",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Configura tus metas nutricionales diarias", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            GoalField(
                label = "Calorías (kcal)",
                value = kcal,
                onValueChange = { kcal = it },
                color = CalorieColor
            )
            GoalField(
                label = "Proteína (g)",
                value = protein,
                onValueChange = { protein = it },
                color = ProteinColor
            )
            GoalField(
                label = "Carbohidratos (g)",
                value = carbs,
                onValueChange = { carbs = it },
                color = CarbColor
            )
            GoalField(
                label = "Grasas (g)",
                value = fat,
                onValueChange = { fat = it },
                color = FatColor
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val k = kcal.toIntOrNull() ?: return@Button
                    val p = protein.toIntOrNull() ?: return@Button
                    val c = carbs.toIntOrNull() ?: return@Button
                    val f = fat.toIntOrNull() ?: return@Button
                    vm.saveGoal(k, p, c, f)
                    showSavedSnackbar = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar objetivos", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GoalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    color: androidx.compose.ui.graphics.Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = color,
            focusedLabelColor = color
        )
    )
}
