package com.example.test1.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test1.data.db.entity.DailyGoalEntity
import com.example.test1.data.db.entity.FoodEntryEntity
import com.example.test1.data.repository.FoodRepository
import com.example.test1.data.repository.GoalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DaySummary(
    val entries: List<FoodEntryEntity> = emptyList(),
    val goal: DailyGoalEntity = DailyGoalEntity(),
    val totalKcal: Int = 0,
    val totalProtein: Float = 0f,
    val totalCarbs: Float = 0f,
    val totalFat: Float = 0f,
    val selectedDate: String = LocalDate.now().toString()
)

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(
    private val foodRepository: FoodRepository,
    private val goalRepository: GoalRepository,
    private val sharedDate: MutableStateFlow<String>
) : ViewModel() {

    val selectedDate: StateFlow<String> = sharedDate.asStateFlow()

    val summary: StateFlow<DaySummary> = combine(
        sharedDate.flatMapLatest { foodRepository.getEntriesForDate(it) },
        goalRepository.getGoal(),
        sharedDate
    ) { entries, goal, date ->
        DaySummary(
            entries = entries,
            goal = goal,
            totalKcal = entries.sumOf { it.kcal },
            totalProtein = entries.sumOf { it.protein.toDouble() }.toFloat(),
            totalCarbs = entries.sumOf { it.carbs.toDouble() }.toFloat(),
            totalFat = entries.sumOf { it.fat.toDouble() }.toFloat(),
            selectedDate = date
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DaySummary())

    fun goToPreviousDay() {
        sharedDate.value = LocalDate.parse(sharedDate.value).minusDays(1).toString()
    }

    fun goToNextDay() {
        sharedDate.value = LocalDate.parse(sharedDate.value).plusDays(1).toString()
    }

    fun deleteEntry(entry: FoodEntryEntity) {
        viewModelScope.launch { foodRepository.delete(entry) }
    }
}
