# Zenit (launcher Android) - plan v1

Stan na 17 listopada 2026. Źródło: dokument „Launcher Android - plan v1” i mockup v1.

## Cel i zakres v1

Własny launcher na Samsunga (Android 14-16) w stylu iwall.design: tapeta pół czerni i pół zdjęcia, karuzela widżetów u góry, 6 przypiętych aplikacji i szuflada z alfabetem. Na własny użytek, instalowany z APK, bez Play Store.

W v1:

- ekran główny z tapetą rysowaną przez launcher
- karuzela: 4 strony (zegar, pogoda, dzień z kalendarzem, tarcza), cały tekst poziomo
- szuflada: wyszukiwarka, alfabet, ostatnio używane, ukrywanie i zmiana nazw
- gesty: w górę, w dół, przytrzymanie
- ekran ustawień

Poza v1: zegar słowny, dwuklik blokujący ekran, tryb czuwania jako osobny ekran, paczki ikon, widżety innych aplikacji, foldery, publikacja w Play Store.

## Decyzje

| Obszar | Decyzja |
| --- | --- |
| Tapeta | Launcher rysuje ją sam: wybrane zdjęcie przycięte do prawej części, z lewej czarny gradient |
| Karuzela | 4 strony: zegar, pogoda, dzień z kalendarzem, tarcza. Domyślna: zegar |
| Orientacja | Cały tekst i liczby poziomo |
| Ikony | Monochromatyczne z systemu (warstwa monochrome, Android 13+), białe |
| Gesty | W górę: szuflada · w dół: powiadomienia · przytrzymanie: ustawienia |
| Urządzenie | Samsung, Android 14-16 (minSdk 33) |
| Język | Polski: daty i powitania |
| Czuwanie | Poza v1; tarcza jako strona karuzeli |
| Szuflada | Wyszukiwarka, ukrywanie, zmiana nazw, ostatnio używane; alfabet przy prawej krawędzi; klawiatura dopiero po stuknięciu w pole |
| Pogoda | Przybliżona lokalizacja z GPS, wpisane miasto jako zapas |
| Akcent | Do wyboru w ustawieniach, domyślnie pomarańczowy |
| Font | Outfit |
| Przypięte | Do 6 aplikacji, tekst plus ikona, wyrównane do lewej |
| Nazwa | Robocza: `com.adrian.launcher` |

## Ekrany i zachowanie

### Ekran główny

Od góry: przezroczysty pasek statusu, karuzela (około 45% wysokości) z kropkami stron, pasek „wt, 17 lis · 80%”, 6 przypiętych aplikacji. Tło: wybrane zdjęcie wyrównane do prawej z czarnym gradientem od lewej krawędzi, szerokość gradientu ustawiana suwakiem.

### Strony karuzeli

1. **Zegar (domyślny):** duża godzina z mniejszymi minutami obok, pod nią „WT · 17 LISTOPADA” i pogoda jednym zdaniem.
2. **Pogoda:** nazwa miesiąca, temperatura, opis, wilgotność, wiatr, bateria.
3. **Dzień:** duże „17.”, powitanie zależne od pory dnia („Dzień dobry, Adrian”), najbliższe wydarzenie z kalendarza w ciągu 24 h.
4. **Tarcza:** godzina, kapsuła z minutami w akcencie, pierścień kolejnych minut; liczby poziome.

Każdą stronę można włączyć albo wyłączyć i zmienić jej kolejność. Po odblokowaniu telefonu karuzela wraca do zegara.

### Szuflada

Przesunięcie w górę, przyciemniona warstwa nad tapetą. U góry pole wyszukiwania, pod nim 4 ostatnio używane, niżej alfabetyczna lista z ikoną i nazwą. Pasek alfabetu przy prawej krawędzi: przesuwasz kciukiem, litera się powiększa, lista skacze. Sortowanie po polsku (Ł, Ś, Ż na swoich miejscach). Klawiatura nie otwiera się sama.

- Jedno trafienie w wyszukiwarce otwiera aplikację automatycznie.
- Przytrzymanie aplikacji: przypnij lub odepnij, zmień nazwę, ukryj, informacje, odinstaluj.
- Ukryte aplikacje nie są na liście, ale wyszukiwarka je znajduje.

### Ustawienia (przytrzymanie na ekranie głównym)

- Tapeta: wybór zdjęcia, szerokość gradientu, podgląd
- Karuzela: włączanie stron i kolejność
- Akcent: kilka kolorów, domyślnie pomarańczowy
- Przypięte: kolejność przeciąganiem
- Ukryte aplikacje i zmienione nazwy: lista z cofnięciem
- Pogoda: GPS, zapasowo wpisane miasto
- Status uprawnień i przycisk „Ustaw jako domyślny ekran główny”

## Architektura

| Warstwa | Wybór | Uwagi |
| --- | --- | --- |
| Język i UI | Kotlin, Jetpack Compose | Jedna `MainActivity` z filtrem `HOME` + `DEFAULT`, `singleTask` |
| Lista aplikacji | `LauncherApps` | `getActivityList()`, callback na instalację i odinstalowanie |
| Ikony | `AdaptiveIconDrawable.monochrome` | Gdy brak warstwy: pierwsza litera w cienkim kółku |
| Ustawienia i stan | DataStore | Przypięte, ukryte, nazwy, licznik uruchomień, strony karuzeli, akcent |
| Pogoda | Open-Meteo, kotlinx.serialization | Bez klucza API, cache 30 min, odświeżanie przy powrocie na ekran |
| Lokalizacja | `ACCESS_COARSE_LOCATION` | Ostatnia znana pozycja; brak = wpisane miasto |
| Kalendarz | `CalendarContract.Instances` | Najbliższe wydarzenie w ciągu 24 h |
| Zegar | `ACTION_TIME_TICK` | Tylko gdy ekran główny jest widoczny |
| Tapeta | Własny `Image` + gradient | Kopia zdjęcia w plikach aplikacji |
| Panel powiadomień | `StatusBarManager` przez refleksję | `expandNotificationsPanel()`, uprawnienie `EXPAND_STATUS_BAR` |
| Build | GitHub Actions | Debug APK jako artefakt przy każdym pushu |

Pakiety: `data/`, `home/`, `carousel/` (plik na stronę), `drawer/`, `settings/`, `system/` (gesty, refleksja).

## Ryzyka na Samsungu

| Element | Ryzyko | Co robimy |
| --- | --- | --- |
| `EXPAND_STATUS_BAR` + refleksja | Aktualizacja One UI może zepsuć gest w dół | Gdy wywołanie się nie uda, gest się wyłącza, ustawienia pokazują informację |
| Lokalizacja | Brak zgody lub sygnału | Wpisane miasto |
| Kalendarz | Brak zgody | Strona pokazuje tylko datę i powitanie |
| Sieć | Brak | Ostatnia zapisana pogoda z godziną pobrania |
| Ikony mono | Część aplikacji nie ma warstwy monochrome | Litera w kółku; w etapie 2 sprawdzamy skalę na telefonie |
| Gesty One UI | Animacje powrotu do ekranu głównego bywają mniej płynne | Test już w etapie 0 |

## Stan realizacji (17 listopada 2026)

Etapy 0-6 są zrobione i zbudowane w CI. Etap 7 jest zrobiony częściowo, czeka na tydzień testów na telefonie.

Odstępstwa od planu:

- Automatyczne uruchomienie przy jednym trafieniu działa dopiero od 2 wpisanych znaków.
- Kolejność stron karuzeli i przypiętych aplikacji zmienia się strzałkami w ustawieniach, nie przeciąganiem.
- Menu aplikacji to panel na środku ekranu, nie menu przy wierszu.
- Pogoda używa `org.json` zamiast kotlinx.serialization (mniej zależności).
- APK release nie jest podpisywany osobnym kluczem. Stały klucz debug w repo rozwiązuje praktyczny problem (aktualizacje bez utraty ustawień); osobny klucz wymaga sekretu w GitHub.
- Kopia ustawień to eksport/import pliku JSON z ekranu ustawień (bez tapety).

## Zmiany po pierwszym teście (17 listopada 2026)

- Karuzela ma 2 strony: „Zegar i pogoda” (zegar, data i pogoda na jednej stronie) oraz „Kalendarz”.
- Tarcza usunięta.
- Strona „Kalendarz” pokazuje widżet wybrany w ustawieniach (domyślnie widżety aplikacji Kalendarz, `com.kachaj96.kalendarz`: Najbliższe, Dziś, Tydzień, Miesiąc) albo wbudowaną kartę z kalendarza telefonu.
- Widżet jest hostowany bez ramki. Wygląd (przezroczystość, kolor tekstu) ustawia aplikacja Kalendarz.

## Wyszukiwarka Brave (17 listopada 2026)

- Pasek „Szukaj w Brave” nad strzałką szuflady (wariant A z mockupu). Po stuknięciu ekran się przyciemnia, pasek wjeżdża nad klawiaturę.
- Strzałka albo Enter otwiera `search.brave.com` w aplikacji Brave (`com.brave.browser`), a bez niej w domyślnej przeglądarce.
- Ikona aparatu: stuknięcie otwiera Aparat, przytrzymanie Google Lens.

## Wersja 1.1 (17 listopada 2026)

- Ekran główny bez linii data/bateria; pogoda w 3 liniach. Stuknięcie w godzinę: budziki; w pogodę: prognoza 12 h.
- Pasek wyszukiwania: silnik do wyboru (domyślnie Google), wyniki w Brave. Podczas pisania aplikacje i kalkulator nad paskiem (Spotlight), tło rozmyte. Aparat (przytrzymanie: Google Lens) i galeria.
- Szuflada: wyszukiwarka na dole.
- Teraz odtwarzane i kropki powiadomień: `system/NotificationService.kt` (dostęp do powiadomień, dla APK spoza Sklepu wymaga „ograniczonych ustawień”).
- Skróty aplikacji w menu przytrzymania (tylko jako domyślny launcher).
- Skupienie (wył./harmonogram/zawsze) ukrywa wybrane aplikacje z ekranu i szuflady. Tryb wieczorny przygasza ekran i zmienia akcent w podanych godzinach.
- Ustawienia w kategoriach z podstronami.

## Wersja 1.2 (17 listopada 2026)

- Bez strzałek: szuflada tylko gestem.
- Gesty konfigurowalne (`data/Gestures.kt`): w górę, w dół, w prawo na pierwszej stronie, w lewo na ostatniej, przytrzymanie, dwuklik, stuknięcie w godzinę i w pogodę. Akcje: szuflada, powiadomienia, wyszukiwanie, ustawienia, zegar, ostatnie połączenia, kontakty, aparat, latarka, prognoza, dowolna aplikacja.
- Stuknięcie w godzinę: aplikacja Zegar. Prognoza 12 h przewijana, z wiatrem; gesty ekranu są wtedy wyłączone.
- Propozycje (wariant B): po stuknięciu w pusty pasek 4 aplikacje dopasowane do pory dnia i typu dnia, z historii uruchomień (ostatnie 400, nie trafia do kopii ustawień).
- Latarka jako akcja gestu (domyślnie dwuklik). Pierścień ładowania po podłączeniu ładowarki.

## Wersja 1.3 (17 listopada 2026)

- „Zapytaj Claude” w wynikach wyszukiwania: tekst idzie do aplikacji Claude (`com.anthropic.claude`) przez `ACTION_SEND` i zawsze do schowka. Claude potrafi wstawić udostępniony tekst do otwartej rozmowy zamiast nowej, schowek to zapas. Wiersz znika, gdy Claude nie jest zainstalowany.

## Etapy

| Etap | Zakres | Odbiór na telefonie |
| --- | --- | --- |
| 0. Szkielet | Repo, Gradle, Compose, filtr `HOME`, workflow Actions | APK się instaluje, da się ustawić jako domyślny, gesty One UI działają |
| 1. Szuflada | `LauncherApps`, polskie sortowanie, alfabet po prawej, wyszukiwarka, uruchamianie | Wszystkie aplikacje na liście, skok po literze, jedno trafienie uruchamia aplikację |
| 2. Ekran główny | Tapeta z gradientem, 6 przypiętych, pasek daty i baterii, ikony mono z zamiennikiem | Wygląd zgodny z mockupem, wiadomo, ile aplikacji dostaje literę |
| 3. Karuzela | 4 strony bez sieci, kropki, powrót do zegara po odblokowaniu | Zegar zmienia się co minutę, płynne przewijanie |
| 4. Dane | Pogoda (GPS z zapasowym miastem, cache), kalendarz, uprawnienia, stany błędu | Odmowa uprawnienia nie psuje strony |
| 5. Gesty | W górę, w dół z zabezpieczeniem, przytrzymanie | Wszystkie 3 działają, nieudany gest w dół nie powoduje błędu |
| 6. Personalizacja | Menu aplikacji, ukrywanie, nazwy, ostatnio używane, akcent, ustawienia | Zmiany przetrwają restart telefonu |
| 7. Szlif | Animacje, podpisany APK release, kopia zapasowa ustawień | Tydzień codziennego używania bez powrotu do One UI Home |

## Wersja 1.4 (17 listopada 2026)

- Nazwa i ikona: **Zenit**. Łuk drogi słońca nad cienkim horyzontem, kropka w kolorze akcentu w najwyższym punkcie. Wektor w `res/drawable/ic_launcher_foreground.xml` (mieści się w bezpiecznej strefie 66 dp), logo w `docs/zenit-logo.svg`. Nazwa pakietu bez zmian (`com.adrian.launcher`), żeby aktualizacja nie kasowała ustawień.
- Pierścień ładowania: na czas jego wyświetlenia ekran główny łagodnie się rozmywa (do 14 dp) i przyciemnia, potem wraca.
- Tapeta: po wyborze zdjęcia otwiera się edytor (szczypanie 1-5x, przesuwanie, suwak gradientu, podgląd zegara). „Ustaw” zapisuje wycięty kadr, „Anuluj” zostawia poprzednią tapetę. Oryginał zostaje w `wallpaper_src.jpg`, więc „Dopasuj” w ustawieniach można użyć później.
