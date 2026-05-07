package com.example.test1.data.repository

import com.example.test1.data.db.dao.FoodEntryDao
import com.example.test1.data.db.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

class FoodRepository(private val dao: FoodEntryDao) {
    fun getEntriesForDate(date: String): Flow<List<FoodEntryEntity>> =
        dao.getEntriesForDate(date)

    suspend fun getById(id: Long): FoodEntryEntity? = dao.getById(id)

    suspend fun insert(entry: FoodEntryEntity): Long = dao.insert(entry)

    suspend fun update(entry: FoodEntryEntity) = dao.update(entry)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun delete(entry: FoodEntryEntity) = dao.delete(entry)
}
