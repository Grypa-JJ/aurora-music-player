package com.aurora.player.domain.util

/**
 * Wyprowadza stabilny [Track.id][com.aurora.player.domain.model.Track.id] (Long) dla utworów
 * spoza lokalnego MediaStore — patrz DESIGN.md Etap 13. Zastępuje wcześniejszy schemat
 * "CLOUD_ID_OFFSET + 31-bitowy String.hashCode()" (Google Drive, Etap 6), który rezerwował
 * JEDNO wspólne pasmo dla całej kategorii "chmura" — przy kolejnych źródłach (Etap 12: OneDrive,
 * Dropbox, NAS/WebDAV...) dzieliłyby to samo pasmo i mogłyby się nawzajem zderzać, bo
 * `String.hashCode()` samych identyfikatorów plików nic nie wie o tym, z jakiego są źródła.
 *
 * Zamiast tego: 64-bitowy FNV-1a (dobrze znany, dobra dystrybucja, deterministyczny, bez
 * zależności) liczony z `"$sourceDiscriminator:$nativeId"` — każde źródło ma WŁASNĄ przestrzeń
 * skrótu przez sam dyskryminator wejściowy, więc dowolna liczba przyszłych źródeł jest bezpieczna
 * bez kolejnych ręcznych pasm przesunięć. Wynik dodatkowo podniesiony nad [CLOUD_ID_FLOOR], żeby
 * nigdy nie kolidować z małymi, sekwencyjnymi `_ID` z lokalnego MediaStore (niezmienione przez
 * ten refaktor — nie ma powodu przepisywać już stabilnych, działających lokalnych identyfikatorów).
 */
object TrackIdHasher {

    /** Dowolne id lokalnego MediaStore mieści się bezpiecznie poniżej tej wartości. */
    const val CLOUD_ID_FLOOR = 1_000_000_000_000L

    private const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL // 0xCBF29CE484222325 jako signed Long
    private const val FNV_PRIME = 0x100000001B3L

    /**
     * @param sourceDiscriminator stała, krótka etykieta źródła (np. "google_drive", "onedrive") —
     *   MUSI być unikalna per źródło i nigdy nie zmieniać się dla już wydanych wersji appki,
     *   inaczej wszystkie id (i powiązana z nimi historia/affinity/cooccurrence) dla tego źródła
     *   "przeskoczą" na nowe wartości.
     * @param nativeId opaque id nadane przez samo źródło (np. Google Drive fileId).
     */
    fun deriveId(sourceDiscriminator: String, nativeId: String): Long {
        var hash = FNV_OFFSET_BASIS
        for (byte in "$sourceDiscriminator:$nativeId".toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toLong() and 0xFFL)
            hash *= FNV_PRIME
        }
        return CLOUD_ID_FLOOR + (hash and 0x7FFFFFFFFFFFFFFL)
    }
}
