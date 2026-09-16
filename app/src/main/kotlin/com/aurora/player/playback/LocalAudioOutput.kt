package com.aurora.player.playback

import android.content.Context
import android.media.AudioManager
import com.aurora.player.domain.audio.AudioCapabilities
import com.aurora.player.domain.audio.AudioOutput
import com.aurora.player.domain.audio.DacFilter
import com.aurora.player.domain.audio.Gain
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AudioOutput] domyślnego wyjścia systemowego — patrz DESIGN.md Etap 21. Owija to, co appka i
 * tak robi dziś milcząco: system jest zawsze "podłączony", głośność idzie przez
 * [AudioManager]/`STREAM_MUSIC` (niezależnie od instancji ExoPlayera — głośność systemowa nie
 * wymaga referencji do playera), reszta możliwości (sample rate/gain/filtr) zwraca jawne
 * `false` — platforma nie daje appce sposobu, żeby to naprawdę zagwarantować (do Android 13
 * włącznie nie istnieje systemowy tryb bit-perfect/exclusive).
 */
@Singleton
class LocalAudioOutput @Inject constructor(
    @ApplicationContext context: Context,
) : AudioOutput {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override val capabilities: AudioCapabilities = AudioCapabilities(
        deviceName = "Wyjście systemowe",
        supportsVolumeControl = true,
    )

    override suspend fun connect(): Boolean = true

    override suspend fun disconnect() = Unit

    override suspend fun setVolume(value: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (value.coerceIn(0f, 1f) * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
    }

    override suspend fun setSampleRate(sampleRate: Int): Boolean = false

    override suspend fun setGain(gain: Gain): Boolean = false

    override suspend fun setFilter(filter: DacFilter): Boolean = false
}
