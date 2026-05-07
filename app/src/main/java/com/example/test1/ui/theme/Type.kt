package com.example.test1.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Aplicar a cualquier Text que muestre métricas numéricas para evitar que los
// dígitos se muevan al actualizar (alinea todos los dígitos a la misma anchura).
const val FontFeatureTnum = "tnum"

val Typography = Typography(

    // ── Display — números hero ────────────────────────────────────────────────
    // displayLarge = "displayHero": número gigante de calorías restantes (64sp)
    displayLarge = TextStyle(
        fontFamily          = FontFamily.Default,
        fontWeight          = FontWeight.Bold,
        fontSize            = 64.sp,
        lineHeight          = 68.sp,
        letterSpacing       = (-1.5).sp,
        fontFeatureSettings = FontFeatureTnum
    ),
    // displayMedium = "displayLarge": estadísticas grandes (48sp)
    displayMedium = TextStyle(
        fontFamily          = FontFamily.Default,
        fontWeight          = FontWeight.Bold,
        fontSize            = 48.sp,
        lineHeight          = 52.sp,
        letterSpacing       = (-1.0).sp,
        fontFeatureSettings = FontFeatureTnum
    ),

    // ── Headlines — títulos de pantalla y sección ─────────────────────────────
    headlineLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 28.sp,
        lineHeight    = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp
    ),

    // ── Title ─────────────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 22.sp,
        lineHeight    = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 16.sp,
        lineHeight    = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Body ──────────────────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Normal,
        fontSize      = 15.sp,
        lineHeight    = 22.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.2.sp
    ),

    // ── Label ─────────────────────────────────────────────────────────────────
    labelLarge = TextStyle(           // botones
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 13.sp,
        lineHeight    = 18.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(          // headers de sección — aplicar .uppercase() en el caller
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 11.sp,
        lineHeight    = 16.sp,
        letterSpacing = 1.2.sp
    ),
    labelSmall = TextStyle(           // metadata, timestamps, unidades
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Medium,
        fontSize      = 10.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.8.sp
    )
)