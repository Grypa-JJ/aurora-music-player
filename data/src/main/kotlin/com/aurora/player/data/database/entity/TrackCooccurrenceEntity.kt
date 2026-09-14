package com.aurora.player.data.database.entity

import androidx.room.Entity

/**
 * Skierowana para (fromTrackId -> toTrackId, NIE symetryczna) — patrz DESIGN.md Etap 14.
 * Wcześniej (Etap 4) była to para znormalizowana (trackIdA < trackIdB) z surowym licznikiem
 * `count`; zmienione na kierunkową z wagą, bo "co zagrało jako następne po X" jest z natury
 * asymetryczne (X->Y i Y->X to różne, niekoniecznie równie prawdopodobne przejścia), a sam
 * `count` traktował identycznie przejście po naturalnym dosłuchaniu utworu do końca i przejście
 * przez gwałtowny skip (czyli "użytkownik uciekał od X", nie "X naturalnie prowadzi do Y").
 *
 * [weight] rośnie o `0.2 + 0.8 * completionRatio` przy każdym przejściu (patrz
 * `PlaybackHistoryRepositoryImpl.recordTransition`) — pełne dosłuchanie X przed przejściem na Y
 * liczy się prawie dwa razy mocniej niż natychmiastowy skip. [lastSeenAt] pozwala dodatkowo
 * wygaszać stare przejścia w czasie odczytu (patrz `GeniusRepositoryImpl`), bez osobnego joba.
 */
@Entity(tableName = "track_cooccurrence", primaryKeys = ["fromTrackId", "toTrackId"])
data class TrackCooccurrenceEntity(
    val fromTrackId: Long,
    val toTrackId: Long,
    val weight: Float,
    val lastSeenAt: Long,
)
