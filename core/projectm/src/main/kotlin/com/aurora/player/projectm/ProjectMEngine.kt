package com.aurora.player.projectm

import android.app.ActivityManager
import android.content.Context
import kotlin.concurrent.read
import kotlin.concurrent.write
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Właściciel jednej natywnej instancji projectM (handle z [ProjectMNative]). Cykl życia:
 * [create] i [destroy] oraz [setWindowSize]/[renderFrame]/[setPresetDuration]/[loadPresets] MUSZĄ
 * być wołane z wątku z aktywnym kontekstem OpenGL (patrz `ProjectMSurfaceView`, wątek GL) — to
 * twardy wymóg samego OpenGL/projectM, nie coś do obejścia.
 *
 * [addPcm] jest bezpieczne z osobnego wątku audio RÓWNOLEGLE z renderem (udokumentowane w
 * projectM: brak wewnętrznego mutexu na buforze PCM, wyścig może najwyżej "zamazać" jedną klatkę
 * wizualizacji, nigdy crash) — ALE musi być zablokowane względem [destroy], bo destroy realnie
 * zwalnia pamięć natywną. Stąd [lock]: addPcm/render/etc. biorą blokadę do odczytu (mogą się
 * przeplatać ze sobą), create/destroy biorą do zapisu (wykluczają wszystko inne).
 */
class ProjectMEngine {

    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var handle: Long = 0L

    val isReady: Boolean get() = handle != 0L

    /**
     * Wołane z wątku GL (np. `GLSurfaceView.Renderer.onSurfaceCreated`). Zweryfikowane na żywo na
     * emulatorze (Etap 9): `onSurfaceCreated` potrafi wystrzelić WIĘCEJ NIŻ RAZ na tej samej
     * instancji `ProjectMSurfaceView`/[ProjectMEngine] — Android potrafi zniszczyć i odtworzyć
     * samą Surface (np. przy zmianie rozmiaru/reparentingu widoku, jak przy przenoszeniu przez
     * `movableContentOf` między ramką inline a nakładką pełnoekranową), mimo że obiekt Kotlin
     * (i `movableContentOf`) przeżywa. Stary natywny kontekst jest wtedy nieważny (powiązany ze
     * zniszczoną Surface) — trzeba go zwolnić i stworzyć nowy, a nie traktować jako błąd
     * programisty (crashowało tu naprawdę: `IllegalStateException` na wątku GLThread, złapane
     * podczas testu przycisku pełnego ekranu).
     */
    fun create() {
        lock.write {
            if (handle != 0L) {
                ProjectMNative.destroy(handle)
                handle = 0L
            }
            handle = ProjectMNative.create()
        }
    }

    /** Wołane z wątku GL (np. przez `GLSurfaceView.queueEvent` przy odłączaniu widoku). */
    fun destroy() {
        lock.write {
            if (handle == 0L) return@write
            ProjectMNative.destroy(handle)
            handle = 0L
        }
    }

    fun setWindowSize(width: Int, height: Int) {
        lock.read {
            if (handle == 0L) return@read
            ProjectMNative.setWindowSize(handle, width, height)
        }
    }

    fun renderFrame() {
        lock.read {
            if (handle == 0L) return@read
            ProjectMNative.renderFrame(handle)
        }
    }

    fun setPresetDuration(seconds: Double) {
        lock.read {
            if (handle == 0L) return@read
            ProjectMNative.setPresetDuration(handle, seconds)
        }
    }

    /** Bez tego presety odwołujące się do tekstur (Milkdrop Texture Pack) renderują się na czarno. */
    fun setTextureSearchPath(path: String) {
        lock.read {
            if (handle == 0L) return@read
            ProjectMNative.setTextureSearchPath(handle, path)
        }
    }

    /** Skanuje [directoryPath] (zwykły katalog na dysku — patrz [PresetInstaller]) w poszukiwaniu `.milk`. */
    fun loadPresets(directoryPath: String, shuffle: Boolean = true) {
        lock.read {
            if (handle == 0L) return@read
            val added = ProjectMNative.playlistAddPath(handle, directoryPath, true)
            if (added <= 0) return@read
            ProjectMNative.playlistSetShuffle(handle, shuffle)
            ProjectMNative.playlistPlayNext(handle, true)
        }
    }

    /** Bezpieczne wołanie z osobnego wątku audio — patrz dokumentacja klasy powyżej. */
    fun addPcm(samples: ShortArray, frameCount: Int, channels: Int) {
        lock.read {
            val h = handle
            if (h == 0L) return@read
            ProjectMNative.addPcmInt16(h, samples, frameCount, channels)
        }
    }

    companion object {
        // projectM wymaga OpenGL ES 3.1 (3.2 zalecane) - potwierdzone bezpośrednio przez
        // maintainera projektu (issue #825). 0x30001 = major 3, minor 1 w formacie
        // ActivityManager.reqGlEsVersion (major << 16 | minor).
        private const val REQUIRED_GLES_VERSION = 0x30001

        /** Sprawdź PRZED jakąkolwiek próbą stworzenia [ProjectMSurfaceView] — patrz DESIGN.md Etap 9. */
        fun isDeviceSupported(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            return activityManager.deviceConfigurationInfo.reqGlEsVersion >= REQUIRED_GLES_VERSION
        }
    }
}
