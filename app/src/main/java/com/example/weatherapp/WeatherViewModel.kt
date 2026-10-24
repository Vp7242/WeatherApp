package com.example.weatherapp

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weatherapp.network.OpenMeteoResponse
import com.example.weatherapp.network.WeatherApi
import com.example.weatherapp.network.weatherCodeToIconName
import com.example.weatherapp.network.weatherCodeToStatusText
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------
// UI STATE
// ---------------------------------------------------------------------------------

data class WeatherUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val cityName: String = "",
    val condition: String = "",
    val iconName: String = "cloudy_sunny",
    val currentTemp: Int = 0,
    val highTemp: Int = 0,
    val lowTemp: Int = 0,
    val humidity: Int = 0,
    val windSpeed: Int = 0,
    val rainChance: Int = 0,
    val dateTimeText: String = "",
    val hourlyItems: List<HourlyModel> = emptyList(),
    val dailyItems: List<FutureModel> = emptyList(),
    val lastUpdated: Long = 0L
)

private const val DEFAULT_LAT = 40.7128 // Fallback: New York City
private const val DEFAULT_LON = -74.0060
private const val AUTO_REFRESH_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private val cache = WeatherCache(application)

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var hasLocationPermission = false
    private var lastLocation: Location? = null
    private var autoRefreshJob: Job? = null
    private var fetchJob: Job? = null
    private var cacheLoadAttempted = false

    init {
        viewModelScope.launch {
            ensureCacheLoaded()
        }
    }

    fun onLocationPermissionGranted() {
        hasLocationPermission = true
        if (_uiState.value.lastUpdated == 0L && fetchJob?.isActive != true) {
            loadWeather(useLocation = true)
        }
        startAutoRefresh()
    }

    fun onLocationPermissionDenied() {
        hasLocationPermission = false
        if (_uiState.value.lastUpdated == 0L && fetchJob?.isActive != true) {
            loadWeather(useLocation = false)
        }
        startAutoRefresh()
    }

    fun refreshNow() {
        loadWeather(useLocation = hasLocationPermission)
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(AUTO_REFRESH_INTERVAL_MS)
                loadWeather(useLocation = hasLocationPermission)
            }
        }
    }

    /** Loads the cached state (once, on first call) so the UI has something to show instantly. */
    private suspend fun ensureCacheLoaded() {
        if (cacheLoadAttempted) return
        cacheLoadAttempted = true
        val cached = cache.load()
        if (cached != null && _uiState.value.dailyItems.isEmpty()) {
            _uiState.update { cached.copy(isLoading = false, isRefreshing = false, error = null) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadWeather(useLocation: Boolean) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            ensureCacheLoaded()

            val hasExistingData = _uiState.value.dailyItems.isNotEmpty()
            _uiState.update {
                if (hasExistingData) it.copy(isRefreshing = true, error = null)
                else it.copy(isLoading = true, error = null)
            }

            var lat = DEFAULT_LAT
            var lon = DEFAULT_LON
            var isUsingFallback = true

            if (useLocation) {
                try {
                    val location = withTimeoutOrNull(5000L) {
                        fusedLocationClient
                            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                            .await()
                    } ?: withTimeoutOrNull(2000L) {
                        fusedLocationClient.lastLocation.await()
                    }

                    if (location != null) {
                        lat = location.latitude
                        lon = location.longitude
                        lastLocation = location
                        isUsingFallback = false
                    } else if (lastLocation != null) {
                        lat = lastLocation!!.latitude
                        lon = lastLocation!!.longitude
                        isUsingFallback = false
                    }
                } catch (_: Exception) {
                    lastLocation?.let {
                        lat = it.latitude
                        lon = it.longitude
                        isUsingFallback = false
                    }
                }
            } else if (lastLocation != null) {
                lat = lastLocation!!.latitude
                lon = lastLocation!!.longitude
                isUsingFallback = false
            }

            try {
                val response = WeatherApi.service.getForecast(latitude = lat, longitude = lon)
                val cityName = fetchCityName(getApplication(), lat, lon, isUsingFallback)
                val newState = mapResponseToUiState(response, cityName)
                val finalState = newState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    lastUpdated = System.currentTimeMillis()
                )
                _uiState.update { finalState }
                cache.save(finalState)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = e.message ?: "Couldn't refresh weather. Check your connection."
                    )
                }
            }
        }
    }

    private suspend fun fetchCityName(
        context: Context,
        lat: Double,
        lon: Double,
        isUsingFallback: Boolean
    ): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val address = addresses?.firstOrNull()
            val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea
            val country = address?.countryName
            when {
                city != null -> city
                country != null -> country
                isUsingFallback -> "New York"
                else -> "Current Location"
            }
        } catch (_: Exception) {
            if (isUsingFallback) "New York" else "Current Location"
        }
    }

    private fun mapResponseToUiState(response: OpenMeteoResponse, cityName: String): WeatherUiState {
        val currentHourPrefix = if (response.current.time.length >= 13) response.current.time.substring(0, 13) else response.current.time
        val matchedIndex = response.hourly.time.indexOfFirst { it.startsWith(currentHourPrefix) }
        val currentIndex = if (matchedIndex >= 0) matchedIndex else 0

        val hourlyList = (currentIndex until minOf(currentIndex + 24, response.hourly.time.size)).map { i ->
            HourlyModel(
                hour = formatHour(response.hourly.time[i]),
                temp = (response.hourly.temperature_2m.getOrNull(i) ?: 0.0).roundToInt(),
                picPath = weatherCodeToIconName(response.hourly.weather_code.getOrNull(i) ?: 0)
            )
        }

        val dailyList = (1 until response.daily.time.size).take(7).map { i ->
            FutureModel(
                day = formatWeekday(response.daily.time[i]),
                picPath = weatherCodeToIconName(response.daily.weather_code.getOrNull(i) ?: 0),
                status = weatherCodeToStatusText(response.daily.weather_code.getOrNull(i) ?: 0),
                highTemp = (response.daily.temperature_2m_max.getOrNull(i) ?: 0.0).roundToInt(),
                lowTemp = (response.daily.temperature_2m_min.getOrNull(i) ?: 0.0).roundToInt()
            )
        }

        val rainChance = response.hourly.precipitation_probability.getOrNull(currentIndex) ?: 0
        val todayHigh = (response.daily.temperature_2m_max.getOrNull(0) ?: 0.0).roundToInt()
        val todayLow = (response.daily.temperature_2m_min.getOrNull(0) ?: 0.0).roundToInt()

        return WeatherUiState(
            isLoading = false,
            error = null,
            cityName = cityName,
            condition = weatherCodeToStatusText(response.current.weather_code),
            iconName = weatherCodeToIconName(response.current.weather_code),
            currentTemp = response.current.temperature_2m.roundToInt(),
            highTemp = todayHigh,
            lowTemp = todayLow,
            humidity = response.current.relative_humidity_2m,
            windSpeed = response.current.wind_speed_10m.roundToInt(),
            rainChance = rainChance,
            dateTimeText = formatDateTime(response.current.time),
            hourlyItems = hourlyList,
            dailyItems = dailyList
        )
    }

    private fun formatHour(isoTime: String): String = try {
        val cleanTime = if (isoTime.length >= 16) isoTime.substring(0, 16) else isoTime
        val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = sdfIn.parse(cleanTime)
        SimpleDateFormat("h a", Locale.getDefault()).format(date ?: Date())
    } catch (_: Exception) {
        isoTime
    }

    private fun formatDateTime(isoTime: String): String = try {
        val cleanTime = if (isoTime.length >= 16) isoTime.substring(0, 16) else isoTime
        val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = sdfIn.parse(cleanTime)
        SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(date ?: Date())
    } catch (_: Exception) {
        isoTime
    }

    private fun formatWeekday(isoDate: String): String = try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdfIn.parse(isoDate)
        SimpleDateFormat("EEEE", Locale.getDefault()).format(date ?: Date())
    } catch (_: Exception) {
        isoDate
    }
}
