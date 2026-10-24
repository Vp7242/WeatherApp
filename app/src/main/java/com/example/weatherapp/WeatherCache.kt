package com.example.weatherapp

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private val Context.weatherDataStore by preferencesDataStore(name = "weather_cache")

/**
 * Persists the last successfully fetched [WeatherUiState] to disk so the app can show
 * something meaningful immediately on launch, before the network call completes -
 * and can still show useful data if a refresh fails (e.g. no connectivity).
 */
class WeatherCache(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val WEATHER_JSON = stringPreferencesKey("weather_json")
    }

    suspend fun save(state: WeatherUiState) {
        try {
            context.weatherDataStore.edit { prefs ->
                prefs[Keys.WEATHER_JSON] = gson.toJson(state)
            }
        } catch (_: Exception) {
            // Caching is best-effort; failures here shouldn't break the app.
        }
    }

    suspend fun load(): WeatherUiState? {
        return try {
            val prefs = context.weatherDataStore.data.first()
            val json = prefs[Keys.WEATHER_JSON] ?: return null
            gson.fromJson(json, WeatherUiState::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
