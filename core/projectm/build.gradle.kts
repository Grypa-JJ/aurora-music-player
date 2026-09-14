plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aurora.player.projectm"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 26

        ndk {
            // Brak x86 (32-bit) w AAR projectM (patrz local-maven-repo) — nieistotne, emulatory
            // i urządzenia 32-bit x86 praktycznie wymarły.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                // Ta sama STL co w prekompilowanym libprojectM-4.so (patrz abi.json w AAR:
                // "stl": "c++_shared") — inna STL między .so w tym samym procesie = crash/ODR.
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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
        // prefab: żeby Gradle/AGP potrafiło skonsumować pakiet Prefab (nagłówki C + .so per ABI)
        // z AAR projectM zamiast zwykłego linkowania jarowego.
        prefab = true
        compose = true
    }
}

dependencies {
    // Zwendorowane lokalnie w local-maven-repo/ — patrz settings.gradle.kts i DESIGN.md Etap 9
    // (kwestia licencji LGPL-2.1: musi zostać osobną, dynamicznie ładowaną biblioteką .so,
    // nigdy statycznie wtopioną w kod appki).
    implementation("net.protyposis.projectm-unofficial:projectm-android:4.1.7")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.lifecycle.runtime.compose)
}
