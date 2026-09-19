import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Etap 36: URL + anon key projektu Supabase — anon key NIE jest sekretem w sensie
// bezpieczeństwa (cały dostęp do danych idzie przez RLS per-user w Postgresie, ten klucz tylko
// identyfikuje projekt), ale mimo to wstrzykiwany z local.properties (gitignored), nie
// zahardkodowany w źródle — tak appka działa "od razu po sklonowaniu repo" bez wycieku URL-a
// konkretnego projektu Supabase w historii gita. Puste stringi jako domyślne, jeśli klucze
// nie są ustawione — appka ma wtedy po prostu wyłączone konto/sync (patrz SupabaseModule.kt).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.aurora.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aurora.player"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY", "")}\"")
        // Etap 37: client_id Jamendo ("Muzyka niezależna") — jak Supabase wyżej, wstrzykiwany z
        // local.properties (gitignored), nie zahardkodowany. To client_id aplikacji (limit 35k
        // zapytań/mies. jest per-aplikacja), nie sekret per-user — bezpieczne do wbudowania na
        // stałe, inaczej niż klucz Podcast Index (patrz PodcastIndexCredentialStore).
        buildConfigField("String", "JAMENDO_CLIENT_ID", "\"${localProperties.getProperty("JAMENDO_CLIENT_ID", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Biblioteki Google API client (google-auth-library-*, transitywne z google-api-client-android
    // / google-api-services-drive) dublują pliki META-INF między jarami — klasyczny, udokumentowany
    // problem tych bibliotek na Androidzie, nie coś specyficznego dla tego projektu.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:projectm"))
    implementation(project(":core:sync"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.guava)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.palette.ktx)
    implementation("androidx.compose.animation:animation")

    // Drag-and-drop reorder kolejki/playlist (Etap 23) — brak oficjalnego API w Compose
    // Foundation dla LazyColumn, hand-rolling drag physics byłoby wyższym ryzykiem (jank, złe
    // progi zamiany) niż ta jedna mała, aktywnie utrzymywana biblioteka.
    implementation(libs.reorderable)

    implementation(libs.coil.compose)
    // Etap 45, zgłoszenie: okładki podkastów/Archiwum/audiobooków na Home nigdy się nie
    // ładowały — Coil 3 (w przeciwieństwie do Coil 2) NIE ma domyślnie żadnego fetchera dla
    // http(s)://, `coil-network-okhttp` trzeba dodać JAWNIE (patrz oficjalny przewodnik migracji:
    // "coil-core no longer supports loading images from the network by default"). Bez tego każdy
    // AsyncImage z URL-em sieciowym cicho nic nie renderował — Coil nie loguje błędu, po prostu
    // nie ma czym obsłużyć żądania. Lokalne okładki (`content://`/`file://`, LocalAlbumArtRepository,
    // Etap 42/44) działały cały czas, bo te fetchery SĄ wbudowane w coil-core — to właśnie
    // maskowało ten brak przez większość sesji.
    implementation(libs.coil.network.okhttp)
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // Biblioteka Google Drive — patrz DESIGN.md, sekcja "Chmura". Sign-in przez
    // play-services-auth (GoogleSignInClient), pliki przez REST Drive API v3
    // (natywne "Drive Android API" jest deprecated od Google, patrz komentarz w kodzie).
    implementation(libs.play.services.auth)
    implementation(libs.google.http.client.gson)
    implementation(libs.google.api.client.android) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation(libs.google.api.services.drive) {
        exclude(group = "org.apache.httpcomponents")
    }

    // NAS/WebDAV (Etap 12/22) — protokół prosty (PROPFIND po HTTP, Basic Auth), bez OAuth/SDK
    // dostawcy. OkHttp już jest transitywną zależnością przez coil-network-okhttp — deklarujemy
    // ją tu jawnie, bo używamy jej API bezpośrednio (nie tylko przez Coil).
    implementation(libs.okhttp)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Tłumaczenie transkrypcji podkastów (EN→PL, DESIGN.md Etap 54) — model offline pobierany
    // NA URZĄDZENIU, zero klucza API, zero kosztu za żądanie (w przeciwieństwie do chmurowego
    // tłumaczenia/STT) — user świadomie wybrał ten kompromis (transkrypcja z feedu, nie live STT).
    implementation(libs.mlkit.translate)

    testImplementation(libs.junit)
}
