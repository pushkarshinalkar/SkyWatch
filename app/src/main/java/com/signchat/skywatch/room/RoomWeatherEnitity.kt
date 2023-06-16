package com.signchat.skywatch.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_weather")
data class RoomWeatherEnitity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String?,
    val temp: Double?,
    val temp_min: Double?,
    val temp_max: Double?,
    val description: String?,
    val feels_like: Double?,
    val speed: Double?,
    val pressure: Int?,
    val humidity: Int?,
    val mainsky: String?
)

