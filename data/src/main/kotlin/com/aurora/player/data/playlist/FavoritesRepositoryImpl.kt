package com.aurora.player.data.playlist

import com.aurora.player.data.database.dao.FavoriteTrackDao
import com.aurora.player.data.database.entity.FavoriteTrackEntity
import com.aurora.player.data.di.ApplicationScope
import com.aurora.player.domain.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val dao: FavoriteTrackDao,
    @ApplicationScope scope: CoroutineScope,
) : FavoritesRepository {

    override val favoriteTrackIds: StateFlow<Set<Long>> =
        dao.observeAll()
            .map { entities -> entities.map { it.trackId }.toSet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override suspend fun toggleFavorite(trackId: Long) {
        if (favoriteTrackIds.value.contains(trackId)) {
            dao.delete(trackId)
        } else {
            dao.insert(FavoriteTrackEntity(trackId = trackId, addedAtMs = System.currentTimeMillis()))
        }
    }
}
