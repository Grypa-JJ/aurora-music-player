package com.aurora.player.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface FavoritesRepository {
    val favoriteTrackIds: StateFlow<Set<Long>>
    suspend fun toggleFavorite(trackId: Long)
}
