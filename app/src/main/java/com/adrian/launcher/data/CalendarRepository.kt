package com.adrian.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CalendarEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
) {
    /** "10:00", "jutro 09:30", "trwa do 11:00" or "cały dzień". */
    fun whenLabel(now: Long = System.currentTimeMillis()): String {
        if (allDay) return "cały dzień"
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        if (begin <= now) return "trwa do " + Instant.ofEpochMilli(end).atZone(zone).format(fmt)
        val start = Instant.ofEpochMilli(begin).atZone(zone)
        val today = LocalDate.now(zone)
        val prefix = if (start.toLocalDate() == today) "" else "jutro "
        return prefix + start.format(fmt)
    }
}

class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** The next timed event (or one in progress) within 24 h; an all-day event only if there is nothing else. */
    fun nextEvent(): CalendarEvent? {
        if (!hasPermission()) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + 24 * 60 * 60 * 1000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val events = mutableListOf<CalendarEvent>()
        runCatching {
            context.contentResolver.query(
                uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    events += CalendarEvent(
                        title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(bez tytułu)",
                        begin = c.getLong(1),
                        end = c.getLong(2),
                        allDay = c.getInt(3) == 1,
                    )
                }
            }
        }
        val upcoming = events.filter { it.end > now }
        return upcoming.firstOrNull { !it.allDay } ?: upcoming.firstOrNull()
    }
}
