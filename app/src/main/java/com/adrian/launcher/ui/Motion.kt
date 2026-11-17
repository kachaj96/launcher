package com.adrian.launcher.ui

import android.graphics.Rect
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt

/** Springs tuned to feel like iOS: quick, slightly soft, no visible bounce on most moves. */
object Springs {
    fun <T> soft() = spring<T>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
    fun <T> press() = spring<T>(dampingRatio = 0.6f, stiffness = Spring.StiffnessHigh)
}

/**
 * iOS-style tap target: no ripple, shrinks slightly while pressed and springs back.
 * [onClick] receives the element's bounds in the window, so an app can open from there.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClick(
    pressedScale: Float = 0.95f,
    onLongClick: (() -> Unit)? = null,
    onClick: (Rect?) -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Springs.press(), label = "press")
    val bounds = remember { mutableStateOf<Rect?>(null) }
    val haptics = LocalHapticFeedback.current
    this
        .onGloballyPositioned { c ->
            val b = c.boundsInWindow()
            bounds.value = Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = { onClick(bounds.value) },
            onLongClick = onLongClick?.let { long ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    long()
                }
            },
        )
}
