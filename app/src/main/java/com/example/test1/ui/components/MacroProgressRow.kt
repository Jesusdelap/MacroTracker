package com.example.test1.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.test1.ui.theme.*

@Composable
fun MacroProgressRow(
    macroType: MacroType,
    current: Float,
    goal: Float,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (goal > 0f) (current / goal).coerceIn(0f, 1f) else 0f,
        label       = "macroProgress_${macroType.name}"
    )
    val macroColor = macroType.color()
    val pct = if (goal > 0f) ((current / goal) * 100).toInt().coerceAtMost(999) else 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label: color neutro — el color del macro va solo en la barra
            Text(
                text  = macroType.fullName().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text  = "${current.toInt()} / ${goal.toInt()} ${macroType.unit()}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFeatureSettings = FontFeatureTnum
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text  = "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
        // Barra 6dp, cápsula, track = Border, fill = color del macro
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(AppShapeXl)
                .background(MaterialTheme.colorScheme.outline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(macroColor)
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            MacroProgressRow(macroType = MacroType.PROTEIN, current = 160f, goal = 150f)
            MacroProgressRow(macroType = MacroType.CARBS,   current = 10f,  goal = 250f)
            MacroProgressRow(macroType = MacroType.FAT,     current = 70f,  goal = 70f)
        }
    }
}
