package com.signchat.skywatch.model

data class CustomWeather(
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
