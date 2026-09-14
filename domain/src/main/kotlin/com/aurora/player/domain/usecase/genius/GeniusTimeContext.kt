package com.aurora.player.domain.usecase.genius

/**
 * Okna czasowe dla kontekstowych miksów Genius — patrz DESIGN.md Etap 15. Jedyny sygnał
 * kontekstu, jaki appka realnie zbiera, to pora dnia (`PlayEventEntity.hourOfDay`) — nazwy
 * celowo trzymają się tego, co faktycznie wykrywamy, zamiast udawać rozpoznawanie aktywności
 * (jazda samochodem, praca), której appka nie mierzy.
 */
enum class GeniusTimeContext(
    val displayName: String,
    private val startHour: Int,
    private val endHourExclusive: Int,
) {
    MORNING("Poranny fokus", 5, 11),
    DAYTIME("Fokus na cały dzień", 11, 17),
    EVENING("Wieczorny relaks", 17, 22),
    NIGHT("Nocna jazda", 22, 5), // zawija się przez północ
    ;

    private fun contains(hourOfDay: Int): Boolean =
        if (startHour < endHourExclusive) {
            hourOfDay in startHour until endHourExclusive
        } else {
            hourOfDay >= startHour || hourOfDay < endHourExclusive
        }

    companion object {
        fun forHour(hourOfDay: Int): GeniusTimeContext = entries.first { it.contains(hourOfDay) }
    }
}
