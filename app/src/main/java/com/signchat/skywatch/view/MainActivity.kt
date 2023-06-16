package com.signchat.skywatch.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.content.IntentSender.SendIntentException
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.signchat.skywatch.room.AppDatabase
import com.signchat.skywatch.room.RoomWeatherEnitity
import com.signchat.skywatch.broadcast.NetworkReceiver
import com.signchat.skywatch.databinding.ActivityMainBinding
import com.signchat.skywatch.singletons.Constants
import com.signchat.skywatch.viewmodel.WeatherViewModel
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var w_viewModel: WeatherViewModel
    val KEY_LATITUDE = "latitude"
    val KEY_LONGITUDE = "longitude"
    val KEY_DATE = "lastdate"
    var isDataRetrieved = false
    private val REQUEST_LOCATION_PERMISSION = 1
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var networkReceiver: NetworkReceiver
    private lateinit var appDatabase: AppDatabase
    private var locationRequest: LocationRequest? = null
    private val REQUEST_CHECK_SETTINGS = 10001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        showLocalData()
        networkReceiver = NetworkReceiver { isConnected ->
            if (isConnected) {
                turnonGPS()
                getLastLocation()
            } else {
                Toasty.error(this@MainActivity, "No Internet Connection.", Toast.LENGTH_SHORT, true).show()
            }
        }

    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                networkReceiver = NetworkReceiver { isConnected ->
                    if (isConnected) {
                        turnonGPS()
                        getLastLocation()
                    }
                }
            }
        }
    }



    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION_PERMISSION
        )
    }


    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }


    fun kelvinToCelsius(kelvin: Double): Float {
        return (kelvin - 273.15).toFloat()
    }


    private fun turnonGPS() {

        locationRequest = LocationRequest.create()
        locationRequest!!.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest!!.setInterval(5000)
        locationRequest!!.setFastestInterval(2000)

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest!!)
        builder.setAlwaysShow(true)

        val result: Task<LocationSettingsResponse> = LocationServices.getSettingsClient(
            applicationContext
        )
            .checkLocationSettings(builder.build())

        result.addOnCompleteListener { task ->
            try {
                val response = task.getResult(ApiException::class.java)
//                Toasty.success(this@MainActivity, "GPS Active", Toast.LENGTH_SHORT, true).show();
            } catch (e: ApiException) {
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        val resolvableApiException = e as ResolvableApiException
                        resolvableApiException.startResolutionForResult(
                            this@MainActivity,
                            REQUEST_CHECK_SETTINGS
                        )
                    } catch (ex: SendIntentException) {
                        ex.printStackTrace()
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {}
                }
            }
        }
    }


    private fun getLastLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                val locationRequest = LocationRequest.create().apply {
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                    interval = 10000
                    fastestInterval = 5000
                }

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(p0: LocationResult) {
                        p0.let {
                            val lastLocation = it.lastLocation
                            val latitude = lastLocation?.latitude
                            val longitude = lastLocation?.longitude

                            if (!isDataRetrieved) {
                                if (latitude != null && longitude != null) {
                                    saveLocation(latitude, longitude)
                                    isDataRetrieved = true
                                    val locationdata = getLocation()
                                    val apiKey = Constants.API_ID
                                    viewModelObserver(
                                        locationdata.first,
                                        locationdata.second,
                                        apiKey
                                    )
                                }
                            }

                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                )
            }
        } else {
            requestLocationPermission()
        }
    }


    fun saveLocation(latitude: Double, longitude: Double) {

        val current = LocalDateTime.now()

        val formatter = DateTimeFormatter.ofPattern("MMM d")
        val formatted = current.format(formatter)

        val editor = sharedPreferences.edit()
        editor.putString(KEY_LATITUDE, latitude.toString())
        editor.putString(KEY_LONGITUDE, longitude.toString())
        editor.putString(KEY_DATE, formatted)
        editor.apply()
    }


    fun getLocation(): Pair<Double, Double> {
        val latitude = sharedPreferences.getString(KEY_LATITUDE, "0.0")?.toDoubleOrNull() ?: 0.0
        val longitude = sharedPreferences.getString(KEY_LONGITUDE, "0.0")?.toDoubleOrNull() ?: 0.0
        return Pair(latitude, longitude)
    }


    @SuppressLint("SetTextI18n")
    private fun showLocalData() {
        appDatabase = AppDatabase.getInstance(this)

        val customWeatherDao = appDatabase.customWeatherDao()
        lifecycleScope.launch {
            val allWeatherData = customWeatherDao.getAllWeatherData()
            if (allWeatherData.isNotEmpty()) {

                binding.apply {

                    val date = sharedPreferences.getString(KEY_DATE, null)
                    textLastupdate.text = "Last Updated : "+ date

                    textDescription.text = allWeatherData[0].description
                    textMaintemp.text = allWeatherData[0].temp?.let { kelvinToCelsius(it).toString() } + "°C"
                    textMaxtemp.text = "Min : "+ allWeatherData[0].temp_max?.let { kelvinToCelsius(it).toString() } + "°C"
                    textMintemp.text = "Max : "+ allWeatherData[0].temp_min?.let { kelvinToCelsius(it).toString() } + "°C"
                    textFeelslike.text = "Feels like : "+ allWeatherData[0].feels_like?.let { kelvinToCelsius(it).toString() } + "°C"
                    textHumidityval.text = allWeatherData[0].humidity.toString()
                    textPressureval.text = allWeatherData[0].pressure.toString()
                    textWindval.text = allWeatherData[0].speed.toString()
                    textMainsky.text = allWeatherData[0].mainsky.toString()
                    textName.text = allWeatherData[0].name.toString()


                    textSky1.text = allWeatherData.get(1).mainsky
                    textMax1.text = allWeatherData.get(1).temp_max?.let { kelvinToCelsius(it).toString() } + "°C"
                    textMin1.text = allWeatherData.get(1).temp_min?.let { kelvinToCelsius(it).toString() } + "°C"
                    textCitytemp1.text = allWeatherData.get(1).temp?.let { kelvinToCelsius(it).toString() } + "°C"

                    textSky2.text = allWeatherData.get(2).mainsky
                    textMax2.text = allWeatherData.get(2).temp_max?.let { kelvinToCelsius(it).toString() } + "°C"
                    textMin2.text = allWeatherData.get(2).temp_min?.let { kelvinToCelsius(it).toString() } + "°C"
                    textCitytemp2.text = allWeatherData.get(2).temp?.let { kelvinToCelsius(it).toString() } + "°C"

                    textSky3.text = allWeatherData.get(3).mainsky
                    textMax3.text = allWeatherData.get(3).temp_max?.let { kelvinToCelsius(it).toString() } + "°C"
                    textMin3.text = allWeatherData.get(3).temp_min?.let { kelvinToCelsius(it).toString() } + "°C"
                    textCitytemp3.text = allWeatherData.get(3).temp?.let { kelvinToCelsius(it).toString() } + "°C"

                    textSky4.text = allWeatherData.get(4).mainsky
                    textMax4.text = allWeatherData.get(4).temp_max?.let { kelvinToCelsius(it).toString() } + "°C"
                    textMin4.text = allWeatherData.get(4).temp_min?.let { kelvinToCelsius(it).toString() } + "°C"
                    textCitytemp4.text = allWeatherData.get(4).temp?.let { kelvinToCelsius(it).toString() } + "°C"

                    textSky5.text = allWeatherData.get(5).mainsky
                    textMax5.text = allWeatherData.get(5).temp_max?.let { kelvinToCelsius(it).toString() } + "°C"
                    textMin5.text = allWeatherData.get(5).temp_min?.let { kelvinToCelsius(it).toString() } + "°C"
                    textCitytemp5.text = allWeatherData.get(5).temp?.let { kelvinToCelsius(it).toString() } + "°C"

                    textSky6.text = allWeatherData.get(6).mainsky
                    textMax6.text = allWeatherData.get(6).temp_max?.let { kelvinToCelsius(it).toString() } + "°C"
                    textMin6.text = allWeatherData.get(6).temp_min?.let { kelvinToCelsius(it).toString() } + "°C"
                    textCitytemp6.text = allWeatherData.get(6).temp?.let { kelvinToCelsius(it).toString() } + "°C"
                }

            }
        }

    }


    @SuppressLint("SetTextI18n")
    fun viewModelObserver(currlat: Double, currlong: Double, apiKey: String) {
        Log.d("locationflow", "inside vm")
        w_viewModel = ViewModelProvider(this@MainActivity)[WeatherViewModel::class.java]

        val coordinates = listOf(
            Pair(currlat, currlong),
            Pair(40.7128, -74.0060),
            Pair(1.3521, 103.8198),
            Pair(19.0760, 72.8777),
            Pair(28.7041, 77.1025),
            Pair(-33.8688, 151.2093),
            Pair(-37.8136, 144.9631)

        )
        w_viewModel.fetchWeatherDataForCoordinates(coordinates, apiKey)

        w_viewModel.weatherListLiveData.observe(this@MainActivity) { weatherList ->

            binding.apply {
                for ((index, weather) in weatherList.withIndex()) {
                    val name = weather.name.toString()
                    val temp = weather.temp.toString()
                    val temp_min = weather.temp_min.toString()
                    val temp_max = weather.temp_max.toString()
                    val description = weather.description.toString()
                    val feels_like = weather.feels_like.toString()
                    val speed = weather.speed.toString()
                    val pressure = weather.pressure.toString()
                    val humidity = weather.humidity.toString()
                    val mainsky = weather.mainsky.toString()

                    lifecycleScope.launch {
                        val eachWeatherobj = RoomWeatherEnitity(
                            id = index + 1,
                            name = name,
                            temp = temp.toDouble(),
                            temp_min = temp_min.toDouble(),
                            temp_max = temp_max.toDouble(),
                            description = description,
                            feels_like = feels_like.toDouble(),
                            speed = speed.toDouble(),
                            pressure = pressure.toInt(),
                            humidity = humidity.toInt(),
                            mainsky = mainsky
                        )
                        appDatabase.customWeatherDao().insert(eachWeatherobj)
                    }

                    when (index) {
                        0 -> {
                            textName.text = name
                            textDescription.text = description
                            textMaintemp.text = (kelvinToCelsius(temp.toDouble()).toString() + "°C")
                            textMaxtemp.text = ("Max : "+ kelvinToCelsius(temp_max.toDouble()).toString() + "°C")
                            textMintemp.text = ("Min : "+ kelvinToCelsius(temp_min.toDouble()).toString() + "°C")
                            textFeelslike.text = ("Feels like : "+ kelvinToCelsius(feels_like.toDouble()).toString() + "°C")
                            textHumidityval.text = humidity
                            textPressureval.text = pressure
                            textWindval.text = speed
                            textMainsky.text = mainsky
                        }
                        1 -> {
                            textSky1.text = mainsky
                            textMax1.text = ("Max : "+ kelvinToCelsius(temp_max.toDouble()).toString() + "°C")
                            textMin1.text = ("Min : "+ kelvinToCelsius(temp_min.toDouble()).toString() + "°C")
                            textCitytemp1.text = (kelvinToCelsius(temp.toDouble()).toString() + "°C")
                        }
                        2 -> {
                            textSky2.text = mainsky
                            textMax2.text = ("Max : "+ kelvinToCelsius(temp_max.toDouble()).toString() + "°C")
                            textMin2.text = ("Min : "+ kelvinToCelsius(temp_min.toDouble()).toString() + "°C")
                            textCitytemp2.text = (kelvinToCelsius(temp.toDouble()).toString() + "°C")
                        }
                        3 -> {
                            textSky3.text = mainsky
                            textMax3.text = ("Max : "+ kelvinToCelsius(temp_max.toDouble()).toString() + "°C")
                            textMin3.text = ("Min : "+ kelvinToCelsius(temp_min.toDouble()).toString() + "°C")
                            textCitytemp3.text = (kelvinToCelsius(temp.toDouble()).toString() + "°C")
                        }
                        4 -> {
                            textSky4.text = mainsky
                            textMax4.text = ("Max : "+ kelvinToCelsius(temp_max.toDouble()).toString() + "°C")
                            textMin4.text = ("Min : "+ kelvinToCelsius(temp_min.toDouble()).toString() + "°C")
                            textCitytemp4.text = (kelvinToCelsius(temp.toDouble()).toString() + "°C")
                        }
                        5 -> {
                            textSky5.text = mainsky
                            textMax5.text = ("Max : "+ kelvinToCelsius(temp_max.toDouble()).toString() + "°C")
                            textMin5.text = ("Min : "+ kelvinToCelsius(temp_min.toDouble()).toString() + "°C")
                            textCitytemp5.text = (kelvinToCelsius(temp.toDouble()).toString() + "°C")
                        }
                        6 -> {
                            textSky6.text = mainsky
                            textMax6.text = ("Max : "+ kelvinToCelsius(temp_max.toDouble()).toString() + "°C")
                            textMin6.text = ("Min : "+ kelvinToCelsius(temp_min.toDouble()).toString() + "°C")
                            textCitytemp6.text = (kelvinToCelsius(temp.toDouble()).toString() + "°C")
                        }

                    }

                }
            }

        }
    }


    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkReceiver, intentFilter)
    }


    override fun onPause() {
        super.onPause()
        unregisterReceiver(networkReceiver)
    }




}





