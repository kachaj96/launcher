package com.adrian.launcher.home

import android.graphics.Rect
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.data.AppEntry
import com.adrian.launcher.data.CalendarEvent
import com.adrian.launcher.data.Gesture
import com.adrian.launcher.data.GestureAction
import com.adrian.launcher.data.NO_WIDGET
import com.adrian.launcher.data.PageId
import com.adrian.launcher.data.WeatherNow
import com.adrian.launcher.data.foldForSearch
import com.adrian.launcher.system.NowPlaying
import com.adrian.launcher.ui.AppIcon
import com.adrian.launcher.ui.Chevron
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.Line
import com.adrian.launcher.ui.Panel
import com.adrian.launcher.ui.Quiet
import com.adrian.launcher.ui.Springs
import com.adrian.launcher.ui.bouncyClick
import com.adrian.launcher.ui.rememberNow
import kotlin.math.abs
import kotlin.math.roundToInt
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
    /** A gesture whose action the activity carries out (drawer, notifications, apps...). */
    val onGesture: (Gesture) -> Unit,
    val onRequestDefault: () -> Unit,
    val onRequestCalendar: () -> Unit,
    val onWebSearch: (String) -> Unit,
    val onCamera: () -> Unit,
    val onLens: () -> Unit,
    val onGallery: () -> Unit,
)

/** What the search pill offers while typing. */
class SpotlightConfig(
    /** All apps that search may show (hidden ones included), already renamed. */
    val apps: List<AppEntry>,
    val showApps: Boolean,
    val calculator: Boolean,
    /** "Szukaj w Google" etc. */
    val placeholder: String,
    val onCopy: (String) -> Unit,
    /** Apps usually opened at this time; shown when the pill is focused and empty. */
    val suggestions: List<AppEntry> = emptyList(),
    /** Engine name for the web row ("Google"). */
    val engineTitle: String = "Google",
    /** Sends the text to the Claude app; null when off or Claude is not installed. */
    val onAskClaude: ((String) -> Unit)? = null,
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
    nowPlaying: NowPlaying?,
    /** Packages with unread notifications; empty when dots are off. */
    notified: Set<String>,
    /** Action bound to each gesture; drives what the home screen does itself (search, forecast). */
    actionOf: (Gesture) -> GestureAction,
    /** Shows the charging ring each time it changes (0 = nothing yet). */
    chargingSignal: Int,
    battery: Int,
    showChargingRing: Boolean,
    focusActive: Boolean,
    evening: Boolean,
    spotlight: SpotlightConfig,
    actions: HomeActions,
) {
    val now by rememberNow()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var showHourly by remember { mutableStateOf(false) }
    BackHandler(enabled = searchFocused) { focusManager.clearFocus() }
    BackHandler(enabled = showHourly) { showHourly = false }
    val searchFocus = remember { FocusRequester() }
    val fire: (Gesture) -> Unit = { g ->
        when (actionOf(g)) {
            GestureAction.NONE -> Unit
            GestureAction.FORECAST -> if (weather != null) showHourly = true
            GestureAction.SEARCH -> runCatching { searchFocus.requestFocus() }
            else -> {
                focusManager.clearFocus()
                actions.onGesture(g)
            }
        }
    }
    // Opening an app or locking the phone ends the search.
    LaunchedEffect(resetSignal) {
        focusManager.clearFocus()
        showHourly = false
    }

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

    val blur by animateDpAsState(if (searchFocused || showHourly) 18.dp else 0.dp, Springs.soft(), label = "blur")

    // Charging ring: the home screen softly blurs in under it and back out.
    val ringAppear = remember { androidx.compose.animation.core.Animatable(0f) }
    val ringSweep = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(chargingSignal) {
        if (chargingSignal == 0 || !showChargingRing) return@LaunchedEffect
        ringSweep.snapTo(0f)
        launch { ringSweep.animateTo(battery / 100f, androidx.compose.animation.core.tween(900)) }
        ringAppear.animateTo(1f, androidx.compose.animation.core.tween(450))
        kotlinx.coroutines.delay(2200)
        ringAppear.animateTo(0f, androidx.compose.animation.core.tween(600))
    }
    val dim by animateFloatAsState(if (evening) 0.72f else 1f, Springs.soft(), label = "evening")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .homeGestures(
                pagerState = pagerState,
                actions = actions,
                enabled = !showHourly,
                upDragsDrawer = actionOf(Gesture.SWIPE_UP) == GestureAction.DRAWER,
                fire = fire,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        fire(Gesture.LONG_PRESS)
                    },
                    onDoubleTap = { fire(Gesture.DOUBLE_TAP) },
                )
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val d = drawerProgress()
                    val e = enterProgress()
                    val scale = (1f - 0.06f * d) * (0.94f + 0.06f * e)
                    scaleX = scale
                    scaleY = scale
                    val ring = ringAppear.value
                    alpha = (1f - 0.7f * d) * (0.3f + 0.7f * e) * dim * (1f - 0.35f * ring)
                    val r = maxOf(blur.toPx(), (14 * ring).dp.toPx())
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                },
        ) {
            WallpaperLayer(wallpaper, gradient)

            val carouselHeight = maxHeight * (if (nowPlaying != null) 0.40f else 0.46f)
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
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
                            PageId.CLOCK -> ClockWeatherPage(
                                now = now,
                                weather = weather,
                                hint = weatherHint,
                                onClockTap = if (actionOf(Gesture.TAP_CLOCK) != GestureAction.NONE) ({ fire(Gesture.TAP_CLOCK) }) else null,
                                onWeatherTap = if (actionOf(Gesture.TAP_WEATHER) != GestureAction.NONE) ({ fire(Gesture.TAP_WEATHER) }) else null,
                            )
                            PageId.CALENDAR -> {
                                val card = @Composable {
                                    CalendarCardPage(now, name, event, calendarGranted, accent, actions.onRequestCalendar)
                                }
                                if (calendarWidgetId == NO_WIDGET) card() else CalendarWidgetPage(calendarWidgetId, createWidgetView, card)
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 28.dp).height(32.dp),
                ) {
                    if (pages.size > 1) PageDots(count = pages.size, current = pagerState.currentPage, accent = accent)
                    if (focusActive) FocusBadge(accent)
                }

                AnimatedVisibility(
                    visible = nowPlaying != null,
                    enter = expandVertically(Springs.soft()) + fadeIn(Springs.soft()),
                    exit = shrinkVertically(Springs.soft()) + fadeOut(Springs.soft()),
                ) {
                    nowPlaying?.let { NowPlayingBar(it, accent) }
                }

                Column(modifier = Modifier.padding(start = 28.dp, top = 4.dp)) {
                    pinned.forEach { app -> PinnedRow(app, app.packageName in notified, accent, actions) }
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
                Spacer(Modifier.height((SEARCH_PILL_HEIGHT_DP + 24).dp))
            }
        }

        // While typing: blurred, dimmed home; a tap outside the pill ends the search.
        val scrim by animateFloatAsState(if (searchFocused) 0.45f else 0f, Springs.soft(), label = "scrim")
        if (scrim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrim))
                    .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
            )
        }

        val pillBottom by animateDpAsState(if (searchFocused) 12.dp else 16.dp, Springs.soft(), label = "pill")
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = pillBottom),
        ) {
            val trimmed = query.trim()
            AnimatedVisibility(
                visible = searchFocused && trimmed.isEmpty() && spotlight.suggestions.isNotEmpty(),
                enter = slideInVertically(Springs.soft()) { it / 4 } + fadeIn(Springs.soft()),
                exit = fadeOut(Springs.snappy()),
            ) {
                SuggestionsPanel(spotlight.suggestions) { app, bounds ->
                    focusManager.clearFocus()
                    actions.onLaunch(app, bounds)
                }
            }
            AnimatedVisibility(
                visible = searchFocused && trimmed.isNotEmpty(),
                enter = slideInVertically(Springs.soft()) { it / 4 } + fadeIn(Springs.soft()),
                exit = slideOutVertically(Springs.soft()) { it / 4 } + fadeOut(Springs.soft()),
            ) {
                SpotlightResults(
                    query = trimmed,
                    config = spotlight,
                    onLaunch = { app, bounds ->
                        query = ""
                        focusManager.clearFocus()
                        actions.onLaunch(app, bounds)
                    },
                    onSearch = {
                        actions.onWebSearch(trimmed)
                        query = ""
                        focusManager.clearFocus()
                    },
                    onAskClaude = spotlight.onAskClaude?.let { ask ->
                        {
                            ask(trimmed)
                            query = ""
                            focusManager.clearFocus()
                        }
                    },
                )
            }
            SearchPill(
                query = query,
                onQueryChange = { query = it },
                focused = searchFocused,
                onFocusChange = { searchFocused = it },
                focusRequester = searchFocus,
                accent = accent,
                placeholder = spotlight.placeholder,
                onSearch = {
                    if (trimmed.isNotEmpty()) {
                        actions.onWebSearch(trimmed)
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
                onGallery = {
                    focusManager.clearFocus()
                    actions.onGallery()
                },
            )
        }

        HourlySheet(visible = showHourly, weather = weather, onDismiss = { showHourly = false })

        ChargingRing(ringAppear.value, ringSweep.value, battery, accent)
    }
}

@Composable
private fun FocusBadge(accent: Color) {
    Text(
        text = "Skupienie",
        color = accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(start = 12.dp)
            .border(1.dp, accent, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** iOS Live Activity in miniature: what is playing, with play/pause and next. */
@Composable
private fun NowPlayingBar(item: NowPlaying, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .bouncyClick(pressedScale = 0.98f) { item.open() }
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.artist.isNotBlank()) {
                Text(item.artist, color = Quiet, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).bouncyClick(pressedScale = 0.85f) { item.togglePlay() },
        ) {
            if (item.playing) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(width = 4.dp, height = 16.dp).background(accent, RoundedCornerShape(1.dp)))
                    Box(Modifier.size(width = 4.dp, height = 16.dp).background(accent, RoundedCornerShape(1.dp)))
                }
            } else {
                PlayGlyph(accent)
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).bouncyClick(pressedScale = 0.85f) { item.next() },
        ) {
            NextGlyph(Ink)
        }
    }
}

@Composable
private fun PlayGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.1f)
            lineTo(size.width * 0.9f, size.height * 0.5f)
            lineTo(size.width * 0.2f, size.height * 0.9f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun NextGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.15f)
            lineTo(size.width * 0.65f, size.height * 0.5f)
            lineTo(size.width * 0.1f, size.height * 0.85f)
            close()
        }
        drawPath(p, color)
        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.15f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.14f, size.height * 0.7f))
    }
}

/** Apps and a calculator result above the pill while typing, like Spotlight. */
@Composable
private fun SpotlightResults(
    query: String,
    config: SpotlightConfig,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onSearch: () -> Unit,
    onAskClaude: (() -> Unit)?,
) {
    val folded = remember(query) { query.foldForSearch() }
    val apps = remember(folded, config.apps, config.showApps) {
        if (!config.showApps) emptyList() else config.apps
            .filter { it.searchKey.contains(folded) }
            .sortedBy { if (it.label.foldForSearch().startsWith(folded)) 0 else 1 }
            .take(5)
    }
    val result = remember(query, config.calculator) {
        if (config.calculator) com.adrian.launcher.data.Calculator.evaluate(query) else null
    }
    Column(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(22.dp))
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(vertical = 6.dp),
    ) {
        if (result != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClick(pressedScale = 0.98f) { config.onCopy(result) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text("= $result", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("kopiuj", color = Quiet, fontSize = 12.sp)
            }
        }
        apps.forEach { app ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .bouncyClick(pressedScale = 0.98f) { b -> onLaunch(app, b) }
                    .padding(horizontal = 18.dp),
            ) {
                AppIcon(app)
                Spacer(Modifier.width(16.dp))
                Text(app.label, color = Ink, fontSize = 16.sp, maxLines = 1)
            }
        }
        if (apps.isNotEmpty() || result != null) {
            Box(Modifier.padding(horizontal = 18.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Line))
        }
        if (onAskClaude != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClick(pressedScale = 0.98f) { onAskClaude() }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                ResultGlyph(CHAT_PATH, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Zapytaj Claude", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("„$query”", color = Quiet, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(Line))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .bouncyClick(pressedScale = 0.98f) { onSearch() }
                .padding(horizontal = 18.dp),
        ) {
            ResultGlyph(GLOBE_PATH, Quiet)
            Spacer(Modifier.width(16.dp))
            Text("Szukaj w ${config.engineTitle}", color = Quiet, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Next 12 hours, opened by tapping the weather. */
@Composable
private fun HourlySheet(visible: Boolean, weather: WeatherNow?, onDismiss: () -> Unit) {
    val scrim by animateFloatAsState(if (visible) 0.45f else 0f, Springs.soft(), label = "hourlyScrim")
    if (scrim > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(Springs.soft()) { it } + fadeIn(Springs.soft()),
            exit = slideOutVertically(Springs.soft()) { it } + fadeOut(Springs.soft()),
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(28.dp))
                    .border(1.dp, Line, RoundedCornerShape(28.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(vertical = 20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .background(Line, CircleShape),
                )
                val place = weather?.place?.takeIf { it.isNotBlank() }
                Text(
                    text = "Najbliższe 12 godzin" + (place?.let { " · $it" } ?: ""),
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 16.dp),
                )
                val hours = weather?.hourly.orEmpty()
                if (hours.isEmpty()) {
                    Text(
                        "Prognoza pojawi się przy następnym odświeżeniu pogody.",
                        color = Quiet, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                } else {
                    Text(
                        "przewiń w bok",
                        color = Quiet, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 24.dp, top = 0.dp, bottom = 12.dp),
                    )
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                        items(hours) { h ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.width(58.dp),
                            ) {
                                Text(h.time, color = Quiet, fontSize = 13.sp)
                                WeatherGlyph(h.code, 24)
                                Text("${h.temperature.roundToInt()}°", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("${h.windKmh.roundToInt()} km/h", color = Quiet, fontSize = 11.sp)
                            }
                        }
                    }
                }
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
private fun Modifier.homeGestures(
    pagerState: PagerState,
    actions: HomeActions,
    enabled: Boolean,
    upDragsDrawer: Boolean,
    fire: (Gesture) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val currentActions by rememberUpdatedState(actions)
    val currentFire by rememberUpdatedState(fire)
    val dragsDrawer by rememberUpdatedState(upDragsDrawer)
    if (!enabled) return this
    return pointerInput(pagerState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Edge swipes: right on the first page, left on the last, where there is no page to go to.
            val atFirst = pagerState.currentPage == 0 && abs(pagerState.currentPageOffsetFraction) < 0.01f
            val atLast = pagerState.currentPage == pagerState.pageCount - 1 && abs(pagerState.currentPageOffsetFraction) < 0.01f
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
                        DragAxis.HORIZONTAL -> {
                            when {
                                atFirst && totalX > SWIPE_DOWN_THRESHOLD_PX -> currentFire(Gesture.SWIPE_RIGHT)
                                atLast && totalX < -SWIPE_DOWN_THRESHOLD_PX -> currentFire(Gesture.SWIPE_LEFT)
                            }
                            scope.launch { settlePager(pagerState, v.x) }
                        }
                        DragAxis.UP -> {
                            if (dragsDrawer) {
                                // A very short flick gives the tracker too few samples; fall back
                                // to the average speed of the whole gesture.
                                val elapsedMs = (change.uptimeMillis - down.uptimeMillis).coerceAtLeast(1L)
                                val average = totalY / elapsedMs * 1000f
                                currentActions.onDrawerDragEnd(if (abs(v.y) > abs(average)) v.y else average)
                            } else if (totalY < -SWIPE_DOWN_THRESHOLD_PX) {
                                currentFire(Gesture.SWIPE_UP)
                            }
                        }
                        DragAxis.DOWN -> if (totalY > SWIPE_DOWN_THRESHOLD_PX) currentFire(Gesture.SWIPE_DOWN)
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
                        if (dragsDrawer) currentActions.onDrawerDrag(delta.y)
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
                        if (axis == DragAxis.UP && dragsDrawer) currentActions.onDrawerDrag(totalY)
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
    Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun PinnedRow(app: AppEntry, hasNotification: Boolean, accent: Color, actions: HomeActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(260.dp)
            .height(44.dp)
            .bouncyClick(onLongClick = { actions.onAppLongPress(app) }) { bounds -> actions.onLaunch(app, bounds) },
    ) {
        AppIcon(app)
        Spacer(Modifier.width(18.dp))
        Text(app.label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Normal, maxLines = 1)
        if (hasNotification) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).background(accent, CircleShape))
        }
    }
}

@Composable
private fun SuggestionsPanel(apps: List<AppEntry>, onLaunch: (AppEntry, Rect?) -> Unit) {
    Column(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(22.dp))
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(top = 14.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
    ) {
        Text(
            "PROPONOWANE",
            color = com.adrian.launcher.ui.Faint, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp,
            modifier = Modifier.padding(start = 10.dp, bottom = 10.dp),
        )
        Row {
            apps.take(4).forEach { app ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClick(pressedScale = 0.9f) { b -> onLaunch(app, b) }
                        .padding(vertical = 8.dp),
                ) {
                    AppIcon(app, size = 24.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(app.label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(4 - apps.take(4).size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Thin ring filled to the battery level; [appear] and [sweep] are driven by the home screen. */
@Composable
private fun ChargingRing(appear: Float, sweep: Float, battery: Int, accent: Color) {
    if (appear < 0.01f) return
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appear.coerceIn(0f, 1f)
                val sc = 0.85f + 0.15f * appear
                scaleX = sc
                scaleY = sc
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(200.dp)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, topLeft, arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(accent, -90f, 360f * sweep, false, topLeft, arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$battery%", color = Ink, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
            Text("ładowanie", color = Quiet, fontSize = 13.sp)
        }
    }
}

private const val CHAT_PATH = "M4 5h16v11H9l-5 4zM8 9.5h8M8 12.5h5"
private const val GLOBE_PATH = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18M3 12h18M12 3c3.2 3 3.2 15 0 18M12 3c-3.2 3-3.2 15 0 18"

@Composable
private fun ResultGlyph(path: String, color: Color) {
    val parsed = remember(path) { androidx.compose.ui.graphics.vector.PathParser().parsePathString(path).toPath() }
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val k = size.width / 24f
        scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(
                parsed, color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.6.dp.toPx() / k,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
    }
}
