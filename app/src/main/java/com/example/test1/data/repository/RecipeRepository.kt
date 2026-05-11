package com.example.test1.data.repository

import com.example.test1.data.db.dao.FoodItemDao
import com.example.test1.data.db.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val dao: FoodItemDao) {
    fun getAllRecipes(): Flow<List<FoodItemEntity>> = dao.getAllFoodItems()

    fun searchRecipes(query: String): Flow<List<FoodItemEntity>> =
        if (query.isBlank()) dao.getRecipes() else dao.searchRecipeItems(query)

    fun searchProducts(query: String): Flow<List<FoodItemEntity>> =
        if (query.isBlank()) dao.getProducts() else dao.searchProductItems(query)

    suspend fun insert(recipe: FoodItemEntity): Long = dao.insert(recipe)

    suspend fun update(recipe: FoodItemEntity) = dao.update(recipe)

    suspend fun delete(recipe: FoodItemEntity) = dao.delete(recipe)
}
