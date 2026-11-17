package com.adrian.launcher.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.data.CalendarEvent
import com.adrian.launcher.data.WeatherNow
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.PolishDate
import com.adrian.launcher.ui.Quiet
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the weather page shows when there is no data, plus an optional fix action. */
data class WeatherHint(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

private fun WeatherNow.shortLine(): String =
    "${description}, ${temperature.roundToInt()}°" + if (place.isNotBlank()) " · $place" else ""

@Composable
fun ClockPage(now: LocalDateTime, weather: WeatherNow?) {
    Column(modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 36.dp)) {
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
        if (weather != null) {
            Text(
                text = weather.shortLine(),
                color = Quiet,
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun WeatherPage(now: LocalDateTime, weather: WeatherNow?, battery: Int, hint: WeatherHint?) {
    Column(modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 40.dp)) {
        if (weather != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CloudGlyph()
                Spacer(Modifier.width(8.dp))
                Text("${weather.temperature.roundToInt()}°", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (weather.place.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(weather.place, color = Quiet, fontSize = 15.sp, fontWeight = FontWeight.Light)
                }
            }
        }
        Text(
            text = PolishDate.monthNominative(now),
            color = Ink,
            fontSize = 48.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp),
        )
        Column(modifier = Modifier.padding(top = 12.dp)) {
            if (weather != null) {
                Detail("${weather.description.replaceFirstChar { it.uppercase() }}, odczuwalna ${weather.apparent.roundToInt()}°")
                Detail("Wilgotność ${weather.humidity}% · wiatr ${weather.windKmh.roundToInt()} km/h")
            } else if (hint != null) {
                Detail(hint.text)
            }
            Detail("Bateria $battery%")
        }
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .width(44.dp)
                .height(1.dp)
                .background(Ink),
        )
        if (weather == null && hint?.actionLabel != null && hint.onAction != null) {
            ActionLink(hint.actionLabel, hint.onAction)
        }
    }
}

@Composable
fun DayPage(
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
 * Hour, minutes in an accent capsule, and a ring of the following minutes curving to the
 * right. All numbers stay horizontal; only their positions follow the arc.
 */
@Composable
fun DialPage(now: LocalDateTime, accent: Color) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cx = (-40).dp
        val cy = maxHeight / 2
        val ring = 172.dp
        val tickRadius = 208.dp
        val m = now.minute

        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(cx.toPx(), cy.toPx())
            for (k in -28..28) {
                val a = Math.toRadians(k * 3.0)
                val len = (if (k % 5 == 0) 10.dp else 5.dp).toPx()
                val r = tickRadius.toPx()
                val dir = Offset(cos(a).toFloat(), -sin(a).toFloat())
                val start = c + dir * (r - len / 2)
                val end = c + dir * (r + len / 2)
                val alpha = (0.9f - abs(k) / 30f).coerceAtLeast(0.12f)
                drawLine(Ink.copy(alpha = alpha), start, end, strokeWidth = 1.2.dp.toPx())
            }
        }

        for (d in -6..6) {
            if (d == 0) continue
            val a = Math.toRadians(d * 13.0)
            val value = ((m + 5 * d) % 60 + 60) % 60
            val x = cx + ring * cos(a).toFloat()
            val y = cy - ring * sin(a).toFloat()
            Text(
                text = "%02d".format(value),
                color = Ink.copy(alpha = (1f - abs(d) / 6.5f).coerceAtLeast(0.15f)),
                fontSize = (17 - abs(d)).coerceAtLeast(11).sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.anchorAt(x, y, 0.5f),
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .anchorAt(cx + ring, cy, 0.5f)
                .size(width = 58.dp, height = 34.dp)
                .border(1.5.dp, accent, RoundedCornerShape(17.dp)),
        ) {
            Text("%02d".format(m), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Text(
            text = "%02d".format(now.hour),
            color = Ink,
            fontSize = 64.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.anchorAt(cx + ring - 40.dp, cy, 1f),
        )

        Column(modifier = Modifier.anchorAt(cx + ring + 50.dp, cy, 0f)) {
            Text(
                "${now.dayOfMonth} ${PolishDate.monthShort(now).uppercase()} ${now.year}",
                color = Quiet, fontSize = 11.sp, letterSpacing = 1.2.sp,
            )
            Text(PolishDate.dayLong(now).uppercase(), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
        }
    }
}

/** Places the element so that the point (x, y) sits at its vertical centre and at [hAlign] of its width. */
private fun Modifier.anchorAt(x: Dp, y: Dp, hAlign: Float): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) {
        p.place((x.roundToPx() - p.width * hAlign).roundToInt(), y.roundToPx() - p.height / 2)
    }
}

@Composable
private fun Detail(text: String) {
    Text(text, color = Color(0xFFD0D0D0), fontSize = 14.sp, fontWeight = FontWeight.Light, lineHeight = 22.sp)
}

@Composable
private fun ActionLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(top = 8.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun CloudGlyph() {
    Canvas(modifier = Modifier.size(20.dp)) {
        val s = size.width / 24f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
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
            size = androidx.compose.ui.geometry.Size(16f * s, 14f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * s),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
        )
        drawLine(color, Offset(4f * s, 10f * s), Offset(20f * s, 10f * s), strokeWidth = w)
        drawLine(color, Offset(8f * s, 3f * s), Offset(8f * s, 7f * s), strokeWidth = w)
        drawLine(color, Offset(16f * s, 3f * s), Offset(16f * s, 7f * s), strokeWidth = w)
    }
}
