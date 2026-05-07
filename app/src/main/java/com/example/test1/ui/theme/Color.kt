package com.example.test1.ui.theme

import androidx.compose.ui.graphics.Color

// ── Fondos y superficies (jerarquía por elevación) ────────────────────────────
// Cada nivel sube ~3-5% de luminosidad. Nunca #000000.
val Background      = Color(0xFF0E0F11)   // base: negro cálido
val Surface         = Color(0xFF16181C)   // cards principales
val SurfaceElevated = Color(0xFF1D2025)   // dialogs, modals, chat bubbles
val SurfaceHigh     = Color(0xFF25282E)   // chips, inputs, pressed states
val Border          = Color(0xFF2A2D33)   // bordes sutiles
val BorderSubtle    = Color(0xFF1F2226)   // separadores casi imperceptibles

// ── Texto (escala de opacidad sobre fondo oscuro) ─────────────────────────────
val TextPrimary   = Color(0xFFF2F3F5)   // no blanco puro, evita fatiga visual
val TextSecondary = Color(0xFFA8ABB2)   // labels secundarios, descripciones
val TextTertiary  = Color(0xFF6B6E76)   // timestamps, metadata, hints
val TextDisabled  = Color(0xFF4A4D54)

// ── Accent único de marca ─────────────────────────────────────────────────────
// Solo para elementos accionables primarios y métricas clave (RESTANTES, etc.)
val AccentPrimary      = Color(0xFF8B7FFF)           // morado suave, no neón
val AccentPrimaryHover = Color(0xFF9D93FF)
val AccentPrimaryMuted = Color(0x268B7FFF)           // AccentPrimary @ ~15%

// ── Macros (tenues, desaturados — premium, no app gratuita de Play Store) ─────
val MacroCalories = Color(0xFFE8A87C)   // ámbar cálido "energía", nunca rojo
val MacroProtein  = Color(0xFFC77DBB)   // rosa-magenta desaturado
val MacroCarbs    = Color(0xFF6FB3C7)   // azul-cyan apagado, tipo "agua"
val MacroFat      = Color(0xFFD4B570)   // dorado mate

// ── Estados semánticos ────────────────────────────────────────────────────────
// Rojo reservado solo para destructivo/error — recupera su significado.
val SemanticSuccess = Color(0xFF6FBF8E)
val SemanticWarning = Color(0xFFD4A55C)
val SemanticError   = Color(0xFFC77B7B)   // rojo apagado

// ── Tokens Material 3 internos (solo usados en Theme.kt) ─────────────────────
internal val M3PrimaryContainer     = Color(0xFF1C1A3A)   // dark indigo container
internal val M3OnPrimaryContainer   = Color(0xFFC4BDFF)   // lavanda claro
internal val M3Secondary            = Color(0xFF7A8290)   // slate neutro
internal val M3SecondaryContainer   = Color(0xFF22252C)   // superficie neutral media
internal val M3OnError              = Color(0xFF2A0E0E)
internal val M3ErrorContainer       = Color(0xFF2A1515)   // fondo rojo muy oscuro

// ── Aliases de compatibilidad hacia atrás ─────────────────────────────────────
// Punk* → eliminados en Paso 2 (Theme.kt migrado a tokens semánticos)
internal val PunkBackground           = Background
internal val PunkSurface              = Surface
internal val PunkSurfaceVar           = SurfaceElevated
internal val PunkOnBackground         = TextPrimary
internal val PunkOnSurface            = TextPrimary
internal val PunkOnSurfaceVar         = TextSecondary
internal val PunkOutline              = Border
internal val PunkOutlineVar           = BorderSubtle
internal val PunkPrimary              = AccentPrimary
internal val PunkOnPrimary            = Background
internal val PunkPrimaryContainer     = M3PrimaryContainer
internal val PunkOnPrimaryContainer   = M3OnPrimaryContainer
internal val PunkSecondary            = M3Secondary
internal val PunkOnSecondary          = Background
internal val PunkSecondaryContainer   = M3SecondaryContainer
internal val PunkOnSecondaryContainer = TextPrimary
internal val PunkError                = SemanticError
internal val PunkOnError              = M3OnError
internal val PunkErrorContainer       = M3ErrorContainer
internal val PunkOnErrorContainer     = SemanticError

// Macro color aliases → eliminados en Paso 3 (screens migradas a MacroType tokens)
@Deprecated("Use MacroCalories") val CalorieColor = MacroCalories
@Deprecated("Use MacroProtein")  val ProteinColor = MacroProtein
@Deprecated("Use MacroCarbs")    val CarbColor    = MacroCarbs
@Deprecated("Use MacroFat")      val FatColor     = MacroFat
