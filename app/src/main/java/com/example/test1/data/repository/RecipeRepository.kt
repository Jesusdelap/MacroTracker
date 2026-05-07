package com.example.test1.data.repository

import com.example.test1.data.db.dao.RecipeDao
import com.example.test1.data.db.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val dao: RecipeDao) {
    fun getAllRecipes(): Flow<List<RecipeEntity>> = dao.getAllRecipes()

    fun searchRecipes(query: String): Flow<List<RecipeEntity>> =
        if (query.isBlank()) dao.getAllRecipes() else dao.searchRecipes(query)

    suspend fun insert(recipe: RecipeEntity): Long = dao.insert(recipe)

    suspend fun update(recipe: RecipeEntity) = dao.update(recipe)

    suspend fun delete(recipe: RecipeEntity) = dao.delete(recipe)
}
