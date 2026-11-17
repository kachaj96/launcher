package com.adrian.launcher.home

import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.adrian.launcher.data.CalendarEvent
import com.adrian.launcher.data.WeatherNow
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.PolishDate
import com.adrian.launcher.ui.Quiet
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** What the clock page shows when there is no weather, plus an optional fix action. */
data class WeatherHint(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/** Clock with today's date and the current weather underneath. */
@Composable
fun ClockWeatherPage(now: LocalDateTime, weather: WeatherNow?, hint: WeatherHint?) {
    Column(modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 32.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = now.hour.toString(),
                color = Ink,
                fontSize = 120.sp,
                lineHeight = 120.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "%02d".format(now.minute),
                color = Ink,
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp),
            )
        }
        Text(
            text = "${PolishDate.dayShort(now).uppercase()} · ${now.dayOfMonth} ${PolishDate.monthGenitive(now).uppercase()}",
            color = Ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(16.dp))
        if (weather != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CloudGlyph()
                Spacer(Modifier.width(10.dp))
                Text("${weather.temperature.roundToInt()}°", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = weather.description + if (weather.place.isNotBlank()) " · ${weather.place}" else "",
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                )
            }
            Text(
                text = "odczuwalna ${weather.apparent.roundToInt()}° · wilgotność ${weather.humidity}% · wiatr ${weather.windKmh.roundToInt()} km/h",
                color = Quiet,
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (hint != null) {
            Text(hint.text, color = Quiet, fontSize = 14.sp, fontWeight = FontWeight.Light)
            if (hint.actionLabel != null && hint.onAction != null) {
                Text(
                    text = hint.actionLabel,
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = hint.onAction)
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

/** Built-in calendar card: greeting, date and the next event from the phone's calendars. */
@Composable
fun CalendarCardPage(
    now: LocalDateTime,
    name: String,
    event: CalendarEvent?,
    calendarGranted: Boolean,
    accent: Color,
    onRequestCalendar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 24.dp)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
            .padding(24.dp),
    ) {
        Column {
            val greeting = PolishDate.greeting(now) + if (name.isNotBlank()) ", $name" else ""
            Text(greeting.uppercase(), color = Color(0xFFC8C8C8), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp)
            Text(
                text = "${now.dayOfMonth}.",
                color = Ink,
                fontSize = 92.sp,
                lineHeight = 92.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text("${PolishDate.monthGenitive(now)} · ${PolishDate.dayLong(now)}", color = Ink, fontSize = 17.sp)
            Box(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalendarGlyph(accent)
                Spacer(Modifier.width(12.dp))
                when {
                    !calendarGranted -> Text(
                        text = "Pokaż wydarzenia z kalendarza",
                        color = Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable(onClick = onRequestCalendar)
                            .padding(vertical = 12.dp),
                    )
                    event == null -> Text("Brak wydarzeń w ciągu 24 h", color = Quiet, fontSize = 14.sp)
                    else -> Column {
                        Text("${event.whenLabel()} · ${event.title}", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("najbliższe wydarzenie", color = Quiet, fontSize = 12.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
        }
    }
}

/**
 * The calendar widget the user picked in settings, hosted without any frame of its own so
 * a transparent widget sits straight on the wallpaper. [createView] returns null when the
 * widget is gone (app uninstalled), and the caller falls back to the built-in card.
 */
@Composable
fun CalendarWidgetPage(widgetId: Int, createView: (Int) -> View?, fallback: @Composable () -> Unit) {
    key(widgetId) {
        val view = remember(widgetId) { createView(widgetId) }
        if (view == null) {
            fallback()
        } else {
            AndroidView(
                factory = { view },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun CloudGlyph() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.5.dp.toPx())
        drawCircle(Ink, radius = 4.5f * s, center = Offset(9f * s, 13f * s), style = stroke)
        drawCircle(Ink, radius = 5.5f * s, center = Offset(14.5f * s, 11f * s), style = stroke)
        drawLine(Ink, Offset(6f * s, 18f * s), Offset(19f * s, 18f * s), strokeWidth = 1.5.dp.toPx())
    }
}

@Composable
private fun CalendarGlyph(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val s = size.width / 24f
        val w = 1.5.dp.toPx()
        drawRoundRect(
            color,
            topLeft = Offset(4f * s, 6f * s),
            size = Size(16f * s, 14f * s),
            cornerRadius = CornerRadius(2f * s),
            style = Stroke(width = w),
        )
        drawLine(color, Offset(4f * s, 10f * s), Offset(20f * s, 10f * s), strokeWidth = w)
        drawLine(color, Offset(8f * s, 3f * s), Offset(8f * s, 7f * s), strokeWidth = w)
        drawLine(color, Offset(16f * s, 3f * s), Offset(16f * s, 7f * s), strokeWidth = w)
    }
}
