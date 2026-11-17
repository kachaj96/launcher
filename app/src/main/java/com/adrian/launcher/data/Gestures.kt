package com.adrian.launcher.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

enum class Gesture(val title: String) {
    SWIPE_UP("Przesunięcie w górę"),
    SWIPE_DOWN("Przesunięcie w dół"),
    SWIPE_RIGHT("W prawo na pierwszej stronie"),
    SWIPE_LEFT("W lewo na ostatniej stronie"),
    LONG_PRESS("Przytrzymanie"),
    DOUBLE_TAP("Dwukrotne stuknięcie"),
    TAP_CLOCK("Stuknięcie w godzinę"),
    TAP_WEATHER("Stuknięcie w pogodę"),
}

enum class GestureAction(val title: String) {
    NONE("Nic"),
    DRAWER("Szuflada aplikacji"),
    NOTIFICATIONS("Powiadomienia"),
    SEARCH("Wyszukiwanie"),
    SETTINGS("Ustawienia launchera"),
    CLOCK("Zegar"),
    RECENT_CALLS("Ostatnie połączenia"),
    CONTACTS("Kontakty"),
    CAMERA("Aparat"),
    FLASHLIGHT("Latarka"),
    FORECAST("Prognoza godzinowa"),
    APP("Inna aplikacja"),
}

/** What a gesture does; [appKey] is set only for [GestureAction.APP]. */
data class GestureBinding(val action: GestureAction, val appKey: String? = null) {
    fun encode(): String = if (action == GestureAction.APP && appKey != null) "APP:$appKey" else action.name

    companion object {
        fun decode(raw: String): GestureBinding? = when {
            raw.startsWith("APP:") -> GestureBinding(GestureAction.APP, raw.removePrefix("APP:"))
            else -> runCatching { GestureBinding(GestureAction.valueOf(raw)) }.getOrNull()
        }
    }
}

val DEFAULT_GESTURES: Map<Gesture, GestureBinding> = mapOf(
    Gesture.SWIPE_UP to GestureBinding(GestureAction.DRAWER),
    Gesture.SWIPE_DOWN to GestureBinding(GestureAction.NOTIFICATIONS),
    Gesture.SWIPE_RIGHT to GestureBinding(GestureAction.RECENT_CALLS),
    Gesture.SWIPE_LEFT to GestureBinding(GestureAction.NONE),
    Gesture.LONG_PRESS to GestureBinding(GestureAction.SETTINGS),
    Gesture.DOUBLE_TAP to GestureBinding(GestureAction.FLASHLIGHT),
    Gesture.TAP_CLOCK to GestureBinding(GestureAction.CLOCK),
    Gesture.TAP_WEATHER to GestureBinding(GestureAction.FORECAST),
)

fun encodeGestures(map: Map<Gesture, GestureBinding>): JSONObject =
    JSONObject().apply { map.forEach { (g, b) -> put(g.name, b.encode()) } }

fun decodeGestures(o: JSONObject?): Map<Gesture, GestureBinding> {
    val stored = o?.keys()?.asSequence()?.mapNotNull { k ->
        val g = runCatching { Gesture.valueOf(k) }.getOrNull() ?: return@mapNotNull null
        val b = GestureBinding.decode(o.getString(k)) ?: return@mapNotNull null
        g to b
    }?.toMap().orEmpty()
    return DEFAULT_GESTURES + stored
}

/** One app launch, for time-of-day suggestions. */
data class LaunchRecord(val key: String, val at: Long)

const val LAUNCH_LOG_SIZE = 400

/**
 * Apps you usually open around this time: each past launch counts more when it happened
 * near the same hour, on the same kind of day (workday or weekend), and recently.
 * Falls back to plain recency while the history is still thin.
 */
fun suggestApps(log: List<LaunchRecord>, now: LocalDateTime, allowed: Set<String>, count: Int = 4): List<String> {
    val zone = ZoneId.systemDefault()
    val nowHour = now.hour + now.minute / 60.0
    val weekend = now.dayOfWeek.value >= 6
    val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    val scores = HashMap<String, Double>()
    for (r in log) {
        if (r.key !in allowed) continue
        val t = Instant.ofEpochMilli(r.at).atZone(zone).toLocalDateTime()
        val h = t.hour + t.minute / 60.0
        val diff = abs(h - nowHour).let { min(it, 24 - it) }
        if (diff > 2.0) continue
        val timeWeight = 1.0 - diff / 2.5
        val dayWeight = if ((t.dayOfWeek.value >= 6) == weekend) 1.0 else 0.4
        val ageDays = (nowMillis - r.at) / 86_400_000.0
        val recency = exp(-ageDays / 21.0)
        scores[r.key] = (scores[r.key] ?: 0.0) + timeWeight * dayWeight * recency
    }
    val ranked = scores.entries.sortedByDescending { it.value }.map { it.key }
    val recent = log.asReversed().map { it.key }.filter { it in allowed }.distinct()
    return (ranked + recent).distinct().take(count)
}
