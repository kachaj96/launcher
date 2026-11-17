package com.adrian.launcher

import android.Manifest
import android.app.ActivityOptions
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.adrian.launcher.data.AppEntry
import com.adrian.launcher.data.AppRepository
import com.adrian.launcher.data.CalendarEvent
import com.adrian.launcher.data.CalendarRepository
import com.adrian.launcher.data.LauncherSettings
import com.adrian.launcher.data.MAX_PINNED
import com.adrian.launcher.data.NO_WIDGET
import com.adrian.launcher.data.SettingsStore
import com.adrian.launcher.data.WEATHER_MAX_AGE_MS
import com.adrian.launcher.data.Wallpaper
import com.adrian.launcher.data.WeatherNow
import com.adrian.launcher.data.WeatherRepository
import com.adrian.launcher.data.WeatherResult
import com.adrian.launcher.data.WidgetChoice
import com.adrian.launcher.data.Widgets
import com.adrian.launcher.data.sortedByLabel
import com.adrian.launcher.drawer.AppDrawer
import com.adrian.launcher.home.HomeActions
import com.adrian.launcher.home.HomeScreen
import com.adrian.launcher.home.WeatherHint
import com.adrian.launcher.settings.SettingsActions
import com.adrian.launcher.settings.SettingsScreen
import com.adrian.launcher.settings.SettingsStatus
import com.adrian.launcher.system.SystemPanels
import com.adrian.launcher.ui.AppMenu
import com.adrian.launcher.ui.AppMenuActions
import com.adrian.launcher.ui.LauncherTheme
import com.adrian.launcher.ui.RenameDialog
import com.adrian.launcher.ui.Springs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Packages pinned on first start, in this order, if installed (Samsung + Google defaults). */
private val DEFAULT_PINS = listOf(
    "com.android.chrome",
    "com.sec.android.app.camera",
    "com.android.vending",
    "com.google.android.apps.messaging",
    "com.samsung.android.messaging",
    "com.sec.android.gallery3d",
    "com.google.android.apps.photos",
    "com.android.settings",
)

private const val REQUEST_WIDGET_CONFIGURE = 4711

class MainActivity : ComponentActivity() {

    private lateinit var appsRepo: AppRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var wallpaper: Wallpaper
    private lateinit var weatherRepo: WeatherRepository
    private lateinit var calendarRepo: CalendarRepository
    private lateinit var widgets: Widgets
    /** Widget id waiting for the bind permission or its configuration screen. */
    private var pendingWidgetId: Int = NO_WIDGET
    private var appsCallback: LauncherApps.Callback? = null
    private var weatherJob: Job? = null

    private val rawApps = mutableStateOf<List<AppEntry>>(emptyList())
    /** Drawer position: 0 closed, 1 open. Driven by the finger, then by a spring. */
    private val drawer = Animatable(0f)
    /** Home entrance after returning from an app: 0 = zoomed in and faded, 1 = at rest. */
    private val homeEnter = Animatable(1f)
    private var returningFromApp = false
    private val settingsOpen = mutableStateOf(false)
    private val menuApp = mutableStateOf<AppEntry?>(null)
    private val renameApp = mutableStateOf<AppEntry?>(null)
    private val isDefaultHome = mutableStateOf(false)
    private val locationGranted = mutableStateOf(false)
    private val calendarGranted = mutableStateOf(false)
    private val nextEvent = mutableStateOf<CalendarEvent?>(null)
    private val weatherProblem = mutableStateOf<WeatherResult?>(null)
    private val weatherLoading = mutableStateOf(false)
    private val resetSignal = mutableIntStateOf(0)

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Next time the phone is unlocked, start from the clock with nothing open.
            closeOverlays()
            resetSignal.intValue++
        }
    }

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshSystemState()
    }
    private val locationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshSystemState()
        refreshWeather(force = true)
    }
    private val calendarRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshSystemState()
    }
    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) saveWallpaper(uri)
    }
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportSettings(uri)
    }
    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importSettings(uri)
    }
    private val widgetBindRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId
        val info = widgets.info(id)
        if (result.resultCode == RESULT_OK && info != null) configureOrCommitWidget(id, info) else abandonWidget(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        appsRepo = AppRepository(this)
        settingsStore = SettingsStore(this)
        wallpaper = Wallpaper(this)
        weatherRepo = WeatherRepository(this)
        calendarRepo = CalendarRepository(this)
        widgets = Widgets(this)

        appsCallback = appsRepo.registerChanges { reloadApps() }
        reloadApps()
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // A home screen has nowhere to go back to; overlays add their own handlers on top.
        onBackPressedDispatcher.addCallback(this) { }

        setContent {
            val settings by settingsStore.flow.collectAsState(initial = null)
            settings?.let { LauncherContent(it) }
        }
    }

    @Composable
    private fun LauncherContent(settings: LauncherSettings) {
        val accent = Color(settings.accent)
        val apps = rawApps.value

        val renamed = remember(apps, settings.renames) {
            apps.map { app -> settings.renames[app.key]?.let { app.copy(label = it) } ?: app }.sortedByLabel()
        }
        val byKey = remember(renamed) { renamed.associateBy { it.key } }
        val visible = remember(renamed, settings.hidden) { renamed.filter { it.key !in settings.hidden } }
        val pinned = remember(byKey, settings.pinned) { settings.pinned.mapNotNull { byKey[it] } }
        val recent = remember(byKey, settings.lastLaunch) {
            settings.lastLaunch.entries.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }.take(4)
        }
        val weather = remember(settings.weatherCache) { WeatherNow.fromJson(settings.weatherCache) }
        val wallpaperBitmap by produceState<ImageBitmap?>(null, settings.wallpaperVersion) {
            value = withContext(Dispatchers.IO) { wallpaper.load()?.asImageBitmap() }
        }
        val widgetChoices by produceState(emptyList<WidgetChoice>(), settingsOpen.value) {
            if (settingsOpen.value) value = withContext(Dispatchers.IO) { widgets.choices() }
        }
        val currentWidget = remember(widgetChoices, settings.calendarWidgetId) {
            val info = widgets.info(settings.calendarWidgetId)
            if (info == null) null else widgetChoices.firstOrNull { it.info.provider == info.provider && it.info.profile == info.profile }
        }

        val menuTarget = menuApp.value
        val renameTarget = renameApp.value

        val drawerVisible by remember { derivedStateOf { drawer.value > 0f } }
        BackHandler(enabled = drawerVisible) { closeDrawer() }
        BackHandler(enabled = settingsOpen.value) { settingsOpen.value = false }
        BackHandler(enabled = menuTarget != null) { menuApp.value = null }

        LauncherTheme(accent) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    accent = accent,
                    wallpaper = wallpaperBitmap,
                    gradient = settings.gradient,
                    pages = settings.enabledPages,
                    pinned = pinned,
                    name = settings.name,
                    weather = weather,
                    weatherHint = weatherHint(settings),
                    event = nextEvent.value,
                    calendarGranted = calendarGranted.value,
                    calendarWidgetId = settings.calendarWidgetId,
                    createWidgetView = { id -> widgets.createView(this@MainActivity, id) },
                    isDefaultHome = isDefaultHome.value,
                    resetSignal = resetSignal.intValue,
                    drawerProgress = { drawer.value },
                    enterProgress = { homeEnter.value },
                    actions = HomeActions(
                        onLaunch = ::launchApp,
                        onAppLongPress = { menuApp.value = it },
                        onDrawerDrag = ::dragDrawer,
                        onDrawerDragEnd = ::settleDrawer,
                        onOpenDrawer = ::openDrawer,
                        onSwipeDown = { swipeDown(settings) },
                        onOpenSettings = { settingsOpen.value = true },
                        onRequestDefault = ::requestDefaultHome,
                        onRequestCalendar = { calendarRequest.launch(Manifest.permission.READ_CALENDAR) },
                    ),
                )

                if (drawerVisible) {
                    AppDrawer(
                        apps = visible,
                        searchPool = renamed,
                        recent = recent,
                        accent = accent,
                        progress = { drawer.value },
                        // On the drawer, a downward drag (positive) closes it.
                        onDrag = ::dragDrawer,
                        onDragEnd = ::settleDrawer,
                        onLaunch = ::launchApp,
                        onLongPress = { menuApp.value = it },
                        onClose = { closeDrawer() },
                    )
                }

                AnimatedVisibility(
                    visible = settingsOpen.value,
                    enter = slideInVertically(Springs.soft()) { it / 8 } + fadeIn(Springs.soft()),
                    exit = slideOutVertically(Springs.soft()) { it / 8 } + fadeOut(Springs.soft()),
                ) {
                    SettingsScreen(
                        settings = settings,
                        allApps = renamed,
                        wallpaper = wallpaperBitmap,
                        accent = accent,
                        status = SettingsStatus(
                            isDefaultHome = isDefaultHome.value,
                            locationGranted = locationGranted.value,
                            calendarGranted = calendarGranted.value,
                            weatherLine = weatherStatusLine(weather),
                            monoIcons = apps.count { it.icon != null },
                            totalApps = apps.size,
                            version = appVersion(),
                        ),
                        widgetChoices = widgetChoices,
                        currentWidget = currentWidget,
                        actions = SettingsActions(
                            update = ::updateSettings,
                            updateWeather = ::updateWeatherSettings,
                            onPickWallpaper = {
                                wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onClearWallpaper = {
                                wallpaper.clear()
                                updateSettings { it.copy(wallpaperVersion = System.currentTimeMillis()) }
                            },
                            onRequestLocation = { locationRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                            onRequestCalendar = { calendarRequest.launch(Manifest.permission.READ_CALENDAR) },
                            onRefreshWeather = { refreshWeather(force = true) },
                            onRequestDefault = ::requestDefaultHome,
                            onChooseWidget = ::chooseCalendarWidget,
                            onExport = { exportPicker.launch("launcher-ustawienia.json") },
                            onImport = { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            onClose = { settingsOpen.value = false },
                        ),
                    )
                }

                if (menuTarget != null) {
                    val isPinned = menuTarget.key in settings.pinned
                    AppMenu(
                        app = menuTarget,
                        isPinned = isPinned,
                        pinnedFull = settings.pinned.size >= MAX_PINNED,
                        isHidden = menuTarget.key in settings.hidden,
                        accent = accent,
                        actions = AppMenuActions(
                            onTogglePin = {
                                updateSettings { s ->
                                    if (isPinned) s.copy(pinned = s.pinned - menuTarget.key)
                                    else s.copy(pinned = (s.pinned + menuTarget.key).take(MAX_PINNED))
                                }
                                menuApp.value = null
                            },
                            onRename = {
                                renameApp.value = menuTarget
                                menuApp.value = null
                            },
                            onToggleHidden = {
                                val key = menuTarget.key
                                updateSettings { s ->
                                    if (key in s.hidden) s.copy(hidden = s.hidden - key) else s.copy(hidden = s.hidden + key)
                                }
                                menuApp.value = null
                            },
                            onInfo = {
                                runCatching { appsRepo.openAppInfo(menuTarget) }
                                menuApp.value = null
                            },
                            onUninstall = {
                                runCatching { appsRepo.uninstall(menuTarget) }
                                menuApp.value = null
                            },
                            onDismiss = { menuApp.value = null },
                        ),
                    )
                }

                if (renameTarget != null) {
                    RenameDialog(
                        app = renameTarget,
                        onConfirm = { name ->
                            val key = renameTarget.key
                            updateSettings { s ->
                                if (name.isBlank() || name == renameTarget.originalLabel) s.copy(renames = s.renames - key)
                                else s.copy(renames = s.renames + (key to name))
                            }
                            renameApp.value = null
                        },
                        onReset = {
                            updateSettings { s -> s.copy(renames = s.renames - renameTarget.key) }
                            renameApp.value = null
                        },
                        onDismiss = { renameApp.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on the launcher: close whatever is open, else go back to the clock.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val somethingOpen = drawer.value > 0f || settingsOpen.value || menuApp.value != null || renameApp.value != null
            closeOverlays()
            if (!somethingOpen) resetSignal.intValue++
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        widgets.stopListening()
        // Whatever took over the screen, come back to a closed drawer with the iOS zoom-out.
        returningFromApp = true
        closeDrawer(animated = false)
        lifecycleScope.launch { homeEnter.snapTo(0f) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (returningFromApp) {
            returningFromApp = false
            lifecycleScope.launch { homeEnter.animateTo(1f, Springs.soft()) }
        }
        refreshSystemState()
        refreshWeather(force = false)
    }

    override fun onDestroy() {
        appsCallback?.let { appsRepo.unregisterChanges(it) }
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    private fun closeOverlays() {
        closeDrawer()
        settingsOpen.value = false
        menuApp.value = null
        renameApp.value = null
    }

    private fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        lifecycleScope.launch { settingsStore.update(transform) }
    }

    /**
     * Calendar page widget. null = built-in card. A new widget id is bound (asking the user
     * once if Android requires it), configured if the widget has a setup screen, and only
     * then replaces the old one.
     */
    private fun chooseCalendarWidget(choice: WidgetChoice?) {
        if (choice == null) {
            lifecycleScope.launch {
                val old = settingsStore.current().calendarWidgetId
                settingsStore.update { it.copy(calendarWidgetId = NO_WIDGET) }
                widgets.delete(old)
            }
            return
        }
        val id = widgets.allocate()
        pendingWidgetId = id
        if (widgets.bindIfAllowed(id, choice.info)) {
            configureOrCommitWidget(id, choice.info)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, choice.info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, choice.info.profile)
            widgetBindRequest.launch(intent)
        }
    }

    private fun configureOrCommitWidget(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            pendingWidgetId = id
            val started = runCatching {
                widgets.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_WIDGET_CONFIGURE, null)
            }.isSuccess
            if (!started) commitWidget(id)
        } else {
            commitWidget(id)
        }
    }

    private fun commitWidget(id: Int) {
        pendingWidgetId = NO_WIDGET
        lifecycleScope.launch {
            val old = settingsStore.current().calendarWidgetId
            settingsStore.update { it.copy(calendarWidgetId = id) }
            if (old != id) widgets.delete(old)
            Toast.makeText(this@MainActivity, "Widżet ustawiony na stronie kalendarza", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abandonWidget(id: Int) {
        pendingWidgetId = NO_WIDGET
        widgets.delete(id)
        Toast.makeText(this, "Widżet nie został dodany", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult, which has no Activity Result API variant.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WIDGET_CONFIGURE) {
            val id = pendingWidgetId
            if (resultCode == RESULT_OK) commitWidget(id) else abandonWidget(id)
        }
    }

    private fun updateWeatherSettings(transform: (LauncherSettings) -> LauncherSettings) {
        lifecycleScope.launch {
            settingsStore.update { transform(it).copy(weatherCache = "") }
            weatherProblem.value = null
            weatherJob?.cancel()
            refreshWeather(force = true)
        }
    }

    private fun reloadApps() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.Default) { appsRepo.loadApps() }
            rawApps.value = apps
            ensureDefaultPins(apps)
        }
    }

    /** First start only: pin common apps that are installed, so the home screen is not empty. */
    private suspend fun ensureDefaultPins(apps: List<AppEntry>) {
        if (apps.isEmpty()) return
        val byPackage = apps.groupBy { it.packageName }
        val current = settingsStore.current()
        if (!current.pinnedInitialized) {
            // One messaging app only: Google Messages if installed, else Samsung's.
            val messaging = listOf("com.google.android.apps.messaging", "com.samsung.android.messaging")
                .firstOrNull { byPackage.containsKey(it) }
            val picks = DEFAULT_PINS
                .filter { it !in setOf("com.google.android.apps.messaging", "com.samsung.android.messaging") || it == messaging }
                .mapNotNull { byPackage[it]?.firstOrNull()?.key }
                .distinct()
                .take(MAX_PINNED)
            settingsStore.update { it.copy(pinned = picks, pinnedInitialized = true, pinsDeduped = true) }
        } else if (!current.pinsDeduped) {
            // Earlier versions pinned both messaging apps; keep Google Messages.
            val samsung = byPackage["com.samsung.android.messaging"].orEmpty().map { it.key }.toSet()
            val google = byPackage["com.google.android.apps.messaging"].orEmpty().map { it.key }.toSet()
            settingsStore.update { s ->
                val both = s.pinned.any { it in samsung } && s.pinned.any { it in google }
                s.copy(pinned = if (both) s.pinned.filter { it !in samsung } else s.pinned, pinsDeduped = true)
            }
        }
    }

    /**
     * Opens the app growing out of the row that was tapped (iOS-style). The drawer stays
     * put underneath and is reset when the launcher goes to the background, so nothing
     * flickers during the transition.
     */
    private fun launchApp(app: AppEntry, bounds: android.graphics.Rect?) {
        val options = bounds?.let {
            ActivityOptions.makeScaleUpAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
        }
        val ok = runCatching { appsRepo.launch(app, bounds, options) }.isSuccess
        menuApp.value = null
        if (!ok) {
            Toast.makeText(this, "Nie udało się otworzyć: ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        returningFromApp = true
        val now = System.currentTimeMillis()
        updateSettings { s ->
            val history = (s.lastLaunch + (app.key to now)).entries
                .sortedByDescending { it.value }.take(20).associate { it.key to it.value }
            s.copy(lastLaunch = history)
        }
    }

    private fun drawerTravelPx(): Float = resources.displayMetrics.heightPixels * 0.55f

    /** Finger moved by [dy] px; up (negative) opens, down closes. */
    private fun dragDrawer(dy: Float) {
        lifecycleScope.launch {
            drawer.snapTo((drawer.value - dy / drawerTravelPx()).coerceIn(0f, 1f))
        }
    }

    /** Finger lifted: a quick flick decides, otherwise the position does. */
    private fun settleDrawer(velocityY: Float) {
        val open = when {
            velocityY < -900f -> true
            velocityY > 900f -> false
            else -> drawer.value > 0.4f
        }
        if (open) openDrawer() else closeDrawer()
    }

    private fun openDrawer() {
        if (drawer.targetValue < 1f) window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        lifecycleScope.launch { drawer.animateTo(1f, Springs.soft()) }
    }

    private fun closeDrawer(animated: Boolean = true) {
        lifecycleScope.launch {
            if (animated) drawer.animateTo(0f, Springs.soft()) else drawer.snapTo(0f)
        }
    }

    private fun swipeDown(settings: LauncherSettings) {
        if (settings.swipeDownBroken) return
        if (!SystemPanels.expandNotifications(this)) {
            updateSettings { it.copy(swipeDownBroken = true) }
            Toast.makeText(this, "Gest w dół nie działa na tym telefonie, wyłączam go", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshSystemState() {
        val roleManager = getSystemService(RoleManager::class.java)
        isDefaultHome.value = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        locationGranted.value = weatherRepo.hasLocationPermission()
        calendarGranted.value = calendarRepo.hasPermission()
        lifecycleScope.launch {
            nextEvent.value = withContext(Dispatchers.IO) { calendarRepo.nextEvent() }
        }
    }

    private fun refreshWeather(force: Boolean) {
        if (weatherJob?.isActive == true) return
        weatherJob = lifecycleScope.launch {
            val s = settingsStore.current()
            val cached = WeatherNow.fromJson(s.weatherCache)
            if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < WEATHER_MAX_AGE_MS) return@launch
            weatherLoading.value = true
            val result = weatherRepo.fetch(s.weatherUseGps, s.weatherCity)
            weatherLoading.value = false
            if (result is WeatherResult.Success) {
                weatherProblem.value = null
                settingsStore.update { it.copy(weatherCache = result.weather.toJson()) }
            } else {
                weatherProblem.value = result
            }
        }
    }

    private fun weatherHint(settings: LauncherSettings): WeatherHint? = when (weatherProblem.value) {
        null -> if (weatherLoading.value) WeatherHint("Pobieram pogodę…") else null
        WeatherResult.NoLocation ->
            if (settings.weatherUseGps && !locationGranted.value) {
                WeatherHint("Brak lokalizacji dla pogody.", "Zezwól na lokalizację") {
                    locationRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            } else {
                WeatherHint("Nie znam lokalizacji. Wpisz miasto w ustawieniach.", "Otwórz ustawienia") { settingsOpen.value = true }
            }
        WeatherResult.CityNotFound ->
            WeatherHint("Nie znalazłem miasta „${settings.weatherCity}”.", "Otwórz ustawienia") { settingsOpen.value = true }
        WeatherResult.NetworkError ->
            WeatherHint("Brak połączenia z serwisem pogody.", "Spróbuj ponownie") { refreshWeather(force = true) }
        is WeatherResult.Success -> null
    }

    private fun weatherStatusLine(weather: WeatherNow?): String {
        if (weatherLoading.value) return "Pobieram…"
        val problem = when (weatherProblem.value) {
            WeatherResult.NoLocation -> "brak lokalizacji i miasta"
            WeatherResult.CityNotFound -> "nie znaleziono miasta"
            WeatherResult.NetworkError -> "brak połączenia"
            else -> null
        }
        val base = weather?.let {
            val time = java.time.Instant.ofEpochMilli(it.fetchedAt).atZone(java.time.ZoneId.systemDefault())
            "Ostatnio: ${it.place.ifBlank { "bez nazwy" }}, %02d:%02d".format(time.hour, time.minute)
        } ?: "Brak danych"
        return if (problem != null) "$base · $problem" else base
    }

    private fun saveWallpaper(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { wallpaper.save(uri) }
            if (ok) {
                settingsStore.update { it.copy(wallpaperVersion = System.currentTimeMillis()) }
            } else {
                Toast.makeText(this@MainActivity, "Nie udało się wczytać zdjęcia", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportSettings(uri: Uri) {
        lifecycleScope.launch {
            val json = settingsStore.exportJson(settingsStore.current())
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } ?: error("no stream")
                }.isSuccess
            }
            Toast.makeText(this@MainActivity, if (ok) "Zapisano kopię ustawień" else "Nie udało się zapisać pliku", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importSettings(uri: Uri) {
        lifecycleScope.launch {
            val ok = runCatching {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("no stream")
                }
                settingsStore.importJson(json)
            }.isSuccess
            Toast.makeText(this@MainActivity, if (ok) "Wczytano ustawienia" else "To nie jest plik ustawień launchera", Toast.LENGTH_SHORT).show()
            if (ok) refreshWeather(force = true)
        }
    }

    private fun appVersion(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    private fun requestDefaultHome() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }
}
