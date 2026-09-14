package com.aurora.player.projectm

/**
 * Surowy most JNI do `projectm_jni` (`core/projectm/src/main/cpp/projectm_jni.cpp`) —
 * 1:1 odbicie C API libprojectM-4. Nie używać bezpośrednio poza [ProjectMEngine]: wszystkie
 * funkcje poza [addPcmInt16] muszą być wołane z wątku z aktywnym kontekstem OpenGL (patrz
 * komentarz w projectm_jni.cpp), co [ProjectMEngine]/[ProjectMSurfaceView] pilnują za appkę.
 */
internal object ProjectMNative {
    init {
        System.loadLibrary("projectm_jni")
    }

    external fun create(): Long
    external fun destroy(handle: Long)
    external fun setWindowSize(handle: Long, width: Int, height: Int)
    external fun renderFrame(handle: Long)
    external fun setPresetDuration(handle: Long, seconds: Double)
    external fun loadPresetFile(handle: Long, path: String, smoothTransition: Boolean)
    external fun setTextureSearchPath(handle: Long, path: String)
    external fun setBeatSensitivity(handle: Long, sensitivity: Float)
    external fun setHardCutEnabled(handle: Long, enabled: Boolean)
    external fun setHardCutSensitivity(handle: Long, sensitivity: Float)
    external fun addPcmInt16(handle: Long, samples: ShortArray, frameCount: Int, channels: Int)
}
