package com.example.test1.data.repository

import com.example.test1.data.db.dao.DailyGoalDao
import com.example.test1.data.db.entity.DailyGoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GoalRepository(private val dao: DailyGoalDao) {
    fun getGoal(): Flow<DailyGoalEntity> =
        dao.getGoal().map { it ?: DailyGoalEntity() }

    suspend fun save(goal: DailyGoalEntity) = dao.upsert(goal)
}
