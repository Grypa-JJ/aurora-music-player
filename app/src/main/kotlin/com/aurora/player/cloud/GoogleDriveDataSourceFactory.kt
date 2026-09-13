package com.aurora.player.cloud

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource

/**
 * Dokłada nagłówek `Authorization: Bearer <token>` do żądań HTTP Media3 przy odtwarzaniu
 * utworów z Google Drive — patrz DESIGN.md, sekcja "Chmura". Token pobierany na świeżo przy
 * każdym `createDataSource()` (wołane przez Media3 raz na załadowanie utworu, na wątku ładowania,
 * nie na głównym), bo tokeny OAuth wygasają po ~godzinie, a `PlaybackService` żyje długo.
 *
 * Dla lokalnych utworów (content://) ten factory nigdy nie jest używany — patrz owijające go
 * `DefaultDataSource.Factory(context, this)` w [com.aurora.player.playback.PlaybackService],
 * które samo dispatch'uje content://file:// do własnych DataSource'ów, a http(s) do tego tutaj.
 */
@UnstableApi
class GoogleDriveDataSourceFactory(
    private val getAccessToken: () -> String?,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val token = getAccessToken()
        val factory = DefaultHttpDataSource.Factory().apply {
            if (token != null) {
                setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            }
        }
        return factory.createDataSource()
    }
}
