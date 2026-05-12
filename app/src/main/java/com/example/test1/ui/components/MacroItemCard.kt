package com.example.test1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.test1.R
import com.example.test1.ui.theme.*
import kotlin.math.roundToInt

/**
 * Shared visual frame for recipe, product, and food-entry tiles.
 *
 * Layout:
 *   ┌─────────────────────────────────────────┐
 *   │  [leadingLabel]  title          trailing │
 *   │  [secondaryTitle]                        │
 *   │  🔥 kcal [kcalSuffix]  [metadata]        │
 *   │─────────────────────────────────────────│
 *   │  [bottomLeading] P pill  C pill  F pill  │
 *   │  [extraContent]                          │
 *   └─────────────────────────────────────────┘
 *
 * All optional slots default to null / no-op.
 */
@Composable
fun MacroItemCard(
    title: String,
    kcal: Int,
    protein: Float,
    carbs: Float,
    fat: Float,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null,
    secondaryTitle: String? = null,
    kcalSuffix: String? = null,
    metadata: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    bottomLeading: (@Composable () -> Unit)? = null,
    bottomTrailing: (@Composable () -> Unit)? = null,
    extraContent: (@Composable () -> Unit)? = null,
    onSwipeDelete: (() -> Unit)? = null
) {
    val card: @Composable () -> Unit = {
        ElevatedCard(
            modifier  = Modifier.fillMaxWidth(),
            shape     = MaterialTheme.shapes.large,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (trailingContent != null) Spacing.sm else 0.dp)
                    ) {
                        // Title line — optional leading label (e.g. timestamp)
                        if (leadingLabel != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text     = leadingLabel,
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = TextTertiary,
                                    modifier = Modifier.alignByBaseline()
                                )
                                Text(
                                    text     = title,
                                    style    = MaterialTheme.typography.headlineMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color    = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.alignByBaseline()
                                )
                            }
                        } else {
                            Text(
                                text     = title,
                                style    = MaterialTheme.typography.headlineMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color    = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Optional secondary line (e.g. brand name)
                        if (secondaryTitle != null) {
                            Text(
                                text  = secondaryTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(Spacing.xs))

                        // Calories row with fire icon
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                modifier           = Modifier.size(13.dp),
                                tint               = MaterialTheme.macroColors.calories
                            )
                            Text(
                                text  = "$kcal kcal${kcalSuffix ?: ""}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.macroColors.calories
                            )
                            metadata?.invoke()
                        }
                    }

                    trailingContent?.invoke()
                }

                Spacer(Modifier.height(Spacing.md))

                // Macro pills row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    bottomLeading?.invoke()
                    MacroPill(MacroType.PROTEIN, "${protein.roundToInt()}g")
                    MacroPill(MacroType.CARBS,   "${carbs.roundToInt()}g")
                    MacroPill(MacroType.FAT,     "${fat.roundToInt()}g")
                    bottomTrailing?.invoke()
                }

                if (extraContent != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    extraContent()
                }
            }
        }
    }

    if (onSwipeDelete != null) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange  = { v ->
                if (v == SwipeToDismissBoxValue.EndToStart) { onSwipeDelete(); true } else false
            },
            positionalThreshold = { it * 0.38f }
        )
        SwipeToDismissBox(
            state                       = dismissState,
            modifier                    = modifier,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
            backgroundContent           = {
                val fraction = dismissState.progress
                val visible  = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large)
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
                        contentDescription = stringResource(R.string.action_delete),
                        tint               = MaterialTheme.colorScheme.onErrorContainer,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        ) { card() }
    } else {
        Box(modifier = modifier) { card() }
    }
}
