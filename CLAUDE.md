# Launcher - instrukcje dla Claude Code

Minimalistyczny launcher na Androida (Samsung, Android 14-16), budowany etapami. Pełna specyfikacja: `docs/PLAN.md`. Przed każdym etapem przeczytaj odpowiedni wiersz tabeli etapów i jego kryterium odbioru.

## Stack

- Kotlin, Jetpack Compose, jedna `MainActivity` (filtr `HOME` + `DEFAULT`, `singleTask`)
- minSdk 33, compileSdk/targetSdk 35 (podbić do 36 przy aktualizacji AGP)
- Stan i ustawienia: DataStore. Bez Room, bez frameworka DI.
- Lista aplikacji: `LauncherApps`, nie `PackageManager.queryIntentActivities`
- Pogoda: Open-Meteo + kotlinx.serialization, cache 30 min
- Font: Outfit (dołączony jako zasób w `res/font`, nie pobierany z sieci)

## Build

- Lokalnie nie ma Android SDK. APK buduje GitHub Actions (`.github/workflows/build.yml`) przy każdym pushu do `main`; artefakt `launcher-debug-<nr>`.
- Gradle jest instalowany w workflow (`gradle/actions/setup-gradle`, wersja 8.11.1). Wrapper można dodać później.
- Klucz debug jest w `keystore/debug.keystore`, żeby kolejne APK instalowały się na poprzednie bez utraty ustawień. Nie zmieniaj go.
- Po pushu sprawdź wynik workflow. Czerwony build = napraw, zanim zaczniesz coś nowego.

## Zasady

- Teksty w UI po polsku, kod i komentarze po angielsku.
- Wszystko poziomo: żadnego obróconego tekstu ani liczb.
- Kolory: tło #000000, tekst #F2F2F2, tekst drugorzędny #B4B4B4, akcent domyślnie #FF8A3D (wybierany w ustawieniach). Akcent na jednym elemencie naraz.
- Odstępy wielokrotnością 4 dp, cele dotyku min. 44 dp, lewy margines 28 dp.
- Każda funkcja zależna od uprawnienia lub ukrytego API musi działać bez niego (zapas opisany w planie).
- Nie dodawaj zależności bez powodu. Jedna zmiana etapu = jeden PR albo seria commitów z opisem.

## Wycięte z v1 (nie dodawać)

Zegar słowny, dwuklik blokujący ekran (i usługa dostępności), osobny tryb czuwania, paczki ikon, widżety innych aplikacji, foldery.
