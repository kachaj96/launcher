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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.adrian.launcher.ui.Springs
import com.adrian.launcher.ui.bouncyClick
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
fun ClockWeatherPage(
    now: LocalDateTime,
    weather: WeatherNow?,
    hint: WeatherHint?,
    /** Tap on the time (opens alarms); null = not tappable. */
    onClockTap: (() -> Unit)? = null,
    /** Tap on the weather (shows the hourly forecast); null = not tappable. */
    onWeatherTap: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 32.dp)) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = if (onClockTap != null) Modifier.bouncyClick(pressedScale = 0.97f) { onClockTap() } else Modifier,
        ) {
            Text(
                text = now.hour.toString(),
                color = Ink,
                fontSize = 120.sp,
                lineHeight = 120.sp,
                fontWeight = FontWeight.SemiBold,
            )
            // Minutes roll up like a flip counter when they change.
            AnimatedContent(
                targetState = now.minute,
                transitionSpec = {
                    (slideInVertically(Springs.soft()) { it / 2 } + fadeIn(Springs.soft())) togetherWith
                        (slideOutVertically(Springs.soft()) { -it / 2 } + fadeOut(Springs.soft()))
                },
                label = "minutes",
                modifier = Modifier.padding(start = 8.dp, top = 16.dp),
            ) { minute ->
                Text(
                    text = "%02d".format(minute),
                    color = Ink,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
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
        if (weather != null) Column(
            modifier = if (onWeatherTap != null) Modifier.bouncyClick(pressedScale = 0.97f) { onWeatherTap() } else Modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(weather.code)
                Spacer(Modifier.width(12.dp))
                Text("${weather.temperature.roundToInt()}°", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = weather.description,
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val placeAndFeel = listOfNotNull(
                weather.place.takeIf { it.isNotBlank() },
                "odczuwalna ${weather.apparent.roundToInt()}°",
            ).joinToString(" · ")
            Text(
                text = placeAndFeel,
                color = Quiet,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "wilgotność ${weather.humidity}% · wiatr ${weather.windKmh.roundToInt()} km/h",
                color = Quiet,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 2.dp),
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

private const val CLOUD_HIGH = "M7 15h10a4.5 4.5 0 0 0 .6-8.96A6 6 0 0 0 6.2 5.6A4.75 4.75 0 0 0 7 15z"

/** Stroke glyphs on a 24-unit grid, picked by WMO weather code. */
private fun weatherGlyphPath(code: Int): String = when (code) {
    0, 1 -> "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2" +
        "M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"
    2 -> "M8 5.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5M8 1.5v1.5M2 8h1.5M3.8 3.8l1 1M12.2 3.8l-1 1" +
        "M9 21h8.5a3.75 3.75 0 0 0 .5-7.47A5 5 0 0 0 8.4 13.1A3.95 3.95 0 0 0 9 21z"
    3 -> "M7 19h10a4.5 4.5 0 0 0 .6-8.96A6 6 0 0 0 6.2 9.6A4.75 4.75 0 0 0 7 19z"
    45, 48 -> "M4 8h16M6 12h12M4 16h16M7 20h10"
    71, 73, 75, 77, 85, 86 -> CLOUD_HIGH + "M8 19v.01M12 21v.01M16 19v.01"
    95, 96, 99 -> CLOUD_HIGH + "M12.5 16l-2.5 3.5h3l-2 3.5"
    else -> CLOUD_HIGH + "M8.5 18l-1 3M12.5 18l-1 3M16.5 18l-1 3"
}

@Composable
fun WeatherGlyph(code: Int, sizeDp: Int = 26) {
    val path = remember(code) { PathParser().parsePathString(weatherGlyphPath(code)).toPath() }
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val s = size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(
                path,
                color = Ink,
                style = Stroke(width = 1.5.dp.toPx() / s, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
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
