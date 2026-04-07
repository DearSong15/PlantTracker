package com.planttracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.planttracker.data.model.Plant

@Database(
    entities = [Plant::class],
    version = 1,
    exportSchema = false
)
abstract class PlantDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao

    companion object {
        const val DATABASE_NAME = "plant_tracker.db"
    }
}
