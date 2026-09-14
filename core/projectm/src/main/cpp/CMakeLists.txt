cmake_minimum_required(VERSION 3.22.1)
project(projectm_jni)

# Pakiet Prefab z AAR projectM (nagłówki C + libprojectM-4.so/libprojectM-4-playlist.so per ABI) —
# patrz core/projectm/build.gradle.kts (buildFeatures.prefab) i local-maven-repo/.
find_package(projectm-android REQUIRED CONFIG)

add_library(projectm_jni SHARED projectm_jni.cpp)

find_library(log-lib log)
find_library(gles3-lib GLESv3)

target_link_libraries(projectm_jni
    projectm-android::projectM-4
    projectm-android::projectM-4-playlist
    ${gles3-lib}
    ${log-lib}
)
