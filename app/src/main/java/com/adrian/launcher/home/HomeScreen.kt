package com.adrian.launcher.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.data.AppEntry
import com.adrian.launcher.data.CalendarEvent
import com.adrian.launcher.data.PageId
import com.adrian.launcher.data.WeatherNow
import com.adrian.launcher.ui.AppIcon
import com.adrian.launcher.ui.Chevron
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.PolishDate
import com.adrian.launcher.ui.Quiet
import com.adrian.launcher.ui.rememberBatteryLevel
import com.adrian.launcher.ui.rememberNow

/** Vertical drag distance (px) that counts as a swipe up or down. */
private const val SWIPE_THRESHOLD_PX = 120f

class HomeActions(
    val onLaunch: (AppEntry) -> Unit,
    val onAppLongPress: (AppEntry) -> Unit,
    val onOpenDrawer: () -> Unit,
    val onSwipeDown: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onRequestDefault: () -> Unit,
    val onRequestCalendar: () -> Unit,
)

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
    isDefaultHome: Boolean,
    resetSignal: Int,
    actions: HomeActions,
) {
    val now by rememberNow()
    val battery by rememberBatteryLevel()

    val homePage = pages.indexOf(PageId.CLOCK).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = homePage) { pages.size }
    LaunchedEffect(resetSignal, pages) {
        if (pagerState.currentPage != homePage) pagerState.scrollToPage(homePage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var dragTotal = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        when {
                            dragTotal < -SWIPE_THRESHOLD_PX -> actions.onOpenDrawer()
                            dragTotal > SWIPE_THRESHOLD_PX -> actions.onSwipeDown()
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    dragTotal += amount
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { actions.onOpenSettings() })
            },
    ) {
        WallpaperLayer(wallpaper, gradient)

        BoxWithConstraints(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            val carouselHeight = maxHeight * 0.46f
            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(carouselHeight),
                ) { index ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (pages[index]) {
                            PageId.CLOCK -> ClockPage(now, weather)
                            PageId.WEATHER -> WeatherPage(now, weather, battery, weatherHint)
                            PageId.DAY -> DayPage(now, name, event, calendarGranted, accent, actions.onRequestCalendar)
                            PageId.DIAL -> DialPage(now, accent)
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

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(48.dp)
                        .clickable(onClick = actions.onOpenDrawer),
                ) {
                    Chevron(pointsUp = true, color = Color(0xFF9A9A9A))
                }
            }
        }
    }
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
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(if (active) 18.dp else 6.dp)
                    .height(6.dp)
                    .background(if (active) accent else Ink.copy(alpha = 0.45f), RoundedCornerShape(3.dp)),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedRow(app: AppEntry, actions: HomeActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(240.dp)
            .height(48.dp)
            .combinedClickable(
                onClick = { actions.onLaunch(app) },
                onLongClick = { actions.onAppLongPress(app) },
            ),
    ) {
        AppIcon(app)
        Spacer(Modifier.width(18.dp))
        Text(app.label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Normal, maxLines = 1)
    }
}
