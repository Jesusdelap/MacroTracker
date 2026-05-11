# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**MacroTracker** — Android native app for logging daily macronutrient intake via natural language. The user describes meals in chat; Claude estimates the macros and persists them. Includes a recipe book and daily summary screen.

- Package: `com.example.test1` | minSdk 32 | targetSdk 36 | Kotlin 2.2.10
- UI: Jetpack Compose + Material 3 | Architecture: MVVM | Navigation: Bottom Nav (3 tabs)

## First-Time Setup: API Key

Get a free key at **aistudio.google.com/apikey** and add it to `local.properties` (never commit this file):
```
GEMINI_API_KEY=AIza...
```
It is injected at build time into `BuildConfig.GEMINI_API_KEY` and read by `GeminiService`.

## Build Commands

```powershell
.\gradlew.bat assembleDebug          # debug APK
.\gradlew.bat installDebug           # build + install on device
.\gradlew.bat test                   # unit tests
.\gradlew.bat test --tests "com.example.test1.ExampleUnitTest"  # single test
.\gradlew.bat connectedAndroidTest   # instrumented tests (device required)
.\gradlew.bat check                  # lint + tests
```

## Architecture

### Dependency injection
No Hilt. `MacroApp` (Application subclass) lazily initialises all singletons — database, repositories, `AnthropicService` — and makes them available as properties. Screens obtain ViewModels via `viewModel { MyVm(app.repo, ...) }` with `LocalContext.current.applicationContext as MacroApp`.

### Data layer (`data/`)
| Path | Purpose |
|---|---|
| `data/db/entity/` | Room entities: `FoodEntryEntity`, `RecipeEntity`, `DailyGoalEntity` |
| `data/db/dao/` | Room DAOs (all queries return `Flow`) |
| `data/db/AppDatabase` | Singleton Room DB with explicit, non-destructive migrations |
| `data/api/GeminiService` | Google AI SDK call; returns `Result<MacroResult>`. Includes exponential backoff on rate limits (HTTP 429 / RESOURCE_EXHAUSTED). |
| `data/api/MacroResult` | `@Serializable` data class for the JSON the AI returns |
| `data/repository/` | Thin wrappers over DAOs and the API service |

### ViewModel layer (`ui/*/`)
Each screen owns its ViewModel. ViewModels expose a single `StateFlow<UiState>` and suspend functions for mutations. `SummaryViewModel` and `RecipeViewModel` use `flatMapLatest` to react to user-driven filter changes.

### UI layer
| Screen | ViewModel | Key functionality |
|---|---|---|
| `ChatScreen` | `ChatViewModel` | Sends description → AI → saves `FoodEntry` → shows macro pills |
| `RecipeScreen` | `RecipeViewModel` | CRUD recipes, search, add to today |
| `SummaryScreen` | `SummaryViewModel` | Day selector (prev/next), calorie + macro progress bars |
| `SettingsScreen` | `SettingsViewModel` | Edit daily macro targets (`DailyGoalEntity`) |

### Navigation
`AppNavigation` wraps a `NavHost` + `NavigationBar`. The bottom bar only appears for the 3 main tab routes (`chat`, `recipes`, `summary`). Settings (`settings`) is reached from the Chat top-bar gear icon.

### Macro colour palette (`ui/theme/Color.kt`)
| Macro | Colour |
|---|---|
| Calories | `CalorieColor` #EF4444 (red) |
| Protein | `ProteinColor` #7C3AED (purple) |
| Carbs | `CarbColor` #0D9488 (teal) |
| Fat | `FatColor` #D97706 (amber) |

## Key Dependencies

Declared in `gradle/libs.versions.toml`:

| Library | Version |
|---|---|
| AGP | 9.2.0 |
| Kotlin | 2.2.10 |
| KSP | 2.2.10-1.0.28 |
| Compose BOM | 2026.02.01 |
| Room | 2.7.0 |
| Navigation Compose | 2.8.5 |
| Lifecycle ViewModel Compose | 2.8.7 |
| kotlinx-serialization-json | 1.7.3 |
| Google Generative AI (Gemini SDK) | 0.9.0 |

If the build fails on the KSP version, check the compatible release at `github.com/google/ksp/releases` — the version must match `{kotlin_version}-{ksp_patch}`.

If the Gemini SDK version can't be resolved, find the latest at `central.sonatype.com` searching for `com.google.ai.client.generativeai`.

## AI Prompt Contract

`GeminiService.estimateMacros()` uses `responseMimeType = "application/json"` in the generation config, which forces Gemini to return clean JSON with no markdown wrapper. The system instruction defines the exact shape:
```json
{"name":"nombre corto","cal":350,"prot":25.0,"carb":40.0,"fat":8.5}
```
The response text is parsed directly with `kotlinx.serialization` into `MacroResult`.

## Rate Limit Handling (free tier)

`GeminiService` retries up to 4 times with exponential backoff (2 s → 4 s → 8 s) on any error matching `429`, `RESOURCE_EXHAUSTED`, or `quota`. After all retries fail it throws `RateLimitException`. `ChatViewModel` detects this type and shows the Spanish message verbatim in the chat bubble — no generic "Error:" prefix.

Free-tier limits for `gemini-2.0-flash`: 15 RPM / 1 500 RPD.
