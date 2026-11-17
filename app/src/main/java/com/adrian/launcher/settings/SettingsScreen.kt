package com.adrian.launcher.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.adrian.launcher.data.LauncherSettings
import com.adrian.launcher.data.PageConfig
import com.adrian.launcher.ui.AccentOptions
import com.adrian.launcher.ui.AppIcon
import com.adrian.launcher.ui.Chevron
import com.adrian.launcher.ui.Faint
import com.adrian.launcher.ui.Ink
import com.adrian.launcher.ui.Line
import com.adrian.launcher.ui.Quiet
import com.adrian.launcher.ui.blockTouchesBelow

class SettingsStatus(
    val isDefaultHome: Boolean,
    val locationGranted: Boolean,
    val calendarGranted: Boolean,
    val weatherLine: String,
    val monoIcons: Int,
    val totalApps: Int,
    val version: String,
)

class SettingsActions(
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    /** Saves a weather setting, then refetches with it. */
    val updateWeather: ((LauncherSettings) -> LauncherSettings) -> Unit,
    val onPickWallpaper: () -> Unit,
    val onClearWallpaper: () -> Unit,
    val onRequestLocation: () -> Unit,
    val onRequestCalendar: () -> Unit,
    val onRefreshWeather: () -> Unit,
    val onRequestDefault: () -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    allApps: List<AppEntry>,
    wallpaper: ImageBitmap?,
    accent: Color,
    status: SettingsStatus,
    actions: SettingsActions,
) {
    val byKey = remember(allApps) { allApps.associateBy { it.key } }
    val update = actions.update

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
                modifier = Modifier.size(48.dp).clickable(onClick = actions.onClose),
            ) { Chevron(pointsUp = false, color = Ink) }
            Text("Ustawienia", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            run {
                Section("TAPETA")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WallpaperPreview(wallpaper, settings.gradient)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Zmień zdjęcie", actions.onPickWallpaper)
                            if (wallpaper != null) Pill("Usuń", actions.onClearWallpaper)
                        }
                        var gradient by remember(settings.gradient) { mutableStateOf(settings.gradient) }
                        Text(
                            "Szerokość czerni: ${(gradient * 100).toInt()}%",
                            color = Quiet, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
                        )
                        Slider(
                            value = gradient,
                            onValueChange = { gradient = it },
                            onValueChangeFinished = { update { it.copy(gradient = gradient) } },
                            valueRange = 0.1f..0.6f,
                        )
                    }
                }
            }

            run {
                Section("KARUZELA")
                settings.pages.forEachIndexed { i, page ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                        Text(page.id.title, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        MoveButtons(
                            canUp = i > 0,
                            canDown = i < settings.pages.lastIndex,
                            onUp = { update { it.copy(pages = it.pages.move(i, i - 1)) } },
                            onDown = { update { it.copy(pages = it.pages.move(i, i + 1)) } },
                        )
                        Switch(
                            checked = page.enabled,
                            onCheckedChange = { on ->
                                update { s -> s.copy(pages = s.pages.map { if (it.id == page.id) PageConfig(it.id, on) else it }) }
                            },
                        )
                    }
                }
                Hint("Po wygaszeniu ekranu karuzela wraca do zegara.")
            }

            run {
                Section("AKCENT")
                Row {
                    AccentOptions.forEach { c ->
                        val selected = c == accent
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { update { it.copy(accent = colorToLong(c)) } },
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
            }

            run {
                Section("POWITANIE")
                TextSetting(
                    value = settings.name,
                    placeholder = "Imię (puste = bez imienia)",
                    onDone = { v -> update { it.copy(name = v) } },
                )
            }

            run {
                Section("PRZYPIĘTE")
                if (settings.pinned.isEmpty()) Hint("Przytrzymaj aplikację w szufladzie, żeby ją przypiąć.")
                settings.pinned.forEachIndexed { i, key ->
                    val app = byKey[key]
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                        if (app != null) AppIcon(app) else Spacer(Modifier.width(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(app?.label ?: "(odinstalowana)", color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        MoveButtons(
                            canUp = i > 0,
                            canDown = i < settings.pinned.lastIndex,
                            onUp = { update { it.copy(pinned = it.pinned.move(i, i - 1)) } },
                            onDown = { update { it.copy(pinned = it.pinned.move(i, i + 1)) } },
                        )
                        SmallAction("Odepnij") { update { it.copy(pinned = it.pinned - key) } }
                    }
                }
            }

            run {
                Section("UKRYTE APLIKACJE")
                if (settings.hidden.isEmpty()) Hint("Brak. Ukryte aplikacje nadal znajdziesz wyszukiwarką.")
                settings.hidden.sortedBy { byKey[it]?.label ?: it }.forEach { key ->
                    KeyRow(byKey[key], key, "Pokaż") { update { it.copy(hidden = it.hidden - key) } }
                }
            }

            run {
                Section("ZMIENIONE NAZWY")
                if (settings.renames.isEmpty()) Hint("Brak.")
                settings.renames.keys.sortedBy { settings.renames[it] }.forEach { key ->
                    KeyRow(byKey[key], key, "Przywróć") { update { it.copy(renames = it.renames - key) } }
                }
            }

            run {
                Section("POGODA")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                    Text("Lokalizacja z GPS", color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = settings.weatherUseGps, onCheckedChange = { on -> actions.updateWeather { it.copy(weatherUseGps = on) } })
                }
                if (settings.weatherUseGps && !status.locationGranted) {
                    SmallAction("Zezwól na lokalizację", actions.onRequestLocation)
                }
                Text("Miasto zapasowe", color = Quiet, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                TextSetting(
                    value = settings.weatherCity,
                    placeholder = "np. Poznań",
                    onDone = { v -> actions.updateWeather { it.copy(weatherCity = v) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(status.weatherLine, color = Quiet, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    SmallAction("Odśwież", actions.onRefreshWeather)
                }
            }

            run {
                Section("UPRAWNIENIA")
                StatusRow("Lokalizacja (pogoda)", status.locationGranted, actions.onRequestLocation)
                StatusRow("Kalendarz", status.calendarGranted, actions.onRequestCalendar)
                StatusRow("Domyślny ekran główny", status.isDefaultHome, actions.onRequestDefault)
            }

            run {
                Section("GESTY")
                Hint("W górę: szuflada · w dół: powiadomienia · przytrzymanie: ustawienia")
                if (settings.swipeDownBroken) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Gest w dół nie działa na tym telefonie, więc jest wyłączony.",
                            color = Color(0xFFFF9B8A), fontSize = 13.sp, modifier = Modifier.weight(1f),
                        )
                        SmallAction("Spróbuj ponownie") { update { it.copy(swipeDownBroken = false) } }
                    }
                }
            }

            run {
                Section("IKONY")
                Hint("Monochromatyczne: ${status.monoIcons} z ${status.totalApps}. Reszta dostaje literę w kółku.")
            }

            run {
                Section("KOPIA USTAWIEŃ")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Eksportuj do pliku", actions.onExport)
                    Pill("Wczytaj z pliku", actions.onImport)
                }
                Hint("Zapisuje przypięte, ukryte, nazwy, karuzelę, akcent i pogodę. Tapeta nie jest w kopii.")
            }

            run {
                Text(
                    "Launcher ${status.version}",
                    color = Faint, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 40.dp),
                )
            }
        }
    }
}

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
            .clickable(onClick = onClick)
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
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
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone(text.trim()) }),
        trailingIcon = {
            if (text.trim() != value) SmallAction("Zapisz") { onDone(text.trim()) }
        },
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
