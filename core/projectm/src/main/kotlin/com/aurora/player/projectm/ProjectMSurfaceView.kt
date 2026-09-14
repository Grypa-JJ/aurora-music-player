package com.aurora.player.projectm

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * `GLSurfaceView` osadzający jedną instancję [ProjectMEngine]. Cały cykl życia natywnego handle'a
 * (create/destroy/render/resize) trzyma się wątku GL tego widoku — projectM tego wymaga (patrz
 * [ProjectMEngine]). Osadzany w Compose przez `AndroidView` — patrz `NowPlayingScreen`.
 */
class ProjectMSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    val engine = ProjectMEngine()

    /** Katalog z podfolderami `presets/` i `textures/` — patrz [PresetInstaller]. */
    @Volatile
    var installedAssetsDir: String? = null

    /**
     * Ustawiane bezpośrednio tylko PRZED zamontowaniem widoku, żeby pierwsze `onSurfaceCreated`
     * od razu załadowało właściwy tryb — patrz `ProjectMSurface`. Po zamontowaniu zmiana trybu
     * w locie idzie przez [setVisualizerMode] (osobna nazwa, bo Kotlin i tak wygenerowałby
     * `setVisualizerMode` jako setter tej właściwości — kolizja JVM, gdyby nazwać ją tak samo).
     */
    @Volatile
    var initialVisualizerMode: ProjectMVisualizerMode = ProjectMVisualizerMode.ALL

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
     * Przełącza tryb wizualizera (Etap 10) w trakcie działania — wymaga wątku GL (jak każda
     * operacja na playliście/instancji projectM), więc idzie przez `queueEvent`, nie wywołuje się
     * bezpośrednio z wątku Compose.
     */
    fun setVisualizerMode(mode: ProjectMVisualizerMode) {
        initialVisualizerMode = mode
        val baseDir = installedAssetsDir ?: return
        queueEvent { engine.loadPresets("$baseDir/presets", mode) }
    }

    /** Wołać z `DisposableEffect.onDispose` po stronie Compose. */
    fun release() {
        ProjectMPcmBridge.detach(pcmSink)
        queueEvent { engine.destroy() }
    }

    private inner class InternalRenderer : Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            engine.create()
            engine.setPresetDuration(PRESET_DURATION_SECONDS)
            installedAssetsDir?.let { baseDir ->
                engine.setTextureSearchPath("$baseDir/textures")
                engine.loadPresets("$baseDir/presets", initialVisualizerMode)
            }
            ProjectMPcmBridge.attach(pcmSink)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            engine.setWindowSize(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            engine.renderFrame()
        }
    }

    private companion object {
        // Jak długo pojedynczy preset gra, zanim projectM sam poprosi playlistę o kolejny
        // (automatycznie, patrz komentarz w ProjectMEngine.create()/ProjectMNative.create()).
        const val PRESET_DURATION_SECONDS = 20.0
    }
}
