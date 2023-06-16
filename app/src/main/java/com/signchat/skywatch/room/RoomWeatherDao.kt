package com.signchat.skywatch.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RoomWeatherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customWeather: RoomWeatherEnitity)

    @Query("SELECT * FROM custom_weather")
    suspend fun getAllWeatherData(): List<RoomWeatherEnitity>

}
