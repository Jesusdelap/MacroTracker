package com.example.test1

import android.app.Application
import com.example.test1.data.api.GeminiService
import com.example.test1.data.db.AppDatabase
import com.example.test1.data.repository.ChatMessageRepository
import com.example.test1.data.repository.FoodRepository
import com.example.test1.data.repository.GoalRepository
import com.example.test1.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class MacroApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val foodRepository by lazy { FoodRepository(database.foodEntryDao()) }
    val recipeRepository by lazy { RecipeRepository(database.recipeDao()) }
    val goalRepository by lazy { GoalRepository(database.dailyGoalDao()) }
    val chatMessageRepository by lazy { ChatMessageRepository(database.chatMessageDao()) }
    val geminiService by lazy { GeminiService() }
    val selectedDate = MutableStateFlow(LocalDate.now().toString())
}
