package com.example.test1

import android.app.Application
import com.example.test1.data.api.BarcodeNutritionService
import com.example.test1.data.api.GeminiService
import com.example.test1.data.db.AppDatabase
import com.example.test1.data.repository.ChatMessageRepository
import com.example.test1.data.repository.FoodRepository
import com.example.test1.data.repository.GoalRepository
import com.example.test1.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.util.Locale

class MacroApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val foodRepository by lazy { FoodRepository(database.foodEntryDao()) }
    val recipeRepository by lazy { RecipeRepository(database.foodItemDao()) }
    val goalRepository by lazy { GoalRepository(database.dailyGoalDao()) }
    val chatMessageRepository by lazy { ChatMessageRepository(database.chatMessageDao()) }
    val geminiService by lazy { GeminiService() }
    val barcodeNutritionService by lazy { BarcodeNutritionService() }
    val selectedDate = MutableStateFlow(LocalDate.now().toString())
    val languageCode: String
        get() = getSharedPreferences("app_settings", MODE_PRIVATE)
            .getString("language", null)
            ?.takeIf { it.isNotBlank() }
            ?: systemLanguageCode()

    private fun systemLanguageCode(): String =
        when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "es" -> "es"
            else -> "en"
        }
}
