package com.aurora.player.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aurora.player.data.database.dao.ArchiveLibraryDao
import com.aurora.player.data.database.dao.ArtistInfoDao
import com.aurora.player.data.database.dao.AudiobookDao
import com.aurora.player.data.database.dao.EqStateDao
import com.aurora.player.data.database.dao.FavoriteTrackDao
import com.aurora.player.data.database.dao.LyricsCacheDao
import com.aurora.player.data.database.dao.PlayEventDao
import com.aurora.player.data.database.dao.PlaylistDao
import com.aurora.player.data.database.dao.PodcastDao
import com.aurora.player.data.database.dao.SkipEventDao
import com.aurora.player.data.database.dao.TrackAffinityDao
import com.aurora.player.data.database.dao.TrackAudioMetadataDao
import com.aurora.player.data.database.dao.TrackCooccurrenceDao
import com.aurora.player.data.database.dao.TrackMetadataOverrideDao
import com.aurora.player.data.database.entity.ArchiveLibraryTrackEntity
import com.aurora.player.data.database.entity.ArtistInfoEntity
import com.aurora.player.data.database.entity.AudiobookEntity
import com.aurora.player.data.database.entity.AudiobookPlaybackPositionEntity
import com.aurora.player.data.database.entity.EqStateEntity
import com.aurora.player.data.database.entity.FavoriteTrackEntity
import com.aurora.player.data.database.entity.LyricsCacheEntity
import com.aurora.player.data.database.entity.PlayEventEntity
import com.aurora.player.data.database.entity.PlaylistEntity
import com.aurora.player.data.database.entity.PlaylistTrackEntity
import com.aurora.player.data.database.entity.PodcastPlaybackPositionEntity
import com.aurora.player.data.database.entity.PodcastSubscriptionEntity
import com.aurora.player.data.database.entity.SkipEventEntity
import com.aurora.player.data.database.entity.TrackAffinityEntity
import com.aurora.player.data.database.entity.TrackAudioMetadataEntity
import com.aurora.player.data.database.entity.TrackCooccurrenceEntity
import com.aurora.player.data.database.entity.TrackMetadataOverrideEntity

/**
 * Etap 3-4: tabele potrzebne do Genius (patrz DESIGN.md sekcja 5.1). `exportSchema = false`
 * bo to wczesny etap projektu — historia migracji schematu dojdzie, gdy struktura się ustabilizuje.
 * Etap 22: playlisty + ulubione (`fallbackToDestructiveMigration` w DatabaseModule kasuje tylko
 * odtwarzalną historię Genius, nie ma tam jeszcze nic nieodtwarzalnego, ale playlisty/ulubione OD
 * TERAZ już są danymi użytkownika, nie cache'em — patrz komentarz w DatabaseModule przy migracji.
 * Etap 24: cache tekstów LRCLIB — odtwarzalny (można dociągnąć ponownie z sieci), więc destrukcyjna
 * migracja przy wersji 5 jest nadal bezpieczna z tych samych powodów co Genius.
 * Etap 25: nadpisania tytułu/wykonawcy z MusicBrainz — też odtwarzalne (moduł sam dociągnie
 * ponownie z sieci po utracie tabeli), ta sama zasada.
 * Etap 28: `TrackMetadataOverrideEntity` dostała kolumnę `albumArtUri` (Cover Art Archive) —
 * ta sama tabela, ta sama zasada odtwarzalności.
 * Etap 29: cache codec/bitrate/sample rate/bit depth (`MediaExtractor`) — odtwarzalny (appka sama
 * doczyta plik ponownie), ta sama zasada.
 * Etap 25 (podcasty): subskrypcje NIE są odtwarzalne z sieci (to wybór usera, nie cache) —
 * pierwsza tabela w tym pliku, której utrata przy destrukcyjnej migracji faktycznie boli. Zostaje
 * mimo to na `fallbackToDestructiveMigration` na tym wczesnym etapie (spójność z resztą appki),
 * ale to już świadomy koszt, nie "nic się nie stanie" jak przy poprzednich tabelach.
 * Etap 38: `EqStateEntity` — trwały zapis equalizera (Etap 20d, wcześniej zapowiedziane w
 * EqRepositoryImpl jako "dojdzie razem z tabelami Genius", nigdy nie zrobione). Odtwarzalny
 * (user może sobie ustawić EQ od nowa) — ta sama zasada co reszta tabel.
 * Etap 37: `AudiobookEntity`/`AudiobookPlaybackPositionEntity` — biblioteka audiobooków LibriVox,
 * ten sam kształt i te same zasady co tabele podkastów (subskrypcja NIE jest odtwarzalna z sieci,
 * pozycja odtwarzania jest kluczowa przy wielogodzinnych książkach).
 * Etap 40: `ArchiveLibraryTrackEntity` — ścieżki z Internet Archive pobrane NA STAŁE (offline) do
 * biblioteki, nie tylko przesłuchane w streamingu. NIE jest odtwarzalna z sieci samym cache'em —
 * strata tej tabeli to strata realnie pobranego pliku (patrz `localFileUri`), ten sam świadomy
 * koszt `fallbackToDestructiveMigration` co subskrypcje podkastów/audiobooki wyżej.
 * Etap 43: `ArtistInfoEntity` — cache bio/gatunku/grafiki z TheAudioDB per wykonawca. Odtwarzalny
 * (appka sama dociągnie ponownie z sieci po utracie tabeli), ta sama zasada co reszta cache'y.
 * Etap 53: `ArchiveLibraryTrackEntity` dostała `remoteUrl`/`category`, a `localFileUri` stała się
 * nullable — pozycja może być teraz dodana jako STREAM (bez lokalnego pliku, `localFileUri = null`,
 * gra z `remoteUrl`) obok dotychczasowego pełnego pobrania. `category` (Muzyka/Podcasty/Audiobooki/
 * Radio) pozwala pokazać pobrane audiobooki/podcasty z Archiwum w tych samych ekranach co realne
 * Audiobooki/Subskrypcje.
 */
@Database(
    entities = [
        PlayEventEntity::class,
        SkipEventEntity::class,
        TrackAffinityEntity::class,
        TrackCooccurrenceEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteTrackEntity::class,
        LyricsCacheEntity::class,
        TrackMetadataOverrideEntity::class,
        TrackAudioMetadataEntity::class,
        PodcastSubscriptionEntity::class,
        PodcastPlaybackPositionEntity::class,
        EqStateEntity::class,
        AudiobookEntity::class,
        AudiobookPlaybackPositionEntity::class,
        ArchiveLibraryTrackEntity::class,
        ArtistInfoEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun playEventDao(): PlayEventDao
    abstract fun skipEventDao(): SkipEventDao
    abstract fun trackAffinityDao(): TrackAffinityDao
    abstract fun trackCooccurrenceDao(): TrackCooccurrenceDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun trackMetadataOverrideDao(): TrackMetadataOverrideDao
    abstract fun trackAudioMetadataDao(): TrackAudioMetadataDao
    abstract fun podcastDao(): PodcastDao
    abstract fun eqStateDao(): EqStateDao
    abstract fun audiobookDao(): AudiobookDao
    abstract fun archiveLibraryDao(): ArchiveLibraryDao
    abstract fun artistInfoDao(): ArtistInfoDao
}
