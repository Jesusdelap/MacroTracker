package com.example.test1.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test1.data.db.entity.DailyGoalEntity
import com.example.test1.data.repository.GoalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val goalRepository: GoalRepository) : ViewModel() {

    val goal: StateFlow<DailyGoalEntity> = goalRepository.getGoal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyGoalEntity())

    fun saveGoal(kcal: Int, protein: Int, carbs: Int, fat: Int) {
        viewModelScope.launch {
            goalRepository.save(DailyGoalEntity(kcal = kcal, protein = protein, carbs = carbs, fat = fat))
        }
    }
}
