package com.planttracker.di

import android.content.Context
import androidx.room.Room
import com.planttracker.alarm.PlantAlarmManager
import com.planttracker.data.db.PlantDao
import com.planttracker.data.db.PlantDatabase
import com.planttracker.data.repository.PlantRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePlantDatabase(@ApplicationContext context: Context): PlantDatabase {
        return Room.databaseBuilder(
            context,
            PlantDatabase::class.java,
            PlantDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun providePlantDao(database: PlantDatabase): PlantDao = database.plantDao()

    @Provides
    @Singleton
    fun providePlantRepository(plantDao: PlantDao): PlantRepository =
        PlantRepository(plantDao)

    @Provides
    @Singleton
    fun providePlantAlarmManager(@ApplicationContext context: Context): PlantAlarmManager =
        PlantAlarmManager(context)
}
