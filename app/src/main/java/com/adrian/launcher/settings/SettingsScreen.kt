package com.adrian.launcher.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adrian.launcher.data.Gesture
import com.adrian.launcher.data.GestureAction
import com.adrian.launcher.data.GestureBinding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adrian.launcher.data.AppEntry
import com.adrian.launcher.data.FocusMode
import com.adrian.launcher.data.LauncherSettings
import com.adrian.launcher.data.PageConfig
import com.adrian.launcher.data.SearchEngine
import com.adrian.launcher.data.WidgetChoice
import com.adrian.launcher.data.foldForSearch
import com.adrian.launcher.ui.AccentOptions
import com.adrian.launcher.ui.AppIcon
import com.adrian.launcher.ui.Chevron
import com.adrian.launcher.ui.Faint
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.Line
import com.adrian.launcher.ui.Panel
import com.adrian.launcher.ui.Quiet
import com.adrian.launcher.ui.Springs
import com.adrian.launcher.ui.blockTouchesBelow
import com.adrian.launcher.ui.bouncyClick

class SettingsStatus(
    val isDefaultHome: Boolean,
    val locationGranted: Boolean,
    val calendarGranted: Boolean,
    val notificationAccess: Boolean,
    val shortcutsAvailable: Boolean,
    val weatherLine: String,
    val monoIcons: Int,
    val totalApps: Int,
    val version: String,
    val braveInstalled: Boolean,
)

class SettingsActions(
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    /** Saves a weather setting, then refetches with it. */
    val updateWeather: ((LauncherSettings) -> LauncherSettings) -> Unit,
    val onPickWallpaper: () -> Unit,
    val onClearWallpaper: () -> Unit,
    val onRequestLocation: () -> Unit,
    val onRequestCalendar: () -> Unit,
    val onRequestNotificationAccess: () -> Unit,
    val onRefreshWeather: () -> Unit,
    val onRequestDefault: () -> Unit,
    val onChooseWidget: (WidgetChoice?) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onClose: () -> Unit,
)

private enum class Page(val title: String, val subtitle: String) {
    LOOK("Wygląd", "tapeta, akcent"),
    HOME("Ekran główny", "karuzela, kalendarz, przypięte, odtwarzanie"),
    SEARCH("Wyszukiwanie", "wyszukiwarka, przeglądarka, kalkulator"),
    DRAWER("Szuflada", "ostatnio używane, ukryte, nazwy"),
    FOCUS("Skupienie", "ukrywanie aplikacji według harmonogramu"),
    EVENING("Tryb wieczorny", "przygaszony ekran, ciepły akcent"),
    WEATHER("Pogoda", "lokalizacja, miasto"),
    GESTURES("Gesty i wibracje", "akcja dla każdego gestu, wibracje"),
    PERMISSIONS("Uprawnienia", "lokalizacja, kalendarz, powiadomienia"),
    BACKUP("Kopia ustawień", "eksport i import"),
    ABOUT("O launcherze", "wersja, ikony"),
}

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    allApps: List<AppEntry>,
    wallpaper: ImageBitmap?,
    accent: Color,
    status: SettingsStatus,
    widgetChoices: List<WidgetChoice>,
    currentWidget: WidgetChoice?,
    actions: SettingsActions,
) {
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    BackHandler(enabled = page != null) { page = null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .blockTouchesBelow()
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .bouncyClick(pressedScale = 0.85f) { if (page != null) page = null else actions.onClose() },
            ) { Chevron(pointsUp = false, color = Ink) }
            Text(page?.title ?: "Ustawienia", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(Springs.soft()) { if (forward) it / 3 else -it / 3 } + fadeIn(Springs.soft())) togetherWith
                    (slideOutHorizontally(Springs.soft()) { if (forward) -it / 3 else it / 3 } + fadeOut(Springs.soft()))
            },
            label = "settingsPage",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
            ) {
                val ctx = Ctx(settings, allApps, accent, status, actions)
                when (current) {
                    null -> Page.entries.forEach { p -> CategoryRow(p) { page = p } }
                    Page.LOOK -> ctx.LookPage(wallpaper)
                    Page.HOME -> ctx.HomePage(widgetChoices, currentWidget)
                    Page.SEARCH -> ctx.SearchPage()
                    Page.DRAWER -> ctx.DrawerPage()
                    Page.FOCUS -> ctx.FocusPage()
                    Page.EVENING -> ctx.EveningPage()
                    Page.WEATHER -> ctx.WeatherPage()
                    Page.GESTURES -> ctx.GesturesPage()
                    Page.PERMISSIONS -> ctx.PermissionsPage()
                    Page.BACKUP -> ctx.BackupPage()
                    Page.ABOUT -> ctx.AboutPage()
                }
            }
        }
    }
}

private class Ctx(
    val s: LauncherSettings,
    val apps: List<AppEntry>,
    val accent: Color,
    val status: SettingsStatus,
    val actions: SettingsActions,
) {
    val byKey = apps.associateBy { it.key }
    fun update(t: (LauncherSettings) -> LauncherSettings) = actions.update(t)
}

@Composable
private fun CategoryRow(page: Page, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(pressedScale = 0.98f) { onClick() }
            .padding(vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(page.title, color = Ink, fontSize = 17.sp)
            Text(page.subtitle, color = Quiet, fontSize = 13.sp)
        }
        Text("›", color = Faint, fontSize = 24.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
}

// ---- Pages -------------------------------------------------------------------------

@Composable
private fun Ctx.LookPage(wallpaper: ImageBitmap?) {
    Section("TAPETA")
    Row(verticalAlignment = Alignment.CenterVertically) {
        WallpaperPreview(wallpaper, s.gradient)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Zmień zdjęcie", actions.onPickWallpaper)
                if (wallpaper != null) Pill("Usuń", actions.onClearWallpaper)
            }
            var gradient by remember(s.gradient) { mutableStateOf(s.gradient) }
            Text("Szerokość czerni: ${(gradient * 100).toInt()}%", color = Quiet, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            Slider(
                value = gradient,
                onValueChange = { gradient = it },
                onValueChangeFinished = { update { it.copy(gradient = gradient) } },
                valueRange = 0.1f..0.6f,
            )
        }
    }
    Section("AKCENT")
    Row {
        AccentOptions.forEach { c ->
            val selected = c == accent
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .bouncyClick(pressedScale = 0.85f) { update { it.copy(accent = colorToLong(c)) } },
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .border(2.dp, if (selected) Ink else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .background(c, CircleShape),
                )
            }
        }
    }
    Hint("Akcent mają kropka strony, przycisk wyszukiwania, kropki powiadomień i alfabet w szufladzie.")
}

@Composable
private fun Ctx.HomePage(widgetChoices: List<WidgetChoice>, currentWidget: WidgetChoice?) {
    Section("KARUZELA")
    s.pages.forEachIndexed { i, page ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
            Text(page.id.title, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            MoveButtons(
                canUp = i > 0,
                canDown = i < s.pages.lastIndex,
                onUp = { update { it.copy(pages = it.pages.move(i, i - 1)) } },
                onDown = { update { it.copy(pages = it.pages.move(i, i + 1)) } },
            )
            Switch(
                checked = page.enabled,
                onCheckedChange = { on ->
                    update { st -> st.copy(pages = st.pages.map { if (it.id == page.id) PageConfig(it.id, on) else it }) }
                },
            )
        }
    }
    Hint("Po wygaszeniu ekranu karuzela wraca do zegara.")

    Section("STRONA KALENDARZA")
    ChoiceRow("Wbudowana karta", "data, powitanie, najbliższe wydarzenie", currentWidget == null) { actions.onChooseWidget(null) }
    val (own, others) = widgetChoices.partition { it.fromCalendarApp }
    if (own.isEmpty()) Hint("Nie widzę widżetów aplikacji Kalendarz. Jest zainstalowana?")
    own.forEach { c -> ChoiceRow(c.label, c.appLabel, currentWidget?.key == c.key) { actions.onChooseWidget(c) } }
    var showOthers by remember { mutableStateOf(false) }
    if (others.isNotEmpty()) {
        SmallAction(if (showOthers) "Ukryj widżety innych aplikacji" else "Widżety innych aplikacji (${others.size})") {
            showOthers = !showOthers
        }
    }
    if (showOthers) others.forEach { c -> ChoiceRow(c.label, c.appLabel, currentWidget?.key == c.key) { actions.onChooseWidget(c) } }
    Hint("Wygląd widżetu ustawia jego aplikacja. W Kalendarzu ustaw przezroczystość 0% i jasny tekst.")

    Section("POWITANIE")
    TextSetting(s.name, "Imię (puste = bez imienia)") { v -> update { it.copy(name = v) } }

    Section("PRZYPIĘTE")
    if (s.pinned.isEmpty()) Hint("Przytrzymaj aplikację w szufladzie, żeby ją przypiąć.")
    s.pinned.forEachIndexed { i, key ->
        val app = byKey[key]
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
            if (app != null) AppIcon(app) else Spacer(Modifier.width(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(app?.label ?: "(odinstalowana)", color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            MoveButtons(
                canUp = i > 0,
                canDown = i < s.pinned.lastIndex,
                onUp = { update { it.copy(pinned = it.pinned.move(i, i - 1)) } },
                onDown = { update { it.copy(pinned = it.pinned.move(i, i + 1)) } },
            )
            SmallAction("Odepnij") { update { it.copy(pinned = it.pinned - key) } }
        }
    }

    Section("DODATKI")
    SwitchRow("Teraz odtwarzane", "pasek z muzyką lub podcastem nad aplikacjami", s.nowPlaying) { on -> update { it.copy(nowPlaying = on) } }
    SwitchRow("Kropki powiadomień", "kropka przy aplikacji z nieprzeczytanym powiadomieniem", s.notificationDots) { on ->
        update { it.copy(notificationDots = on) }
    }
    if ((s.nowPlaying || s.notificationDots) && !status.notificationAccess) {
        Hint("Oba wymagają dostępu do powiadomień.")
        SmallAction("Włącz dostęp do powiadomień", actions.onRequestNotificationAccess)
    }
    SwitchRow("Propozycje aplikacji", "po stuknięciu w pasek: aplikacje, których używasz o tej porze", s.suggestions) { on ->
        update { it.copy(suggestions = on) }
    }
    SwitchRow("Pierścień ładowania", "poziom baterii na chwilę po podłączeniu ładowarki", s.chargingRing) { on ->
        update { it.copy(chargingRing = on) }
    }
    Hint("Stuknięcia w godzinę i pogodę ustawisz w Gesty i wibracje.")
}

@Composable
private fun Ctx.SearchPage() {
    Section("WYSZUKIWARKA")
    SearchEngine.entries.forEach { e ->
        ChoiceRow(e.title, if (e == SearchEngine.GOOGLE) "domyślna" else "", s.searchEngine == e) { update { it.copy(searchEngine = e) } }
    }
    Section("PRZEGLĄDARKA")
    SwitchRow(
        "Otwieraj w Brave",
        if (status.braveInstalled) "wyniki w aplikacji Brave" else "Brave nie jest zainstalowany, użyję domyślnej przeglądarki",
        s.searchInBrave,
    ) { on -> update { it.copy(searchInBrave = on) } }
    Section("PODCZAS PISANIA")
    SwitchRow("Aplikacje w wynikach", "pasujące aplikacje nad paskiem, jak Spotlight", s.searchShowsApps) { on -> update { it.copy(searchShowsApps = on) } }
    SwitchRow("Kalkulator", "„23*4” pokazuje wynik, stuknięcie kopiuje", s.calculator) { on -> update { it.copy(calculator = on) } }
    Hint("Aparat w pasku: stuknięcie otwiera Aparat, przytrzymanie Google Lens. Ikona obok otwiera galerię.")
}

@Composable
private fun Ctx.DrawerPage() {
    Section("SZUFLADA")
    SwitchRow("Ostatnio używane", "4 ostatnie aplikacje na górze listy", s.showRecent) { on -> update { it.copy(showRecent = on) } }
    SwitchRow("Uruchamiaj przy jednym trafieniu", "od 2 wpisanych liter", s.drawerAutoLaunch) { on -> update { it.copy(drawerAutoLaunch = on) } }

    Section("UKRYTE APLIKACJE")
    if (s.hidden.isEmpty()) Hint("Brak. Ukryte aplikacje nadal znajdziesz wyszukiwarką.")
    s.hidden.sortedBy { byKey[it]?.label ?: it }.forEach { key ->
        KeyRow(byKey[key], key, "Pokaż") { update { it.copy(hidden = it.hidden - key) } }
    }

    Section("ZMIENIONE NAZWY")
    if (s.renames.isEmpty()) Hint("Brak. Nazwę zmienisz, przytrzymując aplikację.")
    s.renames.keys.sortedBy { s.renames[it] }.forEach { key ->
        KeyRow(byKey[key], key, "Przywróć") { update { it.copy(renames = it.renames - key) } }
    }
}

@Composable
private fun Ctx.FocusPage() {
    Section("TRYB")
    FocusMode.entries.forEach { m -> ChoiceRow(m.title, "", s.focusMode == m) { update { it.copy(focusMode = m) } } }
    Hint("W trybie skupienia wybrane aplikacje znikają z ekranu głównego i szuflady. Wyszukiwarka nadal je znajdzie.")

    if (s.focusMode == FocusMode.SCHEDULE) {
        Section("DNI")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("pn", "wt", "śr", "cz", "pt", "sb", "nd").forEachIndexed { i, label ->
                val day = i + 1
                val on = day in s.focusDays
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (on) accent else Color.Transparent, CircleShape)
                        .border(1.dp, if (on) accent else Line, CircleShape)
                        .bouncyClick(pressedScale = 0.85f) {
                            update { st -> st.copy(focusDays = if (on) st.focusDays - day else st.focusDays + day) }
                        },
                ) { Text(label, color = if (on) Color.Black else Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
        }
        Section("GODZINY")
        TimeRow("Od", s.focusStart) { v -> update { it.copy(focusStart = v) } }
        TimeRow("Do", s.focusEnd) { v -> update { it.copy(focusEnd = v) } }
    }

    Section("UKRYWANE APLIKACJE (${s.focusApps.size})")
    var filter by remember { mutableStateOf("") }
    OutlinedTextField(
        value = filter,
        onValueChange = { filter = it },
        placeholder = { Text("Filtruj listę", color = Faint) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val folded = filter.trim().foldForSearch()
    val sorted = apps.sortedWith(compareBy<AppEntry>({ it.key !in s.focusApps }, { it.label.lowercase() }))
    sorted.filter { folded.isEmpty() || it.searchKey.contains(folded) }.forEach { app ->
        val checked = app.key in s.focusApps
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable {
                    update { st -> st.copy(focusApps = if (checked) st.focusApps - app.key else st.focusApps + app.key) }
                },
        ) {
            AppIcon(app)
            Spacer(Modifier.width(14.dp))
            Text(app.label, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            CheckMark(checked)
        }
    }
}

@Composable
private fun Ctx.EveningPage() {
    SwitchRow("Tryb wieczorny", "przygasza ekran główny i zmienia akcent na ciepły", s.eveningMode) { on -> update { it.copy(eveningMode = on) } }
    if (s.eveningMode) {
        Section("GODZINY")
        TimeRow("Od", s.eveningStart) { v -> update { it.copy(eveningStart = v) } }
        TimeRow("Do", s.eveningEnd) { v -> update { it.copy(eveningEnd = v) } }
        Hint("Okno może przechodzić przez północ, np. 22:00-6:00.")
    }
}

@Composable
private fun Ctx.WeatherPage() {
    SwitchRow("Lokalizacja z GPS", "przybliżona, tylko do pogody", s.weatherUseGps) { on -> actions.updateWeather { it.copy(weatherUseGps = on) } }
    if (s.weatherUseGps && !status.locationGranted) SmallAction("Zezwól na lokalizację", actions.onRequestLocation)
    Section("MIASTO ZAPASOWE")
    TextSetting(s.weatherCity, "np. Poznań") { v -> actions.updateWeather { it.copy(weatherCity = v) } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(status.weatherLine, color = Quiet, fontSize = 13.sp, modifier = Modifier.weight(1f))
        SmallAction("Odśwież", actions.onRefreshWeather)
    }
}

@Composable
private fun Ctx.GesturesPage() {
    var editing by remember { mutableStateOf<Gesture?>(null) }
    Section("GEST · AKCJA")
    Gesture.entries.forEach { g ->
        val b = s.gesture(g)
        val label = if (b.action == GestureAction.APP) (b.appKey?.let { byKey[it]?.label } ?: "aplikacja usunięta") else b.action.title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClick(pressedScale = 0.98f) { editing = g }
                .padding(vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(g.title, color = Ink, fontSize = 15.sp)
                Text(label, color = accent, fontSize = 13.sp)
            }
            Text("›", color = Faint, fontSize = 24.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
    Hint("„W prawo” działa na pierwszej stronie karuzeli, „w lewo” na ostatniej. Na pozostałych przesunięcie w bok zmienia stronę.")
    if (Gesture.entries.none { s.gesture(it).action == GestureAction.DRAWER }) {
        Text("Żaden gest nie otwiera teraz szuflady. Aplikacje znajdziesz wtedy tylko w wyszukiwarce.", color = Color(0xFFFF9B8A), fontSize = 13.sp)
    }
    if (s.swipeDownBroken) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Rozwijanie powiadomień nie zadziałało na tym telefonie, więc jest wstrzymane.",
                color = Color(0xFFFF9B8A), fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            SmallAction("Spróbuj ponownie") { update { it.copy(swipeDownBroken = false) } }
        }
    }
    Section("WIBRACJE")
    SwitchRow("Wibracje", "przy zmianie strony, otwieraniu szuflady i przytrzymaniu", s.haptics) { on -> update { it.copy(haptics = on) } }

    editing?.let { g ->
        GesturePicker(
            gesture = g,
            current = s.gesture(g),
            apps = apps,
            accent = accent,
            onPick = { binding ->
                update { st -> st.copy(gestures = st.gestures + (g to binding)) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Bottom sheet with every action; "Inna aplikacja" swaps the list for the apps. */
@Composable
private fun GesturePicker(
    gesture: Gesture,
    current: GestureBinding,
    apps: List<AppEntry>,
    accent: Color,
    onPick: (GestureBinding) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingApp by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .background(Panel, RoundedCornerShape(28.dp))
                    .border(1.dp, Line, RoundedCornerShape(28.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(vertical = 20.dp),
            ) {
                Text(gesture.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp))
                Text(
                    if (pickingApp) "Wybierz aplikację" else "Wybierz, co ma się stać",
                    color = Quiet, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                )
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (!pickingApp) {
                        GestureAction.entries
                            // The drawer is offered for whole-screen gestures, not for taps on the clock, weather or edges.
                            .filter { a -> a != GestureAction.DRAWER || gesture in setOf(Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.LONG_PRESS, Gesture.DOUBLE_TAP) }
                            .forEach { a ->
                                val selected = current.action == a
                                ChoiceRowCompact(if (a == GestureAction.APP) "Inna aplikacja…" else a.title, selected, accent) {
                                    if (a == GestureAction.APP) pickingApp = true else onPick(GestureBinding(a))
                                }
                            }
                    } else {
                        apps.forEach { app ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(GestureBinding(GestureAction.APP, app.key)) }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                            ) {
                                AppIcon(app)
                                Spacer(Modifier.width(14.dp))
                                Text(app.label, color = Ink, fontSize = 15.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRowCompact(title: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .border(1.5.dp, if (selected) accent else Quiet, CircleShape)
                .padding(3.dp)
                .background(if (selected) accent else Color.Transparent, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Text(title, color = Ink, fontSize = 15.sp)
    }
}

@Composable
private fun Ctx.PermissionsPage() {
    StatusRow("Domyślny ekran główny", status.isDefaultHome, actions.onRequestDefault)
    StatusRow("Lokalizacja (pogoda)", status.locationGranted, actions.onRequestLocation)
    StatusRow("Kalendarz (karta kalendarza)", status.calendarGranted, actions.onRequestCalendar)
    StatusRow("Powiadomienia (odtwarzanie, kropki)", status.notificationAccess, actions.onRequestNotificationAccess)
    StatusRow("Skróty aplikacji", status.shortcutsAvailable, actions.onRequestDefault)
    Hint(
        "Dostęp do powiadomień to osobna lista w ustawieniach telefonu (szukaj: „dostęp do powiadomień”), " +
            "nie przełącznik „Zezwalaj na powiadomienia”. Jeśli przełącznik Launchera jest zablokowany: " +
            "Ustawienia > Aplikacje > Launcher > ⋮ > Zezwól na ograniczone ustawienia, potem włącz go ponownie. " +
            "Skróty aplikacji działają tylko, gdy launcher jest domyślny.",
    )
}

@Composable
private fun Ctx.BackupPage() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Pill("Eksportuj do pliku", actions.onExport)
        Pill("Wczytaj z pliku", actions.onImport)
    }
    Hint("Zapisuje przypięte, ukryte, nazwy, karuzelę, akcent, wyszukiwanie, skupienie i pogodę. Tapeta i widżet nie są w kopii.")
}

@Composable
private fun Ctx.AboutPage() {
    Section("IKONY")
    Hint("Monochromatyczne: ${status.monoIcons} z ${status.totalApps}. Reszta dostaje literę w kółku.")
    Section("WERSJA")
    Hint("Launcher ${status.version}")
    Hint("Font Outfit, SIL Open Font License.")
}

// ---- Building blocks -----------------------------------------------------------------

fun colorToLong(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

private fun <T> List<T>.move(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    val m = toMutableList()
    val item = m.removeAt(from)
    m.add(to, item)
    return m
}

@Composable
private fun Section(title: String) {
    Text(
        title, color = Faint, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Quiet, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Text(
        label, color = Ink, fontSize = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(22.dp))
            .bouncyClick(pressedScale = 0.95f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Text(
        label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink, fontSize = 15.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = Quiet, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Quiet, CircleShape)
                .padding(4.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Ink, fontSize = 15.sp, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, color = Quiet, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CheckMark(checked: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(22.dp)
            .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.5.dp, if (checked) MaterialTheme.colorScheme.primary else Quiet, RoundedCornerShape(6.dp)),
    ) {
        if (checked) Text("✓", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Time of day in 30-minute steps, wrapping around midnight. */
@Composable
private fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(52.dp)) {
        Text(label, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Stepper("−") { onChange(Math.floorMod(minutes - 30, 24 * 60)) }
        Text(
            "%02d:%02d".format(minutes / 60, minutes % 60),
            color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(72.dp).padding(horizontal = 8.dp),
        )
        Stepper("+") { onChange(Math.floorMod(minutes + 30, 24 * 60)) }
    }
}

@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .background(Panel, CircleShape)
            .border(1.dp, Line, CircleShape)
            .bouncyClick(pressedScale = 0.85f) { onClick() },
    ) { Text(label, color = Ink, fontSize = 20.sp) }
}

@Composable
private fun MoveButtons(canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp).then(if (canUp) Modifier.clickable(onClick = onUp) else Modifier),
    ) { Chevron(pointsUp = true, color = if (canUp) Quiet else Line) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp).then(if (canDown) Modifier.clickable(onClick = onDown) else Modifier),
    ) { Chevron(pointsUp = false, color = if (canDown) Quiet else Line) }
}

@Composable
private fun KeyRow(app: AppEntry?, key: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
        if (app != null) AppIcon(app) else Spacer(Modifier.width(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(app?.label ?: key.substringBefore('/'), color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
        SmallAction(action, onAction)
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(52.dp)) {
        Box(Modifier.size(8.dp).background(if (ok) Color(0xFF7FD18B) else Color(0xFFFF9B8A), CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (ok) Text("tak", color = Quiet, fontSize = 13.sp) else SmallAction("Włącz", onFix)
    }
}

@Composable
private fun TextSetting(value: String, placeholder: String, onDone: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(placeholder, color = Faint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone(text.trim()) }),
        trailingIcon = { if (text.trim() != value) SmallAction("Zapisz") { onDone(text.trim()) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WallpaperPreview(wallpaper: ImageBitmap?, gradient: Float) {
    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 108.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .background(Color.Black),
    ) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper, contentDescription = null, contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color.Black,
                        gradient to Color.Black,
                        (gradient + 0.34f).coerceAtMost(1f) to Color.Transparent,
                    ),
                ),
            )
        } else {
            Text("brak", color = Faint, fontSize = 11.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}
