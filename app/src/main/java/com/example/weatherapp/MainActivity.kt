package com.example.weatherapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.weatherapp.ui.theme.WeatherAppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------------
// DATA MODELS
// ---------------------------------------------------------------------------------

data class HourlyModel(
    val hour: String,
    val temp: Int,
    val picPath: String
)

data class FutureModel(
    val day: String,
    val picPath: String,
    val status: String,
    val highTemp: Int,
    val lowTemp: Int
)

// ---------------------------------------------------------------------------------
// MAIN ACTIVITY
// ---------------------------------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge with no layout limits
        enableEdgeToEdge()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContent {
            WeatherAppTheme {
                Surface {
                    WeatherScreen()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// HELPER: MAP STRING PATH TO DRAWABLE RESOURCE
// ---------------------------------------------------------------------------------

fun getDrawableResourceId(picPath: String): Int {
    return when (picPath) {
        "storm" -> R.drawable.storm
        "cloudy" -> R.drawable.cloudy
        "sunny" -> R.drawable.sunny
        "rainy" -> R.drawable.rainy
        "windy" -> R.drawable.windy
        "cloudy_sunny" -> R.drawable.cloudy_sunny
        "snowy" -> R.drawable.snowy
        else -> R.drawable.cloudy_sunny
    }
}

// ---------------------------------------------------------------------------------
// MAIN SCREEN
// ---------------------------------------------------------------------------------

@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.onLocationPermissionGranted() else viewModel.onLocationPermissionDenied()
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.onLocationPermissionGranted()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF59469D), Color(0xFF643D67))
                )
            )
    ) {
        when {
            uiState.isLoading && uiState.dailyItems.isEmpty() -> {
                LoadingState()
            }
            uiState.error != null && uiState.dailyItems.isEmpty() -> {
                ErrorState(message = uiState.error ?: "Something went wrong") {
                    viewModel.refreshNow()
                }
            }
            else -> {
                WeatherContent(uiState = uiState, onRefresh = { viewModel.refreshNow() })
            }
        }
    }
}

@Composable
fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Fetching your weather…", fontSize = 15.sp, color = Color.White)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = message,
                fontSize = 15.sp,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tap to retry",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.clickable { onRetry() }
            )
        }
    }
}

@Composable
fun WeatherContent(uiState: WeatherUiState, onRefresh: () -> Unit) {
    val statusText = when {
        uiState.isRefreshing -> "Refreshing…"
        uiState.error != null -> "Couldn't refresh · Tap to retry"
        uiState.lastUpdated > 0 ->
            "Updated " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(uiState.lastUpdated)) + " · Tap to refresh"
        else -> ""
    }
    val statusColor = if (uiState.error != null) Color(0xFFFFCDD2) else Color.White.copy(alpha = 0.7f)

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar: Back Icon & City Name
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.back),
                    contentDescription = "Back",
                    modifier = Modifier.size(24.dp)
                )
                if (uiState.cityName.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = uiState.cityName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // 1. Current Weather Header
        item {
            Text(
                text = uiState.condition,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // 2. Main Weather Icon
        item {
            Image(
                painter = painterResource(id = getDrawableResourceId(uiState.iconName)),
                contentDescription = "Current weather icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .size(150.dp)
            )
        }

        // 3. Date & Time
        item {
            Text(
                text = uiState.dateTimeText,
                fontSize = 19.sp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // 4. Temperature
        item {
            Text(
                text = "${uiState.currentTemp}°",
                fontSize = 63.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // 5. High/Low Temp
        item {
            Text(
                text = "H:${uiState.highTemp}° L:${uiState.lowTemp}°",
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // 5b. Last Updated / Refresh / Refreshing / Error status
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 24.dp)
                    .clickable { onRefresh() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier
                            .size(12.dp)
                            .padding(end = 6.dp)
                    )
                }
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = statusColor
                )
            }
        }

        // 6. Weather Details Box
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(colorResource(id = R.color.purple))
                    .padding(vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WeatherDetailItem(
                        icon = R.drawable.rain,
                        value = "${uiState.rainChance}%",
                        label = "Rain"
                    )
                    WeatherDetailItem(
                        icon = R.drawable.wind,
                        value = "${uiState.windSpeed} Km/h",
                        label = "Wind Speed"
                    )
                    WeatherDetailItem(
                        icon = R.drawable.humidity,
                        value = "${uiState.humidity}%",
                        label = "Humidity"
                    )
                }
            }
        }

        // 7. Today Label
        item {
            Text(
                text = "Today",
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 28.dp, bottom = 12.dp)
            )
        }

        // 8. Hourly Forecast Row
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.hourlyItems) { model ->
                    HourlyModelViewHolder(model = model)
                }
            }
        }

        // 9. Future Label
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Future",
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Next 7 days>",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // 10. Daily Forecast List
        items(uiState.dailyItems) { item ->
            FutureItem(item = item)
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------
// WEATHER DETAIL ITEM (Rain / Wind / Humidity)
// ---------------------------------------------------------------------------------

@Composable
fun WeatherDetailItem(icon: Int, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = label,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(id = R.color.white)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = colorResource(id = R.color.white)
        )
    }
}

// ---------------------------------------------------------------------------------
// HOURLY FORECAST ITEM
// ---------------------------------------------------------------------------------

@Composable
fun HourlyModelViewHolder(model: HourlyModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colorResource(id = R.color.purple))
            .padding(vertical = 16.dp, horizontal = 14.dp)
    ) {
        Text(
            text = model.hour,
            fontSize = 14.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(10.dp))
        Image(
            painter = painterResource(id = getDrawableResourceId(model.picPath)),
            contentDescription = model.hour,
            modifier = Modifier.size(45.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "${model.temp}°",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// ---------------------------------------------------------------------------------
// DAILY FORECAST ITEM
// ---------------------------------------------------------------------------------

@Composable
fun FutureItem(item: FutureModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.day,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.width(90.dp)
        )

        Image(
            painter = painterResource(id = getDrawableResourceId(item.picPath)),
            contentDescription = item.status,
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.status,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${item.highTemp}°",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(end = 8.dp)
        )

        Text(
            text = "${item.lowTemp}°",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}
