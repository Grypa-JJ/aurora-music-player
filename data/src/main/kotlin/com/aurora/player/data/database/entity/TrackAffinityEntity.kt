package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tabela materializowana — patrz DESIGN.md sekcja 5.1. Etap 3: przeliczana od razu przy każdym
 * zdarzeniu (patrz PlaybackHistoryRepositoryImpl), nie batchowana przez WorkManager — przy
 * realistycznej skali (pojedynczy użytkownik, jedno zdarzenie na koniec utworu) to tanie i
 * prostsze niż wprowadzanie WorkManagera już teraz; batching zostaje jako możliwa optymalizacja
 * na etap 4, gdyby profiling pokazał, że jest potrzebny.
 */
@Entity(tableName = "track_affinity")
data class TrackAffinityEntity(
    @PrimaryKey val trackId: Long,
    val playCount: Int,
    val skipCount: Int,
    val avgCompletionRatio: Float,
    val lastPlayedAt: Long,
    val affinityScore: Float,
)
