# Zenit (launcher) - instrukcje dla Claude Code

Minimalistyczny launcher na Androida (Samsung, Android 14-16), budowany etapami. Pełna specyfikacja: `docs/PLAN.md`. Przed każdym etapem przeczytaj odpowiedni wiersz tabeli etapów i jego kryterium odbioru.

## Stack

- Kotlin, Jetpack Compose, jedna `MainActivity` (filtr `HOME` + `DEFAULT`, `singleTask`)
- minSdk 33, compileSdk/targetSdk 35 (podbić do 36 przy aktualizacji AGP)
- Stan i ustawienia: jeden dokument JSON w DataStore (`data/SettingsStore.kt`, `LauncherSettings`). Nowe pole = pole w data class + `encode`/`decodeJson` z wartością domyślną. Bez Room, bez frameworka DI.
- Lista aplikacji: `LauncherApps`, nie `PackageManager.queryIntentActivities`. Manifest musi mieć `<queries>` dla `MAIN/LAUNCHER`, inaczej Android 11+ ukrywa zainstalowane aplikacje.
- Pogoda: Open-Meteo przez `HttpURLConnection` + `org.json` (wbudowane, bez kotlinx.serialization), cache 30 min w ustawieniach
- Font: Outfit (OFL, licencja w `licenses/`), dołączony w `res/font`

## Struktura

- `MainActivity.kt`: stan ekranu, nakładki (szuflada, ustawienia, menu aplikacji), uprawnienia, pickery plików
- `data/`: aplikacje i ikony, ustawienia, tapeta, pogoda, kalendarz, host widżetu kalendarza
- `home/`: ekran główny i strony karuzeli
- `drawer/`: szuflada z alfabetem
- `settings/`: ekran ustawień
- `system/`: ukryte API (panel powiadomień przez refleksję), usługa powiadomień (multimedia, kropki)
- `ui/`: kolory, font, wspólne komponenty, menu aplikacji

Nakładki pełnoekranowe muszą mieć `Modifier.blockTouchesBelow()`, inaczej dotyk w pustym miejscu trafia do ekranu głównego pod spodem.

## Build

- Lokalnie nie ma Android SDK. APK buduje GitHub Actions (`.github/workflows/build.yml`) przy każdym pushu do `main`; artefakt `launcher-debug-<nr>`.
- Gradle jest instalowany w workflow (`gradle/actions/setup-gradle`, wersja 8.11.1). Wrapper można dodać później.
- Klucz debug jest w `keystore/debug.keystore`, żeby kolejne APK instalowały się na poprzednie bez utraty ustawień. Nie zmieniaj go.
- Po pushu sprawdź wynik workflow. Czerwony build = napraw, zanim zaczniesz coś nowego. Błędy kompilacji są w adnotacjach przebiegu (krok „Report compile errors”).

## Zasady

- Teksty w UI po polsku, kod i komentarze po angielsku.
- Wszystko poziomo: żadnego obróconego tekstu ani liczb.
- Kolory: tło #000000, tekst #F2F2F2, tekst drugorzędny #B4B4B4, akcent domyślnie #FF8A3D (wybierany w ustawieniach). Akcent na jednym elemencie naraz.
- Odstępy wielokrotnością 4 dp, cele dotyku min. 44 dp, lewy margines 28 dp.
- Każda funkcja zależna od uprawnienia lub ukrytego API musi działać bez niego (zapas opisany w planie).
- Nie dodawaj zależności bez powodu. Jedna zmiana etapu = jeden PR albo seria commitów z opisem.

## Wycięte z v1 (nie dodawać)

Zegar słowny, tarcza z pierścieniem minut, dwuklik blokujący ekran (i usługa dostępności), osobny tryb czuwania, paczki ikon, foldery. Widżety innych aplikacji tylko jako jeden widżet na stronie kalendarza (`data/Widgets.kt`), nigdzie indziej.
