package com.aurora.player.eq

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Wpina procesory DSP (dziś: [EqualizerAudioProcessor], docelowo np. ReplayGain — DESIGN.md
 * Etap 20h/21) w pipeline audio ExoPlayera — to jedyny udokumentowany sposób wstrzyknięcia
 * własnego DSP w Media3 (DefaultAudioSink.Builder.setAudioProcessors), zweryfikowany w
 * źródłach androidx/media przed napisaniem tego kodu (patrz DESIGN.md sekcja 4.1).
 */
class EqualizerRenderersFactory(
    context: Context,
    private val audioProcessors: List<AudioProcessor>,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(audioProcessors.toTypedArray())
            .build()
}
