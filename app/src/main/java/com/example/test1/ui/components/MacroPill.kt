package com.example.test1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.test1.ui.theme.*

@Composable
fun MacroPill(
    macroType: MacroType,
    value: String,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val color = macroType.color()
    val resolvedLabel = label ?: macroType.label()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(color.copy(alpha = 0.10f), AppShapeSm)
            .padding(horizontal = 12.dp, vertical = Spacing.sm)
    ) {
        Text(
            text  = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = FontFeatureTnum
            ),
            color = color
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text  = resolvedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0F1117, name = "MacroPill — 4 tipos")
@Composable
private fun MacroPillAllTypesPreview() {
    Test1Theme {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            MacroPill(MacroType.CALORIES, "350")
            MacroPill(MacroType.PROTEIN,  "28g")
            MacroPill(MacroType.CARBS,    "42g")
            MacroPill(MacroType.FAT,      "12g")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1117, name = "MacroPill — fila natural")
@Composable
private fun MacroPillRowPreview() {
    Test1Theme {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            MacroPill(MacroType.PROTEIN, "28g")
            MacroPill(MacroType.CARBS,   "42g")
            MacroPill(MacroType.FAT,     "12g")
        }
    }
}
