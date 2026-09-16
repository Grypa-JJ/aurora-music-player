package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Playlist
import kotlinx.coroutines.flow.StateFlow

interface PlaylistRepository {
    val playlists: StateFlow<List<Playlist>>

    /** @return id nowo utworzonej playlisty. */
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)

    /** Bez efektu, jeśli [trackId] już jest w playliście — bez duplikatów, patrz DESIGN.md Etap 22. */
    suspend fun addTrack(playlistId: Long, trackId: Long)
    suspend fun removeTrack(playlistId: Long, trackId: Long)
    suspend fun moveTrack(playlistId: Long, fromIndex: Int, toIndex: Int)
}
