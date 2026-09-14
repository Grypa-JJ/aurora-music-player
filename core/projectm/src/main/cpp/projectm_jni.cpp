// Cienki most JNI do libprojectM-4 (C API) — patrz DESIGN.md Etap 9. Żadnej logiki wizualnej
// tutaj: to tylko przełożenie wywołań Kotlin 1:1 na funkcje C z projectM-4/projectM.h i
// projectM-4/playlist.h (nagłówki z Prefab, wersja 4.1.7 — patrz local-maven-repo).
//
// Wątkowość (zweryfikowane w oficjalnym API-Reference/Integration-Quickstart-Guide projectM):
// create/destroy/setWindowSize/renderFrame/playlist* MUSZĄ być wołane z tego samego wątku, który
// ma aktywny kontekst OpenGL (u nas: wątek GL surface'u) — to twardy wymóg samego OpenGL, nie
// tylko projectM. addPcmInt16 można wołać z osobnego wątku audio równolegle z renderem: projectM
// nie synchronizuje wewnętrznie bufora PCM, więc realny wyścig da co najwyżej lekko "zamazaną"
// klatkę wizualizacji, nigdy crash (udokumentowane zachowanie, nie błąd).
#include <jni.h>
#include <android/log.h>

#include <projectM-4/projectM.h>
#include <projectM-4/playlist.h>

#define LOG_TAG "ProjectMJni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct ProjectMContext {
    projectm_handle instance = nullptr;
    projectm_playlist_handle playlist = nullptr;
};

ProjectMContext* toContext(jlong handle) {
    return reinterpret_cast<ProjectMContext*>(handle);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_aurora_player_projectm_ProjectMNative_create(JNIEnv*, jobject) {
    auto* ctx = new ProjectMContext();
    ctx->instance = projectm_create();
    if (ctx->instance == nullptr) {
        LOGE("projectm_create() zwrocil null - brak aktywnego kontekstu OpenGL w tym watku?");
        delete ctx;
        return 0;
    }
    // projectm_playlist_create z niepustym instance od razu podpina callback "preset switch
    // requested" (patrz projectM-4/playlist_core.h) - dalsza automatyczna zmiana presetow po
    // uplywie czasu (setPresetDuration) dzieje sie sama, bez udzialu strony Kotlin/JNI.
    ctx->playlist = projectm_playlist_create(ctx->instance);
    if (ctx->playlist == nullptr) {
        LOGE("projectm_playlist_create() zwrocil null");
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_destroy(JNIEnv*, jobject, jlong handle) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr) return;
    if (ctx->playlist != nullptr) {
        projectm_playlist_destroy(ctx->playlist);
    }
    if (ctx->instance != nullptr) {
        projectm_destroy(ctx->instance);
    }
    delete ctx;
}

JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_setWindowSize(JNIEnv*, jobject, jlong handle,
                                                              jint width, jint height) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->instance == nullptr) return;
    projectm_set_window_size(ctx->instance, static_cast<size_t>(width), static_cast<size_t>(height));
}

JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_renderFrame(JNIEnv*, jobject, jlong handle) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->instance == nullptr) return;
    projectm_opengl_render_frame(ctx->instance);
}

JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_setPresetDuration(JNIEnv*, jobject, jlong handle,
                                                                  jdouble seconds) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->instance == nullptr) return;
    projectm_set_preset_duration(ctx->instance, seconds);
}

// Bez tego presety odwolujace sie do tekstur (Milkdrop Texture Pack) renderuja sie na czarno -
// projectM szuka tekstur tylko w jawnie ustawionych sciezkach, patrz parameters.h.
JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_setTextureSearchPath(JNIEnv* env, jobject,
                                                                     jlong handle, jstring path) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->instance == nullptr) return;
    const char* pathChars = env->GetStringUTFChars(path, nullptr);
    if (pathChars == nullptr) return;
    const char* paths[1] = {pathChars};
    projectm_set_texture_search_paths(ctx->instance, paths, 1);
    env->ReleaseStringUTFChars(path, pathChars);
}

JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_addPcmInt16(JNIEnv* env, jobject, jlong handle,
                                                            jshortArray samples, jint frameCount,
                                                            jint channels) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->instance == nullptr) return;
    jshort* data = env->GetShortArrayElements(samples, nullptr);
    if (data == nullptr) return;
    projectm_pcm_add_int16(
        ctx->instance,
        reinterpret_cast<const int16_t*>(data),
        static_cast<unsigned int>(frameCount),
        channels == 1 ? PROJECTM_MONO : PROJECTM_STEREO);
    // JNI_ABORT: nie odpisujemy z powrotem do jshortArray, bo tylko czytamy próbki wejściowe.
    env->ReleaseShortArrayElements(samples, data, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_com_aurora_player_projectm_ProjectMNative_playlistAddPath(JNIEnv* env, jobject, jlong handle,
                                                                jstring path,
                                                                jboolean recurseSubdirs) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->playlist == nullptr) return 0;
    const char* pathChars = env->GetStringUTFChars(path, nullptr);
    if (pathChars == nullptr) return 0;
    uint32_t added = projectm_playlist_add_path(ctx->playlist, pathChars, recurseSubdirs == JNI_TRUE,
                                                 /*allow_duplicates=*/false);
    env->ReleaseStringUTFChars(path, pathChars);
    return static_cast<jint>(added);
}

// Wymagane do przełączania trybów wizualizera (Etap 10, DESIGN.md) — playlistAddPath tylko
// DOKLADA presety, więc zmiana zestawu (np. Ambient -> Particle) musi najpierw wyczyścić starą
// zawartość playlisty.
JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_playlistClear(JNIEnv*, jobject, jlong handle) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->playlist == nullptr) return;
    projectm_playlist_clear(ctx->playlist);
}

JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_playlistSetShuffle(JNIEnv*, jobject, jlong handle,
                                                                   jboolean shuffle) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->playlist == nullptr) return;
    projectm_playlist_set_shuffle(ctx->playlist, shuffle == JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_com_aurora_player_projectm_ProjectMNative_playlistPlayNext(JNIEnv*, jobject, jlong handle,
                                                                 jboolean hardCut) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->playlist == nullptr) return 0;
    return static_cast<jint>(projectm_playlist_play_next(ctx->playlist, hardCut == JNI_TRUE));
}

} // extern "C"
