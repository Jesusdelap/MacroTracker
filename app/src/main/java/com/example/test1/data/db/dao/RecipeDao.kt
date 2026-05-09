package com.example.test1.data.db.dao

import androidx.room.*
import com.example.test1.data.db.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY isFavorite DESC, createdAt DESC")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE name LIKE '%' || :query || '%' ORDER BY isFavorite DESC, name ASC")
    fun searchRecipes(query: String): Flow<List<RecipeEntity>>

    @Insert
    suspend fun insert(recipe: RecipeEntity): Long

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Delete
    suspend fun delete(recipe: RecipeEntity)
}
