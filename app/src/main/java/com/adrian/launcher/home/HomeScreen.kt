package com.adrian.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.drawer.Chevron
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.Polish
import com.adrian.launcher.ui.Quiet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Upward drag distance (px) that opens the drawer. */
private const val SWIPE_UP_THRESHOLD_PX = 120f

@Composable
private fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
    }
}

@Composable
fun HomeScreen(
    accent: Color,
    isDefaultHome: Boolean,
    onRequestDefault: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val now by rememberNow()
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Polish))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var dragTotal = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = { if (dragTotal < -SWIPE_UP_THRESHOLD_PX) onOpenDrawer() },
                ) { change, amount ->
                    change.consume()
                    dragTotal += amount
                }
            }
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Column(modifier = Modifier.padding(top = 64.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = now.hour.toString(),
                    color = Ink,
                    fontSize = 112.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 112.sp,
                )
                Text(
                    text = "%02d".format(now.minute),
                    color = Ink,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp, top = 14.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(text = date, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(text = "Przesuń w górę: aplikacje", color = Quiet, fontSize = 14.sp)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isDefaultHome) {
                Text(
                    text = "Ustaw jako domyślny",
                    color = Ink,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .border(1.5.dp, accent, RoundedCornerShape(22.dp))
                        .clickable(onClick = onRequestDefault)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .size(48.dp)
                .clickable(onClick = onOpenDrawer),
        ) {
            Chevron(pointsUp = true, color = Color(0xFF9A9A9A))
        }
    }
}
