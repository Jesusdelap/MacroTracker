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

    @Insert
    suspend fun insert(item: FoodItemEntity): Long

    @Update
    suspend fun update(item: FoodItemEntity)

    @Delete
    suspend fun delete(item: FoodItemEntity)
}
