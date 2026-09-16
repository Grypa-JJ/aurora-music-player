package com.aurora.player.domain.util

import com.aurora.player.domain.model.LyricsLine

/**
 * Parser formatu `.lrc` (linie `[mm:ss.xx]tekst`, dopuszcza wiele znaczników czasu na jedną
 * linię) — zwraca listę posortowaną rosnąco po czasie. Linie metadanych (`[ti:]`/`[ar:]` itp.)
 * i puste są pomijane, bo nie pasują do wzorca znacznika czasu.
 */
object LrcParser {
    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    fun parse(raw: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        raw.lineSequence().forEach { line ->
            val matches = TIMESTAMP.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                lines += LyricsLine((minutes * 60_000L) + (seconds * 1000L) + fractionMs, text)
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
