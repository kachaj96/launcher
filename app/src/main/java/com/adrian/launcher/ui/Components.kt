package com.adrian.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.adrian.launcher.data.AppEntry
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * Makes an overlay a hit target everywhere, so touches on its empty areas do not fall
 * through to the home screen underneath (and e.g. launch a pinned app). Children still
 * get every event first; nothing is consumed here.
 */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

/** Monochrome app icon, or the first letter in a thin circle when the app has no monochrome layer. */
@Composable
fun AppIcon(app: AppEntry, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val icon = app.icon
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size - 2.dp)
                .border(1.3.dp, Ink, CircleShape),
        ) {
            Text(app.section, color = Ink, fontSize = (size.value * 0.48f).sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun Chevron(pointsUp: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val yTip = if (pointsUp) h * 0.375f else h * 0.625f
        val yEnds = if (pointsUp) h * 0.625f else h * 0.375f
        drawLine(color, Offset(w * 0.25f, yEnds), Offset(w * 0.5f, yTip), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, yTip), Offset(w * 0.75f, yEnds), stroke, cap = StrokeCap.Round)
    }
}

/** Current time, refreshed right after every full minute. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
    }
}

/** Battery level in percent, kept up to date while the composable is on screen. */
@Composable
fun rememberBatteryLevel(): State<Int> {
    val context = LocalContext.current
    val level = remember { mutableIntStateOf(readBattery(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                level.intValue = readBattery(intent)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return level
}

private fun readBattery(intent: Intent?): Int {
    if (intent == null) return 0
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    return if (level < 0 || scale <= 0) 0 else level * 100 / scale
}
