package com.example.test1.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test1.data.db.entity.FoodEntryEntity
import com.example.test1.data.db.entity.FoodItemEntity
import com.example.test1.data.repository.FoodRepository
import com.example.test1.data.repository.RecipeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class RecipeUiState(
    val recipes: List<FoodItemEntity> = emptyList(),
    val searchQuery: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeViewModel(
    private val recipeRepository: RecipeRepository,
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")

    val uiState: StateFlow<RecipeUiState> = _query
        .flatMapLatest { q -> recipeRepository.searchRecipes(q).map { RecipeUiState(it, q) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeUiState())

    fun onSearchChange(query: String) { _query.value = query }

    fun addRecipe(recipe: FoodItemEntity) {
        viewModelScope.launch { recipeRepository.insert(recipe) }
    }

    fun updateRecipe(recipe: FoodItemEntity) {
        viewModelScope.launch { recipeRepository.update(recipe) }
    }

    fun deleteRecipe(recipe: FoodItemEntity) {
        viewModelScope.launch { recipeRepository.delete(recipe) }
    }

    fun toggleFavorite(recipe: FoodItemEntity) {
        viewModelScope.launch { recipeRepository.update(recipe.copy(isFavorite = !recipe.isFavorite)) }
    }

    fun addToToday(recipe: FoodItemEntity) {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            foodRepository.insert(
                FoodEntryEntity(
                    date        = today,
                    name        = recipe.name,
                    kcal        = recipe.kcalPerServing,
                    protein     = recipe.protein,
                    carbs       = recipe.carbs,
                    fat         = recipe.fat,
                    description = "Receta: ${recipe.name}"
                )
            )
            recipeRepository.update(recipe.copy(usageCount = recipe.usageCount + 1, lastUsedAt = System.currentTimeMillis()))
        }
    }

    fun addToTodayWithGrams(recipe: FoodItemEntity, grams: Float) {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val ratio = grams / 100f
            foodRepository.insert(
                FoodEntryEntity(
                    date        = today,
                    name        = recipe.name,
                    kcal        = (recipe.kcalPerServing * ratio).toInt(),
                    protein     = recipe.protein * ratio,
                    carbs       = recipe.carbs * ratio,
                    fat         = recipe.fat * ratio,
                    description = "Receta: ${recipe.name} (${grams.toInt()}g)"
                )
            )
            recipeRepository.update(recipe.copy(usageCount = recipe.usageCount + 1, lastUsedAt = System.currentTimeMillis()))
        }
    }
}
