package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aurora.player.data.database.entity.PlayEventEntity

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventEntity)

    @Query("SELECT * FROM play_events WHERE trackId = :trackId")
    suspend fun forTrack(trackId: Long): List<PlayEventEntity>

    /** Do wyliczenia kontekstu pory dnia w GeniusScoring — DESIGN.md sekcja 5.2. */
    @Query("SELECT * FROM play_events")
    suspend fun getAll(): List<PlayEventEntity>

    /** Zasila kafel "Kontynuuj" na Android Auto — patrz DESIGN.md sekcja 6. */
    @Query("SELECT trackId FROM play_events ORDER BY timestampEnd DESC LIMIT 1")
    suspend fun getLastPlayedTrackId(): Long?
}
