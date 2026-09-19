package com.aurora.player.archive

/**
 * Statyczna lista popularnych polskich zespołów/artystów + raperów z sukcesem w PL — user: "dodaj
 * top sto zespołów/artystów w PL + raperów z sukcesem" do podpowiedzi wyszukiwania w Archiwum
 * (patrz `LibraryViewModel.archiveArtistSuggestions`). Rozszerza podpowiedzi POZA lokalną
 * bibliotekę — user może nie mieć jeszcze nic od danego artysty na urządzeniu, a i tak warto mu
 * podpowiedzieć nazwę, skoro to ktoś powszechnie rozpoznawalny w Polsce.
 *
 * Brak jakichkolwiek gwarancji, że dany wykonawca faktycznie COŚ ma na Internet Archive (etree/
 * netlabele to głównie sceny niezależna/koncertowa, nie polski mainstream) — to tylko podpowiedź
 * TEKSTU do wpisania, wynik samego wyszukiwania i tak zależy od tego, co IA faktycznie indeksuje.
 */
object PopularPolishArtists {
    val NAMES = listOf(
        // Rock/pop/alternatywa — klasyka i współczesność
        "Dawid Podsiadło", "Sanah", "Kwiat Jabłoni", "Organek", "Happysad", "Coma", "Hey",
        "Myslovitz", "Lady Pank", "Perfect", "Budka Suflera", "T.Love", "Kult", "Republika",
        "Voo Voo", "Closterkeller", "Feel", "Ich Troje", "Maanam", "Big Cyc", "Wilki", "IRA",
        "Dżem", "Chłopcy z Placu Broni", "Krzysztof Krawczyk", "Golec uOrkiestra", "Zakopower",
        "Brathanki", "Varius Manx", "Kayah", "Edyta Górniak", "Natalia Kukulska", "Ania Dąbrowska",
        "Monika Brodka", "Margaret", "Vito Bambino", "Kortez", "Krzysztof Zalewski",
        "Piotr Rogucki", "Muchy", "Farben Lehre", "Post Regiment", "Pidżama Porno", "Świetliki",
        "Lech Janerka", "Kobranocka", "Bajm", "Grzegorz Turnau", "Raz Dwa Trzy", "Stan Borys",
        "Czesław Śpiewa", "Acid Drinkers", "Vader", "Behemoth", "Decapitated", "Riverside",
        "Lunatic Soul", "Nosowska", "Renata Przemyk", "Anita Lipnicka", "John Porter", "Mrozu",
        "Sarsa", "LemON", "Enej", "Kombii", "Bracia", "Urszula", "Anna Jantar", "Maryla Rodowicz",
        "Skaldowie", "Czerwone Gitary", "Czesław Niemen", "Breakout", "SBB", "Blenders", "Video",
        "Boys", "Weekend", "Piersi", "Roksana Węgiel", "Viki Gabor", "Justyna Steczkowska", "Doda",
        "Blue Café", "Kasia Kowalska", "O.N.A.", "Kamil Bednarek", "Dawid Kwiatkowski",
        "Michał Szpak", "Brodka",

        // Rap/hip-hop — sukces w PL
        "Taco Hemingway", "Quebonafide", "Mata", "Young Leosia", "Sokół", "Pezet", "O.S.T.R.",
        "Peja", "Eldo", "Miuosh", "Fisz Emade Tworzywo", "Ten Typ Mes", "Abradab", "Hemp Gru",
        "Molesta Ewenement", "Liroy", "WWO", "Kaliber 44", "Paktofonika", "Bilon", "Solar", "Mero",
        "Malik Montana", "Gibbs", "Kubi Producent", "Szpaku", "Bambi", "Gedz", "Daf", "Skorup",
        "Kartky", "Białas", "PRO8L3M", "Rufuz", "White 2115", "Żabson", "Kizo", "Otsochodzi",
        "OIO", "Young Multi", "Guzior", "Bedoes", "Paluch", "Slums Attack", "Tede", "Popek",
        "Fokus", "Sarius", "Włodi", "Trzeci Wymiar", "WSRH",
    )
}
