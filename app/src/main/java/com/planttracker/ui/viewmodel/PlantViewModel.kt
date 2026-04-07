package com.planttracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.planttracker.alarm.PlantAlarmManager
import com.planttracker.data.model.Plant
import com.planttracker.data.repository.PlantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlantViewModel @Inject constructor(
    private val repository: PlantRepository,
    private val alarmManager: PlantAlarmManager
) : ViewModel() {

    val plants = repository.getActivePlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlants = repository.getAllPlants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 添加植物，并同步刷新闹钟 */
    fun addPlant(name: String, emoji: String, matureAt: Long, note: String = "") {
        viewModelScope.launch {
            repository.addPlant(
                Plant(
                    name = name,
                    emoji = emoji,
                    matureAt = matureAt,
                    note = note
                )
            )
            syncAlarms()
        }
    }

    /** 标记收获，并同步刷新闹钟 */
    fun harvestPlant(plant: Plant) {
        viewModelScope.launch {
            repository.harvestPlant(plant)
            syncAlarms()
        }
    }

    /** 删除植物，并同步刷新闹钟 */
    fun deletePlant(plant: Plant) {
        viewModelScope.launch {
            repository.deletePlant(plant)
            // 如果被删除植物的成熟时间不再被其它植物使用，则取消对应闹钟
            val remaining = repository.getPlantNamesByMatureTime(plant.matureAt)
            if (remaining.isEmpty()) {
                alarmManager.cancelAlarm(plant.matureAt)
            }
            syncAlarms()
        }
    }

    /**
     * 同步闹钟：
     * - 获取所有不重复的未来成熟时间
     * - 相同时间只创建一个闹钟（去重由 PlantAlarmManager 的 requestCode 保证）
     */
    private suspend fun syncAlarms() {
        val distinctTimes = repository.getDistinctFutureMatureTimes()
        alarmManager.syncAlarms(distinctTimes)
    }
}
