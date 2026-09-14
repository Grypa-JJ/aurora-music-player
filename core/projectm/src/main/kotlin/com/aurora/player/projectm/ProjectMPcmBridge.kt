package com.aurora.player.projectm

/** Odbiornik próbek PCM (post-EQ, dokładnie to co słychać) — patrz [ProjectMPcmBridge]. */
fun interface ProjectMPcmSink {
    fun addPcm(samples: ShortArray, frameCount: Int, channels: Int)
}

/**
 * Prosty globalny most między `EqualizerAudioProcessor` (app, widzi każdą próbkę PCM na wątku
 * audio Media3) a aktywnym [ProjectMEngine] (istnieje tylko, gdy wizualizer projectM jest
 * faktycznie widoczny na ekranie). Zwykły singleton obiektowy zamiast Hilt @Singleton — ten
 * moduł świadomie nie ciągnie zależności DI tylko dla jednej klasy pośredniczącej.
 *
 * Gdy nikt nie jest podłączony (wizualizer niewidoczny / urządzenie bez wsparcia GLES 3.1),
 * [dispatch] jest tanim no-opem — appka działa identycznie jak przed Etapem 9.
 */
object ProjectMPcmBridge {

    @Volatile
    private var activeSink: ProjectMPcmSink? = null

    fun attach(sink: ProjectMPcmSink) {
        activeSink = sink
    }

    fun detach(sink: ProjectMPcmSink) {
        if (activeSink === sink) activeSink = null
    }

    fun dispatch(samples: ShortArray, frameCount: Int, channels: Int) {
        activeSink?.addPcm(samples, frameCount, channels)
    }
}
