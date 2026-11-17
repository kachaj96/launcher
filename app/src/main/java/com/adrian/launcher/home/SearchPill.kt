package com.adrian.launcher.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.ui.Faint
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.Outfit
import com.adrian.launcher.ui.Quiet
import com.adrian.launcher.ui.Springs
import com.adrian.launcher.ui.bouncyClick

const val SEARCH_PILL_HEIGHT_DP = 52

/**
 * Brave search on the home screen: type, then the arrow (or Enter) opens Brave Search.
 * The camera button opens the camera; holding it opens Google Lens when installed.
 */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    accent: Color,
    onSearch: () -> Unit,
    onCamera: () -> Unit,
    onLens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(SEARCH_PILL_HEIGHT_DP.dp)
            .background(Color.White.copy(alpha = if (focused) 0.14f else 0.10f), RoundedCornerShape(26.dp))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
            .padding(start = 18.dp, end = 6.dp),
    ) {
        Glyph(SEARCH_PATH, Quiet, 18)
        Spacer(Modifier.width(10.dp))
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Szukaj w Brave", color = Faint, fontSize = 16.sp, fontWeight = FontWeight.Light)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Light, fontFamily = Outfit),
                cursorBrush = SolidColor(Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .bouncyClick(pressedScale = 0.85f, onLongClick = onLens) { onCamera() },
        ) {
            Glyph(CAMERA_PATH, Ink, 22)
        }
        AnimatedVisibility(
            visible = query.isNotBlank(),
            enter = scaleIn(Springs.snappy()) + fadeIn(Springs.snappy()),
            exit = scaleOut(Springs.snappy()) + fadeOut(Springs.snappy()),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(40.dp)
                    .background(accent, CircleShape)
                    .bouncyClick(pressedScale = 0.85f) { onSearch() },
            ) {
                Glyph(ARROW_PATH, Color.Black, 18, stroke = 2f)
            }
        }
    }
}

private const val SEARCH_PATH = "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13M15.5 15.5L20 20"
private const val CAMERA_PATH = "M4 8h4l2-3h4l2 3h4v11H4zM12 10a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7"
private const val ARROW_PATH = "M5 12h14M13 6l6 6l-6 6"

@Composable
private fun Glyph(path: String, color: Color, sizeDp: Int, stroke: Float = 1.6f) {
    val parsed = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val s = size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(parsed, color, style = Stroke(width = stroke.dp.toPx() / s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
