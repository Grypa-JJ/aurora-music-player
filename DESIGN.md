# Dokument projektowy — "Aurora" (nazwa robocza)
## Premium natywny odtwarzacz muzyki offline (Android, Kotlin + Jetpack Compose)

**Kierunek wizualny:** Apple-minimal, spójność clean/soft — ciemny motyw jako pierwszorzędny, jeden dynamiczny akcent koloru z okładki albumu, dużo światła (whitespace), miękkie szkło i cienie zamiast twardych konturów, zero Material You/dynamic color z tapety. Dokument konsoliduje najlepsze, spójne ze sobą rozwiązania z trzech wariantów koncepcyjnych ("Nocturne" a/b/c) oraz z researchu technicznego (UI framework, DSP/EQ, rekomendator).

Wygenerowane przez multi-agentowy workflow (research → 3 niezależne koncepcje → panel sędziów → synteza), 2026-09-13.

---

## 1. Stack technologiczny (potwierdzony)

### 1.1 Fundament UI
- **Jetpack Compose + Material 3 Expressive** (`androidx.compose.material3`, linia 1.5.x) jako fundament — NIE pełny custom design system od zera. Uzasadnienie: M3 Expressive daje gotową dostępność (kontrast, touch targets, TalkBack), stany komponentów, `MaterialTheme.motionScheme` (spring-based presety animacji) i mechanizm theming (ColorScheme/Typography/Shapes) zaprojektowany pod głębokie nadpisywanie. Pisanie własnych focus ringów, ripple, sliderów, switchy od zera to 3-6x więcej pracy przy marginalnym zysku — 90% wrażenia "premium" pochodzi z warstwy tokenów i efektów, nie z fundamentu.
- **`dynamicColor` jawnie WYŁĄCZONY.** Własny, statyczny `darkColorScheme()`/`lightColorScheme()` — apka ma własną tożsamość, niezależną od tapety systemowej.
- Osobny obiekt **`AuroraTokens`** (CompositionLocal obok `MaterialTheme`): spacing (wielokrotności 4dp), gradienty, motion specs, style "glass"/cienia. Wzorzec zgodny z oficjalnym przewodnikiem Google "custom design system on top of M3".

### 1.2 Biblioteki

| Cel | Biblioteka | Uwagi |
|---|---|---|
| Odtwarzanie audio | `androidx.media3:media3-exoplayer` + `media3-session` | MediaSession, powiadomienia, Android Auto za darmo |
| Własny EQ/DSP | `Media3 AudioProcessor` (custom, patrz sekcja 4) | biquad IIR w Kotlinie |
| Okładki | `io.coil-kt:coil-compose` (Coil 3.x) | Crossfade 300ms + blur-placeholder |
| Ekstrakcja koloru z okładki | `androidx.palette:palette-ktx` | `Palette.Builder().generate()` na `Dispatchers.Default`, cache per `trackId` |
| Blur tła (glassmorphism) | `dev.chrisbanes.haze:haze` (Haze 2.0) | `HazeMaterials.thin/regular/thick`, fallback scrim <API 31 |
| Miękkie kolorowe cienie | `graphicsLayer { renderEffect = BlurEffect(...) }` (API 31+) lub `ComposeShadowsPlus` | natywny `Modifier.shadow()` zbyt twardy |
| Przejścia współdzielone | `androidx.compose.animation.SharedTransitionLayout` (Compose 1.7+) | mini-player → Now Playing |
| Baza lokalna | `androidx.room` | historia odtwarzania, EQ presets, Genius |
| Praca w tle | `androidx.work:work-runtime-ktx` | agregacje affinity/cooccurrence, klastrowanie |
| DI | `Hilt` | standard dla Media3 + WorkManager |
| Odczyt tagów ID3/metadanych | `MediaMetadataRetriever` (wbudowany) + `MediaStore` skan | bez zależności od chmury |

### 1.3 Minimalne wymagania
- `minSdk 26` (Android 8.0) — szeroki zasięg dla playera offline, świadomy graceful degradation dla efektów wymagających API 31+.
- `targetSdk` najnowszy stabilny (35).
- Blur/RenderEffect (`Haze`, `BlurEffect`) wymaga API 31+ → poniżej: solid scrim z dobraną alfą (zaprojektowany od początku, nie "łatany").

### 1.4 Architektura aplikacji
- **MVVM + Clean Architecture w 3 warstwach**: `data` (Room DAO, MediaStore repo, DataStore preferencje) → `domain` (use case'y, czyste modele Kotlin) → `presentation` (Compose UI + ViewModel, `StateFlow`/`collectAsStateWithLifecycle`).
- Single source of truth: playback state z `MediaController` (Media3) eksponowany przez `PlayerRepository` jako `StateFlow<PlaybackUiState>`.
- Cały EQ/Genius stan w `StateFlow`, zero współdzielonego mutable state poza ViewModelami.

---

## 2. System wizualny

### 2.1 Kolor

**Zasada nadrzędna:** jeden mocny akcent na ekran, zero palety tęczowej, hierarchia przez rozmiar/wagę/alfę — nie przez kolor. Dynamic Color (Material You) trwale wyłączony.

**Tło (dark, domyślny motyw):**
- Gradient bazowy `#0A0A0F` → `#121218` (3 przystanki tego samego odcienia — lekki chłodno-fioletowy podton, nie czysta czerń `#000000`, żeby zachować głębię na OLED bez efektu "taniej" płaskiej czerni).
- Powierzchnie podniesione (karty, sheet, dialogi): `#16161D`, obwódka `0.5dp Color.White @ 8%` zamiast twardego Material elevation-shadow — to daje wrażenie cienkiej krawędzi szkła.

**Tekst:**
- Primary: `#F2F2F0` (100%)
- Secondary (wykonawca, metadane): `#F2F2F0 @ 60%`
- Tertiary/disabled: `#F2F2F0 @ 35–38%`

Hierarchia wizualna budowana głównie alfą tekstu, nie odcieniem — to jest kluczowy powód, dla którego UI "czyta się" jako spójne i premium zamiast rozdrobnione kolorystycznie.

**Akcent — dwuwarstwowy model:**
1. **Statyczny fallback** (ekran startowy, pusty stan, brak okładki): indygo-fiolet `#6C5CE7`.
2. **Dynamiczny (główny mechanizm, aktywny na Now Playing + mini-playerze):** wyprowadzony z okładki albumu przez `Palette.Builder(bitmap).generate()`:
   - `Vibrant` swatch → kolor progress-baru, glow przycisku play, akcent equalizera na liście.
   - `DarkMuted` swatch (fallback `DarkVibrant`, fallback statyczny) → górny przystanek gradientu tła Now Playing; dolny przystanek zawsze `#06060A`.
   - Ekstrakcja **zawsze async na `Dispatchers.Default`**, wynik cache'owany w `LruCache<Long /*trackId*/, ExtractedPalette>` w pamięci procesu — bez tego scroll listy i przełączanie utworów się zacinają.
   - Zmiana tła między utworami: `animateColorAsState(spring(dampingRatio = MediumBouncy, stiffness = Low))`, crossfade ~400ms.

**Tryb jasny (wtórny):**
- Tło `#FAFAF8`, karty białe z obwódką `Black @ 6%` (ten sam mechanizm co dark, odwrócone wartości).
- Ten sam dynamiczny akcent, wariant `LightVibrant` zamiast `DarkMuted`.
- Włączany ręcznie w ustawieniach; system domyślnie startuje w dark (kontekst słuchania muzyki, OLED, estetyka "audiophile").

**Stan błędu/ostrzeżenia:** stonowana czerwień `#E5484D`, użyta wyłącznie punktowo (np. błąd odczytu pliku).

### 2.2 Typografia

- **Jedna rodzina fontu w całej aplikacji: Inter** (Google Fonts, pełny zakres wag, zaimportowany jako pliki `.ttf` do `res/font/` — nie CDN/runtime download, offline-first). Brak fontu systemowego domyślnego — to samo w sobie odróżnia od aplikacji "budżetowej".
- Zaimplementowane jako własny `Typography()` przekazany do `MaterialTheme`:
  ```kotlin
  val InterFamily = FontFamily(
      Font(R.font.inter_regular, FontWeight.W400),
      Font(R.font.inter_medium, FontWeight.W500),   // etykiety, przyciski
      Font(R.font.inter_semibold, FontWeight.W600)  // tytuły, liczby
  )
  ```
  Nigdy pełny `Bold` (700+) — unikanie efektu "krzykliwego". Maks. 3 wagi w całej apce.

- **Skala (kontrastowa, duże skoki — nie 5 zbliżonych rozmiarów):**

| Token | Rozmiar / waga | Letter-spacing | Użycie |
|---|---|---|---|
| Display | 34sp / SemiBold 600 | -0.5sp | tytuł utworu na Now Playing |
| Headline | 26sp / SemiBold 600 | -0.3sp | tytuły ekranów ("Biblioteka", "Genius") |
| Title | 18–20sp / Medium 500 | -0.2sp | nazwa albumu/playlisty, nagłówki sekcji |
| Body | 16sp / Medium 500 lub Regular 400 | 0 | tytuł utworu na liście |
| Label | 13sp / Regular 400 | 0.1sp | wykonawca, metadane — kolor secondary (60% alfa) |
| Caption | 11sp / Regular 400 | 0 | częstotliwości EQ, znaczniki czasu, badge |

- **Cyfry czasu odtwarzania** (`00:00`) zawsze z `fontFeatureSettings = "tnum"` (tabular figures) — cyfry nie "skaczą" przy animacji licznika.
- Line-height hojny: 1.3–1.4× na Display/Headline (efekt dużej białej przestrzeni).
- Odstępy: padding pionowy elementu listy 14–16dp (nie domyślne 8dp M3), odstępy między sekcjami min. 32dp. Zdefiniowane jako `AuroraTokens.Spacing` (xs=4, s=8, m=16, l=24, xl=32, xxl=48).

### 2.3 Kształt

Zasada: **kontrast promieni, nie jednolitość.** Ten sam promień wszędzie wygląda infantylnie; kontrast dużego i małego wygląda drożej.

```kotlin
val AuroraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // chipy gatunków, mini-badge EQ
    small      = RoundedCornerShape(12.dp),  // pola tekstowe, mini przyciski
    medium     = RoundedCornerShape(20.dp),  // karty Genius, item listy (hover state)
    large      = RoundedCornerShape(24.dp),  // okładka albumu, bottom sheet, karty
    extraLarge = RoundedCornerShape(999.dp)  // pigułki (chipy tabów, CTA), przycisk play
)
```
- Przycisk play/pause: **jedyny pełny okrąg** w całym UI (`CircleShape`, 72–80dp) — element wyróżniony celowo.
- Do unikalnych/morfujących kształtów (ikona play↔pause) — `androidx.graphics.shapes` (Shape Morphing, M3 Expressive `MaterialShapes`), nie ręczny `Path`.

### 2.4 Efekty "premium" — konkretne API

1. **Miękkie kolorowe cienie** (okładka albumu, przycisk play): `graphicsLayer { renderEffect = BlurEffect(radiusX, radiusY, TileMode.Decal) }` rysowany na osobnej warstwie pod komponentem, `ambientColor`/`spotColor` = `Vibrant` swatch z okładki (kolorowy cień, nie czarny) — efekt "unoszenia się". Fallback <API 31: zwykły `Modifier.shadow()` z niską elewacją.
2. **Glassmorphism/blur tła**: `Haze` — `HazeMaterials.thin` na top barze i mini-playerze, `HazeMaterials.regular` na bottom sheet EQ. **Ograniczone wyłącznie do elementów stałych, niescrollowanych** (mini-player, top bar, EQ sheet) — nigdy na kartach przewijanej listy (koszt GPU na słabszym sprzęcie, testowane na API 26-28 / Snapdragon niskiej klasy).
3. **Gradienty**: `Brush.verticalGradient` 2–3 przystanki tego samego odcienia; duotone overlay na dole okładki tam, gdzie tekst nakłada się na obraz.
4. **Rubber-band overscroll** na listach (`rememberOverscrollEffect`).
5. **Wszystkie animacje na sprężynach**, nie `tween()`: `animateFloatAsState/animateDpAsState/animateColorAsState` ze `spring(dampingRatio = MediumBouncy/LowBouncy, stiffness = Low)`; presety spójne przez `MaterialTheme.motionScheme.defaultSpatialSpec()/fastEffectsSpec()` (M3 Expressive) zamiast ręcznego doboru parametrów w każdym miejscu.
6. **Transformacje przez `graphicsLayer{ alpha/scale/translationY }`**, nie przez `Modifier.size/offset` bezpośrednio — unika recompozycji/relayoutu na długich listach utworów, krytyczne dla 60fps.
7. **Shared element transition** mini-player → Now Playing: `SharedTransitionLayout` na okładce albumu, `boundsTransform` = `motionScheme.defaultSpatialSpec()`.
8. **Coil**: `Crossfade(300ms)` + blur-placeholder (mikroskalowana rozmyta miniatura cache'owana obok oryginału) — zero migotania pustych kwadratów przy szybkim scrollu.
9. **Haptyka lekka** (`HapticFeedbackType.TextHandleMove`/custom `TICK`): przy przeciąganiu suwaków EQ co przekroczenie progu ±3dB, przy tapnięciu serca (ulubione), przy commit swipe next/prev — nie na każdym pikselu.

---

## 3. Ekrany

### 3.1 Biblioteka / Lista utworów

**Top bar:** transparentny, treść przewija się pod spodem; przy scrollu (offset > 0) pojawia się scrim/blur (`Haze thin`). Tytuł "Biblioteka" Headline 26sp SemiBold po lewej, ikony wyszukiwania i sortowania po prawej (bez tła, tylko ikona 24dp).

**Taby poziome** pod top barem: Utwory / Albumy / Wykonawcy / Playlisty — pigułki (`extraLarge` shape), aktywna wypełniona akcentem @15% + tekst w kolorze akcentu, nieaktywna: tylko tekst secondary, brak tła/obwódki.

**Lista utworów** (`LazyColumn`):
- Wysokość wiersza 64–72dp, padding pionowy 12–16dp (świadomie większy niż domyślne 8dp M3).
- Okładka 48dp, radius 8dp, `Coil AsyncImage` z Crossfade + blur-placeholder.
- Kolumna: tytuł (Body 16sp Medium) + wykonawca (Label 13sp, secondary) pod spodem.
- Po prawej: czas trwania (Caption, tabular) lub — dla aktualnie granego utworu — **mini equalizer** (3 pionowe słupki, `infiniteRepeatable`, różne opóźnienie fazy per słupek) w kolorze akcentu, zastępujący czas; tytuł tego wiersza podświetlony akcentem, tło wiersza akcent@6–8%.
- Menu "..." (opcjonalne, po prawej lub via long-press): dodaj do playlisty, ulubione, idź do albumu, informacje o pliku.
- Rubber-band overscroll na górze/dole.

**Mini-player** (przyklejony nad dolną nawigacją, NIE na całą szerokość — 16dp marginesu z obu stron, radius 24dp — element "pływający"):
- Wysokość ~64dp, tło `Haze` blur + tint z dynamicznego koloru aktualnego utworu, cienka górna obwódka highlight (`White@10%`).
- Okładka 44dp po lewej, tytuł+wykonawca (Marquee jeśli za długie) w środku, przycisk play/pause 36dp po prawej.
- Cienki progress-track 2dp u samej góry karty (bez widocznego thumbu).
- Tap → `SharedTransitionLayout`: okładka i tło rosną płynnie do Now Playing (spring ~500ms).
- Swipe poziomy na mini-playerze = next/previous, haptyka na commit.

### 3.2 Now Playing (odtwarzacz pełnoekranowy)

- **Tło:** pełnoekranowy `Brush.verticalGradient` animowany między utworami (DarkMuted okładki → `#06060A`), crossfade 400–500ms.
- **Górny pasek:** uchwyt "w dół" (chevron, 40dp tap target, zwija do mini-playera) po lewej, nazwa albumu (Label, wyśrodkowana) w środku, menu "..." (dodaj do playlisty, timer snu, informacje o pliku, udostępnij) po prawej. Zero elevation/tła.
- **Centralnie:** okładka albumu — kwadrat ~85% szerokości ekranu, radius 20–24dp, `graphicsLayer` z kolorowym cieniem (`Vibrant` swatch). Wchodzi shared-elementem z mini-playera/listy. Opcjonalny subtelny parallax/tilt (`rotationX/Y` ±2–3°) reagujący na drag — bez gadżeciarskiego przegięcia.
- **Pod okładką:** tytuł utworu (Display 34sp SemiBold, letterSpacing -0.5sp, jedna linia z ellipsis/Marquee), wykonawca (Body 16sp, secondary 60%) pod spodem. Przycisk serca (ulubione) po prawej tej sekcji — ghost icon, animacja scale+haptyka.
- **Progress bar:** custom `Slider` bez widocznego thumbu w spoczynku — track 2–3dp @20% alfa, wypełnienie akcentem; thumb (kropka 12dp) pojawia się `animateFloatAsState(0→1 scale)` tylko podczas przeciągania. Pod paskiem: czas bieżący / pozostały (Caption, tabular).
- **Kontrolki transportu:** rząd drugorzędny — shuffle/repeat (20dp, secondary); rząd główny — prev (28dp) — **PLAY/PAUSE** (jedyne pełne koło w UI, 72–80dp, tło akcent/gradient, ikona morfująca play↔pause przez `androidx.graphics.shapes`, scale-down 0.94–0.96 na press + spring bounce na release + haptyka) — next (28dp).
- **Dolny pasek ikon:** equalizer, kolejka odtwarzania, urządzenie wyjścia (Bluetooth/speaker), udostępnij — ghost icons 22dp, równo rozłożone.
- Swipe poziomy na okładce = next/prev z haptyką na commit.

### 3.3 Equalizer — patrz sekcja 4 (opis pełny, wygląd + działanie razem).

### 3.4 "Genius" / Rekomendacje (offline)

- Osobna zakładka w dolnej nawigacji (ikona iskry/gwiazdy — jedyne miejsce w apce z nieoczywistym kształtem `MaterialShapes` typu "cookie"/star, żeby podkreślić funkcję "inteligentną").
- **Karta "Genius Mix" na górze**, pełna szerokość, 140dp wysokości, gradient tła wyprowadzony z 2–3 uśrednionych kolorów okładek utworów w miksie, wolno rotujący (`tween` 8–10s) — jedyne miejsce z "żywym" tłem poza Now Playing.
- **Poziome "półki" tematyczne** (`LazyRow`), karty 160×200dp, radius 20dp, okładka + duotone overlay dołem + tytuł playlisty (SemiBold 15sp, biały) na dole karty:
  - "Miksy dla Ciebie" (klastry k-means, patrz 5.5)
  - "Bo słuchałeś [Artysta]" (co-occurrence + podobieństwo gatunku)
  - "Odkryj ponownie" (utwory nieodtwarzane >60–90 dni, wcześniej wysoko oceniane/ulubione)
  - "Twój wieczorny mix" (kontekstowy boost pory dnia)
- Tap na kartę miksu → `SharedTransitionLayout` z kolażu 2×2 okładek prosto do Now Playing/kolejki.
- Pod półkami: standardowa lista rekomendowanych pojedynczych utworów (ten sam komponent co Biblioteka — spójność wizualna, zero nowego stylu listy).
- Pull-to-refresh na ekranie Genius = przeliczenie/przetasowanie miksów lokalnie (bez sieci, na podstawie `TrackAffinity`/`TrackCooccurrence` już policzonych w tle).

---

## 4. Equalizer — implementacja i UI

### 4.1 Decyzja architektoniczna (uzasadnienie)

Stockowy `android.media.audiofx.Equalizer` jest odrzucony jako silnik: liczba pasm i ich częstotliwości środkowe są **zależne od vendora/HAL** (referencyjnie ~5 pasm, ±15dB), nie da się ustawić dowolnych częstotliwości — wyklucza to graficzny EQ 10/20/31-pasmowy. Dodatkowo efekty audio-session bywają pomijane przy HW audio offload (długie pliki skompresowane) i przy Bluetooth codec passthrough (np. aptX) — niewiarygodne dla flagowej funkcji.

**Rozwiązanie:** własny **`Media3 AudioProcessor`** wstawiony w pipeline ExoPlayera, implementujący kaskadę filtrów **biquad IIR (formuły RBJ cookbook)** w czystym Kotlinie:
- Dowolna liczba pasm i dowolne częstotliwości środkowe (10-band domyślnie: 31/62/125/250/500/1k/2k/4k/8k/16k Hz).
- Identyczne działanie na każdym urządzeniu — pomija problem HAL/offloadu, bo przetwarzanie dzieje się w procesie aplikacji, przed `AudioTrack`.
- Koszt CPU kaskady kilkudziesięciu biquadów na buforze stereo 48kHz jest znikomy na współczesnym ARM, wykonywany na wątku audio (nie UI-thread).
- Własny **bass boost** (low-shelf filter) i **stereo widener** (mid-side processing) w tym samym `AudioProcessorze` — pełna parametryzacja zamiast suwaka "siła 0–1000" ze stockowego `BassBoost`/`Virtualizer`.
- **Reverb**: hybryda — stockowy `PresetReverb`/`EnvironmentalReverb` dołączony przez `audioSessionId` z ExoPlayera, jako tania opcja przestrzenna (własny convolution reverb byłby zbyt kosztowny do napisania i utrzymania). W UI z adnotacją info-icon: "EQ i bas działają identycznie na każdym telefonie — reverb korzysta z efektów systemowych".
- Rozważone i odrzucone jako główny silnik: `DynamicsProcessing` (API 28+, alternatywa "w pełni stockowa" z dowolnymi pasmami) — zostawione jako opcjonalny plan B, gdyby z jakiegoś powodu trzeba było zostać wyłącznie w systemowym frameworku efektów. Natywny C++/Oboe — nieuzasadniony narzut inżynieryjny dla samego EQ; czysty Kotlin/Media3 wystarcza wydajnościowo.

### 4.2 Model danych EQ (Room)

```kotlin
@Entity
data class EqPresetEntity(
    @PrimaryKey val id: String,           // "flat", "bass_boost", "custom_<uuid>"
    val name: String,
    val bandGainsDb: List<Float>,         // 10 wartości, -12..+12 dB
    val bassBoostPercent: Float,          // 0..100
    val widthPercent: Float,              // 0..100
    val reverbPreset: ReverbPreset,       // enum: NONE/ROOM/HALL/CATHEDRAL
    val isBuiltIn: Boolean
)

@Entity
data class EqStateEntity(
    @PrimaryKey val id: Int = 0,          // singleton row
    val enabled: Boolean,
    val activePresetId: String,
    val preampDb: Float
)
```
- Stan EQ jako jeden `StateFlow<EqUiState>` w `EqRepository`, subskrybowany zarówno przez ViewModel (renderowanie UI), jak i przez `EqualizerAudioProcessor` (przeliczanie współczynników filtrów).
- Zmiany podczas drag: throttling zapisu do ~30-60Hz (wewnętrzny), audio aktualizowane bez odczuwalnego opóźnienia — dźwięk zmienia się live, bez przycisku "zastosuj".
- `Custom` preset = ostatnio ręcznie ustawiony stan, zapisywany automatycznie przy wyjściu z ekranu, per urządzenie (nie per utwór — unika zaskoczenia użytkownika przy zmianie piosenki).

### 4.3 UI Equalizera

- **Forma:** `ModalBottomSheet` (80% wysokości ekranu), `Haze regular` blur tła Now Playing pod spodem, dziedziczy gradient tła z aktualnej okładki (spójność kontekstu — EQ "należy" do grającego utworu). Grabber + tytuł "Equalizer" (Title 22sp) + `Switch` master ON/OFF w nagłówku.
- **Przełącznik presetów:** pozioma lista pigułek (Flat / Bass Boost / Vocal / Rock / Electronic / Custom...) — aktywny wypełniony akcentem. Zmiana presetu animuje wszystkie suwaki jednocześnie do nowych wartości `animateFloatAsState(spring(dampingRatio=MediumBouncy))` z lekkim staggerem 20–30ms per pasmo — efekt "fali", najbardziej "premium" moment tego ekranu.
- **Główna siatka 10 suwaków pionowych:**
  - Cienki tor (3dp, radius pełny), wypełnienie od linii 0dB w górę/dół gainem, kolor akcentu z gradientem intensywności.
  - Kropka-uchwyt (14dp) pojawia się scale-in tylko podczas przeciągania danego pasma — w spoczynku widoczne samo wypełnienie toru.
  - Etykiety częstotliwości (Caption 10–11sp, @35% alfa) pod każdym suwakiem; linia 0dB jako pozioma kreska @15% alfa w tle siatki.
  - Haptyka `TICK` co przekroczenie ±3dB.
  - Opcjonalny tryb "malowania" krzywej: `Canvas` z gładką krzywą Bezier nad suwakami, long-press+drag interpoluje najbliższe pasma (bardziej intuicyjne dla casualowego użytkownika niż same suwaki).
- **Sekcje dodatkowe** (accordion, `AnimatedVisibility` + `expandVertically`): Bass Boost (jeden poziomy suwak, styl progress-bara z Now Playing), Stereo Widener, Preamp/Gain (±).
- **Reset do Flat**: link tekstowy (nie przycisk z tłem) w prawym górnym rogu — drugorzędny.
- **Stan wyłączony:** cała siatka przygasa (`alpha 0.4`) i staje się disabled bez ukrywania — użytkownik nadal widzi ostatnie ustawienia.
- Mikrointerakcja: krótki radial glow/flash w kolorze akcentu przy zmianie presetu, tłumiony spring — sygnalizuje przeliczenie bez tekstowego komunikatu.

---

## 5. Algorytm "Genius" (rekomendacje lokalne)

W pełni **on-device, bez sieci** — deterministyczny system heurystyczny (ważone punktowanie + proste statystyki), nie sieć neuronowa. Łatwy do debugowania i strojenia na podstawie obserwowanego skip-rate.

### 5.1 Model danych (Room)

```kotlin
@Entity
data class TrackEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val year: Int?,
    val durationMs: Long,
    val dateAdded: Long,
    val filenameNormalized: String
)

@Entity
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val timestampStart: Long,
    val timestampEnd: Long,
    val playedMs: Long,
    val completed: Boolean,       // odtworzone >~80% długości
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val sourcePlaylistId: String? = null
)

@Entity
data class SkipEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val timestamp: Long,
    val playedMs: Long,
    val dayOfWeek: Int,
    val hourOfDay: Int
)

// tabele materializowane, przeliczane w tle — NIGDY liczone na żywo
@Entity
data class TrackAffinity(
    @PrimaryKey val trackId: Long,
    val playCount: Int,
    val skipCount: Int,
    val avgCompletionRatio: Float,
    val lastPlayedAt: Long,
    val affinityScore: Float       // 0..1
)

@Entity(primaryKeys = ["trackIdA", "trackIdB"])
data class TrackCooccurrence(
    val trackIdA: Long,
    val trackIdB: Long,
    val count: Int,
    val lastSeenAt: Long
)

@Entity
data class GeneratedMix(
    @PrimaryKey val id: String,
    val seedTrackId: Long?,        // null dla klastra bez seeda
    val trackIdsOrdered: String,   // JSON List<Long>
    val createdAt: Long
)
```
Indeksy: `TrackEntity(genre, artist)`, `PlayEvent(trackId, timestampStart)`, `TrackCooccurrence(trackIdA)`.

### 5.2 Funkcja scoringu (para seed → kandydat)

```
score(seed, candidate) =
    0.25 * genreMatch(seed, candidate)        // 1.0 identyczny gatunek, 0.5 pokrewny (mapa gatunków), 0 inny
  + 0.20 * artistMatch(seed, candidate)       // 1.0 ten sam artysta, 0.6 wspólny "album artist"/featuring
  + 0.10 * yearProximity(seed, candidate)     // 1 / (1 + |rokA-rokB| / 5)
  + 0.10 * textSimilarity(seed, candidate)    // Jaccard na tokenach tytułu/nazwy pliku po normalizacji
  + 0.15 * cooccurrenceScore(seed, candidate) // log(1+count) / log(1+maxCount) z TrackCooccurrence
  + 0.10 * affinity(candidate)                // z TrackAffinity — karze wysoki skip-rate
  + 0.05 * contextBoost(candidate, now)       // affinity liczone tylko z PlayEvent w tym samym oknie pora-dnia
  + 0.05 * recencyBoost(candidate)            // boost dla utworów dodanych w ostatnich N dniach, wygasający wykładniczo
  - penalty * sameArtistStreak(candidate, playlistSoFar)  // dywersyfikacja
```
Wagi wystawione jako stałe w jednym miejscu — `object GeniusWeights` — do strojenia po obserwacji rzeczywistego skip-rate wygenerowanych miksów.

```
affinityScore(candidate) = clamp01(
    0.6 * (playCount / (playCount + 5))                          // wygładzone (Bayesian-like)
  + 0.4 * (1 - skipCount / (playCount + skipCount + 1))
)
```

### 5.3 Budowa "Instant Mix" z przycisku Genius (per utwór)

1. **Filtr wstępny (obowiązkowy dla bibliotek >10k utworów):** kandydaci = utwory o tym samym gatunku LUB tym samym artyście LUB niepustym `cooccurrence` z seedem — po indeksie, nie pełne O(n) skanowanie.
2. Policz `score(seed, x)` dla przefiltrowanych kandydatów, sortuj malejąco.
3. Buduj playlistę zachłannie z twardymi limitami dywersyfikacji (już w wersji pierwszej, bo to najbardziej odczuwalna jakościowo różnica):
   - max 2 utwory tego samego artysty pod rząd,
   - max ~30% playlisty z jednego albumu,
   - pomiń utwór odtworzony w ciągu ostatnich N minut.
4. Długość docelowa: 25–40 utworów (konfigurowalne w ustawieniach).
5. Zapis jako `GeneratedMix` — użytkownik może "Zapisz jako playlistę" (trwały zapis) lub zostawić jako tymczasową kolejkę.

### 5.4 Pętla uczenia

Każde odtworzenie/skip w playerze zapisuje zdarzenie (`PlayEvent`/`SkipEvent`) przez `PlaybackHistoryRepository`. `WorkManager` (`CoroutineWorker`, `GeniusAggregationWorker`) przelicza `TrackAffinity` i `TrackCooccurrence` **po każdych ~20 nowych zdarzeniach lub raz dziennie** — generowanie miksu jest wtedy szybkim odczytem z gotowych tabel, nigdy live-agregacją po całej historii.

### 5.5 Klastrowanie — "Genius Mixes" bez seeda (etap 2, po działającym Instant Mix)

- Wektor cech utworu: one-hot top-K gatunków biblioteki (+ "inne"), one-hot top artystów (≥3 utwory, inaczej bucket "inni"), znormalizowany rok (min-max), znormalizowany `playCount`.
- Prosty **k-means** (k = liczba głównych gatunków, zwykle 6–10), uruchamiany w tle okresowo przez `WorkManager` — nie na żywo.
- Każdy klaster → gotowa playlista ("Genius Mix: Rock", "Genius Mix: Lata 90.") widoczna na ekranie Genius (sekcja 3.4), odświeżana cyklicznie.

### 5.6 Kolejność implementacji (rekomendowana)
1. Tabele Room: `TrackEntity`, `PlayEvent`, `SkipEvent`.
2. Prosty scoring (genre + artist + year + affinity), bez cooccurrence/klastrowania → działający przycisk Genius jak najszybciej.
3. `WorkManager` job dla `TrackAffinity`/`TrackCooccurrence`.
4. Dywersyfikacja (limity artysta/album) — od razu, nie odkładać.
5. Cooccurrence + kontekst pory dnia + recency boost.
6. Klastrowanie k-means / "Genius Mixes" bez seeda.

---

## 6. Struktura modułów/plików projektu

Gradle multi-module (izolacja kompilacji, czytelne granice warstw):

```
app/                                    -- moduł aplikacji (DI graph, Application, MainActivity)
  src/main/kotlin/com/aurora/player/
    AuroraApplication.kt
    MainActivity.kt
    di/
      AppModule.kt
      PlayerModule.kt                   -- Media3 + AudioProcessor bindings
      DatabaseModule.kt

core/
  designsystem/                         -- theme, tokens, komponenty wspólne
    theme/
      Color.kt                          -- statyczne ColorScheme (dark/light)
      Typography.kt                     -- Inter FontFamily + Typography()
      Shape.kt                          -- AuroraShapes
      AuroraTheme.kt                    -- MaterialTheme wrapper
      AuroraTokens.kt                   -- CompositionLocal: spacing/motion/gradients/glass
    components/
      TrackListItem.kt
      MiniPlayer.kt
      PillTab.kt
      EqualizerBandSlider.kt
      SoftShadowBox.kt                  -- graphicsLayer+BlurEffect wrapper
      HazeSurface.kt                    -- wrapper na Haze z fallbackiem <API31
      EqualizerBars.kt                  -- mini-equalizer 3 słupki (lista)
      MorphingPlayPauseButton.kt
  common/
    Result.kt, Extensions.kt, DispatcherProvider.kt

data/
  media/
    MediaStoreScanner.kt                -- skan biblioteki lokalnej
    MetadataExtractor.kt                -- MediaMetadataRetriever, tagi/embedded art
    TrackRepositoryImpl.kt
  database/
    AuroraDatabase.kt                   -- Room, wersje/migracje
    dao/
      TrackDao.kt, PlayEventDao.kt, SkipEventDao.kt,
      TrackAffinityDao.kt, TrackCooccurrenceDao.kt,
      EqPresetDao.kt, PlaylistDao.kt, GeneratedMixDao.kt
    entity/
      TrackEntity.kt, PlayEvent.kt, SkipEvent.kt,
      TrackAffinity.kt, TrackCooccurrence.kt,
      EqPresetEntity.kt, EqStateEntity.kt, GeneratedMix.kt
  preferences/
    UserPreferencesRepositoryImpl.kt    -- DataStore: motyw, EQ enabled, długość miksu

domain/
  model/
    Track.kt, Album.kt, Artist.kt, Playlist.kt, PlaybackState.kt, EqBand.kt
  repository/                          -- interfejsy
    TrackRepository.kt, PlaybackHistoryRepository.kt,
    EqRepository.kt, GeniusRepository.kt, PlaylistRepository.kt
  usecase/
    playback/
      PlayTrackUseCase.kt, TogglePlayPauseUseCase.kt, SkipNextUseCase.kt
    genius/
      GenerateInstantMixUseCase.kt
      ScoreCandidateUseCase.kt          -- czysta funkcja score()
      DiversifyPlaylistUseCase.kt
    eq/
      UpdateBandGainUseCase.kt, ApplyPresetUseCase.kt, SaveCustomPresetUseCase.kt

audio/                                  -- silnik DSP, niezależny od UI
  EqualizerAudioProcessor.kt            -- Media3 AudioProcessor, kaskada biquad
  BiquadFilter.kt                       -- implementacja RBJ cookbook (LOW_SHELF/PEAK/HIGH_SHELF)
  BassBoostProcessor.kt                 -- low-shelf dedykowany
  StereoWidenerProcessor.kt             -- mid-side processing
  ReverbSessionEffect.kt                -- wrapper na PresetReverb/EnvironmentalReverb (audioSessionId)
  PlayerServiceModule.kt                -- MediaSessionService (Media3), integracja procesorów w pipeline

worker/
  GeniusAggregationWorker.kt            -- przelicza TrackAffinity/TrackCooccurrence
  ClusteringWorker.kt                   -- k-means, "Genius Mixes" bez seeda

feature/
  library/                             -- ekran Biblioteka (3.1)
    LibraryScreen.kt, LibraryViewModel.kt, LibraryTabsSection.kt
  nowplaying/                          -- ekran Now Playing (3.2)
    NowPlayingScreen.kt, NowPlayingViewModel.kt, AlbumArtHero.kt, TransportControls.kt
  equalizer/                           -- bottom sheet EQ (4.3)
    EqualizerSheet.kt, EqualizerViewModel.kt, PresetSelector.kt
  genius/                              -- ekran Genius (3.4)
    GeniusScreen.kt, GeniusViewModel.kt, MixCard.kt
  miniplayer/
    MiniPlayerBar.kt, MiniPlayerViewModel.kt
  settings/
    SettingsScreen.kt, ThemeSelector.kt

navigation/
  AuroraNavHost.kt                      -- Compose Navigation, SharedTransitionLayout scope na poziomie NavHost
```

**Zasady graniczne między modułami:**
- `domain` nie zna Compose ani Media3 — wyłącznie czyste modele/interfejsy Kotlin (testowalność, szybka kompilacja przy iteracji UI).
- `audio` (DSP) nie zależy od `feature/*` — komunikacja wyłącznie przez `StateFlow` z `EqRepository` (domain), żeby procesor audio dało się testować jednostkowo bez Compose.
- `core/designsystem` nie zależy od `feature/*` — komponenty wspólne (lista, mini-player, suwak EQ) reużywalne między ekranami bez cyklicznych zależności.
- `SharedTransitionLayout` scope trzymany na poziomie `AuroraNavHost`, przekazywany przez `CompositionLocal` do `feature/library` i `feature/nowplaying` — jedyne miejsce koordynacji przejścia między ekranami z różnych modułów.

---

## Status implementacji

- [x] Etap 0: scaffold Gradle (multi-module: `app`, `core:designsystem`, `domain`, `data`), theme/tokens (`AuroraTheme`, kolor/typografia/kształt — na razie `FontFamily.SansSerif` jako placeholder zamiast prawdziwego Inter, patrz TODO w `Typography.kt`), MediaStore scan (`MediaStoreScanner`), lista utworów + mini-player (Compose, Hilt DI), podstawowe odtwarzanie przez Media3 `ExoPlayer` (w procesie aplikacji, bez `MediaSessionService`/powiadomienia). `./gradlew :app:assembleDebug` przechodzi (APK w `app/build/outputs/apk/debug/`). Brak jeszcze: prawdziwy plik fontu Inter, Now Playing, EQ, Genius.
- [~] Etap 1 (częściowo): `MediaSessionService` (`PlaybackService`) — odtwarzanie w tle, powiadomienie/kontrolki systemowe, `PlayerController` łączy się przez `MediaController` zamiast trzymać własny `ExoPlayer`. Ekran Now Playing pełnoekranowy (`NowPlayingScreen`) z progress-slider + seek + play/pause, nawigacja Compose (`AuroraNavHost`) między Biblioteką a Now Playing, wspólny `LibraryViewModel` (jeden stan playbacku). Uprawnienie `POST_NOTIFICATIONS` (Android 13+). `./gradlew :app:assembleDebug` przechodzi.
  Dynamiczny kolor z okładki (Palette API) zaimplementowany osobno — patrz niżej. Next/prev dodane razem z etapem 3 (kolejka). Zostało z etapu 1: shared element transition (mini-player ↔ Now Playing), prawdziwy font Inter (nadal placeholder SansSerif — wymaga pobrania plików .ttf), Haze/glassmorphism.
- [x] Etap 1 (dokończenie): dynamiczny kolor tła Now Playing z okładki albumu — `AlbumArtColorExtractor` (Palette API, async + cache per trackId), tło (gradient DarkMuted→prawie czerń) i akcent (Vibrant — slider, przycisk play) animowane przy zmianie utworu.
- [x] Etap 2: własny equalizer — `BiquadFilter` (formuły RBJ Audio Cookbook, peakingEQ, **zweryfikowane** przez porównanie 1:1 z oryginalnym tekstem cookbooka), `EqualizerAudioProcessor` (Media3 `BaseAudioProcessor`, kaskada 10 pasm ISO na kanał, wpięty przez `EqualizerRenderersFactory`/`DefaultAudioSink.Builder.setAudioProcessors` — zweryfikowane ze źródłem androidx/media tag 1.5.0, bo dokumentacja online opisuje już nowszą, zmienioną nazwę metody), `EqRepository` (in-memory StateFlow, presety Flat/Bass Boost/Vocal/Rock/Electronic), `EqualizerSheet` (bottom sheet z pigułkami presetów + 10 pionowych suwaków przez `VerticalSlider`).
  - **Automatyczna weryfikacja odpowiedzi częstotliwościowej (długi ogon, dograne później)**: nikt w tym środowisku nie może fizycznie przesłuchać efektu na słuchawkach, więc zamiast tego `app/src/test/kotlin/com/aurora/player/eq/BiquadFilterTest.kt` (pierwsze testy jednostkowe w tym projekcie — JUnit dodany do `:app`) traktuje `BiquadFilter` jako czarną skrzynkę: przepuszcza przez niego prawdziwe sinusoidy i MIERZY rzeczywisty zysk (RMS wyjścia/wejścia w stanie ustalonym, po odczekaniu na zanik stanu przejściowego IIR), zamiast tylko odczytywać współczynniki. 6 testów, wszystkie przechodzą: podbicie/cięcie peaking EQ mierzy się dokładnie na skonfigurowany dB (±0.2dB) w częstotliwości środkowej, wraca do ~0dB daleko od środka (odróżnia peaking od shelfa), `gainDb=0` jest bit-exact bypassem, tryb bandpass (używany przez wizualizer widma) ma ~0dB na szczycie i realnie odrzuca (>6dB) oktawę od środka w obie strony. To złapałoby błąd typu odwrócony znak/zamienione a1↔b1/źle użyte Q, nawet gdyby wzór wyglądał poprawnie na oko przy przepisywaniu z cookbooka — mocniejszy dowód niż poprzednie "porównanie 1:1 z tekstem cookbooka", choć wciąż nie zastępuje realnego testu słuchowego na docelowym sprzęcie.
- [x] Etap 3: Genius (Instant Mix) + prawdziwa kolejka odtwarzania.
  - `PlayerController` ma teraz prawdziwą kolejkę (`playQueue`, `skipToNext/Previous` przez `MediaController.seekToNext/PreviousMediaItem`) zamiast jednego utworu na raz; `Now Playing` ma działające prev/next.
  - Każde przejście między utworami (`onMediaItemTransition`) zamyka event poprzedniego utworu — naturalny koniec (`MEDIA_ITEM_TRANSITION_REASON_AUTO`) liczy się jako "odsłuchany", każde inne przejście porównuje ostatnią znaną pozycję z progiem 80% długości (`PlaybackHistoryRepositoryImpl`, Room: `play_events`/`skip_events`/`track_affinity`, `exportSchema=false` na tym etapie).
  - `GeniusScoring` (domain, czysta funkcja — gatunek/artysta/rok/podobieństwo tekstu tytułu/affinity/świeżość) + `GeniusDiversifier` (max 2 utwory tego samego artysty pod rząd, max 30% z jednego albumu) → `GeniusRepositoryImpl.generateInstantMix()`.
  - Ikona Genius (gwiazdka) przy każdym utworze w bibliotece → generuje miks (seed + do 30 utworów) i ładuje jako kolejkę.
  - `affinityScore` liczony synchronicznie po każdym zdarzeniu (nie batchowany przez WorkManager) — świadome uproszczenie, wystarczające przy realistycznej skali eventów pojedynczego użytkownika.
  - Zostało z pełnego zakresu etapu 3 (odłożone do etapu 4, bo wymagają najpierw zebranej historii z realnego użytkowania): `TrackCooccurrence` (co-play), kontekst pory dnia, klastrowanie k-means ("Genius Mixes" bez seeda), zapis miksu jako trwałej playlisty.
- [x] Etap 4 (część 1): cooccurrence + kontekst pory dnia + klastrowanie k-means ("Genius Mixes").
  - `TrackCooccurrenceEntity`/`TrackCooccurrenceDao` (Room v2, `fallbackToDestructiveMigration()` — świadomie, historia lokalna jest odtwarzalna, prawdziwe `Migration` dojdą przed pierwszym publicznym wydaniem) — `PlayerController.onMediaItemTransition` zapisuje `recordTransition(poprzedni, następny)` przy każdym przejściu.
  - `GeniusScoring` zaktualizowany do pełnej formuły z DESIGN.md 5.2 (wagi: gatunek .25/artysta .20/rok .10/tekst .10/cooccurrence .15/affinity .10/kontekst .05/świeżość .05) — cooccurrence i kontekst normalizowane `log(1+count)/log(1+maxCount)` względem konkurentów w danym zapytaniu; kontekst pory dnia to okno ±2h (z zawinięciem przez północ) na `play_events.hourOfDay`.
  - `GeniusClustering` (domain, czysta funkcja, k-means z deterministycznym seedem — te same miksy między odświeżeniami dopóki biblioteka/historia się nie zmieni) — wektor cechy: one-hot top-K gatunków + znormalizowany rok + affinity (bez one-hot artystów, świadome uproszczenie wymiarowości). Nowy ekran **Genius Mixes** (`GeniusMixesScreen`, ikona gwiazdki w nagłówku Biblioteki) — lista gotowych playlist ("Genius Mix: Rock" itp.), tap = odtwórz jako kolejkę.
  - Zostało z etapu 4: dopracowanie wizualne ekranu Genius Mixes (hero card + tematyczne "półki" zamiast jednej listy — patrz DESIGN.md 3.4), zapis miksu jako trwałej playlisty, WorkManager batching (na razie affinity liczone synchronicznie — nadal OK przy tej skali).
- [x] Etap 4 (część 2a): Haze/glassmorphism na mini-playerze i arkuszu equalizera.
  - Biblioteka `dev.chrisbanes.haze` — **przypięta na 1.6.10**, nie najnowsza: 1.7.3 wymaga compileSdk 37 (nie mamy zainstalowanej platformy), a 1.7.2 (mimo że sam ma minCompileSdk=1) ciągnie tranzytywnie `androidx.navigationevent:1.0.1`, który wymaga compileSdk 36 + AGP 8.9.1+ — oba fakty zweryfikowane rozpakowaniem `aar-metadata.properties` z Maven Central, nie zgadywaniem. 1.6.10 buduje się czysto na obecnym compileSdk 35/AGP 8.7.3. Do rozważenia przy przyszłym podniesieniu compileSdk/AGP.
  - `MiniPlayerBar` (core:designsystem) i `EqualizerSheet` przyjmują opcjonalny `HazeState` — gdy podany, tło jest prawdziwym blurem (`hazeEffect` + `HazeMaterials.thin/regular`) zamiast płaskiego koloru; gdy `null`, degradacja do zwykłego `background()` (starszy wzorzec, wciąż działa). Degradacja na urządzeniach <API 31 jest wbudowana w samą bibliotekę Haze, nic dodatkowego nie trzeba było pisać.
  - `LibraryScreen`: `LazyColumn` oznaczona jako `hazeSource`, `MiniPlayerBar` czyta z niej blur. `NowPlayingScreen`: cały ekran jako `hazeSource`, `EqualizerSheet` czyta blur (wzorzec `containerColor = Transparent` + `hazeEffect` na modifierze sheeta, zweryfikowany na oficjalnym przykładzie biblioteki — `ScaffoldSample`/`LargeTopAppBar`).
- [x] Etap 4 (część 2b): prawdziwy font Inter.
  - Pobrany z oficjalnego wydania `rsms/inter` v4.1 (GitHub Releases) — ta wersja dystrybuuje Inter wyłącznie jako **variable font** (jeden plik `InterVariable.ttf`, 879KB), nie osobne pliki na wagę jak zakładał wcześniejszy komentarz TODO. Plik w `core/designsystem/src/main/res/font/inter_variable.ttf`, licencja SIL OFL w `licenses/Inter-LICENSE.txt`.
  - `AuroraFontFamily` (`Typography.kt`) buduje 3 wagi (400/500/600) z jednego pliku przez `Font(..., variationSettings = FontVariation.Settings(FontVariation.weight(w)))` (`@OptIn(ExperimentalTextApi::class)`) — nowocześniejsze i lżejsze niż trzy osobne pliki statyczne.
  - `.gitattributes` dodany (`*.ttf binary` itd.), żeby git/CRLF nigdy nie tknął plików binarnych.
- [x] Etap 5: wizualizer widmowy (jak stare wizualizacje Windows Media Player), dodany na życzenie użytkownika.
  - **Świadomie NIE systemowy `android.media.audiofx.Visualizer`** — jego użycie wymaga uprawnienia `RECORD_AUDIO` nawet dla capture z własnej sesji audio (zweryfikowane w źródle AOSP `Visualizer.java`: "the use of the visualizer requires the permission android.permission.RECORD_AUDIO"), co byłoby mylące dla użytkownika (po co odtwarzaczowi mikrofon) i niepotrzebne, skoro nasz własny `EqualizerAudioProcessor` i tak już widzi każdą próbkę PCM.
  - `BiquadFilter.updateBandpassCoefficients()` — wariant BPF "constant 0 dB peak gain" z RBJ Audio Cookbook, wzór zweryfikowany 1:1 z tekstem cookbooka tak samo jak peakingEQ.
  - `AudioVisualizerAnalyzer` (nowy Hilt singleton, `app/visualizer/`) — 24 pasma BPF rozstawione logarytmicznie 60Hz–12kHz, RMS energii per pasmo liczone raz na bufor audio (po miksie do mono, PO przetworzeniu EQ — wizualizuje dokładnie to, co słychać), kompresja logarytmiczna (dB) żeby ciche fragmenty też było widać. `EqualizerAudioProcessor` woła go w tej samej pętli co filtrowanie EQ — zero dodatkowego przejścia po danych PCM.
  - `VisualizerBars` (core:designsystem, Canvas + `animateFloatAsState` per słupek ze sprężyną) — pasek 24 słupków w kolorze akcentu (Vibrant z okładki) pod tytułem utworu na Now Playing, widoczny tylko podczas odtwarzania.
- [x] Etap 5 (dokończenie): shared element transition mini-player↔Now Playing — patrz niżej po etapie 6.
- [x] Etap 6: biblioteka w chmurze (Google Drive) obok biblioteki lokalnej.
  - Użytkownik loguje się własnym kontem Google i appka **tylko czyta** pliki audio z jego Drive — upload robi sam użytkownik przez appkę Google Drive, Aurora niczego nie wysyła. Wymaga jednorazowej rejestracji w Google Cloud Console przez użytkownika (projekt, włączone Drive API, OAuth consent screen z test userem, OAuth client Android z package name `com.aurora.player` + SHA-1 klucza podpisującego) — tego nie da się zrobić z poziomu kodu.
  - **Ważne odkrycie w trakcie budowy:** klasyczne `GoogleSignInClient`/`GoogleSignInOptions` (`com.google.android.gms.auth.api.signin.*`) zostały **całkowicie usunięte** z `play-services-auth` w wersji 22.0.0 (build się wysypał na "Unresolved reference", co to ujawniło — nie było to wcześniej udokumentowane w miejscu, gdzie bym tego szukał). Zastąpione nowym `Identity.getAuthorizationClient()` ("Authorization API"), flow dwuetapowy: cicha próba autoryzacji → jeśli trzeba, `IntentSender` do ekranu zgody przez `ActivityResultContracts.StartIntentSenderForResult`.
  - `GoogleDriveLibraryRepository` (`app/cloud/`): REST Drive API v3 (`google-api-services-drive` + `google-api-client-android` + `google-http-client-gson`) — natywne "Drive Android API" jest osobno deprecated przez Google. `refreshCloudTracks()` listuje wszystkie pliki `mimeType contains 'audio/'` z całego Drive (bez wyboru konkretnego folderu na razie). `currentAccessTokenBlocking()` re-autoryzuje przy KAŻDYM nowym utworze z chmury (Play Services odświeża po cichu) zamiast polegać na cache'owanym tokenie — tokeny OAuth żyją ~1h, a `PlaybackService` żyje długo.
  - `GoogleDriveDataSourceFactory` (Media3 `DataSource.Factory`, dokłada nagłówek `Authorization: Bearer <token>`) owinięty w `DefaultDataSource.Factory(context, this)` w `PlaybackService` — content:// (lokalne) i https:// (Drive) współistnieją w jednym pipeline audio.
  - `Track.source: TrackSource` (LOCAL/CLOUD) + `PlaybackState.durationMs` (realny czas z playera, bo Drive nie zwraca długości audio w metadanych — slider Now Playing czeka aż Media3 sam ją odkryje). Utwory z chmury dostają Long id przez stabilny hash Drive fileId przesunięty o `10^12`, żeby nie kolidować z lokalnymi MediaStore `_ID`.
  - `LibraryScreen`: ikona chmury w nagłówku (połączona/rozłączona), lista łączy `tracks + cloudTracks` z małą ikoną chmury przy utworach z Drive.
  - `packaging { resources { excludes = [...] } }` dodany w `app/build.gradle.kts` — biblioteki Google API client dublują pliki `META-INF/INDEX.LIST` między jarami (`google-auth-library-oauth2-http` vs `google-auth-library-credentials`), znany problem tych bibliotek na Androidzie.
  - Znane ograniczenia v1: cała biblioteka Drive na raz (brak wyboru folderu), brak automatycznego odświeżenia gdy zgoda zostanie cofnięta w trakcie sesji (trzeba wtedy dotknąć ikonę chmury ponownie), brak testu na żywym koncie (wymaga dokończenia konfiguracji Google Cloud Console przez użytkownika).
- [x] Etap 5 — dokończenie: shared element transition mini-player ↔ Now Playing.
  - API zweryfikowane bezpośrednio w źródle `androidx.compose.animation:animation-android` **w wersji faktycznie rozwiązanej przez nasz build** (1.8.0, nie BOM-owa 1.7.6 — coś w grafie zależności ją podbiło) zamiast zgadywania: `SharedTransitionScope`/`SharedTransitionLayout` są `@ExperimentalSharedTransitionApi`, `AnimatedContentScope : AnimatedVisibilityScope` (więc odbiornik `composable { }` z Navigation Compose 2.8.5 nadaje się wprost jako `animatedVisibilityScope`).
  - `Modifier.sharedElementOrSelf()` (nowy plik w `core:designsystem/components`) — bezpieczny no-op gdy scope'y są `null`, więc `MiniPlayerBar`/`NowPlayingScreen` działają identycznie z i bez `SharedTransitionLayout` w drzewie (żadnej twardej zależności od konkretnej nawigacji).
  - `AuroraNavHost` owinięty w `SharedTransitionLayout`; `this@SharedTransitionLayout` (scope) i `this@composable` (AnimatedContentScope) przekazywane do `LibraryScreen`→`MiniPlayerBar` i do `NowPlayingScreen`, wspólny klucz `"album_art"` (domyślna wartość parametru, nie sztywno zaszyty string w dwóch miejscach).
  - `core:designsystem` dostał pełną zależność `androidx.compose.animation:animation` (wcześniej tylko `animation-core`) — `SharedTransitionScope`/`AnimatedVisibilityScope` żyją w pełnym pakiecie.

- [x] Etap 7: "Aurora Visualizer" — generatywny wizualizer na życzenie użytkownika (2000-style, "jak najbardziej WOW"), zastąpił mały pasek słupków z etapu 5.
  - `AudioVisualizerAnalyzer` rozszerzony o `VisualizerFrame` (24 pasma + `bassEnergy`/`midEnergy`/`trebleEnergy`/`overallEnergy` liczone z uśrednienia zakresów pasm + `beatCount`). Wykrywanie bitu: energy-based onset detection na paśmie basowym (surowe RMS vs. jego wygładzona średnia krocząca EMA, próg 1.4×, debounce 220ms) — prosty, ale wystarczający do napędzania efektów; **to nie jest precyzyjny beat-tracker BPM**.
  - `AuroraVisualizer` (`core:designsystem`) — trzy warstwy w jednym Canvasie, pętla klatek przez `withFrameNanos` (nie sztywne 60fps, tempo ekranu): (1) pulsujący radialny blask w tle (rozmiar/intensywność z głośności+basu), (2) 24 "promienie" widma w okrąg wokół centrum, każdy ze świeceniem pod spodem (natywny `android.graphics.BlurMaskFilter`, jeden reużywany obiekt `Paint` na klatkę, nie 24 alokacje) + ostrą kreską na wierzchu, (3) system cząsteczek — eksplodują z centrum na każdy wykryty bit, fizyka (prędkość + tarcie) liczona bezpośrednio w pętli klatek na zwykłych `var` (nie przez Compose snapshoty per-cząsteczka — tylko jawny `frameTick` wymusza przerysowanie), max 160 cząsteczek jednocześnie.
  - Świadomie **jeden akcent koloru** (ten sam Vibrant co reszta Now Playing), zgodnie z zasadą DESIGN.md "jeden mocny akcent na ekran" — nie tęcza.
  - UI: okładka albumu na Now Playing stała się przełącznikiem — tap = `Crossfade` między okładką a wizualizerem na pełną wielkość panelu (nie mały pasek pod tytułem), mała ikonka `GraphicEq` w rogu jako podpowiedź że można tapnąć. Shared element transition nadal działa (modifier na tym samym Boxie, niezależnie od tego co jest w środku).
  - **Nie przetestowane wizualnie przeze mnie** (brak dostępu do ekranu/telefonu w tym środowisku) — matematyka/fizyka jest spójna i się kompiluje, ale dobór kolorów/intensywności/prędkości cząsteczek to z natury rzeczy coś, co trzeba dostroić patrząc na żywo. Stałe do łatwego strojenia zebrane na dole pliku `AuroraVisualizer.kt` (`PARTICLES_PER_BEAT`, `PARTICLE_MIN_SPEED`, `BEAT_THRESHOLD_RATIO` w `AudioVisualizerAnalyzer.kt` itd.).

- [x] Etap 8: przebudowa "Aurora Visualizer" v2 — użytkownik po zobaczeniu v1 na żywo (emulator) ocenił efekt jako niewystarczający ("wciąż 24 kreski w kółku ułożone", "z 1000 lvli wyżej od tego wizualizera"). Pełny rewrite pod kątem efektu "WOW", zweryfikowany na żywo na emulatorze (zrzuty ekranu podczas odtwarzania).
  - **Trwałe smugi/motion blur**: Compose `Canvas` czyści się co klatkę z natury, więc dodano offscreen `ImageBitmap` + natywny `android.graphics.Canvas` trzymany w `remember` między klatkami — zamiast czyszczenia, co klatkę rysowany półprzezroczysty czarny prostokąt (`TRAIL_FADE_ALPHA`), co daje realne, akumulujące się smugi ruchu zamiast statycznego obrazu.
  - **Cykliczna zmiana koloru** (`hue` narastający co klatkę, `HSVToColor`) — świadome odejście od zasady "jeden akcent koloru" z Etapu 7, udokumentowane wprost w komentarzu w pliku jako wyjątek na życzenie użytkownika dla tego jednego ekranu.
  - **Organiczny kształt widma** (`drawSpectrumBlossom`) zamiast 24 sztywnych promieni — gładka zamknięta krzywa Beziera przez 24 punkty pasm, powoli rotująca, z gradientem radialnym + `BlurMaskFilter` (poświata) + ostrym konturem na wierzchu.
  - **Kalejdoskop cząsteczek** (`drawKaleidoParticles`, `KALEIDOSCOPE_SLICES = 6`) — każda cząsteczka z eksplozji na bit liczona raz, ale rysowana w 6 równo obróconych pozycjach (współrzędne biegunowe od centrum), więc jeden system cząsteczek daje symetryczny wzór zamiast rozrzutu.
  - **Pełny ekran zamiast ramki**: `NowPlayingScreen` — tap na okładkę = nakładka na cały ekran (czarne tło, `AuroraVisualizer` na całą powierzchnię), wyjście przez tap gdziekolwiek na nakładce. To zostało uznane za tymczasowe UX (patrz niżej, Etap 9 zmienia to na wizualizer w tej samej ramce co okładka + osobny przycisk pełnego ekranu).
  - Zweryfikowane na żywo na `emulator-5554`: build+install+relaunch+zrzuty ekranu w trakcie odtwarzania potwierdziły brak crasha (`dumpsys activity activities`, logcat bez `FATAL EXCEPTION`) i renderowanie nowego organicznego kształtu z poświatą — jeden zrzut ekranu nie potwierdza jednak w pełni smug/cyklu kolorów/kalejdoskopu cząstek w czasie (wymaga dłuższej obserwacji), a krótkie utwory testowe (~7-10s) ograniczały czas obserwacji.

- [x] Etap 9: **zmiana silnika wizualizera na projectM** (open-source, kompatybilny z Milkdrop, używany m.in. w Winampie/VLC) zamiast dalszego dostrajania własnego Compose Canvas z Etapu 8 — decyzja użytkownika po zobaczeniu v2 na żywo. UX: okładka albumu i wizualizer dzielą tę samą ramkę na Now Playing (tap = przełącznik w miejscu), z osobnym przyciskiem rozwijającym do pełnego ekranu. Zweryfikowane na żywo na emulatorze (GPU host, GLES 3.1) — realny, gotowy preset Milkdrop renderuje się poprawnie zarówno w ramce jak i na pełnym ekranie.
  - **Licencja (research przed kodem, nie zgadywanie)**: projectM = LGPL-2.1-or-later (potwierdzone z `LICENSE.txt`/`COPYING` w tagu v4.1.7). Zgodność dla appki zamkniętoźródłowej: (1) `libprojectM-4.so`/`libprojectM-4-playlist.so` linkowane jako osobne, dynamicznie ładowane biblioteki — **nigdy** wtopione w kod appki (zweryfikowane w zbudowanej APK: `unzip -l` pokazuje je jako odrębne pliki `.so`, nie scalone z resztą), (2) dokładna wersja źródła wskazana w appce (tag `v4.1.7` na GitHubie, nie "żywy" branch), (3) ekran "Licencje open source" (`app/licenses/OpenSourceLicensesScreen.kt`, ikona ⓘ w Now Playing) z notką, copyright, pełnym tekstem LGPL-2.1 (zbundlowany jako asset) i wskazaniem źródła. Jeden punkt prawnie nierozstrzygnięty: czy podpisana APK w ogóle spełnia dosłowny wymóg §6(b) "użytkownik może podmienić bibliotekę" — branżowy standard (i to, co zrobiliśmy) to zgodność przez §6(a)/(c) (dostępność źródła) zamiast dosłownego relinku; **przed realną komercyjną publikacją wymaga realnej konsultacji prawnej**, nie tylko osądu inżynierskiego.
  - **AAR zwendorowany lokalnie** (`local-maven-repo/net/protyposis/projectm-unofficial/projectm-android/4.1.7/`) zamiast ciągnięty z serwera `protyposis.github.io` przy każdym buildzie — to małe, jednoosobowe repo ("experimental... not guaranteed to stay available forever" wprost w jego README). Sumy SHA-256 obu wariantów (debug/release) zweryfikowane przeciw `.module` przed użyciem. Fallback jeśli kiedyś zniknie: upstream `build_android.yml` to gotowy, sprawdzony przepis NDK/CMake do własnego zbudowania.
  - **`core:projectm`** (nowy moduł): `build.gradle.kts` z `buildFeatures.prefab`, `ndkVersion = "27.2.12479018"` (ta sama gałąź NDK co użyta do zbudowania AAR — `abi.json` w środku podaje `"ndk": 27`, `"stl": "c++_shared"` — zgodność STL między `.so` w tym samym procesie jest krytyczna, inna wersja = crash/ODR), CMake 3.22.1. `src/main/cpp/CMakeLists.txt` robi `find_package(projectm-android REQUIRED CONFIG)` (nie `add_subdirectory` — tak explicit radzi maintainer projectM w issue #825) i linkuje `projectm-android::projectM-4`/`projectM-4-playlist`. `projectm_jni.cpp` — cienki most 1:1 na C API (`projectm_create`, `projectm_playlist_create` — auto-podpina się pod callback "preset switch requested", `projectm_opengl_render_frame`, `projectm_pcm_add_int16`, `projectm_set_texture_search_paths`, `projectm_playlist_add_path`+`set_shuffle`+`play_next`), zweryfikowane bezpośrednio z nagłówków wewnątrz zwendorowanej AAR, nie z dokumentacji na pamięć.
  - **`ProjectMEngine`** (`core/projectm`) — właściciel natywnego handle'a, `ReentrantReadWriteLock` (odczyt: render/addPcm, zapis: create/destroy) żeby `destroy()` nigdy nie ścigał się z `addPcm()` wołanym z osobnego wątku audio (projectM nie ma własnego mutexu na buforze PCM — udokumentowane, że wyścig da najwyżej zamazaną klatkę, ale destroy to realne zwolnienie pamięci, więc to *musi* być wykluczające). `isDeviceSupported(context)` sprawdza `ActivityManager.reqGlEsVersion >= GLES 3.1` (twardy wymóg projectM, potwierdzony wprost przez maintainera) — na urządzeniach poniżej appka po cichu spada na stary `AuroraVisualizer` z Etapu 8 (ten kod **został**, nie usunięty, właśnie jako fallback).
  - **`ProjectMPcmBridge`** (zwykły `object`, nie Hilt) — most między `EqualizerAudioProcessor` (już widzi każdą próbkę PCM post-EQ) a aktywnym `ProjectMEngine`; próbka miksowana do mono (ta sama, którą dostaje stary `AudioVisualizerAnalyzer`) trafia do obu naraz w jednym przebiegu bufora. No-op gdy wizualizer niewidoczny.
  - **`PresetInstaller`** — projectM czyta `.milk` zwykłym `std::ifstream` (zweryfikowane w źródle `PresetFileParser.cpp`), nie potrafi czytać z `assets/` w APK wprost — jednorazowa ekstrakcja do `filesDir` z markerem wersji.
  - **Presety**: kurowany podzbiór ~1202 plików `.milk` z oficjalnej paczki "Cream of the Crop" (9795 w całości, ~111MB), dobrany proporcjonalnie z 11 kategorii (deterministyczny seed), plus paczka tekstur Milkdrop — łącznie ~21MB w assets. Status prawny słabszy niż sama biblioteka: paczka ma tylko "zakładamy public domain + usuniemy na życzenie autora" (GitHub oznacza ją `NOASSERTION`), nie formalny grant — stąd osobna notka na ekranie licencji.
  - **Dwa realne bugi znalezione i naprawione na żywo na emulatorze** (nie zgadywane — złapane w logcat i naprawione po analizie):
    1. `IllegalStateException: ProjectMEngine.create() wywołane bez wcześniejszego destroy()` — `GLSurfaceView.onSurfaceCreated()` potrafi wystrzelić więcej niż raz na tej samej instancji (Android niszczy/tworzy samą Surface przy zmianie rozmiaru/reparentingu), co pierwotny twardy `check()` traktował jako błąd programisty. Naprawione: `create()` jest teraz idempotentny (zwalnia stary handle i tworzy nowy zamiast rzucać wyjątek).
    2. **`movableContentOf` między ramką inline a nakładką pełnoekranową NIE wymuszało ponownego layoutu `AndroidView`** — ten sam `GLSurfaceView` po przeniesieniu do większego kontenera zostawał zablokowany na starym rozmiarze (996×996 potwierdzone logiem `onSurfaceChanged`), renderując tylko w rogu pełnoekranowej nakładki zamiast na całą powierzchnię. To dokładnie ryzyko przewidziane w researchu przed budową ("ta specyficzna kombinacja GLSurfaceView+movableContent nie jest sprawdzonym wzorcem, prototypować jako pierwsze"). Naprawione przez **rezygnację z `movableContentOf`** — każdy tryb (ramka/pełny ekran) dostaje własną, świeżo montowaną instancję `ProjectMSurface`; `engine.create()` będąc już idempotentny sprawia, że kosztem jest tylko krótki restart bieżącego presetu przy przełączeniu, nie crash ani zła rozdzielczość. Dokładnie fallback przewidziany z góry w researchu, tylko potwierdzony empirycznie zamiast założony.
  - Zweryfikowane end-to-end na emulatorze (`-gpu host`, prawdziwe GPU NVIDIA przez passthrough, GLES 3.1 — domyślny SwiftShader dawał tylko GLES 3.0, za mało): okładka→tap→ramka inline z realnym renderem Milkdrop (kalejdoskopowy wzór, potwierdzone zrzutem ekranu) → przycisk pełnego ekranu → poprawny render na całą powierzchnię 1080×2400 → tap poza treścią → powrót do ramki. Zero crashy w finalnej wersji.

- [x] Etap 10 (część 1 — mechanizm trybów + dobór wg gatunku): **tryby wizualizera projectM**. Decyzja projektowa: NIE trzy osobne silniki renderujące od zera (to powtórzyłoby błąd Etapów 7-8, które user odrzucił jako niewystarczające) — zamiast tego filtrowanie zestawu presetów Milkdrop, które już mamy skurowane per kategoria (Etap 9). `ProjectMVisualizerMode` (`:core:projectm`): **Wszystkie** (cały zestaw, domyślny — bez zmiany dotychczasowego zachowania), **Ambient** (foldery Hypnotic+Drawing), **Spectrum** (folder Waveform), **Particle** (Particles+Sparkle+Supernova). Przełączanie w locie: nowy `projectm_playlist_clear` w moście JNI (playlist tylko dokłada presety, więc zmiana zestawu wymaga wyczyszczenia najpierw) + `ProjectMEngine.loadPresets(root, mode)` czyści i ładuje tylko wybrane podfoldery. Chipy przełącznika (`ModeSwitcher` w `ProjectMSurface`) pokazują się tylko na pełnym ekranie (`showModeSwitcher=true`) — w małej ramce inline nie ma na nie miejsca. `ProjectMVisualizerMode.defaultForGenre()` — prosta heurystyka na słowach kluczowych z `Track.genre` (jazz/classical→Ambient, electronic/techno→Spectrum, rock/pop→Particle) jako punkt startowy przy otwarciu wizualizera; user zawsze może nadpisać ręcznie. **Zweryfikowane na żywo na emulatorze**: przełączenie na "Particle" realnie wyczyściło starą playlistę i załadowało nowy, wyraźnie inny preset — potwierdzone zrzutem ekranu przed/po, zero crashy.
  - Pierwotna lista z rozmowy (dla kontekstu, co dokładnie te trzy tryby miały znaczyć): **Ambient** (miękkie światło, rozmyte gradienty, wolne ruchy — proponowany domyślny, razem z okładką i falą reagującą na częstotliwości), **Spectrum** (klasyczne widmo częstotliwości, 32-64 pasma, precyzyjne), **Particle** (tysiące cząsteczek, bas = fale cząsteczek, wysokie = iskry — najbardziej efektowne na tablecie). Presety Milkdrop realizują ten sam nastrój innym mechanizmem (gotowy, dopracowany kod shaderów zamiast naszego), nie 1:1 tą samą estetyką co opisana wizja.

- [ ] Etap 10 (część 2, PLANOWANE, nierozpoczęte): reszta wizji z rozmowy, jeszcze nietknięta:
  - Analiza sygnału rozdzielona na pasma: sub-bas 20-60Hz, bas 60-250Hz, środek 250Hz-2kHz, wysokie 2-20kHz — wizualizer ma reagować na te konkretne zakresy (cichy fragment = delikatne pulsowanie, wejście perkusji = szybki impuls, mocny bas = powiększenie formy, refren = większa amplituda).
  - Charakter animacji zależny od gatunku utworu: jazz = spokojne organiczne ruchy, electronic = geometryczne precyzyjne formy, rock = mocniejsze impulsy, classical = subtelna płynna animacja, lo-fi = delikatne pulsowanie + ziarno.
  - Synchronizacja z kolorami okładki albumu (mamy już `AlbumArtColorExtractor`/Palette API z Etapu 1) — dynamiczne tło ambient z dominujących kolorów, płynne przejście palety przy zmianie utworu. **Uwaga techniczna na przyszłość**: presety Milkdrop same decydują o swoich kolorach przez wewnętrzny kod shaderów projectM — nie da się ich po prostu "przefarbować" na kolor okładki z zewnątrz jak zwykłej grafiki. Realistyczna wersja tego pomysłu to sync tła/chrome WOKÓŁ wizualizera (i ewentualnie parametru `projectm_set_hue_shift`, jeśli taki jest w API — do sprawdzenia), nie samego renderu presetu.
  - Ustawienia użytkownika: intensywność, czułość basu, czułość wysokich, szybkość animacji, rozmycie, reakcja na beat (przełącznik), źródło kolorów (okładka/własne/monochromatyczne), FPS (30/60), oszczędzanie baterii, wyłącz podczas blokady ekranu.
  - Pomysł na "Audio Reactive jako informacja, nie tylko dekoracja": subtelny panel z realnymi metrykami audio (LUFS, peak dB, dynamic range, bitrate/bit depth/sample rate) w trybie "profesjonalnym" — z rozmowy padł też pomysł na osobny ekran "Audio Lab" pokazujący cały łańcuch sygnału (źródło→EQ→crossfade→normalizacja→DAC→output) z możliwością wejścia w ustawienia każdego elementu; to może być jeden wspólny mechanizm analizy/telemetrii audio zasilający oba pomysły naraz.
  - User przesłał dwa referencyjne mockupy (ekran Teraz Odtwarzane na tablecie w stylu lofi/kotek, oraz pełny koncept z przełącznikiem trybów Ambient/Spectrum/Particle + panelem metryk LUFS/Peak/DR + suwakami ustawień) — **nie udało się ich zapisać jako plików binarnych w repo**: obrazki wklejone bezpośrednio na czacie nie są dostępne jako ścieżka na dysku dla tego narzędzia. Jeśli mają zostać zachowane jako assety w repozytorium, user musi je zapisać ręcznie (np. przeciągnąć do folderu projektu) — na razie ich treść jest w pełni opisana słownie tutaj i w historii rozmowy.
  - Świadomie odłożone do osobnej rundy — to nowy, spory zakres UX/DSP (adaptacja do gatunku wymaga np. tagów gatunkowych z biblioteki + prostej heurystyki lub klasyfikatora), nie rozszerzenie bieżącego Etapu 9.

- [ ] Etap 11 (PLANOWANE, nierozpoczęte): **inteligentne rozpoznawanie utworów `<unknown>`** — po odtworzeniu utworu bez tagów (artysta/album/tytuł nieznane), appka miałaby przepuścić go przez jakiś mechanizm rozpoznawania (fingerprinting w stylu Shazam/AcoustID/MusicBrainz) i samodzielnie uzupełnić metadane + zmienić nazwę pliku/wpisu w bibliotece. Wymaga wyboru usługi/biblioteki do fingerprintingu (offline vs. zapytanie do zewnętrznego serwisu — do decyzji, bo dotąd cała biblioteka/rekomendacje w tej appce świadomie działają w 100% lokalnie/offline, patrz zasada z Etapu "Genius") i mechanizmu bezpiecznej zmiany nazwy/tagów bez utraty danych o postępie odtwarzania/statystykach powiązanych ze starym wpisem.

- [ ] Etap 12 (PLANOWANE, nierozpoczęte): **wybór chmury, z której się słucha — multi-cloud zamiast tylko Google Drive**. Wizja z rozmowy:
  - Kolejne źródła obok Google Drive (Etap 6): OneDrive (Microsoft Graph API), Dropbox, iCloud Drive, pCloud, Box, oraz — wg usera szczególnie ważne dla audiofilów — NAS/WebDAV/SMB i integracja z Plex/Jellyfin.
  - Kluczowa zasada UX: użytkownik **nie przenosi** muzyki do żadnego specjalnego magazynu appki — łączy konto/wskazuje folder lub adres serwera, appka indeksuje i odtwarza bezpośrednio (streaming), z opcjonalnym jawnym "Pobierz offline" per utwór (`☁ Dostępny online` vs `✓ Dostępny offline`) zamiast wymuszonego ściągania całości z góry.
  - Ekran "Źródła muzyki": lista wszystkich podłączonych źródeł (chmura/NAS/telefon/USB) z liczbą utworów i statusem synchronizacji per źródło, plus opcja "Wszystkie źródła" scalająca je w jedną bibliotekę (Wykonawcy→Albumy→Utwory→Gatunki→Rok), niezależnie od fizycznej lokalizacji plików — mechanizm bardzo zbliżony do już istniejącego `allTracks: List<Track> get() = tracks + cloudTracks` w `LibraryViewModel`, tylko rozszerzony na N źródeł zamiast dwóch.
  - Architektura: warto zaprojektować to modułowo (jeden wspólny interfejs "dostawcy źródła muzyki" z osobną implementacją per chmura/protokół) już na starcie tego etapu, żeby dodawanie kolejnych źródeł później nie wymagało przebudowy — zwłaszcza istotne, jeśli kiedyś appka trafi też na iOS/iPadOS.
  - **Prerekwizyt schematu id — ZAŁATWIONY, patrz Etap 13** (poniżej): stary hack `hash(fileId) + 10^12` dla Google Drive zastąpiony solidnym 64-bitowym hashem z dyskryminatorem źródła, więc kolejne źródła z tego etapu nie będą już kolidować ze sobą tym mechanizmem.
  - Świadomie odłożone — to kolejny duży, samodzielny kawałek pracy (każda integracja to osobne SDK/API, OAuth, obsługa błędów sieci), nie rozszerzenie bieżącego Etapu 9.

- [x] Etap 13: **solidny schemat id utworów niezależny od źródła** — realizacja prerekwizytu z Etapu 12, zgłoszona wprost w rozmowie po przejrzeniu silnika rekomendacji. Nowy `TrackIdHasher` (`:domain`, `com.aurora.player.domain.util`) — 64-bitowy FNV-1a liczony z `"$sourceDiscriminator:$nativeId"` zamiast poprzedniego "CLOUD_ID_OFFSET + 31-bitowy String.hashCode()", które rezerwowało jedno wspólne pasmo dla całej kategorii "chmura". Każde źródło (dyskryminator to zwykły string, np. `"google_drive"`) dostaje własną przestrzeń skrótu przez sam wsad do hasha — dowolna liczba przyszłych źródeł (Etap 12) jest bezpieczna bez kolejnych ręcznych pasm przesunięć. `GoogleDriveLibraryRepository.cloudTrackId()` zmigrowany na nowy mechanizm. Lokalne id z MediaStore celowo NIEZMIENIONE (są już stabilne i unikalne w obrębie urządzenia — nie ma powodu ich przepisywać). Zweryfikowane: kompiluje się czysto (`:domain`, `:app`).
  - Świadomie NIE zrobiono pełnej encji `MusicSource`/`sourceId`+`remoteId` sugerowanej w rozmowie (rozdzielenie typu `Track.id` na złożony klucz) — to dużo większy refaktor (Room FK-e w `TrackAffinityEntity`/`TrackCooccurrenceEntity`/`PlayEventEntity`, ExoPlayer/MediaSession id, LibraryViewModel) nieproporcjonalny do problemu przy obecnych dwóch źródłach. Solidny hash rozwiązuje realny problem kolizji między źródłami bez tego kosztu; pełne modelowanie źródła zostaje do rozważenia w samym Etapie 12, gdy realnie dojdzie 3. źródło.

- [x] Etap 14: **ważona, kierunkowa cooccurrence zamiast surowego licznika** — zgłoszone w rozmowie: "nie traktuj `count` jako podobieństwa, popularny utwór ≠ dobry następny utwór". Zmiany:
  - `TrackCooccurrenceEntity`: z symetrycznej pary (`trackIdA<trackIdB`, `count: Int`) na kierunkową (`fromTrackId`, `toTrackId`, `weight: Float`) — "co grało po X" jest z natury asymetryczne (X→Y i Y→X to różne przejścia), a poprzednia para tego nie rozróżniała. Bump `AuroraDatabase` do wersji 3 (destrukcyjna migracja, jak reszta tabel na tym etapie projektu).
  - `PlaybackHistoryRepositoryImpl.recordTransition()` przyjmuje teraz też `fromPlayedMs`/`fromDurationMs` (dane już liczone w `PlayerController` przy okazji `recordPlaybackEnded`, zero nowego kosztu) i waży przejście wzorem `0.2 + 0.8 × completionRatio` — pełne dosłuchanie utworu przed przejściem dalej liczy się prawie dwa razy mocniej niż natychmiastowy skip, zamiast identycznego +1 dla obu przypadków.
  - `GeniusRepositoryImpl`: czyta tylko kierunek `getFrom(seed.id)` (trafniejsze dla "co puścić dalej" niż poprzednie sprawdzanie obu stron pary) i **wygasza wagę w czasie odczytu** wykładniczo wg `lastSeenAt` (skala 90 dni) — stare sąsiedztwa tracą znaczenie bez osobnego joba czyszczącego, tym samym wzorcem co istniejący `recencyBoost` w `GeniusScoring`.
  - Świadomie NIE zrobiono: uwzględnienia playlisty/sesji (appka nie ma jeszcze pierwszoklasowego pojęcia "playlisty" poza kolejką i Genius Mixes) ani ponownego ważenia gatunkiem/artystą w samej cooccurrence (to już osobno liczy `GeniusScoring.WEIGHT_GENRE`/`WEIGHT_ARTIST` — dodanie tego też tutaj podwójnie liczyłoby ten sam sygnał).
  - **Zweryfikowane żywo, nie tylko kompilacją**: podczas testów na emulatorze tego samego dnia utwory testowe (6-10s) auto-advance'owały się dziesiątki razy, więc `recordPlaybackEnded`+`recordTransition` z nową sygnaturą wykonały się naturalnie wielokrotnie — zero crashy, zero wyjątków w logcat przez cały czas testów.

- [x] Etap 15: **kontekstowe miksy Genius wg pory dnia** ("Morning Focus"/"Night Drive" z rozmowy). Nowy `GeniusTimeContext` (`:domain`) — 4 nienachodzące się okna czasu (Poranny fokus 5-11, Fokus na cały dzień 11-17, Wieczorny relaks 17-22, Nocna jazda 22-5, zawijane przez północ), nazwy trzymają się tego, co appka faktycznie mierzy (pora dnia z `PlayEventEntity.hourOfDay`), bez udawania wykrywania aktywności (jazda/praca), której appka nie śledzi. `GeniusRepositoryImpl.generateGeniusMixes()` dokłada te miksy do już istniejącej listy z k-means (zero zmian UI — `GeniusMixesScreen`/`GeniusMix` już renderują dowolną listę nazwanych miksów) — utwory w oknie rankowane po liczbie odtworzeń w tym oknie, z affinity jako tie-breakerem; okno pomijane całkowicie, jeśli ma mniej niż 5 utworów z historią (świeża biblioteka bez odsłuchań nie dostaje szczątkowych miksów z 1-2 utworami).
  - **Status weryfikacji — częściowy, uczciwie**: kompiluje się czysto (`:domain`, `:data`, `:app`), logika przejrzana ręcznie (typy, `mapNotNull` na brakujące utwory, próg minimalny). Nie udało się jednak potwierdzić na żywo na ekranie `GeniusMixesScreen` — ikona "Genius" w `LibraryScreen` przestała rejestrować dotknięcia pod koniec długiej sesji testowej na tym emulatorze (bez żadnego crasha/wyjątku w logcat — zweryfikowane, że proces appki nie zgłosił ani jednego błędu; analogiczne małe ikony, np. przycisk pełnego ekranu wizualizera, działały wcześniej w tej samej sesji), więc to wygląda na kwestię środowiska testowego/kolejki zdarzeń dotyku, nie na zweryfikowany błąd w tym kodzie — ale to odróżnienie samo w sobie nie jest dowodem poprawności, tylko brakiem dowodu na błąd. Wymaga dodatkowego sprawdzenia na żywo (świeży emulator/urządzenie) zanim uznamy to za w pełni zweryfikowane.

- [x] Etap 16: **naprawa ciągłości presetu + interakcja tap-to-cycle** — zgłoszony bug (użytkownik testował na żywo na telefonie): przejście z ramki inline na pełny ekran (i odwrotnie) losowało zupełnie inny preset, mimo że to ta sama "wizualizacja" w rozumieniu użytkownika. Przyczyna: natywna playlist projectM trzyma swoją pozycję WEWNĄTRZ instancji, a ramka i pełny ekran to od Etapu 9 świadomie dwie OSOBNE instancje (bo `movableContentOf` się nie sprawdziło) — nie dało się tej pozycji przenieść między nimi.
  - **Rozwiązanie — playlist natywny CAŁKOWICIE usunięty** (JNI: `playlistAddPath`/`Clear`/`SetShuffle`/`PlayNext` + link `projectM-4-playlist` w CMake — wszystko wycięte, nie tylko odłączone). Zamiast tego: `PresetLibrary.listPresets()` (`:core:projectm`) sam listuje pliki `.milk` w Kotlinie (`File.walkTopDown()`, sortowane dla stabilnej kolejności), a `ProjectMEngine.loadPresetFile()` (nowe JNI: `projectm_load_preset_file` bezpośrednio na instancji, z pominięciem playlisty) ładuje DOKŁADNIE wskazany plik. "Który plik jest aktualny" to teraz zwykły stan Compose (`currentPresetPath: String?`) **lifted do `NowPlayingScreen`**, więc ramka i pełny ekran (dwie osobne wywołania `ProjectMSurface`) dostają dokładnie tę samą wartość — stąd ciągłość. Automatyczna zmiana presetu po czasie przeniesiona z wewnętrznego timera projectM na `LaunchedEffect` w Kotlinie (`delay(presetDurationSeconds)`, kluczowany na `currentPresetPath` — każda zmiana, ręczna czy automatyczna, resetuje odliczanie).
  - **Zweryfikowane na żywo na emulatorze, ze zrzutami ekranu przed/po**: ten sam, charakterystyczny preset (pomarańczowy trójkąt + czerwony wachlarz) widoczny identycznie w ramce i po przejściu na pełny ekran — bug faktycznie naprawiony, nie tylko teoretycznie.
  - **Dodatkowe zgłoszenia załatwione przy okazji tego samego przeprojektowania**:
    - Chipy trybu (Ambient/Spectrum/Particle) i przycisk ustawień pokazują się teraz TEŻ w małej ramce inline, nie tylko na pełnym ekranie (`showModeSwitcher`/`showSettingsButton` zawsze `true` — zdjęte ograniczenie "za mało miejsca").
    - Nowy model interakcji: tap na okładce → pokaż wizualizer w ramce; KOLEJNY tap na samym wizualizerze (ramka lub pełny ekran) → następny preset z tej samej kategorii (`onTapCyclesPreset`, płynne przejście); powrót do okładki TYLKO przez jawny przycisk **X** w rogu (nowy `CloseVisualizerButton`) — stary gest "tap gdziekolwiek zwija" usunięty, bo kolidowałby z nowym "tap = następny preset".
    - Zatrzymanie odtwarzania → automatyczny powrót do trybu okładki (`LaunchedEffect(playbackState.isPlaying)` w `NowPlayingScreen`) — wizualizer nie zostaje "zawieszony" na animacji, gdy nic nie gra.
  - Świadomie NIE zrobione teraz (patrz Etap 18 niżej): "podgrzewanie" wizualizera od startu utworu (żeby tap pokazywał go bez opóźnienia) i ulubione/wagowane presety — obie rzeczy wymagają osobnego zaprojektowania, nie są prostym rozszerzeniem tej naprawy.

- [x] Etap 17: **systemowa naprawa WindowInsets** — zgłoszony bug ze zrzutem ekranu z telefonu Samsung: przyciski mini-playera chowały się pod belką nawigacji telefonu, a nowy przycisk X (Etap 16) chował się pod paskiem statusu/zegarem. Przyczyna: `enableEdgeToEdge()` (już było) rysuje appkę POD paskami systemowymi celowo (nowoczesny wygląd), ale nigdzie w kodzie nie było ANI JEDNEGO użycia `WindowInsets` do odsunięcia treści z powrotem — zero ochrony klikalnych elementów, nie tylko w jednym miejscu.
  - Naprawione w jednym miejscu dla całej appki: `MainActivity` — `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` na głównym `Surface` (jedyny wspólny korzeń wszystkich ekranów). To rozwiązuje problem wszędzie na raz, kosztem tego, że tło też jest wcięte (nie "wylewa się" pod paski systemowe) — prawdziwie premium wersja tego (tło edge-to-edge, tylko treść/przyciski chronione) wymaga wcięć per-ekran zamiast jednego globalnego, zostawione jako dopracowanie na później (patrz Etap 18).
  - **Zweryfikowane na żywo**: zrzut ekranu po naprawie pokazuje X wyraźnie POD paskiem statusu, nie pod nim.

- [ ] Etap 18 (PLANOWANE, nierozpoczęte) — zebrane zgłoszenia z tej samej rundy feedbacku, których nie dało się bezpiecznie dograć w tym samym przebiegu:
  - **Podgrzewanie wizualizera od startu utworu**: obecnie `ProjectMSurface`/silnik montuje się dopiero gdy user tapnie okładkę, co daje zauważalne opóźnienie (ekstrakcja presetów + `onSurfaceCreated` + pierwszy `loadPresetFile`). Zgłoszenie: silnik powinien zacząć się inicjować w tle OD RAZU gdy zaczyna grać utwór, tak żeby tap tylko odsłaniał już gotowy, renderujący się wizualizer. Wymaga utrzymywania ukrytej (np. `alpha=0`, `size(1.dp)` albo osobnego `Box` poza widocznym drzewem) instancji `ProjectMSurface` przez cały czas trwania Now Playing, nie tylko gdy `visualizerMode != AlbumArt` — do zaprojektowania ostrożnie, żeby nie kosztować baterii/GPU gdy user w ogóle nie zamierza otworzyć wizualizera.
  - **Ulubione presety ("serduszko")**: użytkownik chce oznaczać konkretne presety jako lubiane, żeby pojawiały się częściej. Wymaga: nowej tabeli Room (`preset_affinity` czy podobne, klucz = ścieżka/nazwa pliku presetu — UWAGA, ścieżki są per-instalacja z `filesDir`, więc kluczować raczej po relatywnej nazwie pliku niż pełnej ścieżce), UI serduszka na nakładce wizualizera, i zmiany w `PresetLibrary`/wyborze losowym żeby ważyć w stronę polubionych zamiast czystego `Random.nextInt`.
  - **Obracająca się płyta zamiast statycznej okładki**: gdy gra muzyka, kwadratowa okładka albumu miałaby się wizualnie przeobrazić w okrągłą płytę winylową i obracać w kółko; po zatrzymaniu odtwarzania wraca do zwykłego kwadratowego okienka. Ładny, ale wymaga własnej animacji kształtu (kwadrat→koło) + rotacji + zapewne innego assetu/maski niż zwykły `AsyncImage` prostokątny.
  - **Tryb "uśpienia" UI podczas oglądania wizualizera** ("wybudzanie aplikacji"): po chwili nieaktywności podczas odtwarzania wizualizera, przyciski (pełny ekran, X, itd.) i tekst poza samą wizualizacją miałyby zniknąć, a reszta tła appki rozmyć się "jak za chropowatym szkłem" — NIE instant, tylko płynne, "liquid" przejście. Wybudzenie: jedno dotknięcie ekranu w dowolnym miejscu. Dobrze komponowałoby się z Etapem 10 część 2 (tła reagujące na kolory/gatunek muzyki) — nowoczesne appki mają taki "ambient dimming". To osobny, spory kawałek UX (timery bezczynności, `Modifier.blur()` wymaga API 31+, więc potrzebny fallback dla minSdk 26, płynne animacje alpha/blur).
  - **Bug: logowanie do Google Drive nie działa** — zgłoszone ze zrzutem ekranu: po tapnięciu konta na natywnym ekranie "Wybierz konto" (Android account picker) nic się nie dzieje, flow się nie kończy. Niesprawdzone w tej rundzie (zabrakło czasu) — do zdiagnozowania w `GoogleDriveLibraryRepository`/`LibraryScreen` (`ActivityResultContracts.StartIntentSenderForResult`, `onCloudConsentResult`) przy następnej okazji; może być związane z tym, że `IntentSender`/`AuthorizationClient` flow w `play-services-auth` 22.0.0 bywa kruchy (patrz już udokumentowana w Etapie 6 zmiana `GoogleSignInClient`→`Identity.getAuthorizationClient()`).
  - **Dopracowanie WindowInsets per-ekran** (nie globalne, patrz Etap 17): tło edge-to-edge, tylko treść/kontrolki chronione wcięciami — bardziej "premium" niż obecne rozwiązanie wcinające też tło.

- [x] Etap 19: **runda zgłoszeń "prawdziwej muzyki"** — user zaczął realnie klikać po appce (Genius, equalizer, rotacja ekranu, wizualizer w małym oknie) i trafił na kilka realnych, wcześniej niewykrytych błędów (poprzednie testy operowały na małej syntetycznej bibliotece 6 utworów bez gatunków, co maskowało część z nich). Wszystko poniżej zweryfikowane na żywo na emulatorze (zrzuty ekranu + `uiautomator dump` dla dokładnych współrzędnych obszarów dotykowych, nie zgadywanie z pikseli).
  - **Kolizja przycisków wizualizera w małej ramce** — zgłoszenie ze zrzutem ekranu: ikona pełnego ekranu fizycznie nachodziła na chip "Particle" i przycisk ustawień. Przyczyna potwierdzona `uiautomator`: dwa NIEZALEŻNE systemy narożników (jeden w `NowPlayingScreen.kt`, drugi wewnątrz `ProjectMSurface.kt`) współdzieliły ten sam róg bez wiedzy o sobie. Naprawione przez jawną zasadę własności rogów, żeby kolizja była strukturalnie niemożliwa: przycisk ustawień ZAWSZE top-end, X ZAWSZE top-start (rysowane przez wołającego), przełącznik trybu ZAWSZE na dole (rysowany przez `ProjectMSurface`), ikona pełnego ekranu bottom-end TYLKO w ramce inline (gdzie dół jest wolny, bo tam przełącznik trybu jest teraz kompaktowy — patrz niżej). Panel ustawień, gdy otwarty, chowa przełącznik trybu zamiast ryzykować nakładanie.
  - **"Ambient/Spectrum/Particle w małym oknie wyglądają na zbyt duże i niedopasowane"** — pełny rząd 4 chipów tekstowych zastąpiony w ramce inline jednym kompaktowym okrągłym przyciskiem z ikoną bieżącego trybu (`Icons.Filled.Apps/BlurOn/GraphicEq/Grain`), tap otwiera Material3 `DropdownMenu` z listą trybów i haczykiem przy aktywnym. Pełny ekran zachowuje rząd chipów (tam jest miejsce).
  - **Dostępność dotyku** — kilka elementów (X, ikona pełnego ekranu, chipy trybu, przycisk ustawień) miało realny obszar dotykowy poniżej zalecanego przez Material minimum 48×48dp (np. samo X to było tylko 28dp). Podniesione wszystkie do 48dp (`Modifier.defaultMinSize`/wrapujący `Box`) bez powiększania wyglądu — touch target rośnie niewidocznie wokół małej ikonki.
  - **`Switch` w panelu ustawień "nie wygląda profesjonalnie"** — jawne `SwitchDefaults.colors(...)` na wszystkich stanach (checked/unchecked thumb/track/border) zamiast dziedziczenia domyślnego wyglądu Material3 z jednym nadpisanym kolorem.
  - **Crash appki po kliknięciu "Genius" przy większej/prawdziwszej bibliotece** — przyczyna znaleziona i potwierdzona: `GeniusMixesScreen`'s `LazyColumn` używał `key = { it.name }`, a `GeniusClustering.nameFor()` nazywa miks po dominującym gatunku klastra (`"Genius Mix: $gatunek"`) — przy bibliotece z realnie powtarzającymi się gatunkami DWA różne klastry k-means mogły dostać identyczną nazwę → duplikat klucza → twardy `IllegalArgumentException` Compose. Niewidoczne na małej testowej bibliotece (za mało utworów/gatunków, żeby klastry się kiedykolwiek powtórzyły — tam działał fallback `"Genius Mix ${index+1}"` bo dominującego gatunku brakowało). Naprawione dwutorowo: `GeniusRepositoryImpl.generateGeniusMixes()` deduplikuje nazwy przy źródle (dopisuje `(2)`, `(3)` itd. przy kolizji), a `GeniusMixesScreen` dodatkowo kluczuje `LazyColumn` po indeksie (`itemsIndexed`) jako zabezpieczenie, żeby nazwa `GeniusMix` nigdy więcej nie mogła być jedynym źródłem unikalności klucza.
  - **Equalizer "zbyt mocny", trzeszczenie głośników na dowolnym presecie** — przyczyna: kaskada 10 filtrów peaking (Q=1, rozstaw ~1 oktawa, mocno nakładające się zbocza) bez ŻADNEJ kompensacji headroomu przed sygnałem — kilka sąsiednich podbitych pasm naraz (np. "Bass Boost": +6/+5/+4/+2dB) dawało w paśmie nakładania się realnie większe wzmocnienie niż jakiekolwiek pojedyncze pasmo, sygnał regularnie przekraczał 0dBFS i trafiał w twarde `coerceIn(-1f,1f)` = trzask. Naprawione dwutorowo w `EqualizerAudioProcessor`: (1) automatyczny preamp — ujemne wzmocnienie liczone jako połowa sumy dodatnich gainów pasm, zastosowane przed twardym ograniczeniem; (2) `coerceIn` zastąpiony miękkim ograniczeniem z progiem (`softClip` — poniżej 0.85 sygnał bit-dokładnie nietknięty, powyżej płynna kompresja przez `tanh` do [0.85,1.0]) jako siatka bezpieczeństwa na resztkowe przekroczenia.
  - **Panel equalizera nieczytelny nad wizualizerem pełnoekranowym** — sam `hazeEffect`/`HazeMaterials.regular` dawał za mało krycia nad jasną, ostrą grafiką Milkdrop (tekst/suwaki zlewały się z tłem, zweryfikowane zrzutem ekranu). Dodana gwarantowana, prawie pełna nieprzezroczystość tła treści panelu (`surfaceColor.copy(alpha = 0.94f)`) NAD warstwą blur — blur wciąż widoczny na krawędziach sheeta, treść zawsze czytelna niezależnie od tego, co renderuje się pod spodem.
  - **Obrót ekranu psuje wizualizer** — `MainActivity` nie deklarował `android:configChanges`, więc każdy obrót niszczył i odtwarzał całą Activity od zera: cały natywny silnik projectM (kontekst EGL, wczytany preset) i stan Compose trzymany przez zwykły `remember` (m.in. `currentPresetPath`) znikały. Naprawione jedną linią w manifeście (`orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode`) — Compose sam poprawnie re-layoutuje się na nowe wymiary, GLSurfaceView dostaje normalny `onSurfaceChanged`, żadna dodatkowa obsługa nie jest potrzebna.
  - **Immersywny pełny ekran + brak wygaszania podczas oglądania wizualizera** (nowa prośba, wzorzec "jak fullscreen na YouTube") — w trybie Fullscreen paski systemowe (status/nawigacja) chowają się (wracają na przeciągnięcie od krawędzi, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), a `View.keepScreenOn` blokuje wygaszanie ekranu — oba przez `DisposableEffect` kluczowany na `visualizerMode`, więc zawsze poprawnie się cofają przy wyjściu z Fullscreen.
  - **Dokończone "podgrzewanie" wizualizera z Etapu 18** (pierwszy punkt tamtej listy) — `ProjectMSurface` jest teraz ZAWSZE zamontowany od pojawienia się ekranu Now Playing (nie tylko po tapnięciu), niewidoczny w trybie okładki. Po drodze złapany i naprawiony realny regres, który to podejście wprowadziło: pełnowymiarowy niewidoczny `AndroidView` leżący DOKŁADNIE na klikalnym obszarze okładki przechwytywał tapy, zanim dotarły do gestu "pokaż wizualizer" (zweryfikowane: `uiautomator` pokazywał poprawne `clickable=true` na zewnętrznym `Box`, a mimo to tap nie przełączał trybu, dopóki niewidoczna nakładka nie została fizycznie skurczona do `size(1.dp)` zamiast tylko `alpha(0f)` na pełnym rozmiarze). **Zweryfikowane na żywo dwa razy**: raz jako regres (czarny kwadrat, brak reakcji na tap), raz po naprawie (natychmiastowy render presetu, tap działa, `uiautomator` potwierdza pojawienie się X/ustawień/przełącznika trybu).
  - Świadomie NIE zrobione w tej rundzie (zostają w Etapie 18): ulubione/wagowane presety, obracająca się płyta winylowa, tryb "ambient sleep" ze znikającymi przyciskami, bug logowania Google Drive, per-ekranowe (nie globalne) WindowInsets.
  - **Nierozstrzygnięte, wymaga dalszej pracy**: zgłoszenie o presetach kategorii "losowych zamiast tematycznych" (Ambient/Particle mają pokazywać wizualnie pasujące do nazwy presety) — foldery kuratora (`Hypnotic`/`Drawing`/`Waveform`/`Particles`/`Sparkle`/`Supernova`) istnieją i mają zdrową liczbę plików (34-219 każdy, zweryfikowane), więc to NIE jest pusty/błędny mapping, tylko realna rozbieżność jakościowa między tym, jak curator paczki "Cream of the Crop" nazwał foldery a tym, czego użytkownik intuicyjnie oczekuje po nazwie "Ambient"/"Particle" — wymaga ręcznego, wizualnego przeglądu presetów per folder i ewentualnej rekuracji mappingu, nie jest to coś do naprawienia samym czytaniem kodu.

## Etap 20: Premium Hi-Fi UI — plan realizacji specyfikacji

*Źródło: `premium_hifi_player_ui_master_spec.svg` (dodany do repo 2026-09-14) — spec wizualna + "machine-readable implementation contract" dla redesignu na 6 ekranach. Poniższa synteza opiera się na 7 równoległych przeglądach luk (design system, Biblioteka+Album, Now Playing+Wizualizer, Audio Lab+EQ, Źródła chmurowe+offline, silnik Genius, architektura/nawigacja/dostępność/responsywność), każdy czytający rzeczywisty kod, nie zgadujący.*

### Podsumowanie

Spec to spójna, dopracowana wizja "premium hi-fi" — i dobra wiadomość jest taka, że appka ma pod nią więcej fundamentu niż mogłoby się wydawać z samego brakującego UI: realny silnik DSP EQ w pipeline Media3, działający lokalny silnik rekomendacji (Genius) z materializowanymi tabelami Room, spójny design-token system, i jedno w pełni zintegrowane źródło chmurowe. Reszta to w większości brakujące ekrany/nawigacja nad już istniejącymi danymi (Track ma już album/artist/genre/year), a nie brakująca logika od zera. **Jest jednak jeden twardy KONFLIKT architektoniczny, który trzeba rozstrzygnąć przed jakąkolwiek pracą nad wizualizerem**: spec opisuje tryby Ambient/Spectrum/Particle jako WŁASNY, deterministyczny render (nasz kod kontroluje reaktywność 1:1), a obecna implementacja to projectM/Milkdrop, gdzie "tryby" są wyłącznie filtrem, który podzbiór cudzych plików `.milk` się ładuje — to była świadoma decyzja podjęta PO DWÓCH odrzuconych podejściach z własnym silnikiem (Etap 7 i 8, user ocenił oba jako niewystarczające). Dopisywanie kontrolek ze speca (bass/treble sensitivity, blur) do obecnego silnika w dużej mierze nie ma efektu, dopóki renderem rządzi cudzy shader presetu — więc zanim ruszy się cokolwiek w Now Playing, user musi jawnie powiedzieć, czy Milkdrop zostaje (rekomendacja poniżej), czy appka wraca do własnego silnika po raz trzeci.

### Co już jest zrealizowane ze speca

| Obszar | Co już działa |
|---|---|
| Design system | Skala spacing 8dp-grid, promienie zaokrągleń (24dp karty, 999dp pigułki), typografia Inter z jasną hierarchią, motion 300-500ms na zmianę palety Now Playing, spring na kontrolkach wizualizera, pauza wizualizera poza widocznością — wszystko realnie egzekwowane w kodzie, nie martwe tokeny |
| Biblioteka + Album | Pływający mini-player ze shared-element transition, podświetlenie aktualnie granego utworu, scalanie lokalnej+chmurowej biblioteki w jedną listę, model `Track` ma już album/artist/genre/year jako fundament pod grupowanie |
| Now Playing + Wizualizer | Ekran z okładką/wizualizerem w jednej ramce (tap→pokaż, tap→kolejny preset, X→powrót), 3 tryby Ambient/Spectrum/Particle jako chipy, poświata (halo) reagująca na energię basu, panel ustawień z suwakami czułości/prędkości presetów, analiza audio poprawnie POZA wątkiem UI (Media3 AudioProcessor), fallback generatywny na słabszych GPU |
| Audio Lab + EQ | **Realny, działający silnik DSP** — biquad peaking EQ (RBJ cookbook) wpięty w pipeline Media3, 10 pasm, 5 presetów, auto-kompensacja headroomu + soft-clip przeciw trzaskom (naprawione w Etapie 19) |
| Źródła chmurowe + offline | Google Drive w pełni zintegrowany (OAuth, streaming przez token, mapowanie na `Track`), hash id ze świadomym dyskryminatorem źródła żeby przyszłe źródła się nie kolidowały |
| Silnik Genius | Materializowane tabele Room (`TrackAffinity`, `TrackCooccurrence`) aktualizowane przyrostowo, affinity liczone z play/skip, cooccurrence kierunkowa z wagą wg completion + wygaszaniem recency, scoring łączący affinity+cooccurrence+kontekst pory dnia, dywersyfikacja artysty/albumu w wyniku |
| Architektura/nawigacja/a11y | Kotlin+Compose+M3, MVVM+StateFlow, podział modułowy app/core:designsystem/core:projectm/domain/data zgodny ze spec, Room jako trwały magazyn historii/rekomendacji, zamknięta pętla PlayEvent→SkipEvent→Affinity, zero reklam/chmury w silniku rekomendacji, 48dp touch target punktowo już wdrożony (Etap 19) |

### Priorytetyzowany plan realizacji

- [ ] **Etap 20a — Higiena dostępności i tokenów w całej appce** (tanie, bez decyzji architektonicznych, rób od razu)
  - `MiniPlayerBar` 36dp→48dp touch target (dosłownie jedna liczba, ale to najczęściej widoczny element UI w appce).
  - Uogólnienie dzisiejszego, punktowego `.defaultMinSize(48.dp)` (NowPlayingScreen/ProjectMSurface) na współdzielony komponent `AuroraIconButton` w `core:designsystem` (owijka M3 `IconButton`, który daje 48dp "za darmo"), i rollout do `LibraryScreen.kt` (dziś klikalne ikony 24-26dp), `EqualizerSheet.kt`, `GeniusMixesScreen.kt` — dziś zero użyć `IconButton(` w całym repo.
  - Podłączenie `AuroraMotion.bouncy()/smooth()` (zdefiniowane, ale nieużywane) w `AmbientGlow.kt` i `VisualizerBars.kt`, które dziś ręcznie duplikują te same parametry `spring()` — czysty refaktor, zero zmiany zachowania.
  - Przy okazji: dodać promień 16dp do `AuroraShapes` i zamienić ad-hoc `14.dp` w `VisualizerSettingsPanel.kt`/`ProjectMSurface.kt` na token.
  - **Zakres:** `core/designsystem/.../theme/{Shape,AuroraTokens}.kt`, `core/designsystem/.../components/{MiniPlayerBar,AuroraIconButton(nowy)}.kt`, `core/projectm/.../{AmbientGlow,VisualizerBars,VisualizerSettingsPanel,ProjectMSurface}.kt`, `app/.../library/LibraryScreen.kt`, `app/.../eq/EqualizerSheet.kt`, `app/.../genius/GeniusMixesScreen.kt`.

- [ ] **Etap 20b — Powłoka nawigacyjna (blocker dla większości reszty planu)**
  - `NavigationBar` (bottom nav): Biblioteka / Teraz odtwarzane / Radio AI (przemianowana ikona Genius) / Ustawienia.
  - Nowy ekran Ustawienia — nawet minimalny na start, żeby zakładka miała gdzie prowadzić.
  - Rozszerzenie `AuroraNavHost` o trasy dla przyszłych ekranów drugorzędnych (album/artist/queue/audio_lab/cloud_sources/track_details), nawet jeśli część na razie prowadzi do zaślepek — żeby kolejne etapy (20c, 20f, 20h) nie musiały retrofitować nawigacji z dzisiejszego wzorca ręcznych callbacków.
  - **Dlaczego teraz:** dziś appka ma tylko 4 trasy stosu okablowane ręcznie, zero `NavigationBar` w repo i zero ekranu Ustawień — to wprost blokuje "zamieszkanie" Audio Lab, Cloud Sources, Album i innych ekranów planowanych niżej. Koszt niski: `androidx.navigation.compose` już jest zależnością.
  - **Zakres:** `app/.../navigation/AuroraNavHost.kt`, nowy `app/.../settings/SettingsScreen.kt`, `MainActivity.kt` (Scaffold z `bottomBar`).

- [ ] **Etap 20c — Biblioteka: wyszukiwarka + ekran Albumu + grupowanie**
  - Pasek wyszukiwania — filtr w pamięci po `allTracks`, zero zmian w warstwie danych.
  - `AlbumScreen.kt` — grupowanie po `Track.album` (pole już istnieje), przycisk "odtwórz cały album", nawigacja z listy utworów ("idź do albumu"). To odblokowuje sensowną nawigację "w głąb" biblioteki, której dziś nie ma wcale (klik na utwór tylko go odtwarza).
  - `ArtistsScreen`/`AlbumsScreen`/`GenresScreen` jako warianty tego samego wzorca grupowania po `artist`/`album`/`genre` — naturalna kontynuacja Album screen, bez nowej infrastruktury danych.
  - **Dlaczego w tej kolejności:** Album screen ma nieproporcjonalnie duży wpływ (odblokowuje "idź do albumu" z każdego innego miejsca), a cała potrzebna informacja już jest w `Track` — to przebudowa UI na gotowych danych, nie nowa funkcja.
  - **Zakres:** `app/.../library/{LibraryScreen,LibraryViewModel}.kt`, nowe `app/.../library/{AlbumScreen,ArtistsScreen,AlbumsScreen,GenresScreen}.kt`, trasy z 20b.

- [ ] **Etap 20d — EQ: rozszerzenie modelu + trwałość (blocker dla UI EQ)**
  - `EqBand`: dodać `filterType` (PEAK/LOW_SHELF/HIGH_SHELF/LOW_PASS/HIGH_PASS), `q`, `enabled` per pasmo.
  - `BiquadFilter`: dopisać formuły RBJ dla shelf/low-pass/high-pass obok już zaimplementowanego peaking — plik już cytuje ten sam cookbook, to ograniczone ryzykiem rozszerzenie, nie budowa od zera.
  - Trwały zapis EQ/presetów w Room — już zapowiedziany w komentarzu kodu jako "Etap 3, dojdzie razem z tabelami Genius", ale nigdy zrobiony; `EqRepositoryImpl` dziś trzyma stan tylko w pamięci.
  - **Dlaczego teraz:** UI graficzne z przeciąganymi punktami, A/B, zapis presetu i profil słuchawek (spec) wszystkie zależą od tego modelu — bez tego "Zapisz preset" byłby funkcją cicho gubiącą dane po restarcie appki.
  - **Zakres:** `domain/.../model/EqState.kt`, `app/.../eq/{BiquadFilter,EqualizerAudioProcessor,EqRepositoryImpl}.kt`, nowa encja/DAO w `data/.../database/`.

- [ ] **Etap 20e — EQ UI + szybkie wygrane Genius** (niezależne od siebie, mogą iść równolegle)
  - `EqualizerSheet`: wykres z przeciąganymi punktami (Canvas+krzywa) zamiast 10 suwaków, zakładki Parametryczny/Graficzny/Presety, porównanie A/B, zapis własnego presetu, profil słuchawek — wszystko na bazie modelu z 20d.
  - Genius: **"avoid recently skipped tracks"** — `SkipEventDao` jest dziś zapisywane, ale nigdy odczytywane przez `GeniusRepositoryImpl`; jeden dodatkowy query + filtr, żaden nowy schemat. Najwyższy ROI z całego obszaru Genius.
  - Genius: kontekst dnia tygodnia — `dayOfWeek` jest już zbierane na każdym `PlayEvent`/`SkipEvent`, ale nigdy czytane; naturalne rozszerzenie istniejącego mechanizmu godzinowego (`GeniusTimeContext`).
  - **Zakres:** `app/.../eq/EqualizerSheet.kt`; `data/.../genius/GeniusRepositoryImpl.kt`, `domain/.../genius/{GeniusScoring,GeniusTimeContext}.kt`.

- [ ] **Etap 20f — Naprawa Google Drive + minimalny ekran "Źródła muzyki"**
  - Zdiagnozować i naprawić flow logowania Google Drive — zgłoszony, niesprawdzony bug z Etapu 18 (tap na koncie w natywnym pickerze nic nie robi, flow się nie kończy).
  - `CloudSourcesScreen` z tylko 2 realnymi kartami (Google Drive + Ten telefon, z licznikami), reszta (OneDrive/Dropbox/NAS) wyszarzona/"wkrótce".
  - **Dlaczego w tej kolejności:** rozbudowa o kolejnych dostawców na bazie flow logowania, który sam dziś nie działa na żywo, powielałaby ten sam błąd 3-4 razy zamiast raz go naprawić. Ekran nawet z 2 źródłami to tani, wysoki-ROI krok zgodności ze spec.
  - **Zakres:** `app/.../cloud/GoogleDriveLibraryRepository.kt`, `app/.../library/LibraryScreen.kt`, nowy `app/.../cloud/CloudSourcesScreen.kt`, trasy z 20b.

- [ ] **Etap 20g — Wizualizer: tanie usprawnienia w obecnym kierunku (Milkdrop)** — dopiero PO decyzji z sekcji "Pytania" niżej
  - FPS/battery saver: zamiana `RENDERMODE_CONTINUOUSLY` na throttlowany render + jeden przełącznik w panelu.
  - Rozszerzenie `AmbientGlow` o `midEnergy`/`trebleEnergy` — dane już istnieją w `VisualizerFrame`, to zmiana sygnatury + wag, żaden nowy DSP.
  - Ograniczenie `AmbientGlow` wyłącznie do trybu Ambient (dziś renderowany globalnie we wszystkich trybach, wbrew intencji speca).
  - **Świadomie NIE w tym etapie:** LUFS/peak, info o DAC/formacie (FLAC/24-bit/96kHz) — to nie są dopiski UI, tylko osobny kawałek pracy (patrz 20h).
  - **Zakres:** `core/projectm/.../{ProjectMSurfaceView,AmbientGlow,VisualizerSettingsPanel}.kt`, `app/.../nowplaying/NowPlayingScreen.kt`.

- [ ] **Etap 20h — Duże, odłożone kawałki** (każdy wymaga osobnej rundy i osobnej decyzji, patrz niżej)
  - Metadane audio na `Track` (codec/bitDepth/sampleRate/bitrate/isAvailableOffline/sourceId/remoteId) — prerekwizyt blokujący zarówno Audio Lab jak i Offline; potem Audio Lab samo (kolejność: karta ŹRÓDŁO → łańcuch sygnału → Wyjście/DAC/bit-perfect na samym końcu, bo to najbardziej ryzykowna część zgodnie z ostrzeżeniem samej speca).
  - Multi-cloud: interfejs `MusicSource` + `SourceType` enum + OneDrive/Dropbox/WebDAV/NAS + stany offline (`ONLINE_ONLY`/`DOWNLOADING`/`OFFLINE_AVAILABLE`/`ERROR`) + wznawialne pobrania przez WorkManager. Refaktor `CloudLibraryRepository`→`MusicSource` warto zrobić RAZEM z pierwszym nowym dostawcą, nie osobno wcześniej.
  - Playlists/Favorites jako pierwszoklasowe encje domenowe (nowe tabele Room+DAO) — blokuje zakładki Playlisty/Ulubione i "source playlist" w cooccurrence Genius.
  - LUFS/peak metering — realny DSP (klasa ITU-R BS.1770/EBU R128), zero istniejącego kodu dziś.
  - Materializacja rankingu Genius (nie tylko affinity/cooccurrence) w Room+WorkManager — dziś liczona na żywo przy każdym wejściu na ekran.
  - WindowSizeClass/tablet layout.
  - **Zakres:** rozproszony po `domain/.../model/Track.kt`, nowe moduły/repozytoria per dostawca, `data/.../database/AuroraDatabase.kt` (nowe tabele), `core:projectm`/`app` DSP.

### Pytania do usera / decyzje architektoniczne

1. **KONFLIKT — silnik wizualizera.** Zostajemy przy projectM/Milkdrop (obecne, świadomie wybrane po dwóch odrzuconych podejściach z własnym silnikiem — Etap 7 i 8) i traktujemy Ambient/Spectrum/Particle jako kuratorowane kategorie nastroju, czy appka ma iść w stronę literalnego kontraktu ze speca (własny, w pełni kontrolowany render per tryb)? Rekomendacja: zostać przy Milkdrop, chyba że jest świeży sygnał od usera, że obecny wygląd go nie satysfakcjonuje — dwukrotne odrzucenie własnego silnika to silny sygnał w drugą stronę.
2. **Paleta kolorów.** Nowy spec narzuca inną rodzinę hex (`#050713` granatowo-fioletowa, jawny trzeci poziom Surface/Elevated/Border, akcent `#9B7CFF`) niż obecna, wdrożona i przetestowana paleta z DESIGN.md 2.1 (`#0A0A0F`, akcent `#6C5CE7`, hierarchia przez alfę tekstu). Czy spec ma realnie zastąpić Color.kt (przemalowanie wszystkich 6 ekranów), czy to tylko inspiracja mockupu, a obecna paleta zostaje?
3. **Które chmury faktycznie wchodzą w grę?** OneDrive/Dropbox wymagają rejestracji aplikacji deweloperskiej i OAuth per dostawca (to już był znany blocker w Etapie 12 DESIGN.md) — NAS/WebDAV nie wymaga konta chmurowego i jest prostszym protokołem, dobrym kandydatem na "drugie źródło" do testowania abstrakcji `MusicSource`. Ile z wymienionych w spec (Google Drive/OneDrive/Dropbox/NAS-WebDAV/iCloud/pCloud/Box/Plex/Jellyfin) user faktycznie chce realizować, a nie tylko pokazywać jako "wkrótce"?
4. **Tablet/WindowSizeClass.** Appka jest dziś telefon-first, jednodostawcowa. Czy inwestować w layout tabletowy bez fizycznego tabletu/emulatora do testowania na żywo, czy odłożyć do konkretnej potrzeby?
5. **Audio Lab / "bit-perfect".** Spec explicite ostrzega, żeby nigdy nie twierdzić "bit-perfect" bez pewności i rozdzielić dostępne/aktywne. W repo nie ma dziś żadnej integracji z `AudioDeviceInfo`/routingiem, która mogłaby to zweryfikować. Czy zaczynać tę część bez realnego DAC/urządzenia do testu na żywo, czy poczekać?
6. **Playlists/Favorites.** To nowa, samodzielna funkcja domenowa (nowe tabele, nie przeróbka UI) — kiedy ją priorytetyzować względem reszty planu (blokuje zakładki ze speca i "source playlist" w Genius)?
7. **LUFS/peak metering** — realny nowy DSP bez dzisiejszego fundamentu. Wart inwestycji teraz, czy odłożyć razem z resztą Audio Lab?
8. **Kolejność Google Drive → multi-cloud** (Etap 20f przed 20h) — potwierdzić, że user się zgadza z naprawą istniejącego bugu logowania przed dokładaniem kolejnych dostawców, zamiast odwrotnie.
9. **Materializacja rankingu Genius w tle (WorkManager).** Robić prewencyjnie teraz, czy czekać na realny sygnał wydajnościowy (appka działa dziś na niewielkiej skali, brak dowodów na problemy z wydajnością)?

### Jak zbudować / uruchomić

```bash
./gradlew.bat :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# albo od razu na podłączone urządzenie/emulator:
./gradlew.bat :app:installDebug
```

## Etap 21: Architektura wyjścia audio — przygotowanie pod DAC

*Źródło: propozycja usera (interfejs `AudioOutput` jako przygotowanie appki pod zewnętrzne DAC-i/USB/BLE/własny przyszły sprzęt "Aurelis") skonfrontowana z 3 równoległymi analizami: (1) rzeczywisty kod pipeline'u audio Media3, (2) research możliwości Android SDK dot. USB DAC/routingu/bit-perfect, (3) wpływ na już zaplanowany Etap 20 i precedens architektoniczny w tym projekcie. Status: **decyzja architektoniczna — kod poniżej jeszcze NIE jest napisany**, patrz "Status" na końcu sekcji.*

### Propozycja i werdykt

User zaproponował interfejs `AudioOutput` (`capabilities`/`connect`/`disconnect`/`setVolume`/`setSampleRate`/`setGain`/`setFilter`) z implementacjami `LocalOutput`/`UsbAudioOutput`/`BluetoothOutput`/`ExternalDeviceOutput` (docelowo `AurelisDacOutput`), żeby appka nie musiała przepisywać playera, gdy pojawi się realny sprzęt.

**Werdykt: sam interfejs — tak, teraz. Konkretne protokoły dla nieistniejącego sprzętu — nie.** Uzasadnienie z realnego kodu, nie teorii:

- `PlayerRepository`/`PlayerController` dziś **nie mają żadnej metody volume/routing** (`PlayerRepository.kt:7-16` eksponuje wyłącznie transport: `playQueue/togglePlayPause/seekTo/skipToNext/skipToPrevious`; grep `Volume|volume` po całym `app/src/main/kotlin` — 0 wyników). Dodanie `AudioOutput` **nie wymaga przepisywania niczego istniejącego** — nie ma z czego "odklejać" volume/routing, bo nigdy tam nie było. To dokładnie sytuacja, w której tania abstrakcja jest faktycznie tania, nie tylko z nazwy.
- Android SDK realnie coś tu daje za darmo, samym publicznym API, zero własnego sprzętu: `AudioManager.getDevices()` + `AudioDeviceCallback` (detekcja podłączenia/odłączenia USB DAC, API 23+, bez dotykania `UsbManager`) i `ExoPlayer.setPreferredAudioDevice()` (Media3 miało to już w 1.1.1, repo ma `media3 = "1.5.0"` — `gradle/libs.versions.toml:9` — więc dostępne bez podnoszenia wersji). `LocalAudioOutput` i w przyszłości `UsbAudioOutput` mają więc realną treść do włożenia, to nie są z góry puste zaślepki.
- Rozstrzygające ograniczenie: `setGain`/`setFilter`/`setSampleRate` jako coś więcej niż deklarowany brak wsparcia nie mają dziś czystej ścieżki w publicznym SDK. Sterowanie analogowym gainem/filtrem cyfrowym DAC-a (feature-unit USB Audio Class) jest zamknięte wewnątrz audio policy/HAL — appka nie ma jak wysłać takiego żądania bez własnego kanału HID/vendor-specific na przyszłym sprzęcie. Realny `setSampleRate` koliduje z tym, że do Android 13 włącznie **nie istnieje** systemowy tryb exclusive/bit-perfect — `AudioFlinger` miksuje i resampluje wszystko bez pytania appki i bez informowania jej, co realnie dotarło do DAC-a. Jedyny wyjątek to `AudioManager.setPreferredMixerAttributes(MIXER_BEHAVIOR_BIT_PERFECT)` od **Android 14, wyłącznie USB**, z niepewnym wsparciem producentów telefonów. To dokładnie pokrywa się z ostrzeżeniem już zapisanym w `premium_hifi_player_ui_master_spec.svg` i zacytowanym w Etapie 20 (pytanie 5, linia 707) — appka nie powinna niczego tu obiecywać na sztywno.
- Pisanie realnych `UsbAudioOutput`/`BluetoothOutput`/`ExternalDeviceOutput`/`AurelisDacOutput` (protokół BLE, HID, OTA firmware) **teraz** oznacza kod, którego nie da się zweryfikować na żywo — sprzeczne z zasadą, na której stoi reszta tego dziennika (Etap 14/16/19 — każda zmiana potwierdzona na emulatorze/telefonie, nigdy samą kompilacją). Sprzeczne też ze STYLEM tego projektu: dokładnie ta sama sytuacja (interfejs na wiele przyszłych implementacji, dziś realna jedna) już się zdarzyła przy `MusicSource`/multi-cloud i projekt **dwukrotnie świadomie odłożył** pełną abstrakcję do momentu, gdy druga implementacja jest naprawdę blisko (Etap 13, linia 576; Etap 20h, linia 694) zamiast budować ją "na zapas". `AudioOutput` z trzema implementacjami-widmami dla urządzeń spoza najbliższej kolejki powtórzyłby błąd, którego ten projekt już się nauczył unikać.

### Projekt interfejsu (co robimy teraz)

**Miejsce — bez nowego modułu Gradle.** Repo ma dziś 5 modułów (`app`, `core:designsystem`, `core:projectm`, `domain`, `data` — `settings.gradle.kts`); `core:designsystem`/`core:projectm` istnieją tylko dlatego, że owijają realną, sporą technologię (współdzielony design system, natywny projectM). Ani jedno z 6 istniejących repozytoriów (`PlayerRepository`, `EqRepository`, `CloudLibraryRepository`, `TrackRepository`, `GeniusRepository`, `PlaybackHistoryRepository`) nie dostało własnego modułu — interfejs zawsze w `domain`, jedyna implementacja zawsze w `app`, bindowanie przez Hilt `@Module` w `app/.../di/`. Nowy `core:audiooutput` dla jednej implementacji byłby dziś przedwczesną ceremonią infrastrukturalną.

- **Interfejs** — nowy plik `domain/src/main/kotlin/com/aurora/player/domain/audio/AudioOutput.kt` (nowy pakiet `audio/`, osobny od `repository/`: to sterowanie urządzeniem z `connect()/disconnect()`, nie repozytorium danych — żadne z 6 istniejących repo nie ma tej semantyki, więc odejście od sufiksu `*Repository` jest tu świadome, nie przeoczenie; warto to zaznaczyć w KDoc pliku, tak jak `CloudLibraryRepository.kt:7-9` już tłumaczy swoje własne odstępstwo od normy modułu).
- **`domain` zostaje czystym Kotlinem** — ten sam wymóg, który `CloudLibraryRepository.kt:7-9` dokumentuje dla `signIn()` — więc `AudioCapabilities`/`Gain`/`DacFilter` to zwykłe klasy Kotlin, zero `android.media.AudioDeviceInfo` w sygnaturze interfejsu:

  ```kotlin
  package com.aurora.player.domain.audio

  /** Zdolności JEDNEGO wyjścia audio. Domyślnie same "nie" — świadomy model
   *  realnych ograniczeń platformy (patrz DESIGN.md Etap 21), nie skrót do
   *  wypełnienia później. Audio Lab (Etap 20h) ma czytać ten model wprost
   *  zamiast wymyślać własny status DAC-a. */
  data class AudioCapabilities(
      val deviceName: String? = null,
      val supportsVolumeControl: Boolean = false,
      val supportsSampleRateControl: Boolean = false,
      val supportsGainControl: Boolean = false,
      val supportsFilterControl: Boolean = false,
      val bitPerfectStatus: BitPerfectStatus = BitPerfectStatus.UNKNOWN,
  )

  enum class BitPerfectStatus { UNKNOWN, LIKELY_RESAMPLED, GUARANTEED_BY_SYSTEM }

  enum class DacFilter { UNSUPPORTED } // realne warianty dopiero z pierwszym urządzeniem, które je definiuje
  data class Gain(val value: Float)     // jednostka/zakres nieznane bez realnego DAC-a

  interface AudioOutput {
      val capabilities: AudioCapabilities
      suspend fun connect(): Boolean
      suspend fun disconnect()
      suspend fun setVolume(value: Float)
      suspend fun setSampleRate(sampleRate: Int): Boolean  // false = zignorowane, nigdy cicha nieprawda
      suspend fun setGain(gain: Gain): Boolean
      suspend fun setFilter(filter: DacFilter): Boolean
  }
  ```

  Różnica względem propozycji usera: operacje, których platforma nie gwarantuje (`setSampleRate`/`setGain`/`setFilter`), zwracają `Boolean` zamiast `Unit` — żeby `AudioOutput` fizycznie nie mogło udawać sukcesu, którego nie ma. To ostrzeżenie usera o "bit-perfect" zastosowane konsekwentnie do całego interfejsu, nie tylko do jednego pola.

- **Implementacja** — `LocalAudioOutput` w `app/src/main/kotlin/com/aurora/player/playback/LocalAudioOutput.kt`, obok `PlayerController.kt` (nie jako metoda w samym `PlayerController` — to osobny komponent, nie część transportu). Owija to, co appka i tak robi dziś milcząco: `connect()` zawsze `true` (domyślne wyjście systemowe jest zawsze "podłączone"), `setVolume()` przez `AudioManager`/`STREAM_MUSIC` (nie wymaga referencji do `ExoPlayer` — głośność systemowa jest niezależna od instancji playera), reszta `capabilities` = `false`/`UNKNOWN`.
- **DI** — nowy `app/src/main/kotlin/com/aurora/player/di/AudioOutputModule.kt`, dokładnie wzorzec `EqModule.kt`/`CloudModule.kt`: `@Binds abstract fun bindAudioOutput(impl: LocalAudioOutput): AudioOutput`.
- **Podłączenie do pipeline'u — `PlaybackService`, NIE `PlayerController`.** `PlayerController` (`app/.../playback/PlayerController.kt:40-69`) nie trzyma `ExoPlayera` — tylko `MediaController` połączony z `PlaybackService` przez `SessionToken`. Realny silnik audio (ExoPlayer/AudioSink) żyje w `PlaybackService.onCreate()` (`app/.../playback/PlaybackService.kt:43-72`), które już dziś wstrzykuje `eqRepository`/`visualizerAnalyzer`/`cloudLibraryRepository` tym samym wzorcem `@Inject lateinit var`. `audioOutput: AudioOutput` dołącza do tej samej listy — zero nowej infrastruktury.
- **DSP-owa część łańcucha** (żeby diagram Plik→DSP→System→DAC miał gdzie rosnąć) — `EqualizerRenderersFactory.buildAudioSink()` (`app/.../eq/EqualizerRenderersFactory.kt:23-27`) ma dziś sztywne `setAudioProcessors(arrayOf(equalizerAudioProcessor))`, jednoelementową tablicę. Zmiana konstruktora na `audioProcessors: List<AudioProcessor>` + `.setAudioProcessors(audioProcessors.toTypedArray())` to trywialny, zero-ryzykowny refaktor, który otwiera węzeł DSP na kolejne procesory (np. przyszły ReplayGain z Etapu 20h) — Media3 już to obsługuje, `EqualizerAudioProcessor` to dowodzi.

### Wpływ na Etap 20 (Audio Lab)

Etap 20h już poprawnie sekwencjonuje kartę "Wyjście/DAC/bit-perfect" na sam koniec Audio Lab i już cytuje właściwe ostrzeżenie speca — ta kolejność się nie zmienia. Dwie rzeczy do doprecyzowania w świetle tej decyzji:

1. **Karta DAC w Audio Lab powinna czytać `AudioOutput.capabilities`, nie wymyślać własny status od zera.** Budując interfejs teraz (nawet z jedną, lokalną implementacją), Audio Lab z Etapu 20h dostaje gotowy, uczciwy kontrakt danych (`AudioCapabilities.bitPerfectStatus`) zamiast musieć go zaprojektować w momencie, gdy plan w końcu dojdzie do tego punktu.
2. **Pytanie 5 z Etapu 20 (linia 707: "czy zaczynać bez realnego DAC-a do testu na żywo") dostaje tu częściową odpowiedź, nie pełną**: tak dla samego interfejsu + `LocalAudioOutput` (nie wymaga żadnego DAC-a — to model dzisiejszego, milczącego zachowania systemu). Nadal nie dla realnego `UsbAudioOutput`/routingu — to czeka na fizyczne urządzenie (choćby najtańszy generyczny dongle USB-C), zgodnie z pierwotną intencją tego pytania.
3. `BitPerfectStatus.GUARANTEED_BY_SYSTEM` może w tym projekcie nigdy nie zostać osiągnięty, jeśli user odpowie "nie" na pytanie niżej — Audio Lab pokazywałby wtedy wyłącznie `UNKNOWN`/`LIKELY_RESAMPLED`, nigdy zielony checkmark. To w pełni poprawny, uczciwy stan końcowy, nie brakująca funkcja.

### Świadomie NIE teraz

- **`UsbAudioOutput`/`BluetoothOutput`/`ExternalDeviceOutput` jako realne implementacje** — nawet ta część, którą samo SDK by pozwoliło zrobić (detekcja + wybór urządzenia przez `AudioManager`/`ExoPlayer.setPreferredAudioDevice`) jest technicznie gotowa do napisania, ale **niemożliwa do zweryfikowania na żywo bez fizycznego USB DAC-a podłączonego do telefonu**. To nie jest "protokół dla nieistniejącego urządzenia" w tym samym sensie co Aurelis (generyczny DAC USB Audio Class to sprzęt z półki, nie coś do zaprojektowania) — ale dopóki nie ma choćby najtańszego dongla do testu, kod zostaje niezweryfikowany, więc zgodnie z zasadą tego projektu ("zweryfikowane na żywo", nie samą kompilacją) nie wchodzi teraz.
- **`setGain`/`setFilter` jako coś więcej niż `false`** — feature-unit USB Audio Class (analogowy gain, przełącznik filtra cyfrowego chipu DAC) nie ma czystej ścieżki w publicznym Android SDK; jedyna droga to osobny kanał HID/vendor-specific na przyszłym własnym sprzęcie (Aurelis) albo BLE GATT — oba wymagają specyfikacji urządzenia, której dziś nie ma.
- **`setSampleRate` jako realnie działająca funkcja** — do Android 13 nie istnieje systemowy tryb bit-perfect/exclusive; jedyny wyjątek (`setPreferredMixerAttributes`, Android 14+, tylko USB) jest warunkowy i niepewny co do wsparcia producentów telefonów — do zaimplementowania dopiero razem z realnym testem na konkretnym telefonie+DAC-u.
- **Cały przyszły własny sprzęt (`AurelisDacOutput`)**: protokół komunikacji (BLE GATT custom service albo HID/vendor-specific USB), OTA firmware (BLE/USB DFU), panel sterowania AMP-em — czysta koncepcja/dokumentacja, zero kodu. Nie ma urządzenia, nie ma firmware'u, nie ma specyfikacji do zweryfikowania — pisanie tego kodu teraz byłoby zgadywaniem kontraktu, który i tak trzeba będzie przeprojektować, gdy powstanie pierwszy prototyp.

### Pytanie do usera

Research (raport 2) pokazuje, że nawet w najlepszym możliwym scenariuszu (Android 14+, wyjście USB, wsparcie producenta telefonu) "bit-perfect" byłby warunkowym, rzadkim stanem — a przy `minSdk 26` (sekcja 1.3) zdecydowana większość userów nigdy nie zobaczy ścieżki, która to gwarantuje. Czy Audio Lab ma w ogóle KIEDYKOLWIEK próbować wykrywać/deklarować "bit-perfect: TAK" (warunkowo, tylko gdy system to faktycznie gwarantuje przez `setPreferredMixerAttributes`), czy appka ma świadomie nigdy nie pokazywać zielonego stanu bit-perfect i ograniczyć się wyłącznie do przezroczystego "system może miksować/resamplować" niezależnie od wersji Androida — innymi słowy: czy `BitPerfectStatus.GUARANTEED_BY_SYSTEM` w ogóle powinien istnieć jako osiągalna wartość w tym kodzie?

### Status

**Zaimplementowane w kolejnej rundzie:** `AudioOutput.kt` (`domain/.../domain/audio/`), `LocalAudioOutput.kt` (`app/.../playback/`), `AudioOutputModule.kt` (`app/.../di/`), oraz zmiana `EqualizerRenderersFactory`/`PlaybackService` dokładnie wg projektu opisanego wyżej — `PlaybackService` woła `audioOutput.connect()` w `onCreate()`/`disconnect()` w `onDestroy()` przez istniejący `@ApplicationScope` (ten sam wzorzec co `PlayerController`/`SleepTimerController`).

`:domain:compileKotlin` i `:app:compileDebugKotlin` (`--rerun-tasks`, bez cache) — oba zielone. (Pierwsza próba tej samej sesji pokazała błędy w `PlaylistDetailScreen.kt`/`PlaylistsScreen.kt`/`QueueScreen.kt` — okazały się przejściowym artefaktem cache'u inkrementalnej kompilacji Kotlina po `git stash`/`stash pop` w tej samej sesji, nie realnym stanem repo; wymuszony rebuild bez cache to potwierdził.) Weryfikacja na żywo na telefonie/emulatorze (że głośność nadal działa identycznie jak dziś) wciąż czeka na kolejną rundę z dostępem do urządzenia.

## Etap 22: Podstawowe funkcje odtwarzacza zapisane w wizji, nigdy nie zbudowane — kolejka, timer snu, ulubione, playlisty

*Źródło: pytanie usera "co ma Spotify/inne appki, czego brakuje u nas" — porównanie z sekcją 3.1/3.2 tego dokumentu (pierwotna wizja appki, sesja 1) i z faktycznym stanem kodu.*

### Diagnoza

Cztery funkcje były zapisane w projekcie UI appki OD PIERWSZEJ SESJI: sekcja 3.1 (linia 141) — taby "Utwory/Albumy/Wykonawcy/**Playlisty**"; sekcja 3.2 (linia 161) — menu "..." z "**timer snu**"; (linia 166) — "**kolejka odtwarzania**" jako ikona dolnego paska Now Playing; (linia 131/163) — przycisk serca/**ulubione**. Żadna z nich nigdy nie została zaimplementowana. Co ważniejsze: Etap 20 (analiza luk względem `premium_hifi_player_ui_master_spec.svg`) był skoncentrowany na TYM nowym dokumencie, nie porównywał się z oryginalną wizją z sekcji 3 — więc te rzeczy wypadły z aktywnego trackingu, mimo że leżą zapisane w tym samym pliku od początku. Playlisty jedyne z nich trafiły z powrotem do planu (Etap 20h, jako "duży odłożony kawałek"), pozostałe trzy nie miały żadnego wpisu w Etapie 20 w ogóle.

Potwierdzone w kodzie (grep `NowPlayingScreen.kt` na `Queue|Kolejka|Favorite|Ulubion|Timer`): zero wystąpień — żadna z tych funkcji nie istnieje dziś, nie tylko brakuje jej dopracowania.

### Co dokładnie brakuje

1. **Ekran kolejki** — silnik JUŻ DZIAŁA (`PlayerController.playQueue`, `skipToNext/Previous` przez `MediaController.seekToNext/PreviousMediaItem` — patrz Etap 3), brakuje wyłącznie UI: lista "co dalej", reorder (drag), "usuń z kolejki", "dodaj następny" vs "dodaj na koniec" z menu utworu. Najtańsza z czterech — dane już płyną przez `PlaybackState`, to czysty widok nad gotowym stanem.
2. **Timer snu** — zatrzymanie odtwarzania po N minutach (albo "po końcu utworu"). Zero istniejącego kodu. Prosty do zrobienia: coroutine `delay` + `playerRepository.togglePlayPause()`, stan w `PlayerController` albo małym osobnym repo. UI: opcja w menu "..." Now Playing (5/15/30/60 min, "koniec utworu").
3. **Ulubione (serce)** — wymaga nowej tabeli Room (`FavoriteEntity(trackId, addedAt)`), DAO, use case, ikona serca w Now Playing + liście utworów. Naturalnie zasila też Genius jako dodatkowy, jawny sygnał (silniejszy niż samo dosłuchanie, które już liczy `TrackAffinity`).
4. **Playlisty** — już w planie jako Etap 20h, bez zmian tutaj (nowa encja, największy koszt z czwórki, bo to nowy koncept domenowy, nie nakładka na istniejący `Track`).
5. **Menu "..." per utwór** — hub łączący punkty 1/3/4 (dodaj do kolejki, dodaj do playlisty, ulubione, idź do albumu, informacje o pliku) — jeden reużywalny komponent (lista biblioteki, Now Playing, przyszły ekran Albumu z Etapu 20c).

### Rekomendowana kolejność

Kolejka i Timer snu **przed** Playlistami/Ulubionymi — nie wymagają nowego schematu Room (kolejka już istnieje w pamięci, timer to czysta logika czasowa), więc to dopisanie UI nad gotowym fundamentem, zero ryzyka migracji. Ulubione i Playlisty razem (ta sama kategoria: nowe tabele + DAO), po Etapie 20b (nawigacja), skoro obie docelowo chcą własnych zakładek.

### Świadomie NIE w planie

Sync między urządzeniami, Chromecast, podcasty, publiczne udostępnianie — wymagałyby konta/backendu, którego appka celowo nie ma (ta sama zasada "100% lokalnie" co przy silniku Genius). Jeśli to się kiedyś zmieni, to osobna, świadoma decyzja produktowa usera, nie naturalne rozszerzenie tego planu.

## Etap 23: Implementacja Etapu 22 (kolejka, timer snu, ulubione, playlisty) + podgląd Geniusa + ekran startowy na poziomie Spotify

*Źródło: "Stwórz nam brakujące ekrany aplikacji, budowanie playlist, podgląd playlist dla Geniusa, rozbuduj apkę o to co w 22 etapie" + "ekran startowy chcemy mieć taką użyteczność bądź wyższą niż spotify" (ze zrzutem ekranu Spotify).*

### Co zbudowano

**Warstwa danych (Room, `aurora.db` v3→v4):** `PlaylistEntity`+`PlaylistTrackEntity` (pozycja jako `Int`, `onDelete = CASCADE`, bez duplikatów utworu w tej samej playliście) i `FavoriteTrackEntity`, każde z własnym DAO zwracającym `Flow`. `PlaylistRepositoryImpl`/`FavoritesRepositoryImpl` w nowym pakiecie `data.playlist`, `combine()`-owane do reaktywnego `StateFlow<List<Playlist>>`. `:data` nie zależy od `:app`, więc dostał WŁASNY `@ApplicationScope`/`CoroutineScope` (`data.di.CoroutineModule`) — celowa duplikacja na granicy modułów, nie przeoczenie.

**Kolejka:** `PlaybackState.queue` + `PlayerRepository.addToQueue/removeFromQueue/moveQueueItem/playAt` zaimplementowane w `PlayerController` wprost na natywnych metodach Media3 `Player` (`addMediaItem`/`removeMediaItem`/`moveMediaItem`). `QueueScreen` — reorder strzałkami góra/dół (świadomie NIE drag-and-drop, żeby nie ciągnąć nowej zależności dla jednego ekranu).

**Timer snu:** nowy `SleepTimerController` (singleton, osobny od `PlayerController` — inny rodzaj stanu). Jawne `PlayerRepository.pause()` (nie `togglePlayPause`), żeby odpalenie timera nigdy nie wznowiło odtwarzania. `SleepTimerSheet` z presetami 15/30/45/60 min, dostępny z Now Playing.

**Ulubione i Playlisty:** `FavoritesScreen`, `PlaylistsScreen`, `PlaylistDetailScreen` (zmiana nazwy, usuwanie, dodawanie/usuwanie utworów, odtwarzanie od wybranego miejsca). Nowy reużywalny `TrackActionsSheet`/`TrackAction` w `core:designsystem` (menu "..." z Etapu 22 pkt 5) — przyjmuje surowe wartości, nie model domenowy, tak jak `TrackListItem`, żeby moduł zostawał niezależny od `:domain`. `AddToPlaylistSheet` (jeden utwór → wybór playlisty) i `AddTracksSheet` (playlista → wiele utworów z biblioteki) to lustrzane, celowo osobne komponenty, nie jeden przeciążony.

**Podgląd Geniusa:** `GeniusMixPreviewScreen` — tap na karcie miksu otwiera teraz tracklistę z przyciskami "Odtwórz" i "Zapisz jako playlistę" (tworzy prawdziwą playlistę z tych samych utworów) zamiast odtwarzać od razu. `LibraryViewModel.onPlayMix` usunięty jako martwy kod po tej zmianie.

**Ekran startowy (parytet ze Spotify):** dotąd Biblioteka nie miała ŻADNEGO wyszukiwania — to była największa pojedyncza luka usability, nie drobiazg. Dodano pigułkowe pole wyszukiwania (filtr lokalny po tytule/artyście/albumie, czysto w Compose state, bez zmian w VM/repozytorium) oraz poziomą, przewijalną "półkę" kart szybkiego dostępu (Ulubione/Playlisty/Genius z licznikami) zastępującą cztery ledwo klikalne ikonki w nagłówku — ten sam wzorzec co skróty na ekranie głównym Spotify, dostosowany do przewijania kciukiem zamiast siatki.

### Zweryfikowane na żywo

Build (`:app:compileDebugKotlin`) czysty na wszystkich modułach, `installDebug` + uruchomienie na emulatorze: wyszukiwarka, pełna "półka" trzech kart, lista utworów z ikoną "..." obok ikony Geniusa, oraz cały łańcuch "..." → "Dodaj do playlisty" → "Nowa playlista" potwierdzone zrzutami ekranu.

### Świadomie NIE w tym etapie

Przeglądanie wg Albumów/Wykonawców (osobny, większy koszt — grupowanie + nowe ekrany + nawigacja) i drag-and-drop reorder kolejki/playlisty — oba to naturalne następne kroki, nie przeoczenia.

## Etap 24: Tekst utworu (zsynchronizowane napisy) — LRCLIB, offline-first

*Źródło: "zajmij się pobieraniem napisów, nazw utworów i metadanych do utworów w bibliotece" — sprecyzowane przez usera: zsynchronizowane napisy (przewijają się z utworem), źródło LRCLIB, z lokalnym cache żeby działało offline po pierwszym pobraniu. Naprawa błędnych/brakujących tytułów i wykonawców (MusicBrainz) też potwierdzona jako "tak" — patrz "Świadomie NIE w tym etapie" niżej, to osobna runda.*

### Diagnoza

Appka dziś czyta WYŁĄCZNIE to, co da `MediaStore` (`MediaStoreScanner.kt`) — zero kodu do tekstu utworu, zero wbudowanego czytania tagów ID3 `USLT`/Vorbis `LYRICS`. Uwaga na nazewnictwo: `GeniusRepository`/"Genius Mixes" w tym projekcie to WŁASNY, w pełni lokalny silnik rekomendacji (podobne utwory z historii odsłuchań) — nie ma nic wspólnego z genius.com, więc ta funkcja świadomie nazywa się "Lyrics"/"tekst utworu", nie "Genius", żeby nie kolidować.

### Projekt

**Źródło danych — LRCLIB (`lrclib.net/api/get`), bez klucza API, bez Retrofit/Moshi.** Jedno proste GET (`track_name`/`artist_name`/`album_name`/`duration`), sparsowane wbudowanym `org.json` — ten sam minimalistyczny wzorzec co `WebDavLibraryRepository` (OkHttp bezpośrednio, żadna nowa zależność sieciowa/JSON).

**Offline-first cache (decyzja usera: "pobierać za 1 razem... zapisywać i używać offline") — nowa tabela Room `lyrics_cache` (`aurora.db` v4→v5, `LyricsCacheEntity`/`LyricsCacheDao`):** `LyricsRepositoryImpl.getLyrics()` czyta cache PRZED siecią; jeśli wpisu nie ma, pyta LRCLIB raz i zapisuje wynik — **także brak wyniku** (`syncedLrc`/`plainText` oba `null`), żeby appka nigdy nie odpytywała ponownie tego samego utworu bez tekstu. Dokładnie ta sama zasada "zapisz też negatyw" co `favorite_tracks`/inne tabele tego projektu, tu zastosowana do wyniku sieciowego zamiast lokalnej akcji użytkownika.

**Warstwy:** `LyricsResult` (sealed: `Synced(lines)`/`Plain(text)`/`NotFound`) + `LyricsLine`+`LrcParser` (parser formatu `.lrc`, pure Kotlin) w `domain`; `LyricsCacheEntity`/`LyricsCacheDao` w `data` (Room); `LrcLibClient`+`LyricsRepositoryImpl`+`LyricsModule` w `app` (potrzebują OkHttp — ten sam podział co `CloudLibraryRepository`/`GoogleDriveLibraryRepository`: interfejs w `domain`, implementacja tam gdzie jest sieć).

**UI — `LyricsSheet` (Now Playing, nowa ikona obok Equalizera/Timera snu):** `LyricsResult.Synced` renderuje się jako `LazyColumn` z auto-scrollem (`animateScrollToItem` na linię, której `timestampMs <= positionMs`) i podświetleniem bieżącej linii kolorem akcentu okładki — ten sam `accentColor` co reszta ekranu. `Plain` (LRCLIB nie zawsze ma zsynchronizowaną wersję) to statyczny blok tekstu bez podświetlania. `LibraryViewModel` dociąga tekst tym samym wzorcem co paleta koloru okładki — `distinctUntilChanged` po ID bieżącego utworu, nie po każdej emisji `playbackState` (ta zmienia się co ~300ms przy odtwarzaniu).

### Zweryfikowane

`:domain:compileKotlin`, `:data:compileDebugKotlin`, `:app:compileDebugKotlin` (`--rerun-tasks`, bez cache) — wszystkie zielone. Weryfikacja na żywo (realny utwór z LRCLIB, offline po drugim odtworzeniu) czeka na dostęp do telefonu/emulatora, tak jak reszta zaległej weryfikacji z Etapu 21.

### Świadomie NIE w tym etapie

**Naprawa błędnych/brakujących tytułów i wykonawców przez MusicBrainz** — user potwierdził "tak", ale to osobny, samodzielny kawałek pracy: wymaga (1) heurystyki "ten tag wygląda źle" (pusty/placeholder typu "Track 01"/nazwa pliku), (2) zapytania do MusicBrainz (inny kontrakt API niż LRCLIB, wymaga nagłówka `User-Agent` z kontaktem wg ich zasad + limitu ~1 zapytanie/sekundę), (3) nowej tabeli nadpisań (`track_metadata_override` czy podobnej — MediaStore nie jest bezpiecznie zapisywalny ze scoped storage) i scalenia jej w `TrackRepositoryImpl` przy odczycie. Nie zrobione teraz, żeby nie mieszać dwóch różnych kontraktów sieciowych i dwóch różnych modeli cache'u w jednej rundzie — zrobione w następnej rundzie, patrz Etap 25.

## Etap 25: Naprawa błędnych/brakujących tytułów i wykonawców — MusicBrainz

*Źródło: kontynuacja Etapu 24 — user: "dalej dalej" po propozycji zrobienia tego jako osobnej rundy.*

### Projekt

**Heurystyka "ten tag wygląda źle" — `TrackMetadataHeuristics.looksIncomplete()` (domain, pure Kotlin).** Świadomie konserwatywna: puste pole, dokładne placeholdery już używane w `MediaStoreScanner` (`"Nieznany utwór"`/`"Nieznany wykonawca"`), albo wzorzec typu `"Track 01"`/`"untitled"`/`"unknown"`. Fałszywy negatyw (nie złapany zły tag) jest tańszy niż fałszywy pozytyw (appka nadpisuje poprawny, tylko nietypowo nazwany utwór) — żadnego rozmytego dopasowania, tylko jawne wzorce.

**Sieć — MusicBrainz (`musicbrainz.org/ws/2/recording`), wolny tekst z nazwy pliku jako zapytanie** (jedyny sygnał, jaki appka ma, gdy tytuł/wykonawca są puste/placeholder). Ten sam brak Retrofit/Moshi co LRCLIB — `org.json` wystarcza na jedno pole `recordings[0]` (`title`/`artist-credit[0].name`/`releases[0].title`). `MusicBrainzRateLimiter` (Hilt singleton, `Mutex` + `delay`) pilnuje ~1 zapytania/sekundę wymaganego przez MusicBrainz dla anonimowych klientów — appka odpytuje sekwencyjnie, jeden utwór na raz, więc pojedynczy limiter globalny wystarcza. `USER_AGENT` w `MusicBrainzClient` ma jawny komentarz ostrzegający, że przed publicznym wydaniem potrzebuje realnego kontaktu, nie tylko nazwy appki — inaczej MusicBrainz może zacząć throttlować/blokować.

**Cache — nowa tabela Room `track_metadata_override` (`aurora.db` v5→v6, `TrackMetadataOverrideEntity`/`TrackMetadataOverrideDao`).** Ta sama zasada "zapisz też negatyw" co `lyrics_cache` z Etapu 24: `title`/`artist`/`album` wszystkie `null` = appka już próbowała i nie znalazła dopasowania, `matchedAtMs` i tak ustawione, więc `trackId` nigdy nie trafia do sieci drugi raz. `TrackRepositoryImpl.getAllTracks()` nakłada nadpisania na wynik `MediaStoreScanner` PRZED zwróceniem listy — czysto lokalny odczyt Room, zero sieci, więc zwykłe ładowanie biblioteki się nie spowalnia.

**Orkiestracja — `MetadataEnrichmentRepository.enrichLibrary(tracks): Flow<Track>` (domain interfejs, `MetadataEnrichmentRepositoryImpl` w `app` — ten sam podział warstw co Lyrics: sieć wymaga `app`, kontrakt w `domain`).** Filtruje kandydatów przez heurystykę, dla każdego pyta cache (pomija jeśli już sprawdzony), inaczej pyta MusicBrainz i **emituje utwór PO KOLEI** w miarę jak dopasowania wracają — nie czeka na całą partię, więc UI może aktualizować listę utwór po utworze zamiast zamrażać się na czas rate-limitu. `LibraryViewModel.refresh()` odpala to jako osobną korutynę PO ustawieniu pierwszego stanu `tracks` (nie opóźnia pierwszego wyświetlenia biblioteki) i podmienia trafione utwory w `uiState.tracks` w miarę napływania.

### Zweryfikowane

`:domain:compileKotlin`, `:data:compileDebugKotlin`, `:app:compileDebugKotlin` (`--rerun-tasks`, bez cache) — wszystkie zielone. Weryfikacja na żywo (realna biblioteka z plikami o złych tagach, potwierdzenie że MusicBrainz faktycznie trafia i że appka nie strzela częściej niż raz/sekundę) czeka na dostęp do telefonu/emulatora, razem z resztą zaległej weryfikacji z Etapów 21/24.

### Świadomie NIE w tym etapie

Ręczna edycja/odrzucenie sugestii przez usera (appka dziś nadpisuje automatycznie, bez potwierdzenia) — rozsądne dla oczywistych placeholderów, ale gdyby heurystyka kiedyś zaczęła łapać fałszywe pozytywy, to naturalne miejsce na krok pośredni ("zaproponuj, nie nadpisuj"). Zapis poprawionych tagów z powrotem do samego pliku (ID3) — appka trzyma nadpisanie wyłącznie w Room, oryginalny plik i `MediaStore` zostają nietknięte.

## Etap 26: Napisy jako "kanał" w kwadraciku Now Playing + panel transportu (ulubione/powtarzaj/losowo)

*Źródło: user obejrzał zrzut ekranu żywej appki i zgłosił dwie rzeczy naraz: (1) oddzielny przycisk/arkusz Napisów z Etapu 24 ma zniknąć — napisy mają grać w tym samym kwadraciku co wizualizer, "jak nasze okno na świat", w kolejności okładka→wizualizer→napisy jak kanały telewizora, na tych samych zasadach fullscreen co wizualizer (always-on, kontrolki znikają po 5s, bez obracania ekranu); (2) panel transportu na dole to tylko Poprzedni/Play/Następny — brakuje serduszka (ulubione), powtarzania i losowej kolejności. User jawnie zastrzegł: "zanim dodasz cokolwiek to zapytaj gdzie to ma się znaleźć bez zgadywania" — 4 pytania doprecyzowujące (gest zmiany kanału, co dzieje się z wizualizerem pod napisami, kontrolki fullscreen, zachowanie gdy brak tekstu) zadane i odpowiedziane PRZED napisaniem kodu.*

### Kanały Now Playing (zastępuje `LyricsSheet` z Etapu 24)

`LyricsSheet.kt`/przycisk w górnym rzędzie **usunięte** — funkcjonalnie zastąpione przez trzeci "kanał" w tym samym kwadraciku, gdzie dotąd żył tylko wizualizer/okładka. `VisualizerMode { AlbumArt, Inline, Fullscreen }` zastąpione przez `NowPlayingChannel { AlbumArt, Visualizer, Lyrics }` (treść) + osobny `isFullscreen: Boolean` (rozmiar) — te dwa wymiary były dotąd spleciony w jeden enum, co nie skalowało się na trzecią treść.

**Decyzje z pytań doprecyzowujących:**
1. **Gest zmiany kanału — swipe poziomy** po kwadraciku (nie tap, bo tap na wizualizerze jest już zajęty przez zmianę presetu ProjectM). Tap na okładce → Wizualizer zostaje jak było (Etap 16); swipe działa z każdego kanału, cyklicznie, przez `availableChannels`.
2. **Wizualizer zatrzymuje się pod Napisami** — kanał Napisy dostaje WŁASNE, jednolite tło (`backgroundTop`, ten sam derywowany kolor okładki co reszta ekranu — dosłownie "tło tekstu kopiuje kolor albumu"), `ProjectMSurface` zostaje zmontowany w tle na `1dp`/`alpha=0` (ten sam trik pre-warm co przy Okładce, Etap 18), nie renderowany na pełen ekran.
3. **Fullscreen Napisów — tylko X**, żadnych ikon presetów/ustawień (te są pojęciami ProjectM, nic nie znaczą dla przewijanego tekstu). Ta sama nakładka co wizualizer: `DisposableEffect`/immersywne paski/`keepScreenOn` (Etap 22) rozszerzone z `visualizerMode == Fullscreen` na ogólne `isFullscreen`, więc Napisy dostają dokładnie ten sam always-on + auto-hide-po-5s za darmo, bez nowego mechanizmu.
4. **Kanał Napisy pomijany w cyklu, gdy nie ma tekstu** — `availableChannels` filtruje po `hasLyrics` (`LyricsResult.Synced`/`Plain`), `NotFound`/`null` (jeszcze się ładuje) wypada z listy. To samo pomijanie zastosowane też do Okładki (`hasAlbumArt`), co dało naturalne rozwiązanie dodatkowego zgłoszenia usera w trakcie sesji: **"jeśli nie ma okładki ANI tekstu, appka od razu otwiera wizualizer"** — `defaultChannel = if (hasAlbumArt) AlbumArt else Visualizer` jako kanał startowy; Wizualizer zawsze istnieje, więc to jedyny bezpieczny fallback (Napisy nie mogą być kandydatem na start, bo ich dostępność jeszcze nie jest znana w momencie otwarcia ekranu — ładują się asynchronicznie).

**Karaoke — `SyncedLyricsKaraoke`** (nowy prywatny composable w `NowPlayingScreen.kt`, zastępuje usunięty `LyricsSheet`): `LazyColumn` + `derivedStateOf { lines.indexOfLast { it.timestampMs <= positionMs } }`, `animateScrollToItem` na bieżącą linię przy każdej zmianie indeksu, podświetlenie kolorem akcentu okładki. Współdzielony 1:1 między ramką w kwadraciku i pełnym ekranem (jeden kod, dwa rozmiary przez `Modifier`).

**NIEZWERYFIKOWANE NA ŻYWO — ryzyko gestów.** Swipe (nowy `pointerInput`+`detectHorizontalDragGestures`) współistnieje na tym samym Boxie z istniejącym `.clickable` (tap Okładka→Wizualizer) i z wewnętrznym gestem tap-cykluje-preset w `ProjectMSurface`. Kod jest napisany zgodnie z tym, jak Compose *powinien* rozróżniać tap od swipe (próg przesunięcia w `detectHorizontalDragGestures`), ale to jedna z tych rzeczy, których nie da się uczciwie nazwać "działa", dopóki ktoś fizycznie nie przeciągnie palcem po telefonie — zgodnie z zasadą tego dziennika (Etap 14/16/19: zweryfikowane na żywo, nie samą kompilacją).

### Panel transportu — ulubione/powtarzanie/losowo

Zgłoszenie: "ubogi panel sterowania — gdzie serduszko, zapętlenie, losowe odtwarzanie". Ulubione miało już pełną infrastrukturę (Etap 22, `FavoritesRepository`) — brakowało wyłącznie ikony w Now Playing (dodana obok tytułu/wykonawcy, wzorzec Spotify). Powtarzanie i losowa kolejność nie istniały wcale — nowe od zera:

- **`PlaybackState`** — `repeatMode: RepeatMode` (`OFF`/`ALL`/`ONE`, nowy `domain/model/RepeatMode.kt`) + `isShuffleEnabled: Boolean`.
- **`PlayerRepository`** — `cycleRepeatMode()`/`toggleShuffle()`. `PlayerController` mapuje 1:1 na `Player.repeatMode`/`Player.shuffleModeEnabled` z Media3 (istniało w silniku od zawsze, appka po prostu tego nie czytała/pisała) — `onRepeatModeChanged`/`onShuffleModeEnabledChanged` w `Player.Listener` trzymają `_playbackState` zgodny ze stanem NAWET gdy zmieniony z zewnątrz (np. przez kontrolki na powiadomieniu systemowym MediaSession, nie tylko z naszego UI).
- **UI** — rząd transportu rozszerzony do Losowo/Poprzedni/Play/Następny/Powtarzaj. Losowo wyszarzone i niekliknięte, gdy `playbackState.queue.size <= 1` (user: "losowe odtwarzanie może być użyte, gdy JEST co losowo odtwarzać") — jedyny w tym etapie przypadek jawnie wyłączonej kontrolki, nie tylko nieaktywnej wizualnie. Powtarzanie ma dwie różne ikony (`Repeat`/`RepeatOne`) zależnie od trybu, nie jeden kolor na tej samej ikonie.

### Zweryfikowane

`:domain:compileKotlin`, `:data:compileDebugKotlin`, `:app:compileDebugKotlin` (`--rerun-tasks`, bez cache) — wszystkie zielone. Weryfikacja na żywo (gest swipe vs tap, karaoke na realnym utworze z LRCLIB, repeat/shuffle przez `adb`/realny telefon) czeka na dostęp do urządzenia — patrz ostrzeżenie o gestach wyżej, to NAJWAŻNIEJSZA zaległa weryfikacja z całej tej rundy, nie formalność.

### Świadomie NIE w tym etapie

Reakcja appki na zmianę dostępności kanału W TRAKCIE odtwarzania (np. Napisy dociągają się PO otwarciu ekranu i user już siedzi na Wizualizerze) — appka świadomie NIE przeskakuje sama na nowo dostępny kanał, żeby nie "szarpać" ekranu bez akcji usera; `defaultChannel`/dostępność liczą się przy starcie i przy swipe'ach, nie jako ciągła reakcja na `hasLyrics`. Kontrolki fullscreen Napisów identyczne wizualnie z Wizualizerem (pytanie doprecyzowujące, opcja odrzucona) — wybrano samo X.

## Etap 27: Genius — "avoid recently skipped tracks"

*Źródło: user poprosił o dokończenie listy zaległych, wysokiego ROI usprawnień z Etapu 20d/23 — `SkipEventDao` zbierane od Etapu 3, ale nigdy nie czytane przy scoringu Instant Mix.*

`SkipEventDao.getAll()` (nowy query, dotąd DAO miało tylko `insert`) czytane w `GeniusRepositoryImpl.generateInstantMix()`. Kara liczona od NAJNOWSZEGO skipu danego kandydata (nie liczby skipów — "niedawno" to pytanie o czas, nie o częstość), wygaszana wykładniczo w ~14 dni (`decayedSkipPenalty`, ten sam kształt matematyczny co `decayedCooccurrenceWeight`/`recencyBoost`, krótsza skala niż obie: świeży skip ma być realnie odczuwalny, ale nie wieczny). `GeniusScoring.score()` dostał nowy parametr `skipPenalty` — CELOWO odejmowany POZA budżetem ośmiu wag sumujących się do 1.00 (`WEIGHT_SKIP_PENALTY = 0.20f`), bo to nie kolejny pozytywny sygnał podobieństwa do zrównoważenia, tylko osobna korekta za jawny negatywny feedback usera.

**Zweryfikowane:** `:domain:compileKotlin`/`:data:compileDebugKotlin` zielone. Zweryfikowane na żywo (czy realnie unika niedawno pominiętych w Instant Mix) czeka na telefon, jak reszta tej sesji.

## Etap 28: Naprawa metadanych — MusicBrainz auto-apply (nie review) + okładki z Cover Art Archive

*Źródło: kontynuacja Etapu 25. Propozycja "zaproponuj, nie nadpisuj" (patrz "Świadomie NIE" w Etapie 25) trafiła do realizacji, ale user słusznie zatrzymał ją w połowie: "jak będzie pobierać 5k utworów to co, będzie się o każdy pytać?" — recenzja sugestii jedna-po-jednej nie skaluje się do rozmiaru realnej biblioteki. Zamiast tego user poprosił o rozszerzenie na pobieranie okładek albumów.*

### Decyzja: zostaje auto-apply, NIE review queue

Etap 25 już auto-nadpisywał cicho — to zostaje bez zmian, plan "krok pośredni" z listy zaległości się nie zmaterializował i nie powinien: heurystyka (`TrackMetadataHeuristics.looksIncomplete`) jest już celowo konserwatywna (fałszywy negatyw tańszy niż fałszywy pozytyw), a UI proszące o potwierdzenie setek/tysięcy pojedynczych dopasowań byłoby gorsze niż sam problem, który miało rozwiązać.

### Okładki — Cover Art Archive (coverartarchive.org), darmowe, bez klucza

Nowy `CoverArtArchiveClient` (HEAD na `coverartarchive.org/release/{mbid}/front-250` — appka NIE ściąga bajtów obrazu, tylko sprawdza istnienie i zwraca URL; Coil, już użyty w `AsyncImage`, dociąga go leniwie przy renderze, dokładnie jak dziś robi to dla okładek z Google Drive). Wymaga MBID wydania z MusicBrainz — `MusicBrainzClient.parseBestRecording()` teraz też wyciąga `releases[0].id`, `MusicBrainzMatch` dostał pole `releaseMbid`.

**Drugie, NIEZALEŻNE kryterium kandydowania — `TrackMetadataHeuristics.needsCoverArt()` (`albumArtUri == null`).** Kluczowa poprawka względem naiwnej wersji: utwór z DOBRYMI tagami, ale bez okładki, musi zapytać MusicBrainz o dopasowanie (żeby dostać MBID), ale **NIE WOLNO** mu przy okazji nadpisać poprawnego tytułu/wykonawcy kanonicznymi wartościami z MusicBrainz — `MetadataEnrichmentRepositoryImpl` liczy `hasBadTags = looksIncomplete(track)` OSOBNO i warunkuje nim zapis title/artist/album, niezależnie od tego czy `albumArtUri` się znalazło. `MusicBrainzClient.queryFor()` też się rozgałęzia: złe tagi → wolny tekst z nazwy pliku (jak w Etapie 25); dobre tagi (utwór tu tylko po okładkę) → precyzyjne zapytanie polowe `artist:"..." AND recording:"..."`, bo appka ma już wiarygodne dane wejściowe.

`TrackMetadataOverrideEntity` dostała kolumnę `albumArtUri` (`aurora.db` v6→v7); `TrackRepositoryImpl.getAllTracks()` nakłada ją tak samo jak title/artist/album.

**Zweryfikowane:** pełny build (`domain`/`data`/`app`, `--rerun-tasks`) zielony. Realne dopasowanie/pobranie okładki na żywym urządzeniu — jak reszta zaległej weryfikacji tej sesji.

## Etap 29: Metadane audio na Track (codec/bitrate/sample rate/bit depth)

*Źródło: kontynuacja listy z Etapu 20h — prerekwizyt blokujący przyszły Audio Lab, realizowany teraz jako samodzielny kawałek (bez samego ekranu Audio Lab, to wciąż osobna, większa runda).*

**`MediaExtractor` (nie MediaStore — ten tych danych nie ma) — nowy `AudioMetadataExtractor` w `data`.** Otwiera pierwszy ścieżkę audio pliku, czyta `MediaFormat` (MIME→etykieta codeca, `KEY_SAMPLE_RATE`, `KEY_BIT_RATE`/1000, opcjonalny `bits-per-sample`). Świadomie NIE estymuje bit depth z rozmiaru pliku/czasu trwania, gdy ekstraktor go nie wystawia (częste dla skompresowanych formatów) — appka ma milczeć (`null`), nie zgadywać, ta sama zasada uczciwości co `AudioCapabilities`/`BitPerfectStatus` z Etapu 21.

**Tylko `TrackSource.LOCAL`.** `MediaExtractor` czytałby zdalne `https://` (Drive/WebDAV) bez nagłówków autoryzacji, którymi dysponuje wyłącznie warstwa odtwarzania w `app` (`AuthenticatingHttpDataSourceFactory`) — świadomie odłożone, nie przeoczone, żeby nie mieszać dwóch różnych kontraktów dostępu do pliku w jednej rundzie. Dzięki temu cała funkcja mieści się w `data` (lokalny I/O, zero sieci) — inaczej niż Lyrics/MusicBrainz, które musiały wejść do `app` po OkHttp.

**Warstwy:** `AudioTrackMetadata` (domain, cztery nullable pola) + `Track.audioMetadata` (nowe pole, domyślnie `null`) w `domain`; `TrackAudioMetadataEntity`/`Dao` (`aurora.db` v7→v8, ta sama zasada "zapisz też negatyw" co pozostały cache tej sesji) w `data`; `AudioMetadataExtractor`+`AudioMetadataRepositoryImpl` w `data/media` (bez potrzeby `app`, patrz wyżej); `TrackRepositoryImpl.getAllTracks()` nakłada cache tak samo jak metadane MusicBrainz. `LibraryViewModel.refresh()` odpala ekstrakcję jako trzecią, osobną korutynę w tle (obok Lyrics/MusicBrainz), tym samym wzorcem "podmień utwór na liście, gdy wynik wróci".

**Zweryfikowane:** pełny build zielony. Rzeczywista poprawność odczytanych wartości na różnych formatach (MP3/FLAC/AAC/WAV) z prawdziwej biblioteki — czeka na telefon.

### Świadomie NIE w tym etapie

Sam ekran Audio Lab (wizualizacja tych danych) — DESIGN.md Etap 20h już to sekwencjonuje jako osobną, większą rundę (karta ŹRÓDŁO → łańcuch DSP → Wyjście/DAC na końcu). To tylko fundament danych, na którym ten ekran będzie mógł stanąć.

## Etap 30: Logowanie Google Drive nie działa — diagnoza + decyzja biznesowa o publicznym wydaniu

*Źródło: user zgłosił zrzutem ekranu, że logowanie Google nie przechodzi nawet z kontami widocznymi na telefonie. Zero kodu w tym etapie — to diagnoza + zapisana decyzja architektoniczna do wykonania w kolejnej rundzie.*

### Diagnoza: `UNREGISTERED_ON_API_CONSOLE`

Błąd `Błąd logowania Google (kod 8): [status=UNREGISTERED_ON_API_CONSOLE]` nie ma nic wspólnego z kontem na telefonie — `GoogleDriveLibraryRepository` (Google Identity Services, `Identity.getAuthorizationClient()`) nie trzyma w kodzie żadnego zaszytego client ID, tylko dopasowuje appkę po parze **package name (`com.aurora.player`) + SHA-1 podpisu APK** względem klienta OAuth zarejestrowanego w Google Cloud Console. User potwierdził: **dla tej appki nie istnieje jeszcze żaden projekt Google Cloud** — to nie regresja, tylko funkcja nigdy w pełni nie skonfigurowana od strony konsoli.

**SHA-1 debug keystore (`~/.android/debug.keystore`, ten sam na tym komputerze dla wszystkich projektów budowanych lokalnie):**
```
AF:71:2B:00:BC:86:45:81:9C:EB:06:34:B1:C7:B2:FE:CA:81:60:34
```

Kroki do wykonania przez usera w Google Cloud Console (poza zasięgiem Claude Code — wymaga jego konta): nowy projekt → włącz Google Drive API → OAuth consent screen (External, dodać własny mail jako test user, dopóki projekt zostaje w "Testing") → Credentials → OAuth client ID → Android → package `com.aurora.player` + SHA-1 wyżej.

### Decyzja: `drive.readonly` → `drive.file` + Picker, gdy appka ma wyjść poza test userów

User zapytał, jak rozwiązać logowanie biznesowo, żeby "każdy mógł się zalogować" po publicznym wydaniu. Kluczowy fakt z klasyfikacji scope'ów Google: dzisiejszy `DriveScopes.DRIVE_READONLY` (pełny odczyt całego Dysku) to scope **restricted** — publiczne wydanie z tym scope'em wymaga pełnej weryfikacji Google ORAZ płatnego/czasochłonnego **CASA security assessment**, zanim userzy spoza listy testowej przestaną widzieć ostrzegawczy ekran "Google nie zweryfikował tej aplikacji".

**Rekomendacja (nie zaimplementowane): przejście na `drive.file` + Google Picker.** User sam wybiera plik/folder z muzyką przez natywny Picker Google, appka widzi tylko to, co wskazał — `drive.file` to scope **niewrażliwy**, weryfikacja ogranicza się do formularza (link do polityki prywatności + strona appki), bez CASA i bez kosztów. Kompromis: appka przestaje sama skanować cały Dysk usera, wymaga jednorazowego wyboru folderu w Pickerze — mniejsza automatyczna wygoda, ale realnie osiągalne dla jednoosobowego/niszowego wydania. Wymaga realnej zmiany w `GoogleDriveLibraryRepository` (dodanie Google Picker UI, zmiana `AuthorizationRequest.setRequestedScopes`) — odłożone do kolejnej rundy, user ma jeszcze potwierdzić że to kierunek, w który chce iść, zanim appka w ogóle działa z Drive (Etap 30 diagnoza wyżej to twardy blocker sam w sobie).

## Etap 31: Radio internetowe — Radio-Browser, geolokalizacja z ręcznym fallbackiem

*Źródło: user, materiał porównawczy z Powerampem + jego luki ("implementacja radia ale z zastrzeżeniem że wyświetlamy stacje do wyboru popularne w kraju w którym użytkownik jest -> jeśli nie zgodzi się na geolokalizację to apka prosi o wybranie preferowanego kraju"). Świadomie pomijamy wszystko wymagające wbudowanego AI (user: "pomijamy wszystko co wymaga wbudowanego AI") — to prosta integracja API, nie rekomendacja.*

### Co zbudowano

**`RadioRepositoryImpl`** (`app/radio`) — Radio-Browser (radio-browser.info), otwarta baza ~40k stacji, **zero klucza API**. Stały, znany mirror (`de1.api.radio-browser.info`) zamiast round-robin przez DNS SRV (oficjalnie zalecane dla większej skali, wymagałoby dodatkowej biblioteki) — kompromis świadomy, wystarczający dla appki mobilnej.

**Geolokalizacja z pełnym fallbackiem** — `RadioCountryResolver`: ostatnia znana lokalizacja (`LocationManager`) + `Geocoder` → kod kraju ISO. Zwraca `null` nie tylko przy odmowie zgody, ale też przy braku lokalizacji/niedziałającym Geocoderze (częste na niektórych ROM-ach) — `RadioScreen` w KAŻDYM z tych przypadków pokazuje `CountryPickerSheet` (pełna lista `Locale.getISOCountries()` z wyszukiwaniem), więc user zawsze kończy z jakąś listą stacji, nigdy z pustym ekranem.

**Odtwarzanie** — `RadioStation.toTrack()`: stacja jako syntetyczny `Track` (`TrackSource.RADIO`, `durationMs = 0`), odtwarzany przez already-istniejący `PlayerRepository`/kolejkę/Now Playing — zero osobnej ścieżki odtwarzania. `AuthenticatingHttpDataSourceFactory` (Etap 12/22) już obsługuje dowolny host bez autoryzacji poprawnie (dopasowuje Bearer/Basic Auth tylko po znanym hoście Google Drive/WebDAV), więc strumienie radiowe zadziałały bez ŻADNEJ zmiany w `PlaybackService`.

### Świadomie NIE w tym etapie

Live ICY metadata (tytuł aktualnie granego utworu z nagłówka strumienia) — wymagałoby dodatkowego wsparcia w Media3 dla `Icy-MetaData: 1`, niezweryfikowanego bez realnego sprzętu; stacja pokazuje na razie statyczną nazwę. Ulubione stacje (osobna tabela Room) — czysty dodatek na później, nie blocker.

### Zweryfikowane na żywo

Pełny łańcuch na emulatorze: zgoda na lokalizację → `Geocoder` zwrócił `US` (domyślna lokalizacja emulatora) → realne stacje z API (Radio Paradise, WALM HD, 101 Smooth Jazz...) → tap → **faktyczne odtwarzanie strumienia** (`dumpsys media_session`: `state=PLAYING`, pozycja realnie postępuje, `metadata: Rockin' Around The Christmas Tree by Brenda Lee - Christmas Vinyl on walmradio.com`) → mini-player poprawnie pokazuje "Radio na żywo".

## Etap 32: Podcasty — RSS + dwa niezależne katalogi wyszukiwania (iTunes + Podcast Index)

*Źródło: user, ten sam materiał o Powerampie + dyskusja o modelu biznesowym wspólnego klucza API ("mam dać tylko jeden ten api i będzie na wszystkich użytkowników -> jak to rozwiązać biznesowo?" → decyzja: "zróbmy obie opcje żeby zawsze jakieś podcasty były, a tamten jako dodatek").*

### Diagnoza: czemu NIE jeden wspólny klucz Podcast Index

Limit zapytań Podcast Index jest per-klucz — jeden wspólny klucz appki dzieliłby budżet requestów między WSZYSTKICH userów appki naraz (przy realnej skali funkcja przestałaby działać dla wszystkich jednocześnie). Klucz zaszyty w APK jest też trywialny do wyciągnięcia dekompilacją, co zwykle łamie regulamin API rejestrowanych per-developer, nie per-anonimowy-user. Stąd dwutorowa architektura:

1. **iTunes Search API** — publiczne, bez klucza, ZAWSZE działa. Domyślna, główna ścieżka wyszukiwania.
2. **Podcast Index** — tylko gdy user poda WŁASNY, darmowy klucz (`PodcastIndexSetupDialog`, trwały w `PodcastIndexCredentialStore`, ten sam wzorzec co `WebDavCredentialStore`) — dodatek, nie wymóg. Auth: SHA-1(key+secret+unixTime) w nagłówkach `X-Auth-*`, zgodnie z dokumentacją API.

### Co zbudowano

**RSS parsing bez nowej zależności** — `PodcastRssParser` używa wbudowanego `javax.xml.parsers.DocumentBuilderFactory` (namespace-aware dla `itunes:`), ten sam wzorzec co PROPFIND w `WebDavLibraryRepository`. Obsługuje `itunes:duration` w obu formatach (sekundy albo `HH:MM:SS`) i `pubDate` przez `DateTimeFormatter.RFC_1123_DATE_TIME`.

**Subskrypcje + pozycja odtwarzania w Room** — `PodcastSubscriptionEntity`, `PodcastPlaybackPositionEntity` (`AuroraDatabase` → wersja 9). Pozycja kluczowana po `trackId` (hash przez `TrackIdHasher`, ten sam identyfikator co Ulubione/Playlisty), NIE po surowym `guid` z RSS — appka nigdy nie musi odwracać hasha. Auto-zapis co ~5s odtwarzania (`LibraryViewModel` init, bucket po `positionMs / 5000` — nie na każdym ticku 300ms z `PlayerController`), auto-wznowienie przy ponownym odtworzeniu tego samego odcinka.

**Odcinki NIE cache'owane** — świeże parsowanie RSS przy każdym wejściu w podcast (`fetchEpisodes`), świadomie, żeby uniknąć rozjazdu ze stanem kanału; cache jako możliwa optymalizacja na później, gdyby okazał się potrzebny.

**Prędkość odtwarzania** — `PlayerRepository.setPlaybackSpeed`/`PlaybackState.playbackSpeed`, chipy 0.75×–2× widoczne w Now Playing TYLKO dla `TrackSource.PODCAST` (muzyka prawie nigdy tego nie potrzebuje, stały rząd chipów zaśmiecałby ekran odtwarzania muzyki).

**Ekrany** — `PodcastsScreen` (subskrypcje + "Dodaj po URL"), `AddPodcastSheet` (RSS URL + wyszukiwanie w obu katalogach naraz, deduplikacja po `feedUrl`), `PodcastDetailScreen` (opis + lista odcinków).

### Świadomie NIE w tym etapie

Pomijanie ciszy, auto-cofanie po dłuższej pauzie, rozdziały — realne funkcje z materiału usera o Powerampie, ale osobny, dobrze wyodrębniony krok na później, nie blocker startowej wersji.

### Zweryfikowane na żywo

Pełny łańcuch: wyszukiwanie "Radiolab" przez iTunes → realne wyniki (Radiolab/WNYC Studios, Dolly Parton's America, Terrestrials...) → subskrypcja przez wklejony RSS URL (`feeds.wnyc.org/radiolab`) → **cały feed sparsowany poprawnie: 670 odcinków**, tytuły/daty (polska lokalizacja: "11 wrz 2026")/czasy trwania ("1h 3min") wszystkie poprawne → tap na odcinek → `dumpsys media_session` potwierdza `state=BUFFERING` (realny plik MP3 odcinka pobierany przez `PlayerRepository`/ExoPlayer, ten sam mechanizm co Radio).

## Etap 33: "Dodaj podcast" — domyślna lista top podcastów kraju (zamiast pustego ekranu)

*Źródło: user zobaczył zrzut ekranu wyszukiwarki podcastów i zapytał, czemu widać tylko wyniki jednego zapytania ("Radiolab") — po doprecyzowaniu (`AskUserQuestion`) okazało się, że chodzi o coś innego niż liczba wyników: "chciałbym by wyświetlały się różne dla danego regionu, podobnie jak stacje dla radia". `AddPodcastSheet` (Etap 32) dotąd był pusty, dopóki user sam czegoś nie wpisał.*

**Reużyty wprost cały mechanizm geolokalizacji z Radia (Etap 31), nie duplikat — ale przeniesiony do neutralnego pakietu.** `resolveCountryCodeFromLastKnownLocation()` i `CountryPickerSheet` okazały się ogólnymi, bezstanowymi narzędziami bez niczego specyficznego dla radia, więc zamiast `AddPodcastSheet` importującego je z cudzego pakietu `radio` (myląca zależność feature→feature), obie przeniesione do nowego, neutralnego `com.aurora.player.location` — `RadioScreen` i `AddPodcastSheet` importują z tego samego, wspólnego miejsca. `LaunchedEffect(Unit)` przy otwarciu arkusza, ten sam wzorzec "user zawsze kończy z jakąś listą, nigdy z pustym ekranem" co `RadioScreen`.

**Źródło danych — Apple "Top Charts" (`rss.applemarketingtools.com`), darmowe, bez klucza — plus jeden dodatkowy hop.** Ten endpoint daje tylko numeryczne ID kolekcji w kolejności rankingu, NIE realny `feedUrl` — `PodcastCatalogRepositoryImpl.topPodcastsByCountry()` dociąga go przez iTunes Lookup (ten sam endpoint co istniejący `searchITunes`) jednym zapytaniem wsadowym po przecinku (`id=1,2,3...`), więc to dwa zapytania sieciowe na jedno odświeżenie, nie jedno. Kolejność wyniku wraca z listy TOP (ranking), nie z odpowiedzi Lookup — ta druga nie gwarantuje kolejności.

**UI:** ikonka "Zmień kraj" (`Icons.Filled.Public`) obok istniejącej zębatki ustawień Podcast Index; wyczyszczenie pola wyszukiwania i ponowne submitowanie wraca do listy top zamiast zostawiać stare wyniki wyszukiwania. `LibraryViewModel.loadTopPodcasts()` używa TEGO SAMEGO stanu co `searchPodcasts()` (`podcastSearchResults`/`isSearchingPodcasts`) — z punktu widzenia UI to po prostu inny sposób wypełnienia tej samej listy, nie osobny ekran/stan.

**Przy okazji naprawione, ten sam zrzut ekranu: `MusicSourcesSheet` ("Źródła muzyki") wyglądała "średnio".** Ten sam bug i ta sama poprawka co przy EqualizerSheet (Etap 19/20): brak jawnego `shape` → sheet dziedziczył `MaterialTheme.shapes.extraLarge` (999dp, myślany do pigułek) i renderował się jako kopuła nachodząca na listę pod spodem. Dodano jawny `shape = RoundedCornerShape(topStart/topEnd = 24.dp)`, `containerColor`/`scrimColor` (opcjonalny blur przez `hazeState` z `LibraryScreen`, ten sam wzorzec `HazeMaterials.regular`), a wiersze źródeł (`MusicSourceRow`) dostały styl karty (zaokrąglone tło, odstępy) zamiast płaskiej listy — spójne z `PlaylistCard`/`PodcastRow`.

## Etap 34: Android Auto — drzewo przeglądania (Media3 MediaLibraryService)

*Źródło: user, "Android Auto ma działać lepiej niż portale muzyczne gigantów" — poprzedzone dwuagentowym researchem (techniczny: migracja Media3, wymagania manifestu, content styling, limity; UX: jak robią to Spotify/YT Music/Apple Music, częste narzekania userów, twarde ograniczenia platformy "driver distraction").*

### Diagnoza stanu wyjściowego

`PlaybackService` był gołym `MediaSessionService` — sama sesja bez drzewa przeglądania, więc Android Auto nie miał czego pokazać. Dodatkowo `MediaItem` w `PlayerController` budowany był przez `MediaItem.fromUri()` bez żadnych metadanych — Now Playing (i powiadomienie systemowe) nie miały gwarancji poprawnego tytułu/wykonawcy/okładki dla utworów bez własnych tagów ID3 (chmura, radio). Naprawione niezależnie od Auto — patrz [MediaItemMapper.kt](app/src/main/kotlin/com/aurora/player/playback/MediaItemMapper.kt), `Track.toMediaItem()` z pełnym `MediaMetadata` + `mediaId = track.id`.

### Co zbudowano

- **`PlaybackService` → `MediaLibraryService`** (superset `MediaSessionService`, bezpieczna migracja) z `AuroraLibrarySessionCallback` — `onGetLibraryRoot`/`onGetChildren`/`onGetItem`/`onSearch`/`onGetSearchResult`/`onAddMediaItems`, wszystko async przez `SettableFuture` odpalany na `applicationScope` (blokujące wywołanie w tych callbackach = ANR i appka znika z listy Auto).
- **`AuroraBrowseTree`** — całe drzewo liczone z ISTNIEJĄCYCH repozytoriów domenowych (`TrackRepository`/`PlaylistRepository`/`GeniusRepository`/`FavoritesRepository`/`PodcastRepository`/`PlaybackHistoryRepository`), zero nowej logiki biznesowej. 4 zakładki roota (kolejność wg częstości użycia w aucie):
  - **Genius** (grid) — "Kontynuuj: ostatni utwór" (nowa metoda `PlaybackHistoryRepository.getLastPlayedTrackId()`, `PlayEventDao` query po `timestampEnd DESC`) + Genius Mixy. Jedyna kategoria z override `onAddMediaItems` — tap ma odtworzyć CAŁY miks, nie jeden utwór, więc kafelek jest browsable+playable bez własnego URI, rozwiązywany po mediaId (`genius_mix:<index>`) na pełną listę. Mixy cache'owane w pamięci procesu na czas życia serwisu (ta sama lista musi wyjść z `onGetChildren` co potem z resolvera, inaczej tap zagrałby inny miks niż widoczny).
  - **Biblioteka** (lista) — Ulubione/Utwory/Albumy(grid)/Wykonawcy, reużywa wprost `groupTracksByAlbum`/`groupTracksByArtist` z `LibraryGrouping.kt` (ten sam kod co ekran Biblioteki).
  - **Playlisty** (grid) → utwory w kolejności `trackIds`.
  - **Podcasty** (grid) → odcinki (żywe RSS przez `fetchEpisodes`, ten sam świadomy brak cache'u co Etap 32).
  - Content styling (grid/lista/kategorie) przez `MediaConstants.EXTRAS_KEY_CONTENT_STYLE_*` na `MediaMetadata.extras` każdego browsable node'a.
- **Custom action w Now Playing: serce (Ulubione)** — `SessionCommand` + `CommandButton`, dwie wersje ikony (`ic_favorite_filled`/`ic_favorite_outline`), odświeżane zarówno na zmianę utworu (`Player.Listener.onMediaItemTransition`), jak i na zmianę zbioru ulubionych z innego miejsca (`favoritesRepository.favoriteTrackIds.collect`) — nie tylko po tapnięciu samego przycisku.
- **Manifest**: `res/xml/automotive_app_desc.xml` (`<uses name="media"/>`) + meta-data `com.google.android.gms.car.application`, intent-filter serwisu rozszerzony o `androidx.media3.session.MediaLibraryService` i `android.media.browse.MediaBrowserService` (kompatybilność wstecz).

### Świadomie NIE w tym etapie

- **Radio w drzewie Auto** — apka nie ma trwałej listy ulubionych/domyślnych stacji, tylko live geolokalizację+wyszukiwanie w `RadioScreen` (Etap 31); przeniesienie tego do auta wymagałoby nowej funkcji persystencji (poza zakresem tego etapu) i koliduje z wytycznymi "driver distraction" — Spotify z premedytacją nie ma tam wyszukiwania z tego samego powodu.
- **Quick-toggle presetów EQ jako custom action** — jedyna funkcja, której Spotify/YT Music nie mają (brak własnego DSP), ale research techniczny ocenił to jako ryzykowne: Android Auto może dodatkowe przyciski schować/odrzucić, więc bez niezawodnego UI to strata inżynierii bez gwarantowanej wartości. Zostaje jako pomysł na później, jeśli DHU pokaże, że jest miejsce na drugi custom button.
- Paginacja `TrackRepository` dla bibliotek rzędu dziesiątek tysięcy utworów — `onGetChildren` już paginuje odpowiedź (page/pageSize), ale samo pobranie (`getAllTracks()`) nadal ładuje całość do pamięci; wystarczające dla typowej biblioteki osobistej, do rewizji gdyby się okazało za wolne.

### Zweryfikowane

`:app:compileDebugKotlin` przechodzi (`BUILD SUCCESSFUL`) — cały nowy kod (Media3 `LibraryResult`/`MediaConstants`/`CommandButton`/`setCustomLayout`, nowe pliki, zmieniony manifest) zgadza się z API Media3 1.5.0 i kompiluje się przez wszystkie moduły (`domain`→`data`→`app`). **NIE zweryfikowane jeszcze na żywo** — brak testu przez Desktop Head Unit (DHU) ani prawdziwe/emulowane Android Auto; to następny krok przed uznaniem etapu za w pełni gotowy.

**Zweryfikowane na żywo (na emulatorze, ta sesja):** `AddPodcastSheet` przy otwarciu pokazuje realny, aktualny Top Chart USA (The Daily/NYT, Crime Junkie, The Joe Rogan Experience, In The Dark, REAL AF...) zamiast poprzedniego pustego/przypadkowego stanu wyszukiwania. `MusicSourcesSheet` renderuje się z poprawnymi zaokrąglonymi rogami i kartowymi wierszami, bez kopuły.

## Etap 37: Przeprojektowanie nawigacji frontu — nowe domeny treści + kolor per domena

*Źródło: user zgłosił plan dodania trzech nowych domen treści (Audiobooki — LibriVox, Muzyka niezależna — Jamendo, Archiwum — Internet Archive) obok istniejących (Biblioteka/Playlisty/Genius/Radio/Podkasty) i ocenił, że obecna nawigacja się nie skaluje ("fajnie by wyglądało w innej kompozycji niż mamy teraz"). Poprzedzone dwuagentowym researchem (audyt obecnego stanu + propozycja architektury informacji z benchmarkiem Spotify/Apple Podcasts/Audible/YouTube Music), zatwierdzone przez usera. Numeracja: ten etap pierwotnie zapisany jako "35", przenumerowany na 37, bo kod już ma nieudokumentowany "Etap 36" (moduł `core:sync` + appka `desktop/` + `AccountScreen` — logowanie/sync między telefonem a desktopem) — luka w DESIGN.md do uzupełnienia osobno, nie w tej rundzie.*

### Diagnoza stanu wyjściowego

Audyt na żywo potwierdził: **appka nie ma dziś żadnej trwałej nawigacji** — zero `NavigationBar`/`NavigationRail`/drawer w całym repo (`AuroraNavHost.kt` rejestruje 13 tras płasko, obok siebie, każda jako push ze swoim `onBack`). Jedyny sposób dotarcia do Radio/Podkastów/Playlist/Ulubionych/Geniusa to poziomy "quick access shelf" na 5 kart wewnątrz `LibraryScreen` (Etap 23) — de facto ekran startowy. Plan bottom-nav istniał już wcześniej (**Etap 20b**: Biblioteka/Teraz odtwarzane/Radio AI/Ustawienia) i był wtedy nazwany "blockerem dla reszty planu", ale **nigdy nie został wdrożony** — Etap 22/23 poszły inną drogą (płaskie trasy + shelf). Dodanie 3 kolejnych domen do 5-kartowego shelfa przekroczyłoby jego sensowną pojemność.

`AuroraTokens` dziś obejmuje wyłącznie spacing — kolor (`MaterialTheme.colorScheme`), kształty (`AuroraShapes`), typografia (`AuroraTextStyles`) i blur (`Haze`, wołany bezpośrednio) żyją osobno; to już wcześniej (Etap 20a) oznaczone jako niedokończona konsolidacja.

### Decyzja: architektura nawigacji

**4 zakładki na dole (`NavigationBar`, w granicach zalecenia Material 3 3–5 pozycji), stałe niezależnie od liczby domen treści:**

- **Home** — kuratorski dashboard, wzorzec Spotify Home / Audible Home: pasek wyszukiwania u góry → poziomy rząd 8 chipów-skrótów (po jednym na domenę: Biblioteka/Playlisty/Genius/Radio/Podkasty/Audiobooki/Muzyka niezależna/Archiwum) → karuzela "Kontynuuj" (cross-domain: utwór/odcinek/rozdział, cokolwiek leciało ostatnio) → kolejne poziome karuzele per domena, kolejność adaptacyjna wg użycia.
- **Biblioteka** — wyłącznie treści usera: segmented chips Ulubione/Utwory/Albumy/Wykonawcy/Playlisty/Subskrypcje (podkasty+audiobooki, bo to też "moje").
- **Odkrywaj** — wyłącznie treści zewnętrzne: chips Radio/Muzyka niezależna (Jamendo)/Archiwum (Internet Archive)/katalog audiobooków/katalog podkastów; każda sekcja z własnym sposobem przeglądania (gatunek/nastrój dla Jamendo, kolekcje — nie płaska wyszukiwarka — dla Archiwum, patrz Etap "Archiwum/Jamendo/Audiobooki" jeszcze do napisania).
- **Szukaj** — jedno globalne pole + chipy filtra domeny (Wszystko/Biblioteka/Radio/Podkasty/Audiobooki/Jamendo/Archiwum), wyniki grupowane sekcjami.

Odrzucone warianty: "Twoje vs Odkryj" jako jedyna oś (3 zakładki, bez Home) — więcej tarcia decyzyjnego przy pierwszym użyciu, brak wspólnego miejsca łączącego obie strony. Drawer boczny (Home/Biblioteka/Szukaj + hamburger z 8 domenami) — skaluje się dobrej do 9./10. domeny, ale to idiom "power-user/utility", kłóci się z estetyką "premium Apple-minimal" z sekcji 2.

Świadoma poprawka względem starego Etapu 20b: **"Teraz odtwarzane" i "Ustawienia" NIE są zakładkami dolnymi** — Now Playing wjeżdża z mini-playera (swipe up), Ustawienia to ikona w rogu Home, nie pozycja równorzędna z domenami treści. To zgodne ze współczesną konwencją (Spotify/Apple Podcasts/Audible żaden nie trzyma "now playing" w bottom nav).

### Decyzja: kolor per domena (odejście od "jeden akcent" z sekcji 2.1)

User świadomie poprosił o kolorową kompozycję zamiast dotychczasowej zasady "jeden mocny akcent, zero tęczowej palety". Zatwierdzony wariant: **stłumiony/pastelowy akcent**, nie pełne nasycenie — żeby nie kolidować z założeniem "dużo światła/whitespace" z sekcji 2.

- 8 stałych kolorów w palecie, jeden na domenę (Biblioteka/Playlisty/Genius/Radio/Podkasty/Audiobooki/Muzyka niezależna/Archiwum) — do zdefiniowania w kolejnej rundzie jako rozszerzenie `AuroraTheme.kt`.
- Nagłówek każdej karuzeli domeny na Home w jej kolorze + delikatny gradient w rogu karty (~8–15% krycości, nie plama na całej karcie — okładki/artwork zostają czytelne).
- Chipy-skróty na Home i chipy filtra w Szukaj/Bibliotece/Odkrywaj przejmują ten sam kolor jako obrys/tło stanu aktywnego.
- **Now Playing NIE dziedziczy tego systemu** — zostaje przy dynamicznym akcencie z okładki albumu (Palette.Builder, sekcja 2.1) — inny kontekst ekranu, mieszanie obu systemów kolorów groziłoby niespójnością.
- Ryzyko zaakceptowane świadomie: przy 8 kolorach na jednym ekranie Home łatwo o "przeładowanie" — stąd wybór pastelowy, nie pełne nasycenie, i zasada maks. 1 wyraźny akcent widoczny naraz poza samym Home.

### Komponenty UI do zbudowania (kolejna runda)

- `NavigationBar` (nowy) + przebudowa `AuroraNavHost.kt` z płaskich tras na graf zagnieżdżony pod 4 zakładkami; `MainActivity.kt` dostaje scaffold z `bottomBar`.
- Nowy `HomeScreen` (dashboard z karuzelami) — zastępuje dzisiejszy `LibraryScreen` jako start destination; `LibraryScreen` zwęża się do czystego "Biblioteka" (bez shelfa/search, bo to przenosi się na Home/Szukaj).
- Nowy `DiscoverScreen` ("Odkrywaj") i `SearchScreen` (globalne wyszukiwanie z filtrem domeny — dziś wyszukiwanie to tylko inline filtr w Library, trzeba wydzielić).
- Jedna reużywalna karta karuzeli (okładka+tytuł+podtytuł), różniąca się tylko metadanymi per domena, nie strukturą.
- Rozszerzenie `AuroraTheme.kt`/`AuroraTokens` o 8 kolorów domen (dziś tokens obejmują wyłącznie spacing — do skonsolidowania, patrz Etap 20a).
- Onboarding/empty state dla Audiobooków/Jamendo/Archiwum przy pierwszym wejściu (nowe koncepcje dla użytkownika, ekrany jeszcze nie istnieją — patrz osobny etap do napisania dla samych źródeł treści).
- `SettingsScreen` (nadal nie istnieje, potrzebny jako cel ikony w rogu Home).

### Świadomie NIE w tym etapie

- Sam kod źródeł treści (LibriVox/Jamendo/Internet Archive: repozytoria domenowe, klienci sieciowi, klucze API) — to osobna runda, ta tu dotyczy wyłącznie kompozycji frontu.
- Wdrożenie w Android Auto (`AuroraBrowseTree.kt`) nowych domen — do zrobienia po tym, jak każde źródło ma działający ekran w telefonie (ten sam wzorzec co Etap 34: drzewo Auto reużywa istniejących repozytoriów, zero nowej logiki).
- Konkretne wartości hex 8 kolorów domen — zatwierdzony kierunek (pastelowy, per domena), dobór konkretnej palety zostaje do fazy implementacji.

### Zweryfikowane na żywo (na emulatorze `Aurora_Test`, ta sesja)

`:app:compileDebugKotlin` i `:app:assembleDebug` przechodzą (`BUILD SUCCESSFUL`) — w trakcie tej rundy równolegle inna sesja pracowała nad backendami Jamendo/Internet Archive/LibriVox w tym samym module `:app` i chwilowo zostawiła błąd kompilacji niezwiązany z tym etapem (eksperymentalna flaga Kotlina "break/continue w inline lambdach"); naprawiony po ich stronie w międzyczasie, zero kolizji w plikach tego etapu.

APK zainstalowany i uruchomiony, zweryfikowane realnymi tapnięciami (nie tylko czytaniem kodu):
- Root pokazuje 4 zakładki dolne (Home/Biblioteka/Odkrywaj/Szukaj), ikony i etykiety poprawne.
- Home: pasek "Aurora" + ikona konta, pole wyszukiwania (tap → Szukaj), pozioma lista 8 kolorowych kart domen (każda swój odcień — niebieski/fioletowy/różowy/koralowy/bursztynowy/turkusowy/błękitny/piaskowy), karuzela "Miksy Geniusa" z realnymi danymi z `GeniusRepository` (różne nazwy miksów między uruchomieniami — losowość działa).
- Biblioteka: własny inline search + skrócony rząd "Ulubione/Playlisty/Subskrypcje" (3 karty, nie dawnych 5) + zakładki Utwory/Albumy/Wykonawcy + realna lista utworów z okładkami (biblioteka testowa na emulatorze: Eminem/Metallica/Linkin Park).
- Odkrywaj: 5 wierszy (Radio/Podkasty w kolorze, Audiobooki/Muzyka niezależna/Archiwum wyszarzone z etykietą "Wkrótce").
- Tap na "Audiobooki" → `ComingSoonScreen` z ikoną/kolorem/opisem domeny, przyciskiem wstecz, bottom nav poprawnie UKRYTY (ekran push, nie zakładka root) — potwierdza warunek `showBottomChrome` w `AuroraNavHost`.
- Szukaj: wpisanie "Eminem" przy filtrze "Biblioteka" zwraca realne, poprawne wyniki z lokalnej biblioteki; pozostałe chipy filtra (Playlisty/Genius/Radio/Podk...) widoczne i klikalne.
- Nawigacja między zakładkami zachowuje stan (nie resetuje scrolla/wybranego `LibraryTab`) — `navigateToTab` z `popUpTo/saveState/restoreState` działa zgodnie z zamiarem.

**Świadomie uproszczone względem pierwotnego opisu w tej rundzie** (nie błędy, celowe decyzje): karuzela "Kontynuuj" na Home czyta `playbackState.currentTrack` (aktualnie załadowany utwór) zamiast dedykowanego `PlaybackHistoryRepository.getLastPlayedTrackId()` — prościej, mniej nowego okablowania przez `LibraryViewModel`. **Dogrywka testu** (odtworzono realny utwór z testowej biblioteki na emulatorze): mini-player poprawnie pokazuje się nad `AuroraBottomNav` i na Bibliotece, i na Home (ten sam grający utwór, bez przerwy przy przełączaniu zakładek), a karuzela "Kontynuuj" na Home poprawnie pokazała aktualnie odtwarzany utwór z kolorem `primary` (nie kolorem domeny — zgodnie z zamiarem "cross-domain"). Jedyna faktycznie potwierdzona strata: mini-player hostowany centralnie stracił `sharedTransitionScope`/`animatedVisibilityScope` (dostępne tylko wewnątrz pojedynczego `composable{}` bloku aktywnej trasy) — animacja "okładka leci do Now Playing" nie działa już z poziomu mini-playera (NowPlayingScreen otwarty bezpośrednio nadal ją ma, to nie zostało jeszcze wizualnie zweryfikowane osobno, ale mechanizm jest niezmieniony dla tej ścieżki).

### Poprawka po żywej reakcji usera na zrzut ekranu (ta sama sesja) — porównanie ze Spotify

User zobaczył pierwszy wynik na zrzucie ekranu i porównał go bezpośrednio z appką Spotify (referencyjny zrzut Wrapped 2024, wklejony do czatu): "ma być kolorowo, spójnie, czytelnie, bez powtarzania tych samych elementów wszędzie". Dwie osobne naprawy:

1. **Dolna nawigacja znikała na każdym ekranie poza 4 zakładkami root** (Radio/Podkasty/Audiobooki/Archiwum/Muzyka niezależna/szczegóły itd. jej nie pokazywały) — w Spotify bottom nav jest widoczny WSZĘDZIE poza pełnoekranowym Now Playing. Naprawione: `showBottomChrome` w `AuroraNavHost.kt` zmienione z `currentRoute in BOTTOM_NAV_ROUTES` na `currentRoute != ROUTE_NOW_PLAYING` — jedna linia, zweryfikowane na żywo (Podkasty jako przykład ekranu push).

2. **8 kart domen na Home wyglądało monotonnie** — ciemne tło + mały, stonowany kolorowy krążek 40dp z ikoną, wszystkie karty niemal identyczne. Zlecone jednemu agentowi (opisałem mu referencyjny zrzut Spotify słowo w słowo, bo agent nie widzi obrazków) z jasnym zakresem (tylko `core/designsystem` + `HomeScreen.kt` + `DiscoverScreen.kt`, zakaz dotykania plików drugiej równoległej sesji). Zbudował:
   - Nowy komponent `ColorfulMosaicTile` (core/designsystem/.../components/) — kafel z PEŁNYM, nasyconym kolorem tła (nie tinted-circle), kontrast tekstu liczony automatycznie z luminancji tła.
   - 8 nowych "vivid" kolorów w `Color.kt` (osobne od istniejących stonowanych — te zostały bez zmian tam, gdzie już działają: Szukaj, ComingSoonScreen), jeden wyraźnie różny odcień na domenę.
   - `ContentDomain` dostał drugie pole `vividColor` obok `accentColor` (nie migracja, dodanie) — zero zmian w plikach poza zakresem.
   - Home i Odkrywaj: siatka 2-kolumnowa `ColorfulMosaicTile` zamiast rzędu/listy — ten sam komponent w obu miejscach (spójność, o którą prosił user), różni je tylko zawartość (8 domen vs 5 zewnętrznych źródeł).
   - Jeden hero-banner na Home ("Odkryj miksy Geniusa", gradient fiolet→róż→czerwień z `Brush.linearGradient` z kolorów domen, bez zewnętrznej grafiki).
   - Naprawione osobno po mojej własnej weryfikacji na emulatorze: `HomeScreen`'owy zewnętrzny `Column` nie miał `verticalScroll` — przy 8 dużych kafli + hero banner + karuzele treść była wyższa niż ekran, ostatni rząd (Muzyka niezależna/Archiwum) nachodził na poprzedni i chował się pod bottom nav. Dodano `.verticalScroll(rememberScrollState())`.

Zweryfikowane na żywo na emulatorze po obu poprawkach: mozaika 8 kolorów renderuje się czysto bez nakładania, scroll odsłania hero banner + karuzele, Odkrywaj ma tę samą siatkę z 5 kaflami, bottom nav zostaje widoczny na ekranie Podkastów (push, nie root tab).

## Etap 35: Windows desktop — Kotlin Multiplatform + Compose Desktop, nowy moduł `:desktop`

*Źródło: user, wprost — "przepisz ta apke na windowsa". Przedstawione trzy opcje architektoniczne (KMP+Compose Desktop / osobna natywna appka od zera / emulator WSA bez przepisywania) przez `AskUserQuestion`; user wybrał KMP+Compose Desktop.*

### Kluczowe odkrycie, które odcięło duże ryzyko

`:domain` (Track, TrackIdHasher, EqState, wszystkie modele domenowe + interfejsy repozytoriów) jest już czystym modułem `org.jetbrains.kotlin.jvm` — zero pluginu Androida, zero importów `android.*` poza jednym komentarzem KDoc w `AudioOutput.kt`, który wprost TŁUMACZY, dlaczego interfejs UNIKA `android.media.AudioDeviceInfo`. Zweryfikowane `grep -rln "android\.\|import android"` przed jakąkolwiek pracą. Efekt: `:domain` jest reużywalny 1:1 przez nowy moduł `:desktop` bez żadnej restrukturyzacji na source-sety `androidMain`/`jvmMain` — Android i JVM/Desktop czytają dokładnie te same klasy z tego samego jara.

### Stos technologiczny i wersje (dobrane pod kątem zgodności z istniejącym projektem, nie "najnowsze za wszelką cenę")

- **Kotlin 2.0.21** (już pinowany w projekcie) → Compose Multiplatform ≥1.8.0 wymaga Kotlin ≥2.1.0 (przejście na kompilator K2), więc wybrana linia to **Compose Multiplatform 1.7.3** (najnowsza wersja 1.7.x wg metadanych Maven Central) — jedyna kompatybilna bez podnoszenia Kotlina w całym projekcie.
- **JavaFX 21** (plugin `org.openjfx.javafxplugin` 0.1.0) — realne odtwarzanie lokalnych plików audio. `javax.sound.sampled` (czyste Kotlin/JVM, bez zależności) obsługuje tylko PCM/WAV/AIFF, nie MP3/AAC — za mało dla odtwarzacza muzyki. `javafx.scene.media.MediaPlayer` obsługuje MP3/WAV/AIFF/M4A(AAC); **NIE obsługuje natywnie FLAC/OGG/Opus** — skaner plików (`SUPPORTED_EXTENSIONS` w `Main.kt`) świadomie filtruje tylko do formatów, które faktycznie dają się odtworzyć, zamiast pokazywać w bibliotece utwory, które i tak nie zagrają.
- **`JFXPanel()`** wołany raz na starcie `main()` — jedyny sposób, żeby wystartować toolkit JavaFX bez własnego `javafx.application.Application`/`Stage`, bo Compose Desktop rysuje przez Skiko/AWT, nie JavaFX. Wymaga dodania modułu `javafx.swing` (nie tylko `javafx.controls`/`javafx.media`) — pierwsza wersja tego nie miała i failowała kompilacją (`Unresolved reference 'JFXPanel'`), złapane przed pierwszym udanym buildem.
- Moduł kompiluje się przez zwykłe `compilerOptions { jvmTarget.set(JVM_17) }` + `java { sourceCompatibility/targetCompatibility }`, **nie** przez `kotlin { jvmToolchain(17) }` — ten drugi wymaga auto-detekcji/pobrania konkretnego JDK przez Gradle, co od razu zawiodło (`Cannot find a Java installation... toolchain download repositories have not been configured`) na tej maszynie; reszta modułów w projekcie już i tak używa tego samego wzorca `compilerOptions`+`java{}`, więc `:desktop` jest teraz z nimi spójny, nie wyjątkiem.

### Co appka robi (zweryfikowane: kompiluje się czysto I uruchamia się bez wyjątku — `./gradlew :desktop:run` żyło 45s bez błędu w logu, tylko nieszkodliwe ostrzeżenie JavaFX o unnamed module)

Jedno okno Compose Desktop, ciemny motyw. `JFileChooser` (Swing) do wyboru folderu z muzyką → rekurencyjny skan (`File.walkTopDown()`) filtrowany do wspieranych rozszerzeń → `Track` budowany z samej nazwy pliku/folderu (tytuł = nazwa pliku bez rozszerzenia, wykonawca/album = nazwa folderu nadrzędnego — ten sam wzorzec co `WebDavLibraryRepository.toTrack()`), `id` przez `TrackIdHasher.deriveId("desktop_local", absolutePath)` (własny dyskryminator, żaden inny moduł go nie używa, więc nie koliduje z lokalnymi id z Androida ani innymi źródłami). Lista utworów w `LazyColumn`, klik odtwarza przez `MediaPlayer`, transport play/pause/next/prev, slider pozycji z seekiem, auto-next na końcu utworu.

### Świadomie NIE w tym etapie

EQ/DSP, wizualizator projectM, panel ocen/Genius, chmura (Drive/WebDAV), Radio, Podcasty, trwałe playlisty/ulubione (lista jest sesyjna — czyszczona przy każdym nowym wyborze folderu), prawdziwe tagi audio (ID3/Vorbis — wszystko z nazwy pliku/folderu), okładki albumów, kolejka poza ręcznym next/prev (brak shuffle/repeat/reorder), integracja z systemem (klawisze multimedialne, teraz-odtwarzane w pasku zadań/SMTC), dystrybucja jako `.msi`/`.exe` (`compose.desktop.application.nativeDistributions` już skonfigurowane w `build.gradle.kts`, ale nie zbudowane/przetestowane). To fundament (skan + realne odtwarzanie + reużycie `:domain`), nie parytet funkcji z appką na Androida — każda z powyższych rzeczy to osobna, świadomie odłożona runda.

## Etap 36: Konta + fundament synchronizacji między telefonem a desktopem

*Źródło: user, wprost — "zrob zeby był cross platform miedzy apkami na pc i tel. pusc agentow a to w jaki sposob to rozwijac, stworz fundamenty do tworzenia kont". Poprzedzone researchem (3 agenty: viability `supabase-kt` na JVM/Desktop, czy Firebase w ogóle ma klienta na JVM/Desktop, praktyczne wzorce konfliktów sync dla tego konkretnego kształtu danych appki) — nie od razu implementacja.*

### Decyzja: Supabase, nie Firebase

**Firebase odpada twardo dla desktopu**: brak jakiegokolwiek oficjalnego klienta JVM/Desktop (Android/iOS/Web/Flutter/Unity — nie ma JVM). Jedyna opcja to nieoficjalny, alfa-jakości port społecznościowy (`GitLiveApp/firebase-java-sdk`) z Auth ograniczonym do email/hasła + anonimowego logowania (bez OAuth), albo REST API bez cache offline, albo Admin SDK (server-side, zły model zaufania dla appki desktopowej).

**Supabase-kt (`io.github.jan-tennert.supabase`) ma JVM/Desktop jako pełnoprawny target** — potwierdzone wprost w tabeli wsparcia platform każdego modułu (Auth/Postgrest/Realtime/Storage — wszystkie ✅ na JVM, nie tylko Android/iOS), z własnymi przykładowymi appkami multiplatformowymi (`chat-demo-mpp`) używającymi dokładnie tego zestawu (Auth+Postgrest+Realtime) na Desktop/Android/iOS z jednej bazy kodu.

**Wersja dobrana pod kompatybilność, nie "najnowsza"**: najnowszy supabase-kt (3.8.0) jest budowany Kotlinem 2.4.0 — cztery wersje feature'owe przed naszym pinem 2.0.21, realne ryzyko niezgodności metadanych/ABI. **Supabase-kt 3.0.1** to ostatnie wydanie budowane dokładnie Kotlinem **2.0.21** (potwierdzone przez `gradle/libs.versions.toml` z tagu release, nie przez changelog, który ma literówkę myloną z numerem wydania) — i dodatkowo Ktor 3.0.0 + coroutines 1.9.0, oba dokładnie zgodne z tym, co projekt już ma. Zaakceptowany koszt: brak najnowszych funkcji (passkeys, binary broadcast) do czasu, aż całościowy bump Kotlina (2.4.x, w parze z Compose Multiplatform) będzie miał sens dla całego projektu — nie robimy tego tylko dla jednej biblioteki.

**Silnik HTTP: `ktor-client-cio`, nie `ktor-client-java`** — udokumentowany, wciąż otwarty bug (`supabase-kt#963`): `java-` silnik crashuje `NoClassDefFoundError` w SPAKOWANEJ dystrybucji Compose Desktop (działa w `./gradlew run`, nie w zbudowanym `.exe`). CIO dodatkowo jest jedynym z bezpiecznych wyborów działającym identycznie na Androidzie (który w ogóle nie ma `java.net.http.HttpClient`, na czym opiera się silnik `java`) — jeden silnik, zero rozjazdu między platformami.

### Architektura: `:core:sync` — jedyny PRAWDZIWIE multiplatformowy moduł w projekcie

W przeciwieństwie do `:domain`/`:desktop` (zwykły `kotlin.jvm`, reużywany 1:1 bo Android i JVM współdzielą bajtkod), `:core:sync` to `kotlin("multiplatform")` z `androidTarget()` + `jvm()`. Powód: supabase-kt publikuje OSOBNE warianty per-target (Android dostaje integrację z AndroidX/Custom Tabs do OAuth, JVM dostaje swój odpowiednik) — zwykły moduł `kotlin.jvm` dostałby wszędzie wariant "jvm", tracąc integrację specyficzną dla Androida. Zero adnotacji Hilt wewnątrz modułu (Hilt+KSP dla multiplatformowego `androidTarget` to dodatkowe ryzyko, którego nie było powodu podejmować) — wiązanie w `:app` robi zwykły ręczny `@Provides` w `SyncModule.kt`, dokładnie tym samym wzorcem co `DatabaseModule.provideAuroraDatabase` (zewnętrzna klasa bez adnotacji, budowana ręcznie w module Hilt). Na desktopie — zero Hilt w ogóle (appka go nie ma), więc `AccountPanel.kt` buduje `SupabaseAuthRepository` ręcznie przez `by lazy`.

Zależności supabase-kt w `:core:sync` zadeklarowane jako `api`, nie `implementation` — konsumenci (`:app`, `:desktop`) trzymają typy `SupabaseClient`/`SessionStatus` bezpośrednio w swoich ViewModelach/panelach, więc muszą być widoczne transytywnie. Złapane na żywo: pierwsza wersja z `implementation` kompilowała `:core:sync` samodzielnie, ale `:app:kspDebugKotlin` failował `error.NonExistentClass` przy próbie zbindowania Hilt-modułu zwracającego `SupabaseClient`.

### Konfiguracja: wstrzykiwana, nie zahardkodowana — ale nie z tego samego powodu co Podcast Index

Supabase anon key NIE jest sekretem w sensie bezpieczeństwa (cały dostęp do danych idzie przez RLS per-user w Postgresie — to jest CAŁY sens pary "anon key + RLS" w architekturze Supabase, w przeciwieństwie do klucza Podcast Index, gdzie jeden wspólny klucz dla wszystkich userów łamie limity per-developer, patrz Etap 32). Mimo to URL/klucz idą z `local.properties`→`BuildConfig` na Androidzie (ten sam mechanizm co świeżo dodany `JAMENDO_CLIENT_ID`, Etap 37) i ze zmiennych środowiskowych (`SUPABASE_URL`/`SUPABASE_ANON_KEY`) na desktopie — żeby appka działała "od razu po sklonowaniu repo" bez URL-a konkretnego projektu Supabase w historii gita. Brak konfiguracji NIE crashuje appki na żadnej platformie: `AccountScreen`/`AccountPanel` po prostu pokazują pusty formularz logowania (Android — próba logowania po prostu zwróci błąd sieciowy z pustego URL-a, złapany przez `Result<Unit>`) albo jawny komunikat "Konto niedostępne, ustaw zmienne środowiskowe" (desktop — sprawdzone przy starcie, zanim appka w ogóle spróbuje zbudować klienta).

### Co jest zrobione i zweryfikowane

`AuthRepository` (`:core:sync`, wspólny) — `signUp`/`signIn`/`signOut`/`sessionStatus: StateFlow<SessionStatus>`/`currentUserId`, owinięte w `Result<Unit>` (appka i tak musi pokazać błąd w UI, więc wymuszenie obsługi w miejscu wywołania jest tańsze niż osobna hierarchia wyjątków). Realna implementacja przez `supabase.auth.signUpWith(Email)`/`signInWith(Email)`/`signOut()` — API zweryfikowane przez realne fetche dokumentacji referencyjnej Kotlin (nie zgadywane), potwierdzone kompilacją. **Zweryfikowane na żywo na emulatorze**: nowa ikona "Konto" w `LibraryScreen` (obok istniejącej ikony "Źródła muzyki" — dwa różne pojęcia: skąd appka BIERZE utwory vs. czyje konto SYNCHRONIZUJE dane) otwiera `AccountScreen` z formularzem logowania, `uiautomator dump` potwierdził poprawną hierarchię (pola e-mail/hasło, przycisk "Zaloguj się" poprawnie disabled dopóki hasło <6 znaków), zero crasha. Desktop: `:desktop:compileKotlin` + `:app:compileDebugKotlin` + `:core:sync:compileKotlinJvm`/`compileDebugKotlinAndroid` wszystkie czyste. Pełne, żywe potwierdzenie logowania (realny e-mail/hasło przez prawdziwy projekt Supabase) **NIE zweryfikowane** — wymaga, żeby user założył własny projekt Supabase (supabase.com, darmowy tier) i wkleił URL+anon key do `local.properties`/zmiennych środowiskowych; dokładnie ten sam rodzaj zewnętrznej zależności co Etap 30 (Google Cloud Console dla Google Drive) — appka jest gotowa, blocker jest po stronie konfiguracji zewnętrznego serwisu, nie kodu.

### Zaprojektowane, ale NIE wdrożone w tej rundzie — synchronizacja danych

Sam research (streszczenie, pełne uzasadnienie w historii sesji) ustalił konkretny, praktyczny wzorzec zamiast pełnego CRDT:

- **Baseline**: Postgres + RLS (`user_id = auth.uid()`) + kolumna `updated_at` ustawiana PO STRONIE SERWERA (nigdy zegarem klienta — inaczej zły zegar telefonu wygrywa nad realnie nowszą zmianą) + okresowy/on-resume delta pull po kursorze (monotoniczny `bigserial`, nie `updated_at`, żeby uniknąć problemów zegara przy samym pytaniu "co się zmieniło"). To sprawdzony wzorzec (Anki sync, Obsidian Sync), nie "naiwny" — pod warunkiem miękkiego usuwania (`deleted_at`, nie prawdziwy `DELETE`), inaczej urządzenie offline może "wskrzesić" skasowany rekord.
- **Per typ danych**: Ulubione/subskrypcje podcastów — wiersze + tombstone, unia zbiorów, trywialne. EQ presety — całościowy LWW po `updated_at`, wystarczające (rzadka kolizja, niska stawka). Pozycja odtwarzania podcastu — LWW po `updated_at` serwera, **nie** `max(pozycja)` (świadome cofnięcie się usera nie może przegrywać ze starym, większym numerem — dokładnie ten błąd zgłaszany w Pocket Casts). Historia odtworzeń (wejście do lokalnego Geniusa) — **świadomie NIE synchronizowana w ogóle**: append-only, appka i tak liczy rekomendacje lokalnie per urządzenie (zgodne z zasadą "silnik rekomendacji zostaje lokalny", tylko dane KURACJI usera — Ulubione/Playlisty/EQ/subskrypcje — mają sensować się synchronizować). **Playlisty — jedyny naprawdę trudny przypadek**: całościowy LWW jednego wiersza/blobu JSON cichcem KASUJE tydzień edycji jednego urządzenia, jeśli oba były offline i edytowały tę samą playlistę. Właściwy wzorzec: `playlist_items` jako osobne wiersze z tombstone (jak Ulubione) + **fractional indexing** (klucz pozycji jako sortowalny string/ułamek, nie integer) do mergowania kolejności bez kolizji — technika z Figmy/Lineara, nie pełny CRDT.
- **Problem tożsamości utworu między urządzeniami** (odkryty PRZED researchem, przy czytaniu istniejących encji Room): `FavoriteTrackEntity`/`PlaylistTrackEntity` kluczują po `Track.id`, który dla RADIA/PODCASTÓW/WEBDAV/DRIVE jest już przenośny (`TrackIdHasher.deriveId(source, nativeId)` — ten sam hash na każdym urządzeniu, bo `nativeId` to stały identyfikator źródła: guid odcinka, uuid stacji, href WebDAV, fileId Drive'a). Dla LOKALNYCH plików `Track.id` NIE jest przenośny — na Androidzie to autoinkrementowane id z MediaStore, na desktopie to `TrackIdHasher.deriveId("desktop_local", absolutePath)` — ten sam fizyczny utwór dostaje RÓŻNE id na dwóch urządzeniach. Rozwiązanie (zaprojektowane, niezaimplementowane): osobny, przenośny "fingerprint" (hash znormalizowanego `artysta|album|tytuł`) używany WYŁĄCZNIE w warstwie sync dla lokalnych utworów, rozwiązywany z powrotem na lokalny `Track.id` przez dopasowanie do biblioteki na każdym urządzeniu osobno; utwór bez dopasowania pokazuje się jako "niedostępny na tym urządzeniu" (wzorzec znany z Plex/Spotify), nie znika po cichu.

To zostaje jako udokumentowany plan na kolejną rundę — implementacja realnego push/pull dla Ulubionych/Playlist/Podcastów/EQ, migracja lokalnych tabel Room o `deletedAtMs`/kolumny sync, `SyncManager` orkiestrujący. Fundament tej rundy to konto działające identycznie na obu platformach, nie sama synchronizacja danych.

## Etap 38: Audyt DESIGN.md vs kod (5 agentów) + naprawa realnych luk

*Źródło: user — "spradz czy szystko z listy jest zrobione - jesli znajdziesz luki to sie nimi zajmij". Pięć agentów równolegle: Etap 20a-20h, Android Auto (Etap 34), dostawcy chmury (Etap 12/30), starszy backlog (Etap 10 część 2/11/18), brak wpisu dla modułu desktop.*

### Wynik audytu — w większości dryf dokumentacji, nie realne luki

Zdecydowana większość "niezaznaczonych" checkboxów w Etapie 20 i starszym backlogu (Etap 10 część 2/11/18) okazała się już zbudowana — tylko pod INNYM numerem etapu niż ten, gdzie plan pierwotnie to opisywał (np. wyszukiwarka biblioteki/ekran Albumu/Playlisty/Ulubione z Etapu 20c/20h są gotowe, dostarczone w Etapie 22/23; naprawa Google Drive z 20f gotowa w Etapie 30; "ambient sleep" dimming i per-ekranowe WindowInsets z Etapu 18 gotowe, ale nigdy nie odhaczone; Genius "avoid recently skipped" z 20e gotowe w Etapie 27). Android Auto (Etap 34) — zero rozbieżności, drzewo przeglądania w pełni podłączone (`PlaybackService` realnie dziedziczy `MediaLibraryService`, manifest ma poprawne `automotive_app_desc.xml`), jedyny brakujący krok to test na żywym Desktop Head Unit — dokładnie to, co dokumentacja i tak już uczciwie zaznacza jako otwarte. Dostawcy chmury poza Google Drive/WebDAV (OneDrive/Dropbox/iCloud/pCloud/Box/Plex/Jellyfin) — zgodnie z dokumentacją, nadal tylko wyszarzone "Wkrótce" bez kodu, brak rozbieżności.

### Realne luki znalezione i naprawione w tej rundzie

- **EQ nie przeżywał restartu appki** (Etap 20d) — `EqRepositoryImpl` był czystym `MutableStateFlow` w pamięci od Etapu 2, z komentarzem "trwały zapis dojdzie" nigdy niezrealizowanym. Naprawione: nowa `EqStateEntity`/`EqStateDao` (Room, wersja bazy 9→10), gainy 10 pasm trzymane jako CSV (stała lista częstotliwości, normalizacja nie dodaje nic poza narzutem JOIN-a), wczytywane raz przy starcie, zapisywane przy KAŻDEJ zmianie ale z `debounce(500ms)` — bez tego przeciąganie suwaka w `EqualizerSheet` (`onValueChange`, nie `onValueChangeFinished`) waliłoby Room dziesiątkami zapisów/sekundę.
- **Przycisk play/pause w mini-playerze miał 36dp obszar dotykowy** (Etap 20a) — poniżej minimalnego 48dp (Material/WCAG). Naprawione bezpośrednio w `MiniPlayerBar.kt` (36dp→48dp, ikona 20dp→24dp dla proporcji).

### Świadomie NIE naprawione w tej rundzie (realny, ale świadomie odłożony backlog)

Reszta Etapu 20a (rollout `AuroraIconButton`/`AuroraMotion` po całej appce, konsolidacja tokenów koloru/kształtu), 20d reszta (typ filtra/Q na pasmo EQ, nie tylko peaking), 20e (edytor krzywej EQ, A/B compare), genre-grouping w bibliotece, LUFS/peak metering + ekran Audio Lab, materializacja rankingu Geniusa przez WorkManager, layout pod tablet, `drive.file`+Picker dla Google Drive (Etap 30 — blokowane też na akcji usera w Google Cloud Console, nie tylko kodzie), pozostali dostawcy chmury. Żadne z nich nie jest "zepsute" — to udokumentowany, świadomie nieporuszony w tej rundzie backlog, nie regresja.

## Etap 39: Home — przebudowa na dashboard "art-first" (koniec mozaiki domen na Home)

*Źródło: user zobaczył zrzut ekranu Spotify Home/Wrapped i ocenił, że Etap 37 (mozaika 8 kolorowych kafli domen) to "poziom 2005 roku" — poprosił o realny wzorzec Spotify Home: pod paskiem szukaj mały ostry rząd "Szybkiego dostępu", potem "Biblioteka" → albumy → "Playlisty" → playlisty "w kolejce", okładka/logo zamiast płaskiego koloru wszędzie gdzie to możliwe, bez usuwania "lupy" — usunięcie ZAKŁADKI Szukaj z dołu, bo duplikuje pole wyszukiwania na Home. Dwie rundy `AskUserQuestion` rozstrzygnęły: (1) mozaika 8 domen ZNIKA z Home całkowicie (zostaje na Odkrywaj, bez zmian), (2) dolny nav 3 zakładki (Szukaj usunięta), (3) Genius/Podkasty dostają własne pełne shelfy pod Playlistami, nie tylko kafle w Szybkim dostępie. Pytanie o dokładny rozmiar shelfa albumów ("4 na sztywno" vs "pełna lista, kilka naraz w viewporcie") user oddał do oceny estetycznej ("okładki trochę nas ratują") — wybrano pełną przewijaną listę (108dp karty), żeby Biblioteka/Playlisty/Genius/Podkasty były tym samym, spójnym wzorcem "shelf z peekiem", nie wyjątkiem.*

### Co zbudowano

- **3 nowe komponenty w `core:designsystem`**: [`ArtworkQuickTile`](core/designsystem/src/main/kotlin/com/aurora/player/designsystem/components/ArtworkQuickTile.kt) (56dp, promień `AuroraShapes.extraSmall`=6dp, okładka flush z lewą krawędzią — górny rząd "Szybkiego dostępu"), [`AlbumArtCard`](core/designsystem/src/main/kotlin/com/aurora/player/designsystem/components/AlbumArtCard.kt) (108dp kwadrat + tekst PONIŻEJ, shelf albumów) i [`ArtworkOverlayCard`](core/designsystem/src/main/kotlin/com/aurora/player/designsystem/components/ArtworkOverlayCard.kt) (160dp, tekst NA okładce ze scrimem, reużyty identycznie dla Playlist/Genius/Podkastów — to jest dosłowna realizacja "zamiast statycznego koloru tła odtwarzamy okładkę albumu bądź logo podcastu"). Oba "shelfowe" komponenty dzielą jeden promień 10dp (`HomeShelfCardShape`) — świadomie ostrzejszy niż `large`=24dp z Biblioteki, inny język wizualny dla dashboardu.
- **`HomeScreen.kt` przepisany od zera**: usunięta cała siatka `ColorfulMosaicTile`×8 + `HomeHeroBanner` z Etapu 37 (mozaika zostaje bez zmian na `DiscoverScreen`). Nowa struktura: nagłówek+konto → pole szukaj → `QuickAccessGrid` (2 kolumny, max 6 kafli: ostatnio odtwarzany utwór, do 2 najnowszych playlist, 1 miks Geniusa, kafel "Wszystkie źródła"→Odkrywaj z fallbackiem `AuroraAccentFallback`) → "Biblioteka" + shelf albumów (`groupTracksByAlbum`, sort po `max(dateAddedMs)` malejąco — jedyna "recency" dostępna bez nowej pracy w danych, `Track` nie ma play-counta) → "Playlisty" + shelf (cała lista `playlists`) → "Miksy Geniusa" + shelf → "Podkasty" + shelf (`Podcast.artworkUrl` wprost). Okładka playlisty/miksu = pierwszy `Track.albumArtUri` znaleziony wśród jej utworów (`firstArtworkUrl` — brak dedykowanego pola okładki na `Playlist`/`GeniusMix`, to czysto derywowane, jak `groupTracksByAlbum`).
- **Dolny nav: 4→3 zakładki** (`AuroraBottomNav.kt`, `AuroraRoutes.kt` — `BOTTOM_NAV_ROUTES` też skrócony). "Szukaj" zostaje jako trasa/ekran (dostępna z pola na Home), przestaje być zakładką. Pasek: `NavigationBar(containerColor = Color.Transparent)` + `Haze thin` blur na wrapującym `Column`, hairline `White@8%` na górnej krawędzi — fallback bez `hazeState`: flat `surface@92%`, nigdy w pełni niewidzialny.
- **Naprawiony przy okazji realny gap**: `AuroraNavHost` miał `rememberHazeState()` NIGDZIE na poziomie hosta — `MiniPlayerBar` od Etapu 37 przyjmuje parametr `hazeState`, ale wywołanie w `AuroraNavHost` nigdy go nie przekazywało, więc mini-player nigdy nie miał prawdziwego blura, tylko płaski fallback. Naprawione: jeden `hazeState` na poziomie hosta, `NavHost` oznaczony `Modifier.hazeSource`, przekazany do `MiniPlayerBar` i nowego `AuroraBottomNav`. Lokalne `rememberHazeState()` w `NowPlayingScreen`/`LibraryScreen`/`EqualizerSheet` zostają bez zmian (osobne okna blur, np. pod EQ sheet).
- Zweryfikowane `./gradlew :core:designsystem:compileDebugKotlin` + `:app:compileDebugKotlin` (`--rerun-tasks`, oba `BUILD SUCCESSFUL`, zero nowych błędów/ostrzeżeń poza istniejącym w appce wzorcem deprecacji `Icons.Filled.QueueMusic`→AutoMirrored). **NIE zweryfikowane wizualnie na urządzeniu/emulatorze** — ta sesja nie miała dostępu do Android emulatora ani `adb`, tylko do kompilatora Kotlina.

### Świadomie NIE zrobione w tej rundzie

Bez zmian: `LibraryScreen` (Biblioteka jako zakładka, z własną wyszukiwarką/tabami/gruping), `DiscoverScreen` (mozaika 8 domen), `SearchScreen`. Brak nowego pola sortowania/play-count w `Track` — recency albumów oparta wyłącznie o `dateAddedMs`. Brak testów UI/snapshotów dla nowych komponentów.

### Poprawka po testach na żywym emulatorze (ten sam Etap, druga runda)

*Źródło: pierwsza wersja przetestowana na żywo (`Aurora_Test` AVD, Android 15) zamiast tylko przez kompilator — user zobaczył zrzuty ekranu i zgłosił: (1) kafel "Wszystkie źródła" w `QuickAccessGrid` (ikona kompasu) wizualnie powiela ikonę taba "Odkrywaj" na dole ("to nie pasuje i powiela przycisk"), całą siatkę Szybkiego Dostępu uznał za niepotrzebną, (2) ekran ma być "żywy" — wypełniony realną treścią z Podkastów/Archiwum, a jak user niczego nie subskrybuje/nie ma historii, to propozycjami, nie pustką, (3) później dopisał też Audiobooki i Radio "na tych samych zasadach".*

- **Realny bug znaleziony na żywym urządzeniu, niewidoczny przy samej kompilacji**: `Track.albumArtUri`/`ArchiveItem.coverUrl` itp. bywają NIEPUSTYMI URI, które i tak się nie wczytają (MediaStoreScanner buduje URI przez `ContentUris.withAppendedId` niezależnie od tego, czy album faktycznie ma okładkę — `MediaProvider` loguje wtedy `IOException: No album art found`). Kod sprawdzający tylko `artworkUrl != null` przed pokazaniem `AsyncImage` renderował w tym wypadku czarną/przezroczystą dziurę, NIE fallback. Naprawione we wszystkich trzech komponentach z tej rundy (`AlbumArtCard`, `ArtworkOverlayCard`, wtedy jeszcze `ArtworkQuickTile`): fallback (gradient/ikona) rysowany ZAWSZE jako spód, `AsyncImage` zawsze na wierzchu — porażka Coila ujawnia fallback, nie pustkę. Ogólna lekcja: `!= null` na URI z MediaStore nie znaczy "da się wczytać".
- **Usunięty `QuickAccessGrid` + komponent `ArtworkQuickTile` (skasowany plik)** — Home zaczyna się teraz od razu od shelfów po polu szukaj, bez odrębnej siatki miniaturowych kafli-skrótów. Usunięte z sygnatury `HomeScreen`: `onOpenDomain`, `onOpenDiscover` (nieużywane po usunięciu siatki).
- **Podkasty NIGDY nie są puste**: subskrypcje jeśli są (nagłówek "Podkasty"), inaczej `loadTopPodcasts(countryCode)` → `podcastSearchResults` jako "Podkasty dla Ciebie". Kraj: `resolveCountryCodeFromLastKnownLocation` (może wrócić `null` na emulatorze/bez zgody — fallback `"US"`), rozwiązywany RAZ w `HomeScreen` i współdzielony z Radiem (nie dwa niezależne zapytania do Geocodera o to samo).
- **Nowa sekcja "Archiwum"** — `loadPersonalizedArchive()` (już istniejące w `LibraryViewModel`, samo decyduje personalizacja-vs-`LIVE_MUSIC`-fallback), karty `ArtworkOverlayCard` z `ArchiveItem.coverUrl`. Nowy callback `onOpenArchiveItem` → `archive_item_detail/{identifier}`.
- **Nowa sekcja "Audiobooki"** (ten sam wzorzec co Podkasty) — biblioteka (`audiobookLibrary`) jeśli user coś dodał, inaczej `loadRecommendedAudiobooks(Locale.getDefault().isO3Language)` → "Audiobooki dla Ciebie". Nowy callback `onOpenAudiobook` → `audiobook_detail/{id}` (ten sam route co `AudiobooksScreen`).
- **Nowa sekcja "Radio"** — Radio nie ma koncepcji "biblioteki/zapisanych stacji" w tej appce, więc zawsze `loadTopRadioStations(countryCode)`; tap = `viewModel.onPlayRadioStation(station)` odtwarza OD RAZU (ten sam wzorzec co `RadioScreen`), bez ekranu szczegółów — bo radio nie ma "szczegółów", tylko strumień.
- **Znalezione, ale NIE naprawione w tej rundzie**: dolny pasek nawigacji na `Aurora_Test` AVD renderuje się jako płaski półprzezroczysty scrim, NIE prawdziwy blur, mimo poprawnego okablowania (`hazeSource`/`hazeEffect`, identyczne jak istniejący `MiniPlayerBar`). `adb shell getprop ro.hardware.egl` → `emulation` (software EGL, nie prawdziwe GPU passthrough) — `RenderEffect`/`BlurEffect` (wymagane przez Haze na API 31+) typowo nie działa pod software-EGL, tylko na prawdziwym GPU. To zgodne z udokumentowaną w sekcji 2.4 appki degradacją (scrim jako zaprojektowany fallback), więc kod NIE jest zmieniany na podstawie tego jednego emulatora — **wymaga potwierdzenia na prawdziwym telefonie albo emulatorze z włączonym host GPU** zanim uznamy blur za faktycznie działający.
