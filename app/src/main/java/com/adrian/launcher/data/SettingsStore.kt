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

    // Search
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    /** Open results in Brave (true) or the default browser. */
    val searchInBrave: Boolean = true,
    val searchShowsApps: Boolean = true,
    val calculator: Boolean = true,
    /** "Zapytaj Claude" row in the search results. */
    val askClaude: Boolean = true,

    // Drawer
    val drawerAutoLaunch: Boolean = true,
    val showRecent: Boolean = true,

    // Home
    val nowPlaying: Boolean = true,
    val notificationDots: Boolean = true,
    val tapActions: Boolean = true,
    val haptics: Boolean = true,
    val swipeDownEnabled: Boolean = true,

    // Focus: apps hidden from home and drawer during a schedule
    val focusMode: FocusMode = FocusMode.OFF,
    val focusApps: Set<String> = emptySet(),
    /** ISO days, 1 = Monday. */
    val focusDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** Minutes after midnight. */
    val focusStart: Int = 9 * 60,
    val focusEnd: Int = 17 * 60,

    // Evening: dimmer screen and a warm accent
    val eveningMode: Boolean = false,
    val eveningStart: Int = 22 * 60,
    val eveningEnd: Int = 6 * 60,

    val gestures: Map<Gesture, GestureBinding> = DEFAULT_GESTURES,
    /** Recent launches, oldest first, for suggestions; never exported. */
    val launchLog: List<LaunchRecord> = emptyList(),
    val suggestions: Boolean = true,
    val chargingRing: Boolean = true,
) {
    fun gesture(g: Gesture): GestureBinding = gestures[g] ?: DEFAULT_GESTURES.getValue(g)

    val enabledPages: List<PageId>
        get() = pages.filter { it.enabled }.map { it.id }.ifEmpty { listOf(PageId.CLOCK) }

    fun focusActive(now: java.time.LocalDateTime): Boolean = when (focusMode) {
        FocusMode.OFF -> false
        FocusMode.ALWAYS -> true
        FocusMode.SCHEDULE -> now.dayOfWeek.value in focusDays && inWindow(now, focusStart, focusEnd)
    }

    fun eveningActive(now: java.time.LocalDateTime): Boolean = eveningMode && inWindow(now, eveningStart, eveningEnd)
}

enum class SearchEngine(val title: String) {
    GOOGLE("Google"),
    BRAVE("Brave Search"),
    DUCKDUCKGO("DuckDuckGo");

    fun url(query: String): String {
        val q = android.net.Uri.encode(query)
        return when (this) {
            GOOGLE -> "https://www.google.com/search?q=$q"
            BRAVE -> "https://search.brave.com/search?q=$q"
            DUCKDUCKGO -> "https://duckduckgo.com/?q=$q"
        }
    }
}

enum class FocusMode(val title: String) {
    OFF("Wyłączony"),
    SCHEDULE("Według harmonogramu"),
    ALWAYS("Zawsze"),
}

/** True when the time of day falls in [start, end); a window may cross midnight (22:00-6:00). */
private fun inWindow(now: java.time.LocalDateTime, start: Int, end: Int): Boolean {
    val m = now.hour * 60 + now.minute
    return if (start <= end) m in start until end else m >= start || m < end
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
        encode(settings.copy(weatherCache = "", lastLaunch = emptyMap(), launchLog = emptyList())).toString(2)

    suspend fun importJson(json: String) {
        val imported = decodeJson(JSONObject(json))
        update { old ->
            imported.copy(
                weatherCache = old.weatherCache,
                lastLaunch = old.lastLaunch,
                launchLog = old.launchLog,
                calendarWidgetId = old.calendarWidgetId,
            )
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
            searchEngine = enumOr(o.optString("searchEngine"), d.searchEngine),
            searchInBrave = o.optBoolean("searchInBrave", d.searchInBrave),
            searchShowsApps = o.optBoolean("searchShowsApps", d.searchShowsApps),
            calculator = o.optBoolean("calculator", d.calculator),
            askClaude = o.optBoolean("askClaude", d.askClaude),
            drawerAutoLaunch = o.optBoolean("drawerAutoLaunch", d.drawerAutoLaunch),
            showRecent = o.optBoolean("showRecent", d.showRecent),
            nowPlaying = o.optBoolean("nowPlaying", d.nowPlaying),
            notificationDots = o.optBoolean("notificationDots", d.notificationDots),
            tapActions = o.optBoolean("tapActions", d.tapActions),
            haptics = o.optBoolean("haptics", d.haptics),
            swipeDownEnabled = o.optBoolean("swipeDownEnabled", d.swipeDownEnabled),
            focusMode = enumOr(o.optString("focusMode"), d.focusMode),
            focusApps = o.optJSONArray("focusApps").strings().toSet(),
            focusDays = o.optJSONArray("focusDays")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: d.focusDays,
            focusStart = o.optInt("focusStart", d.focusStart),
            focusEnd = o.optInt("focusEnd", d.focusEnd),
            eveningMode = o.optBoolean("eveningMode", d.eveningMode),
            eveningStart = o.optInt("eveningStart", d.eveningStart),
            eveningEnd = o.optInt("eveningEnd", d.eveningEnd),
            gestures = decodeGestures(o.optJSONObject("gestures")).let { g ->
                if (o.has("gestures")) g else g.toMutableMap().apply {
                    // Carry over the switches that existed before gestures were configurable.
                    if (!o.optBoolean("swipeDownEnabled", true)) put(Gesture.SWIPE_DOWN, GestureBinding(GestureAction.NONE))
                    if (!o.optBoolean("tapActions", true)) {
                        put(Gesture.TAP_CLOCK, GestureBinding(GestureAction.NONE))
                        put(Gesture.TAP_WEATHER, GestureBinding(GestureAction.NONE))
                    }
                }
            },
            launchLog = o.optJSONArray("launchLog")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val r = a.optJSONObject(i) ?: return@mapNotNull null
                    LaunchRecord(r.getString("k"), r.getLong("t"))
                }
            }.orEmpty(),
            suggestions = o.optBoolean("suggestions", d.suggestions),
            chargingRing = o.optBoolean("chargingRing", d.chargingRing),
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
        put("searchEngine", s.searchEngine.name)
        put("searchInBrave", s.searchInBrave)
        put("searchShowsApps", s.searchShowsApps)
        put("calculator", s.calculator)
        put("askClaude", s.askClaude)
        put("drawerAutoLaunch", s.drawerAutoLaunch)
        put("showRecent", s.showRecent)
        put("nowPlaying", s.nowPlaying)
        put("notificationDots", s.notificationDots)
        put("tapActions", s.tapActions)
        put("haptics", s.haptics)
        put("swipeDownEnabled", s.swipeDownEnabled)
        put("focusMode", s.focusMode.name)
        put("focusApps", JSONArray(s.focusApps.toList()))
        put("focusDays", JSONArray(s.focusDays.sorted()))
        put("focusStart", s.focusStart)
        put("focusEnd", s.focusEnd)
        put("eveningMode", s.eveningMode)
        put("eveningStart", s.eveningStart)
        put("eveningEnd", s.eveningEnd)
        put("gestures", encodeGestures(s.gestures))
        put("launchLog", JSONArray(s.launchLog.map { JSONObject().put("k", it.key).put("t", it.at) }))
        put("suggestions", s.suggestions)
        put("chargingRing", s.chargingRing)
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    runCatching { enumValueOf<E>(name) }.getOrDefault(default)

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

private fun JSONObject?.stringMap(): Map<String, String> =
    if (this == null) emptyMap() else keys().asSequence().associateWith { getString(it) }
