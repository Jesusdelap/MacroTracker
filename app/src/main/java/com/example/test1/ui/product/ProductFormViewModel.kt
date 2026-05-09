package com.example.test1.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test1.data.db.entity.FoodEntryEntity
import com.example.test1.data.db.entity.FoodItemEntity
import com.example.test1.data.repository.FoodRepository
import com.example.test1.data.repository.RecipeRepository
import kotlinx.coroutines.launch
import java.time.LocalDate

class ProductFormViewModel(
    private val recipeRepository: RecipeRepository,
    private val foodRepository: FoodRepository
) : ViewModel() {

    suspend fun saveProduct(entity: FoodItemEntity): Long =
        recipeRepository.insert(entity)

    fun addToLogAndSave(entity: FoodItemEntity, grams: Float, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = if (entity.id == 0L) recipeRepository.insert(entity) else entity.id
            val saved = if (entity.id == 0L) entity.copy(id = id) else entity
            val ratio = grams / 100f
            val displayName = listOfNotNull(saved.name.trim(), saved.brand?.let { "($it)" }).joinToString(" ")
            foodRepository.insert(
                FoodEntryEntity(
                    date        = LocalDate.now().toString(),
                    name        = saved.name.trim(),
                    kcal        = (saved.kcalPerServing * ratio).toInt(),
                    protein     = saved.protein * ratio,
                    carbs       = saved.carbs * ratio,
                    fat         = saved.fat * ratio,
                    description = "Producto: $displayName (${grams.toInt()}g)"
                )
            )
            recipeRepository.update(
                saved.copy(usageCount = saved.usageCount + 1, lastUsedAt = System.currentTimeMillis())
            )
            onDone()
        }
    }
}
