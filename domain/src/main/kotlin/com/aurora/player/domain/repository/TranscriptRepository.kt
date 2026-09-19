package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TranscriptResult

/** Transkrypcja odcinka podkastu (`<podcast:transcript>`) — DESIGN.md Etap 54. */
interface TranscriptRepository {
    suspend fun getTranscript(track: Track): TranscriptResult
}
