package com.adrian.launcher.home

import android.graphics.Rect
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.data.AppEntry
import com.adrian.launcher.data.CalendarEvent
import com.adrian.launcher.data.NO_WIDGET
import com.adrian.launcher.data.PageId
import com.adrian.launcher.data.WeatherNow
import com.adrian.launcher.ui.AppIcon
import com.adrian.launcher.ui.Chevron
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.PolishDate
import com.adrian.launcher.ui.Quiet
import com.adrian.launcher.ui.Springs
import com.adrian.launcher.ui.bouncyClick
import com.adrian.launcher.ui.rememberBatteryLevel
import com.adrian.launcher.ui.rememberNow
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Downward drag (px) that pulls the notification shade. */
private const val SWIPE_DOWN_THRESHOLD_PX = 120f
/** Horizontal fling speed (px/s) that turns the page regardless of how far it moved. */
private const val PAGE_FLING_VELOCITY = 600f

class HomeActions(
    val onLaunch: (AppEntry, Rect?) -> Unit,
    val onAppLongPress: (AppEntry) -> Unit,
    /** Upward drag in progress: delta in px, negative = finger moving up. */
    val onDrawerDrag: (Float) -> Unit,
    /** Upward drag released with this vertical velocity (px/s). */
    val onDrawerDragEnd: (Float) -> Unit,
    val onOpenDrawer: () -> Unit,
    val onSwipeDown: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onRequestDefault: () -> Unit,
    val onRequestCalendar: () -> Unit,
    val onWebSearch: (String) -> Unit,
    val onCamera: () -> Unit,
    val onLens: () -> Unit,
)

private enum class DragAxis { NONE, HORIZONTAL, UP, DOWN }

@Composable
fun HomeScreen(
    accent: Color,
    wallpaper: ImageBitmap?,
    gradient: Float,
    pages: List<PageId>,
    pinned: List<AppEntry>,
    name: String,
    weather: WeatherNow?,
    weatherHint: WeatherHint?,
    event: CalendarEvent?,
    calendarGranted: Boolean,
    calendarWidgetId: Int,
    createWidgetView: (Int) -> View?,
    isDefaultHome: Boolean,
    resetSignal: Int,
    /** 0 = drawer closed, 1 = open; the home screen recedes as the drawer comes up. Read per frame. */
    drawerProgress: () -> Float,
    /** 0..1 entrance when coming back from an app (iOS-style zoom out). Read per frame. */
    enterProgress: () -> Float,
    actions: HomeActions,
) {
    val now by rememberNow()
    val battery by rememberBatteryLevel()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = searchFocused) { focusManager.clearFocus() }
    // Opening the drawer or going to an app ends the search.
    LaunchedEffect(resetSignal) { focusManager.clearFocus() }

    val homePage = pages.indexOf(PageId.CLOCK).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = homePage) { pages.size }
    LaunchedEffect(resetSignal, pages) {
        if (pagerState.currentPage != homePage) pagerState.scrollToPage(homePage)
    }
    // A light tick when a page settles, like the iOS page dots.
    val lastPage = remember { intArrayOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != lastPage[0]) {
            lastPage[0] = pagerState.settledPage
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .homeGestures(pagerState, actions)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.onOpenSettings()
                })
            },
    ) {
        WallpaperLayer(wallpaper, gradient)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val d = drawerProgress()
                    val e = enterProgress()
                    val scale = (1f - 0.06f * d) * (0.94f + 0.06f * e)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - 0.7f * d) * (0.3f + 0.7f * e)
                }
                .systemBarsPadding(),
        ) {
            val carouselHeight = maxHeight * 0.42f
            Column(modifier = Modifier.fillMaxSize()) {
                // Paging is driven by homeGestures for the whole screen height.
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(carouselHeight),
                ) { index ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (pages[index]) {
                            PageId.CLOCK -> ClockWeatherPage(now, weather, weatherHint)
                            PageId.CALENDAR -> {
                                val card = @Composable {
                                    CalendarCardPage(now, name, event, calendarGranted, accent, actions.onRequestCalendar)
                                }
                                if (calendarWidgetId == NO_WIDGET) card() else CalendarWidgetPage(calendarWidgetId, createWidgetView, card)
                            }
                        }
                    }
                }

                if (pages.size > 1) {
                    PageDots(count = pages.size, current = pagerState.currentPage, accent = accent)
                } else {
                    Spacer(Modifier.height(32.dp))
                }

                Text(
                    text = "${PolishDate.dayShort(now)}, ${now.dayOfMonth} ${PolishDate.monthShort(now)} · $battery%",
                    color = Quiet,
                    fontSize = 13.sp,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 8.dp),
                )

                Column(modifier = Modifier.padding(start = 28.dp)) {
                    pinned.forEach { app -> PinnedRow(app, actions) }
                }

                Spacer(Modifier.weight(1f))

                if (!isDefaultHome) {
                    Text(
                        text = "Ustaw jako domyślny ekran główny",
                        color = Ink,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .padding(start = 28.dp, bottom = 8.dp)
                            .border(1.5.dp, accent, RoundedCornerShape(22.dp))
                            .clickable(onClick = actions.onRequestDefault)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                // Room for the search pill, which is drawn as an overlay so it can rise with the keyboard.
                Spacer(Modifier.height((SEARCH_PILL_HEIGHT_DP + 8).dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(48.dp)
                        .bouncyClick(pressedScale = 0.85f) {
                            focusManager.clearFocus()
                            actions.onOpenDrawer()
                        },
                ) {
                    Chevron(pointsUp = true, color = Color(0xFF9A9A9A))
                }
            }

            // While typing: dim the screen; a tap outside the pill ends the search.
            val scrim by animateFloatAsState(if (searchFocused) 0.6f else 0f, Springs.soft(), label = "scrim")
            if (scrim > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrim))
                        .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
                )
            }

            val pillBottom by animateDpAsState(if (searchFocused) 12.dp else 56.dp, Springs.soft(), label = "pill")
            Box(modifier = Modifier.fillMaxSize().imePadding()) {
                SearchPill(
                    query = query,
                    onQueryChange = { query = it },
                    focused = searchFocused,
                    onFocusChange = { searchFocused = it },
                    accent = accent,
                    onSearch = {
                        val q = query.trim()
                        if (q.isNotEmpty()) {
                            actions.onWebSearch(q)
                            query = ""
                            focusManager.clearFocus()
                        }
                    },
                    onCamera = {
                        focusManager.clearFocus()
                        actions.onCamera()
                    },
                    onLens = {
                        focusManager.clearFocus()
                        actions.onLens()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 20.dp, end = 20.dp, bottom = pillBottom),
                )
            }
        }
    }
}

/**
 * One gesture detector for the whole screen. After the touch slop it locks to an axis:
 * horizontal moves the carousel anywhere on screen (also over the calendar widget),
 * up drags the drawer open under the finger, down pulls the notification shade.
 *
 * Horizontal is decided in the Initial pass, before children see the move, so even a
 * widget that grabs touches cannot block paging. Vertical is decided in the Main pass,
 * after children, so a scrolling widget list keeps its own vertical scroll. Nothing is
 * consumed before the slop, so taps and long presses on children still work.
 */
@Composable
private fun Modifier.homeGestures(pagerState: PagerState, actions: HomeActions): Modifier {
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val currentActions by rememberUpdatedState(actions)
    return pointerInput(pagerState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var axis = DragAxis.NONE
            var childOwnsVertical = false
            var totalX = 0f
            var totalY = 0f

            while (true) {
                val initial = awaitPointerEvent(PointerEventPass.Initial)
                val change = initial.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val v = tracker.calculateVelocity()
                    when (axis) {
                        DragAxis.HORIZONTAL -> scope.launch { settlePager(pagerState, v.x) }
                        DragAxis.UP -> {
                            // A very short flick gives the tracker too few samples; fall back
                            // to the average speed of the whole gesture.
                            val elapsedMs = (change.uptimeMillis - down.uptimeMillis).coerceAtLeast(1L)
                            val average = totalY / elapsedMs * 1000f
                            currentActions.onDrawerDragEnd(if (abs(v.y) > abs(average)) v.y else average)
                        }
                        DragAxis.DOWN -> if (totalY > SWIPE_DOWN_THRESHOLD_PX) currentActions.onSwipeDown()
                        DragAxis.NONE -> Unit
                    }
                    break
                }
                val delta = change.position - change.previousPosition
                tracker.addPosition(change.uptimeMillis, change.position)
                totalX += delta.x
                totalY += delta.y

                if (axis == DragAxis.NONE && abs(totalX) > slop && abs(totalX) > abs(totalY)) {
                    axis = DragAxis.HORIZONTAL
                }
                when (axis) {
                    DragAxis.HORIZONTAL -> {
                        change.consume()
                        pagerState.dispatchRawDelta(-delta.x)
                    }
                    DragAxis.UP -> {
                        change.consume()
                        currentActions.onDrawerDrag(delta.y)
                    }
                    DragAxis.DOWN -> change.consume()
                    DragAxis.NONE -> Unit
                }

                // Same event, after the children had their turn.
                val main = awaitPointerEvent(PointerEventPass.Main)
                if (axis == DragAxis.NONE && !childOwnsVertical && abs(totalY) > slop && abs(totalY) >= abs(totalX)) {
                    val mainChange = main.changes.firstOrNull { it.id == down.id }
                    if (mainChange == null || mainChange.isConsumed) {
                        childOwnsVertical = true
                    } else {
                        axis = if (totalY < 0) DragAxis.UP else DragAxis.DOWN
                        mainChange.consume()
                        if (axis == DragAxis.UP) currentActions.onDrawerDrag(totalY)
                    }
                }
            }
        }
    }
}

/** Snaps the pager to the nearest page, or the next one in the fling direction. */
private suspend fun settlePager(pagerState: PagerState, velocityX: Float) {
    val current = pagerState.currentPage
    val offset = pagerState.currentPageOffsetFraction
    val target = when {
        velocityX < -PAGE_FLING_VELOCITY -> if (offset > -0.01f) current + 1 else current
        velocityX > PAGE_FLING_VELOCITY -> if (offset < 0.01f) current - 1 else current
        offset > 0.5f -> current + 1
        offset < -0.5f -> current - 1
        else -> current
    }.coerceIn(0, pagerState.pageCount - 1)
    pagerState.animateScrollToPage(target, animationSpec = Springs.soft())
}

@Composable
private fun WallpaperLayer(wallpaper: ImageBitmap?, gradient: Float) {
    if (wallpaper == null) return
    Image(
        bitmap = wallpaper,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.CenterEnd,
        modifier = Modifier.fillMaxSize(),
    )
    val solid = gradient.coerceIn(0.05f, 0.9f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black,
                    solid to Color.Black,
                    (solid + 0.34f).coerceAtMost(1f) to Color.Transparent,
                ),
            ),
    )
}

@Composable
private fun PageDots(count: Int, current: Int, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 28.dp)
            .height(32.dp),
    ) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(if (active) 18.dp else 6.dp, Springs.snappy(), label = "dot")
            val color by animateColorAsState(if (active) accent else Ink.copy(alpha = 0.45f), Springs.snappy(), label = "dotColor")
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(width)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun PinnedRow(app: AppEntry, actions: HomeActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(240.dp)
            .height(44.dp)
            .bouncyClick(onLongClick = { actions.onAppLongPress(app) }) { bounds -> actions.onLaunch(app, bounds) },
    ) {
        AppIcon(app)
        Spacer(Modifier.width(18.dp))
        Text(app.label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Normal, maxLines = 1)
    }
}
