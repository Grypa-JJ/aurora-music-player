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
- [x] Etap 2: własny equalizer — `BiquadFilter` (formuły RBJ Audio Cookbook, peakingEQ, **zweryfikowane** przez porównanie 1:1 z oryginalnym tekstem cookbooka), `EqualizerAudioProcessor` (Media3 `BaseAudioProcessor`, kaskada 10 pasm ISO na kanał, wpięty przez `EqualizerRenderersFactory`/`DefaultAudioSink.Builder.setAudioProcessors` — zweryfikowane ze źródłem androidx/media tag 1.5.0, bo dokumentacja online opisuje już nowszą, zmienioną nazwę metody), `EqRepository` (in-memory StateFlow, presety Flat/Bass Boost/Vocal/Rock/Electronic), `EqualizerSheet` (bottom sheet z pigułkami presetów + 10 pionowych suwaków przez `VerticalSlider`). **Do zweryfikowania na słuchawkach** — matematyka jest poprawna, ale nie mam jak przesłuchać efektu w tym środowisku; jeśli coś brzmi nie tak (trzaski, brak efektu, zniekształcenia), zgłoś.
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

- [ ] Etap 9 (W TOKU): **zmiana silnika wizualizera na projectM** (open-source, kompatybilny z Milkdrop, używany m.in. w Winampie/VLC) zamiast dalszego dostrajania własnego Compose Canvas z Etapu 8 — decyzja użytkownika po zobaczeniu v2 na żywo. Dodatkowo zmiana UX: okładka albumu i wizualizer mają dzielić tę samą ramkę na Now Playing (tap = przełącznik w miejscu, nie pełny ekran od razu), z osobnym przyciskiem rozwijającym do pełnego ekranu.
  - Wymaga: integracji natywnej (C++/NDK/JNI, CMake w Gradle), renderu OpenGL ES osadzonego w Compose przez `AndroidView` (GLSurfaceView/TextureView), karmienia projectM surowymi próbkami PCM (mamy je już w `EqualizerAudioProcessor`) zamiast własnej analizy pasmowej, zweryfikowania licencji projectM i presetów `.milk` pod kątem dystrybucji w apce komercyjnej.
  - Research (licencja, wykonalność Android/NDK, dokładne API projectM, presety do spakowania) w toku równolegle w tle przed pisaniem kodu integracji — zbyt kosztowna w budowie zmiana architektury, żeby zgadywać.

### Jak zbudować / uruchomić

```bash
./gradlew.bat :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# albo od razu na podłączone urządzenie/emulator:
./gradlew.bat :app:installDebug
```
