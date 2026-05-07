package com.example.test1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test1.ui.theme.*

// ── API actual ────────────────────────────────────────────────────────────────

/**
 * Pill de macro. El color y la etiqueta se derivan automáticamente de [macroType].
 * Pasa [label] explícitamente para sobreescribir la etiqueta por defecto.
 */
@Composable
fun MacroPill(
    macroType: MacroType,
    value: String,
    label: String = macroType.label(),
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(macroType.containerColor(), AppShapeXs)
            .padding(horizontal = Spacing.sm, vertical = 6.dp)
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            color = macroType.color(),
            fontSize = 13.sp
        )
        Text(
            text = label,
            color = macroType.color().copy(alpha = 0.72f),
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold
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

@Preview(showBackground = true, backgroundColor = 0xFF0F1117, name = "MacroPill — expandidas (weight)")
@Composable
private fun MacroPillExpandedPreview() {
    Test1Theme {
        Row(
            modifier = Modifier
                .padding(Spacing.lg)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            MacroPill(MacroType.CALORIES, "350",  modifier = Modifier.weight(1f))
            MacroPill(MacroType.PROTEIN,  "28g",  modifier = Modifier.weight(1f))
            MacroPill(MacroType.CARBS,    "42g",  modifier = Modifier.weight(1f))
            MacroPill(MacroType.FAT,      "12g",  modifier = Modifier.weight(1f))
        }
    }
}
