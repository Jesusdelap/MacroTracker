package com.example.test1.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test1.ui.theme.*

// ── API actual ────────────────────────────────────────────────────────────────

/**
 * Fila de progreso de macro. El color, etiqueta y unidad se derivan de [macroType].
 */
@Composable
fun MacroProgressRow(
    macroType: MacroType,
    current: Float,
    goal: Float,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (goal > 0f) (current / goal).coerceIn(0f, 1f) else 0f,
        label = "macroProgress_${macroType.name}"
    )
    val color = macroType.color()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = macroType.fullName().uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = color
            )
            Text(
                text = "${current.toInt()} / ${goal.toInt()} ${macroType.unit()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(color.copy(alpha = 0.15f), AppShapeXs)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(color, AppShapeXs)
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0F1117, name = "MacroProgressRow — 3 macros")
@Composable
private fun MacroProgressRowPreview() {
    Test1Theme {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            MacroProgressRow(macroType = MacroType.PROTEIN, current = 95f,  goal = 150f)
            MacroProgressRow(macroType = MacroType.CARBS,   current = 180f, goal = 250f)
            MacroProgressRow(macroType = MacroType.FAT,     current = 55f,  goal = 70f)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1117, name = "MacroProgressRow — objetivo superado")
@Composable
private fun MacroProgressRowOverPreview() {
    Test1Theme {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            MacroProgressRow(macroType = MacroType.PROTEIN, current = 160f, goal = 150f)
            MacroProgressRow(macroType = MacroType.CARBS,   current = 10f,  goal = 250f)
            MacroProgressRow(macroType = MacroType.FAT,     current = 70f,  goal = 70f)
        }
    }
}
