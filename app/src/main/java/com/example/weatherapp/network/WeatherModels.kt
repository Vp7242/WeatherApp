package com.example.weatherapp.network

// ---------------------------------------------------------------------------------
// OPEN-METEO API RESPONSE MODELS
// Docs: https://open-meteo.com/en/docs (free, no API key required)
// ---------------------------------------------------------------------------------

data class OpenMeteoResponse(
    val current: CurrentWeather = CurrentWeather(),
    val hourly: HourlyWeather = HourlyWeather(),
    val daily: DailyWeather = DailyWeather()
)

data class CurrentWeather(
    val time: String = "",
    val temperature_2m: Double = 0.0,
    val relative_humidity_2m: Int = 0,
    val weather_code: Int = 0,
    val wind_speed_10m: Double = 0.0
)

data class HourlyWeather(
    val time: List<String> = emptyList(),
    val temperature_2m: List<Double?> = emptyList(),
    val weather_code: List<Int?> = emptyList(),
    val precipitation_probability: List<Int?> = emptyList()
)

data class DailyWeather(
    val time: List<String> = emptyList(),
    val weather_code: List<Int?> = emptyList(),
    val temperature_2m_max: List<Double?> = emptyList(),
    val temperature_2m_min: List<Double?> = emptyList()
)

// ---------------------------------------------------------------------------------
// WMO WEATHER CODE -> APP ICON / STATUS TEXT MAPPING
// ---------------------------------------------------------------------------------

fun weatherCodeToIconName(code: Int): String = when (code) {
    0 -> "sunny"
    1, 2 -> "cloudy_sunny"
    3 -> "cloudy"
    45, 48 -> "cloudy"
    51, 53, 55, 56, 57 -> "rainy"
    61, 63, 65, 66, 67 -> "rainy"
    71, 73, 75, 77 -> "snowy"
    80, 81, 82 -> "rainy"
    85, 86 -> "snowy"
    95, 96, 99 -> "storm"
    else -> "cloudy_sunny"
}

fun weatherCodeToStatusText(code: Int): String = when (code) {
    0 -> "Clear Sky"
    1 -> "Mainly Clear"
    2 -> "Partly Cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing Drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing Rain"
    71, 73, 75 -> "Snow"
    77 -> "Snow Grains"
    80, 81, 82 -> "Rain Showers"
    85, 86 -> "Snow Showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm w/ Hail"
    else -> "Mostly Cloudy"
}
