package com.aurora.player.eq

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Wpina [EqualizerAudioProcessor] w pipeline audio ExoPlayera — to jedyny udokumentowany
 * sposób wstrzyknięcia własnego DSP w Media3 (DefaultAudioSink.Builder.setAudioProcessors),
 * zweryfikowany w źródłach androidx/media przed napisaniem tego kodu (patrz DESIGN.md sekcja 4.1).
 */
class EqualizerRenderersFactory(
    context: Context,
    private val equalizerAudioProcessor: EqualizerAudioProcessor,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(equalizerAudioProcessor))
            .build()
}
