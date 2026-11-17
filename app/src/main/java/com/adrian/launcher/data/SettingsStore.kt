package com.adrian.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "launcher")

enum class PageId(val title: String) {
    CLOCK("Zegar i pogoda"),
    CALENDAR("Kalendarz"),
}

/** Page ids from older versions: the day page became the calendar page, weather merged into the clock, the dial is gone. */
private fun pageIdOf(name: String): PageId? = when (name) {
    "DAY" -> PageId.CALENDAR
    else -> runCatching { PageId.valueOf(name) }.getOrNull()
}

/** No calendar widget chosen: the calendar page shows the built-in card. */
const val NO_WIDGET = -1

data class PageConfig(val id: PageId, val enabled: Boolean)

const val MAX_PINNED = 6

data class LauncherSettings(
    val pinned: List<String> = emptyList(),
    val pinnedInitialized: Boolean = false,
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val lastLaunch: Map<String, Long> = emptyMap(),
    val accent: Long = 0xFFFF8A3D,
    val pages: List<PageConfig> = PageId.entries.map { PageConfig(it, true) },
    val gradient: Float = 0.3f,
    val wallpaperVersion: Long = 0L,
    val name: String = "Adrian",
    val weatherUseGps: Boolean = true,
    val weatherCity: String = "",
    val weatherCache: String = "",
    val swipeDownBroken: Boolean = false,
    val calendarWidgetId: Int = NO_WIDGET,
    /** One-time cleanup of duplicate default pins from older versions has run. */
    val pinsDeduped: Boolean = false,
) {
    val enabledPages: List<PageId>
        get() = pages.filter { it.enabled }.map { it.id }.ifEmpty { listOf(PageId.CLOCK) }
}

/**
 * All launcher state lives in one JSON document inside DataStore. It is small, and one
 * format keeps export/import trivial.
 */
class SettingsStore(private val context: Context) {

    private val key = stringPreferencesKey("settings_json")

    val flow: Flow<LauncherSettings> = context.dataStore.data.map { decode(it) }

    suspend fun current(): LauncherSettings = flow.first()

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        context.dataStore.edit { prefs ->
            prefs[key] = encode(transform(decode(prefs))).toString()
        }
    }

    /** Settings for backup: everything except caches and launch history. */
    fun exportJson(settings: LauncherSettings): String =
        encode(settings.copy(weatherCache = "", lastLaunch = emptyMap())).toString(2)

    suspend fun importJson(json: String) {
        val imported = decodeJson(JSONObject(json))
        update { old ->
            imported.copy(weatherCache = old.weatherCache, lastLaunch = old.lastLaunch, calendarWidgetId = old.calendarWidgetId)
        }
    }

    private fun decode(prefs: Preferences): LauncherSettings {
        val raw = prefs[key] ?: return LauncherSettings()
        return runCatching { decodeJson(JSONObject(raw)) }.getOrDefault(LauncherSettings())
    }

    private fun decodeJson(o: JSONObject): LauncherSettings {
        val d = LauncherSettings()
        val pages = o.optJSONArray("pages")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val p = arr.getJSONObject(i)
                val id = pageIdOf(p.getString("id")) ?: return@mapNotNull null
                PageConfig(id, p.optBoolean("enabled", true))
            }
        }.orEmpty().distinctBy { it.id }
        // Keep any page added in a later version, appended at the end.
        val allPages = pages + PageId.entries.filter { id -> pages.none { it.id == id } }.map { PageConfig(it, true) }

        return LauncherSettings(
            pinned = o.optJSONArray("pinned").strings(),
            pinnedInitialized = o.optBoolean("pinnedInitialized", d.pinnedInitialized),
            hidden = o.optJSONArray("hidden").strings().toSet(),
            renames = o.optJSONObject("renames").stringMap(),
            lastLaunch = o.optJSONObject("lastLaunch")?.let { m -> m.keys().asSequence().associateWith { m.getLong(it) } }.orEmpty(),
            accent = o.optLong("accent", d.accent),
            pages = allPages,
            gradient = o.optDouble("gradient", d.gradient.toDouble()).toFloat(),
            wallpaperVersion = o.optLong("wallpaperVersion", d.wallpaperVersion),
            name = o.optString("name", d.name),
            weatherUseGps = o.optBoolean("weatherUseGps", d.weatherUseGps),
            weatherCity = o.optString("weatherCity", d.weatherCity),
            weatherCache = o.optString("weatherCache", d.weatherCache),
            swipeDownBroken = o.optBoolean("swipeDownBroken", d.swipeDownBroken),
            calendarWidgetId = o.optInt("calendarWidgetId", d.calendarWidgetId),
            pinsDeduped = o.optBoolean("pinsDeduped", d.pinsDeduped),
        )
    }

    private fun encode(s: LauncherSettings): JSONObject = JSONObject().apply {
        put("pinned", JSONArray(s.pinned))
        put("pinnedInitialized", s.pinnedInitialized)
        put("hidden", JSONArray(s.hidden.toList()))
        put("renames", JSONObject(s.renames as Map<*, *>))
        put("lastLaunch", JSONObject(s.lastLaunch as Map<*, *>))
        put("accent", s.accent)
        put("pages", JSONArray(s.pages.map { JSONObject().put("id", it.id.name).put("enabled", it.enabled) }))
        put("gradient", s.gradient.toDouble())
        put("wallpaperVersion", s.wallpaperVersion)
        put("name", s.name)
        put("weatherUseGps", s.weatherUseGps)
        put("weatherCity", s.weatherCity)
        put("weatherCache", s.weatherCache)
        put("swipeDownBroken", s.swipeDownBroken)
        put("calendarWidgetId", s.calendarWidgetId)
        put("pinsDeduped", s.pinsDeduped)
    }
}

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

private fun JSONObject?.stringMap(): Map<String, String> =
    if (this == null) emptyMap() else keys().asSequence().associateWith { getString(it) }
