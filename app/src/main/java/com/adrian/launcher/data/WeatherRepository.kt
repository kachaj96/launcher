package com.adrian.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.adrian.launcher.ui.Polish
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

const val WEATHER_MAX_AGE_MS = 30 * 60 * 1000L

/** One hour of the forecast; [time] is local "HH:mm". */
data class HourForecast(val time: String, val temperature: Double, val code: Int)

data class WeatherNow(
    val temperature: Double,
    val apparent: Double,
    val humidity: Int,
    val windKmh: Double,
    val code: Int,
    val place: String,
    val fetchedAt: Long,
    val hourly: List<HourForecast> = emptyList(),
) {
    val description: String get() = describeWeather(code)

    fun toJson(): String = JSONObject()
        .put("t", temperature).put("a", apparent).put("h", humidity)
        .put("w", windKmh).put("c", code).put("p", place).put("at", fetchedAt)
        .put("hr", org.json.JSONArray(hourly.map { JSONObject().put("t", it.time).put("v", it.temperature).put("c", it.code) }))
        .toString()

    companion object {
        fun fromJson(raw: String): WeatherNow? = runCatching {
            if (raw.isBlank()) return null
            val o = JSONObject(raw)
            val hr = o.optJSONArray("hr")
            val hourly = if (hr == null) emptyList() else (0 until hr.length()).map { i ->
                val h = hr.getJSONObject(i)
                HourForecast(h.getString("t"), h.getDouble("v"), h.getInt("c"))
            }
            WeatherNow(
                o.getDouble("t"), o.getDouble("a"), o.getInt("h"),
                o.getDouble("w"), o.getInt("c"), o.optString("p"), o.getLong("at"), hourly,
            )
        }.getOrNull()
    }
}

sealed interface WeatherResult {
    data class Success(val weather: WeatherNow) : WeatherResult
    /** Neither a location (no permission or no fix) nor a fallback city. */
    data object NoLocation : WeatherResult
    /** City name the geocoder does not know. */
    data object CityNotFound : WeatherResult
    data object NetworkError : WeatherResult
}

/** WMO weather codes as used by Open-Meteo, in Polish. */
fun describeWeather(code: Int): String = when (code) {
    0 -> "bezchmurnie"
    1 -> "przeważnie bezchmurnie"
    2 -> "częściowe zachmurzenie"
    3 -> "pochmurno"
    45, 48 -> "mgła"
    51, 53, 55 -> "mżawka"
    56, 57 -> "marznąca mżawka"
    61 -> "słaby deszcz"
    63 -> "deszcz"
    65 -> "ulewa"
    66, 67 -> "marznący deszcz"
    71 -> "słaby śnieg"
    73 -> "śnieg"
    75 -> "intensywny śnieg"
    77 -> "ziarna śniegu"
    80, 81, 82 -> "przelotny deszcz"
    85, 86 -> "przelotny śnieg"
    95 -> "burza"
    96, 99 -> "burza z gradem"
    else -> "brak opisu"
}

class WeatherRepository(private val context: Context) {

    private data class Place(val lat: Double, val lon: Double, val name: String)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun fetch(useGps: Boolean, city: String): WeatherResult = withContext(Dispatchers.IO) {
        val fromGps = if (useGps && hasLocationPermission()) locate() else null
        val place = fromGps ?: run {
            if (city.isBlank()) return@withContext WeatherResult.NoLocation
            val g = geocode(city) ?: return@withContext WeatherResult.NetworkError
            if (g === GEOCODE_MISS) return@withContext WeatherResult.CityNotFound
            g
        }
        val forecast = runCatching { forecast(place) }.getOrNull()
            ?: return@withContext WeatherResult.NetworkError
        WeatherResult.Success(forecast)
    }

    private fun forecast(place: Place): WeatherNow {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code" +
                "&hourly=temperature_2m,weather_code&forecast_hours=13" +
                "&timezone=auto",
            place.lat, place.lon,
        )
        val root = JSONObject(httpGet(url))
        val current = root.getJSONObject("current")
        val hourly = root.optJSONObject("hourly")?.let { h ->
            val times = h.getJSONArray("time")
            val temps = h.getJSONArray("temperature_2m")
            val codes = h.getJSONArray("weather_code")
            (0 until times.length()).map { i ->
                // "2026-11-17T11:00" -> "11:00"
                HourForecast(times.getString(i).substringAfter('T'), temps.getDouble(i), codes.getInt(i))
            }.drop(1).take(12)
        }.orEmpty()
        return WeatherNow(
            temperature = current.getDouble("temperature_2m"),
            apparent = current.getDouble("apparent_temperature"),
            humidity = current.getInt("relative_humidity_2m"),
            windKmh = current.getDouble("wind_speed_10m"),
            code = current.getInt("weather_code"),
            place = place.name,
            fetchedAt = System.currentTimeMillis(),
            hourly = hourly,
        )
    }

    /** Returns the place, [GEOCODE_MISS] when the name is unknown, or null on a network failure. */
    private fun geocode(city: String): Place? = runCatching {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(city.trim(), "UTF-8") + "&count=1&language=pl&format=json"
        val results = JSONObject(httpGet(url)).optJSONArray("results")
        if (results == null || results.length() == 0) return GEOCODE_MISS
        val r = results.getJSONObject(0)
        Place(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name", city))
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun locate(): Place? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = manager.getProviders(true)
        val last: Location? = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val fresh = last?.takeIf { System.currentTimeMillis() - it.time < 3 * 60 * 60 * 1000L }
        val location = fresh ?: currentLocation(manager, providers) ?: last ?: return null
        return Place(location.latitude, location.longitude, placeName(location))
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(manager: LocationManager, providers: List<String>): Location? {
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in providers } ?: return null
        return withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    manager.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
                }.onFailure { cont.resume(null) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun placeName(location: Location): String = runCatching {
        if (!Geocoder.isPresent()) return ""
        val a = Geocoder(context, Polish).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: ""
    }.getOrDefault("")

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val GEOCODE_MISS = Place(Double.NaN, Double.NaN, "")
    }
}
