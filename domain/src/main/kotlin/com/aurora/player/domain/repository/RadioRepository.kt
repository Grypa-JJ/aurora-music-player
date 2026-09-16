package com.aurora.player.domain.repository

import com.aurora.player.domain.model.RadioStation

interface RadioRepository {
    suspend fun topStationsByCountry(countryCode: String, limit: Int = 40): List<RadioStation>
    suspend fun search(query: String, limit: Int = 40): List<RadioStation>
}
