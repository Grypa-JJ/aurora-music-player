package com.aurora.player.data.playlist

import com.aurora.player.data.database.dao.PlaylistDao
import com.aurora.player.data.database.entity.PlaylistEntity
import com.aurora.player.data.database.entity.PlaylistTrackEntity
import com.aurora.player.data.di.ApplicationScope
import com.aurora.player.domain.model.Playlist
import com.aurora.player.domain.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val dao: PlaylistDao,
    @ApplicationScope scope: CoroutineScope,
) : PlaylistRepository {

    override val playlists: StateFlow<List<Playlist>> =
        combine(dao.observePlaylists(), dao.observeAllPlaylistTracks()) { playlistEntities, trackEntities ->
            val tracksByPlaylist = trackEntities.groupBy { it.playlistId }
            playlistEntities.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    trackIds = tracksByPlaylist[entity.id].orEmpty()
                        .sortedBy { it.position }
                        .map { it.trackId },
                    createdAtMs = entity.createdAtMs,
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name, createdAtMs = System.currentTimeMillis()))

    override suspend fun renamePlaylist(playlistId: Long, name: String) {
        dao.renamePlaylist(playlistId, name)
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
    }

    override suspend fun addTrack(playlistId: Long, trackId: Long) {
        val existing = dao.getTracksForPlaylistOnce(playlistId)
        if (existing.any { it.trackId == trackId }) return
        val nextPosition = (existing.maxOfOrNull { it.position } ?: -1) + 1
        dao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = nextPosition,
                addedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun removeTrack(playlistId: Long, trackId: Long) {
        dao.deletePlaylistTrack(playlistId, trackId)
    }

    override suspend fun moveTrack(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val tracks = dao.getTracksForPlaylistOnce(playlistId).toMutableList()
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices) return
        val moved = tracks.removeAt(fromIndex)
        tracks.add(toIndex, moved)
        dao.updatePlaylistTracks(tracks.mapIndexed { index, entity -> entity.copy(position = index) })
    }
}
