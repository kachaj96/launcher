package com.adrian.launcher.settings

import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.Panel
import com.adrian.launcher.ui.Quiet
import com.adrian.launcher.ui.blockTouchesBelow
import com.adrian.launcher.ui.bouncyClick
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Full-screen preview of the wallpaper exactly as the home screen will show it. Pinch to
 * zoom, drag to move; the black gradient and a faint outline of the clock show what will
 * sit on top. "Ustaw" cuts the visible part out of the photo.
 */
@Composable
fun WallpaperEditor(
    source: ImageBitmap,
    initialGradient: Float,
    accent: Color,
    onCancel: () -> Unit,
    onAccept: (crop: Rect, gradient: Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .blockTouchesBelow(),
    ) {
        val density = LocalDensity.current
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val imgW = source.width.toFloat()
        val imgH = source.height.toFloat()
        // Smallest scale that still covers the whole screen.
        val cover = max(screenW / imgW, screenH / imgH)

        var zoom by remember { mutableFloatStateOf(1f) }
        // Start aligned to the right, like the home screen does.
        var offset by remember { mutableStateOf(Offset(screenW - imgW * cover, (screenH - imgH * cover) / 2f)) }
        var gradient by remember { mutableFloatStateOf(initialGradient) }

        fun clamp(o: Offset, z: Float): Offset {
            val w = imgW * cover * z
            val h = imgH * cover * z
            return Offset(o.x.coerceIn(screenW - w, 0f), o.y.coerceIn(screenH - h, 0f))
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                        val factor = newZoom / zoom
                        // Zoom around the fingers, then follow the pan.
                        val moved = (offset - centroid) * factor + centroid + pan
                        zoom = newZoom
                        offset = clamp(moved, newZoom)
                    }
                },
        ) {
            val s = cover * zoom
            drawImage(
                image = source,
                dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                dstSize = IntSize((imgW * s).roundToInt(), (imgH * s).roundToInt()),
            )
            val solid = gradient.coerceIn(0.05f, 0.9f)
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Black,
                    solid to Color.Black,
                    (solid + 0.34f).coerceAtMost(1f) to Color.Transparent,
                ),
            )
        }

        // Ghost of the home screen so the framing can be judged.
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 28.dp, top = 32.dp),
        ) {
            Text("12", color = Ink.copy(alpha = 0.35f), fontSize = 120.sp, lineHeight = 120.sp, fontWeight = FontWeight.SemiBold)
            Text("WT · 17 LISTOPADA", color = Ink.copy(alpha = 0.35f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .background(Panel.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Text("Przesuń i przybliż zdjęcie", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("Szerokość czerni: ${(gradient * 100).toInt()}%", color = Quiet, fontSize = 13.sp)
            Slider(value = gradient, onValueChange = { gradient = it }, valueRange = 0.1f..0.6f)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(24.dp))
                        .bouncyClick(pressedScale = 0.96f) { onCancel() },
                ) { Text("Anuluj", color = Ink, fontSize = 15.sp) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(accent, RoundedCornerShape(24.dp))
                        .bouncyClick(pressedScale = 0.96f) {
                            val s = cover * zoom
                            val crop = Rect(
                                (-offset.x / s).roundToInt(),
                                (-offset.y / s).roundToInt(),
                                ((screenW - offset.x) / s).roundToInt(),
                                ((screenH - offset.y) / s).roundToInt(),
                            )
                            onAccept(crop, gradient)
                        },
                ) { Text("Ustaw", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}
