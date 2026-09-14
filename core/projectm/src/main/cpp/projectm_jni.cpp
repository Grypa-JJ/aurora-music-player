// Cienki most JNI do libprojectM-4 (C API) — patrz DESIGN.md Etap 9/16. Żadnej logiki wizualnej
// tutaj: to tylko przełożenie wywołań Kotlin 1:1 na funkcje C z projectM-4/projectM.h.
//
// Etap 16: biblioteka playlist USUNIĘTA — wybór presetu jest teraz w całości po stronie Kotlin
// (patrz PresetLibrary.kt/ProjectMEngine.loadPresetFile), bo natywnej pozycji playlisty nie dało
// się odczytać/przenieść między dwiema osobnymi instancjami silnika (ramka inline vs. pełny ekran),
// co powodowało zgłoszony bug: przejście między nimi losowało zupełnie inny preset.
//
// Wątkowość (zweryfikowane w oficjalnym API-Reference/Integration-Quickstart-Guide projectM):
// create/destroy/setWindowSize/renderFrame/loadPresetFile MUSZĄ być wołane z tego samego wątku,
// który ma aktywny kontekst OpenGL (u nas: wątek GL surface'u) — to twardy wymóg samego OpenGL,
// nie tylko projectM. addPcmInt16 można wołać z osobnego wątku audio równolegle z renderem:
// projectM nie synchronizuje wewnętrznie bufora PCM, więc realny wyścig da co najwyżej lekko
// "zamazaną" klatkę wizualizacji, nigdy crash (udokumentowane zachowanie, nie błąd).
#include <jni.h>
#include <android/log.h>

#include <projectM-4/projectM.h>

#define LOG_TAG "ProjectMJni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct ProjectMContext {
    projectm_handle instance = nullptr;
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
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_destroy(JNIEnv*, jobject, jlong handle) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr) return;
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

// Bezposredni odczyt jednego, konkretnego presetu z podanej sciezki - patrz PresetLibrary.kt:
// zastepuje playlist jako mechanizm wyboru presetu, zeby Kotlin mogl trzymac i przenosic
// dokladnie ten sam indeks/plik miedzy dwiema oddzielnymi instancjami (ramka<->pelny ekran).
JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_loadPresetFile(JNIEnv* env, jobject, jlong handle,
                                                                jstring path,
                                                                jboolean smoothTransition) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->instance == nullptr) return;
    const char* pathChars = env->GetStringUTFChars(path, nullptr);
    if (pathChars == nullptr) return;
    projectm_load_preset_file(ctx->instance, pathChars, smoothTransition == JNI_TRUE);
    env->ReleaseStringUTFChars(path, pathChars);
}

// Realne kontrolki z API projectM (parameters.h) pod panel ustawien z Etapu 10 czesc 2 - swiadomie
// TYLKO te trzy, bo to jedyne parametry API faktycznie odpowiadajace temu, co user opisal
// ("reakcja na beat", "czulosc") - zaden fikcyjny suwak bez pokrycia w bibliotece.
JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_setBeatSensitivity(JNIEnv*, jobject, jlong handle,
                                                                   jfloat sensitivity) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->instance == nullptr) return;
    projectm_set_beat_sensitivity(ctx->instance, sensitivity);
}

JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_setHardCutEnabled(JNIEnv*, jobject, jlong handle,
                                                                  jboolean enabled) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->instance == nullptr) return;
    projectm_set_hard_cut_enabled(ctx->instance, enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_aurora_player_projectm_ProjectMNative_setHardCutSensitivity(JNIEnv*, jobject, jlong handle,
                                                                      jfloat sensitivity) {
    auto* ctx = toContext(handle);
    if (ctx == nullptr || ctx->instance == nullptr) return;
    projectm_set_hard_cut_sensitivity(ctx->instance, sensitivity);
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

} // extern "C"
