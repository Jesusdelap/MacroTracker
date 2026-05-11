package com.example.test1.data.db.dao

import androidx.room.*
import com.example.test1.data.db.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {
    @Query("SELECT * FROM food_items ORDER BY isFavorite DESC, createdAt DESC")
    fun getAllFoodItems(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE name LIKE '%' || :query || '%' ORDER BY isFavorite DESC, name ASC")
    fun searchFoodItems(query: String): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE itemType = 'RECIPE' ORDER BY isFavorite DESC, createdAt DESC")
    fun getRecipes(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE itemType = 'RECIPE' AND name LIKE '%' || :query || '%' ORDER BY isFavorite DESC, name ASC")
    fun searchRecipeItems(query: String): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE itemType = 'PRODUCT' ORDER BY COALESCE(lastUsedAt, 0) DESC, usageCount DESC, name ASC")
    fun getProducts(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE itemType = 'PRODUCT' AND (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%') ORDER BY usageCount DESC, name ASC")
    fun searchProductItems(query: String): Flow<List<FoodItemEntity>>

    @Insert
    suspend fun insert(item: FoodItemEntity): Long

    @Update
    suspend fun update(item: FoodItemEntity)

    @Delete
    suspend fun delete(item: FoodItemEntity)
}
