import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

/**
 * Etap 36: warstwa kont + synchronizacji między telefonem (Android) a desktopem (Windows) —
 * jedyny moduł w projekcie, który jest PRAWDZIWYM Kotlin Multiplatform (androidTarget + jvm),
 * nie "zwykłym" `kotlin.jvm` reużywanym 1:1 jak :domain. Powód: supabase-kt publikuje OSOBNE
 * warianty per-target (Android dostaje integrację z AndroidX/Custom Tabs do OAuth, JVM dostaje
 * odpowiednik dla desktopu) — zwykły moduł kotlin.jvm dostałby wszędzie sam wariant "jvm",
 * tracąc integrację specyficzną dla Androida. Patrz DESIGN.md Etap 36.
 */
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain"))
            // `api`, nie `implementation` — konsumenci (:app, :desktop) trzymają w swoich
            // ViewModelach/modułach DI typy z tych bibliotek bezpośrednio (SupabaseClient,
            // SessionStatus), więc muszą być widoczne transytywnie, nie tylko wewnątrz tego modułu.
            api(libs.supabase.auth)
            api(libs.supabase.postgrest)
            implementation(libs.ktor.client.cio)
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
    }
}

android {
    namespace = "com.aurora.player.sync"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
