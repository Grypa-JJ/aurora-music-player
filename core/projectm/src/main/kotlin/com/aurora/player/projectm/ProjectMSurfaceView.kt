package com.aurora.player.projectm

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * `GLSurfaceView` osadzający jedną instancję [ProjectMEngine]. Cały cykl życia natywnego handle'a
 * (create/destroy/render/resize) trzyma się wątku GL tego widoku — projectM tego wymaga (patrz
 * [ProjectMEngine]). Osadzany w Compose przez `AndroidView` — patrz `ProjectMSurface`.
 *
 * Etap 16: wybór presetu (KTÓRY plik `.milk` jest widoczny) jest w całości sterowany z zewnątrz
 * przez [jumpToPreset] — ten widok nie ma już własnej playlisty/losowania, patrz [PresetLibrary].
 */
class ProjectMSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    val engine = ProjectMEngine()

    /** Katalog z podfolderami `presets/` i `textures/` — patrz [PresetInstaller]. */
    @Volatile
    var installedAssetsDir: String? = null

    /** Ustawiane bezpośrednio tylko PRZED zamontowaniem — patrz `ProjectMSurface`. */
    @Volatile
    var initialSettings: ProjectMVisualizerSettings = ProjectMVisualizerSettings()

    /** Jak [initialSettings] — dokładna ścieżka `.milk` do załadowania od razu przy tworzeniu. */
    @Volatile
    var initialPresetPath: String? = null

    private val pcmSink = ProjectMPcmSink { samples, frameCount, channels ->
        engine.addPcm(samples, frameCount, channels)
    }

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(InternalRenderer())
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /**
     * Ładuje dokładnie wskazany plik `.milk` w locie — wymaga wątku GL, stąd `queueEvent`.
     * Wołające (patrz `ProjectMSurface`) trzyma stan "który plik jest aktualny" jako zwykły stan
     * Compose lifted do `NowPlayingScreen`, więc ta sama wartość może zostać przekazana kolejnej,
     * osobnej instancji tego widoku (ramka<->pełny ekran) — to jest naprawa zgłoszonego bugu
     * "przejście do pełnego ekranu losuje inny preset".
     */
    fun jumpToPreset(path: String, smoothTransition: Boolean) {
        queueEvent { engine.loadPresetFile(path, smoothTransition) }
    }

    /** Stosuje ustawienia z panelu (Etap 10 część 2) w locie — jak [jumpToPreset], wymaga wątku GL. */
    fun applySettings(settings: ProjectMVisualizerSettings) {
        initialSettings = settings
        queueEvent {
            engine.setPresetDuration(settings.presetDurationSeconds)
            engine.setBeatSensitivity(settings.beatSensitivity)
            engine.setHardCut(settings.hardCutEnabled, settings.hardCutSensitivity)
        }
    }

    /** Wołać z `DisposableEffect.onDispose` po stronie Compose. */
    fun release() {
        ProjectMPcmBridge.detach(pcmSink)
        queueEvent { engine.destroy() }
    }

    private inner class InternalRenderer : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            engine.create()
            val settings = initialSettings
            engine.setPresetDuration(settings.presetDurationSeconds)
            engine.setBeatSensitivity(settings.beatSensitivity)
            engine.setHardCut(settings.hardCutEnabled, settings.hardCutSensitivity)
            installedAssetsDir?.let { baseDir -> engine.setTextureSearchPath("$baseDir/textures") }
            initialPresetPath?.let { path -> engine.loadPresetFile(path, smoothTransition = false) }
            ProjectMPcmBridge.attach(pcmSink)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            engine.setWindowSize(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            engine.renderFrame()
        }
    }
}
