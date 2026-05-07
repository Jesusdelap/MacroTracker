package com.example.test1.data.db.dao

import androidx.room.*
import com.example.test1.data.db.entity.DailyGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyGoalDao {
    @Query("SELECT * FROM daily_goals WHERE id = 1")
    fun getGoal(): Flow<DailyGoalEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: DailyGoalEntity)
}
