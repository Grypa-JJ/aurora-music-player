package com.aurora.player.domain.model

/** Audiobook dodany do biblioteki usera — LibriVox, domena publiczna. [id] to numeryczne id książki z API LibriVox. */
data class Audiobook(
    val id: String,
    val title: String,
    val author: String,
    val language: String?,
    val coverUrl: String?,
    val description: String,
)

/** Jeden rozdział — LibriVox zwraca je już w kolejności odsłuchu przez `sections[]` w API. */
data class AudiobookChapter(
    val id: String,
    val bookId: String,
    val title: String,
    val audioUrl: String,
    val durationMs: Long,
    val chapterIndex: Int,
)

/** Wynik wyszukiwania w katalogu LibriVox (przed dodaniem do biblioteki). */
data class AudiobookSearchResult(
    val id: String,
    val title: String,
    val author: String,
    val language: String?,
    val coverUrl: String?,
)
