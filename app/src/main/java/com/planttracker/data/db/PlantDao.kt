package com.planttracker.data.db

import androidx.room.*
import com.planttracker.data.model.Plant
import kotlinx.coroutines.flow.Flow

@Dao
interface PlantDao {

    @Query("SELECT * FROM plants ORDER BY matureAt ASC")
    fun getAllPlants(): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE isHarvested = 0 ORDER BY isPendingReview DESC, matureAt ASC")
    fun getActivePlants(): Flow<List<Plant>>

    @Query("SELECT * FROM plants WHERE id = :id")
    suspend fun getPlantById(id: Long): Plant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlant(plant: Plant): Long

    @Update
    suspend fun updatePlant(plant: Plant)

    @Delete
    suspend fun deletePlant(plant: Plant)

    @Query("DELETE FROM plants WHERE id = :id")
    suspend fun deletePlantById(id: Long)

    /**
     * 查询所有不重复的成熟时间（用于闹钟去重）
     * 只返回未收获且成熟时间在未来的植物成熟时间
     */
    @Query("""
        SELECT DISTINCT matureAt 
        FROM plants 
        WHERE isHarvested = 0 AND matureAt > :now
        ORDER BY matureAt ASC
    """)
    suspend fun getDistinctFutureMatureTimes(now: Long = System.currentTimeMillis()): List<Long>

    /**
     * 查询指定成熟时间的所有植物名称（用于闹钟通知内容）
     */
    @Query("SELECT name FROM plants WHERE matureAt = :matureAt AND isHarvested = 0")
    suspend fun getPlantNamesByMatureTime(matureAt: Long): List<String>

    /**
     * 批量取消所有待审核状态
     */
    @Query("UPDATE plants SET isPendingReview = 0 WHERE isPendingReview = 1")
    suspend fun confirmAllPendingReviews()
}
