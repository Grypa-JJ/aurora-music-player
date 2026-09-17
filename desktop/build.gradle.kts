import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.javafx)
}

// Odtwarzanie lokalnych plików audio (MP3/FLAC) po stronie desktopu — javax.sound.sampled
// wspiera tylko PCM/WAV/AIFF, więc realny playback wymaga JavaFX Media. Patrz DESIGN.md.
javafx {
    version = "21"
    // javafx.swing dostarcza JFXPanel — jedyny sposób, żeby wystartować JavaFX toolkit bez
    // własnego javafx.application.Application/Stage (Compose Desktop rysuje przez Skiko/AWT).
    modules("javafx.controls", "javafx.media", "javafx.swing")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Reużywamy :domain 1:1 — moduł jest już czystym Kotlin/JVM bez zależności od Androida
    // (zweryfikowane: brak importów android.* poza jednym komentarzem KDoc). Patrz DESIGN.md.
    implementation(project(":domain"))
    // Etap 36: konto + sync — ten sam moduł co po stronie Androida, dostaje wariant "jvm"
    // supabase-kt (Realtime/Auth/Postgrest pełnoprawne na JVM/Desktop, zweryfikowane researchem
    // przed implementacją — patrz DESIGN.md Etap 36).
    implementation(project(":core:sync"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

compose.desktop {
    application {
        mainClass = "com.aurora.player.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Aurora"
            packageVersion = "0.1.0"
            description = "Aurora — lokalny odtwarzacz muzyki"
            vendor = "Aurora"
        }
    }
}
