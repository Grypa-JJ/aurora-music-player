cmake_minimum_required(VERSION 3.22.1)
project(projectm_jni)

# Pakiet Prefab z AAR projectM (nagłówki C + libprojectM-4.so per ABI) — patrz
# core/projectm/build.gradle.kts (buildFeatures.prefab) i local-maven-repo/. Moduł
# projectM-4-playlist świadomie NIE linkowany od Etapu 16 — wybór presetu jest w Kotlinie
# (patrz projectm_jni.cpp, PresetLibrary.kt).
find_package(projectm-android REQUIRED CONFIG)

add_library(projectm_jni SHARED projectm_jni.cpp)

find_library(log-lib log)
find_library(gles3-lib GLESv3)

target_link_libraries(projectm_jni
    projectm-android::projectM-4
    ${gles3-lib}
    ${log-lib}
)
