package com.adrian.launcher.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.data.AppEntry
import com.adrian.launcher.data.foldForSearch
import com.adrian.launcher.ui.Faint
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.Quiet
import kotlin.math.abs
import kotlinx.coroutines.launch

private sealed interface DrawerRow {
    val key: String

    data class Header(val letter: String) : DrawerRow {
        override val key = "h:$letter"
    }

    data class App(val app: AppEntry) : DrawerRow {
        override val key = "a:${app.key}"
    }
}

/** Minimum query length before a single match launches on its own, so the first typed letter never fires an app. */
private const val AUTO_LAUNCH_MIN_CHARS = 2

@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    accent: Color,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val folded = query.trim().foldForSearch()

    val results = remember(apps, folded) {
        if (folded.isEmpty()) apps else apps.filter { it.searchKey.contains(folded) }
    }

    // Grouped rows for the full list; a flat list while searching.
    val rows: List<DrawerRow> = remember(results, folded) {
        if (folded.isNotEmpty()) {
            results.map { DrawerRow.App(it) }
        } else {
            results.groupBy { it.section }
                .flatMap { (letter, group) -> listOf(DrawerRow.Header(letter)) + group.map { DrawerRow.App(it) } }
        }
    }
    val headerIndex: Map<String, Int> = remember(rows) {
        rows.withIndex()
            .filter { it.value is DrawerRow.Header }
            .associate { (it.value as DrawerRow.Header).letter to it.index }
    }

    LaunchedEffect(results, folded) {
        if (folded.length >= AUTO_LAUNCH_MIN_CHARS && results.size == 1) {
            onLaunch(results.first())
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .systemBarsPadding()
            .imePadding(),
    ) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onClose = onClose,
            onSubmit = { results.firstOrNull()?.let(onLaunch) },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 28.dp, end = 64.dp, bottom = 32.dp,
                ),
            ) {
                if (folded.isNotEmpty() && results.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "Brak wyników",
                            color = Quiet,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                }
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is DrawerRow.Header -> SectionHeader(row.letter)
                        is DrawerRow.App -> AppRow(row.app, onClick = { onLaunch(row.app) })
                    }
                }
            }

            if (folded.isEmpty() && headerIndex.size > 1) {
                AlphabetScrubber(
                    letters = headerIndex.keys.toList(),
                    accent = accent,
                    onLetter = { letter ->
                        headerIndex[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(0.82f)
                        .padding(end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onClose),
        ) {
            Chevron(pointsUp = false, color = Ink)
        }
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .drawBehind {
                    drawLine(
                        color = Color(0xFF3A3A3A),
                        start = Offset(0f, size.height - 1f),
                        end = Offset(size.width, size.height - 1f),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
        ) {
            if (query.isEmpty()) {
                Text("Szukaj", color = Faint, fontSize = 17.sp, fontWeight = FontWeight.Light)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Light),
                cursorBrush = SolidColor(Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionHeader(letter: String) {
    Text(
        text = letter,
        color = Faint,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
    ) {
        // Letter placeholder; monochrome icons come in stage 2.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .border(1.3.dp, Ink, CircleShape),
        ) {
            Text(app.section, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(18.dp))
        Text(app.label, color = Ink, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
private fun AlphabetScrubber(
    letters: List<String>,
    accent: Color,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeIndex by remember { mutableIntStateOf(-1) }
    var heightPx by remember { mutableIntStateOf(1) }
    val haptics = LocalHapticFeedback.current

    fun pick(y: Float) {
        val index = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (index != activeIndex) {
            activeIndex = index
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetter(letters[index])
        }
    }

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(40.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y) },
                    onDragEnd = { activeIndex = -1 },
                    onDragCancel = { activeIndex = -1 },
                ) { change, _ ->
                    change.consume()
                    pick(change.position.y)
                }
            }
            .pointerInput(letters) {
                detectTapGestures(onTap = { pick(it.y); activeIndex = -1 })
            },
    ) {
        letters.forEachIndexed { i, letter ->
            val distance = if (activeIndex < 0) Int.MAX_VALUE else abs(i - activeIndex)
            val (size, shift) = when (distance) {
                0 -> 22.sp to (-28).dp
                1 -> 15.sp to (-14).dp
                2 -> 12.5.sp to (-5).dp
                else -> 11.sp to 0.dp
            }
            Text(
                text = letter,
                color = if (distance == 0) accent else Quiet,
                fontSize = size,
                fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.offset(x = shift),
            )
        }
    }
}

@Composable
fun Chevron(pointsUp: Boolean, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val yTip = if (pointsUp) h * 0.375f else h * 0.625f
        val yEnds = if (pointsUp) h * 0.625f else h * 0.375f
        drawLine(color, Offset(w * 0.25f, yEnds), Offset(w * 0.5f, yTip), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, yTip), Offset(w * 0.75f, yEnds), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
