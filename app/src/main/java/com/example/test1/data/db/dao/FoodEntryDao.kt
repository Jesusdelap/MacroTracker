package com.example.test1.data.db.dao

import androidx.room.*
import com.example.test1.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY timestamp ASC")
    fun getEntriesForDate(date: String): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun getById(id: Long): FoodEntryEntity?

    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Update
    suspend fun update(entry: FoodEntryEntity)

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(entry: FoodEntryEntity)
}
