package com.planttracker.data.repository

import com.planttracker.data.db.PlantDao
import com.planttracker.data.model.Plant
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantRepository @Inject constructor(
    private val plantDao: PlantDao
) {
    fun getAllPlants(): Flow<List<Plant>> = plantDao.getAllPlants()

    fun getActivePlants(): Flow<List<Plant>> = plantDao.getActivePlants()

    suspend fun getPlantById(id: Long): Plant? = plantDao.getPlantById(id)

    suspend fun addPlant(plant: Plant): Long = plantDao.insertPlant(plant)

    suspend fun updatePlant(plant: Plant) = plantDao.updatePlant(plant)

    suspend fun deletePlant(plant: Plant) = plantDao.deletePlant(plant)

    suspend fun harvestPlant(plant: Plant) {
        plantDao.updatePlant(plant.copy(isHarvested = true))
    }

    /**
     * 确认审核通过：取消待审核状态
     */
    suspend fun confirmReview(plant: Plant) {
        plantDao.updatePlant(plant.copy(isPendingReview = false))
    }

    /**
     * 批量确认审核通过：取消所有待审核状态
     */
    suspend fun confirmAllPendingReviews() {
        plantDao.confirmAllPendingReviews()
    }

    /**
     * 获取所有不重复的未来成熟时间（闹钟去重用）
     */
    suspend fun getDistinctFutureMatureTimes(): List<Long> =
        plantDao.getDistinctFutureMatureTimes()

    /**
     * 获取指定成熟时间的植物名列表（用于闹钟通知文案）
     */
    suspend fun getPlantNamesByMatureTime(matureAt: Long): List<String> =
        plantDao.getPlantNamesByMatureTime(matureAt)
}
