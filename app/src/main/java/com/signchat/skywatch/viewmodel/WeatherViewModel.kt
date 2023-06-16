package com.signchat.skywatch.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signchat.skywatch.model.CustomWeather
import com.signchat.skywatch.model.WeatherApiService
import com.signchat.skywatch.singletons.RetrofitInstance
import kotlinx.coroutines.launch

class WeatherViewModel : ViewModel() {

    private val _weatherListLiveData = MutableLiveData<List<CustomWeather>>()
    val weatherListLiveData: LiveData<List<CustomWeather>> = _weatherListLiveData

    private val coursesApiService = RetrofitInstance.getInstance().create(WeatherApiService::class.java)

    fun fetchWeatherDataForCoordinates(coordinates: List<Pair<Double, Double>>, apiId: String) {
        viewModelScope.launch {
            val weatherList = mutableListOf<CustomWeather>()
            for (coordinate in coordinates) {
                val lat = coordinate.first
                val lon = coordinate.second
                val weatherResponse = coursesApiService.getWeatherData(lat, lon, apiId)
                if (weatherResponse.isSuccessful) {
                    val weatherInfo = weatherResponse.body()
                    val weather = CustomWeather(
                        name = weatherInfo?.name,
                        temp = weatherInfo?.main?.temp,
                        temp_min = weatherInfo?.main?.temp_min,
                        temp_max = weatherInfo?.main?.temp_max,
                        description = weatherInfo?.weather?.get(0)?.description,
                        feels_like = weatherInfo?.main?.feels_like,
                        speed = weatherInfo?.wind?.speed,
                        pressure = weatherInfo?.main?.pressure,
                        humidity = weatherInfo?.main?.humidity,
                        mainsky = weatherInfo?.weather?.get(0)?.main
                    )
                    weatherList.add(weather)
                }
            }
            _weatherListLiveData.postValue(weatherList)
        }
    }
}
