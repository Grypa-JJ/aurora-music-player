package com.aurora.player.metadata

import com.aurora.player.data.database.dao.ArtistInfoDao
import com.aurora.player.data.database.entity.ArtistInfoEntity
import com.aurora.player.domain.model.ArtistInfo
import com.aurora.player.domain.repository.ArtistInfoRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Łączy [ArtistInfoDao] (cache, także negatywny) z [TheAudioDbClient] (sieć, rate-limited) —
 * DESIGN.md Etap 43. Klucz cache to znormalizowana nazwa wykonawcy (trim+lowercase) — appka nie
 * ma stabilnego id wykonawcy poza tym stringiem z tagów.
 */
@Singleton
class ArtistInfoRepositoryImpl @Inject constructor(
    private val dao: ArtistInfoDao,
    private val client: TheAudioDbClient,
) : ArtistInfoRepository {

    override suspend fun getArtistInfo(artistName: String): ArtistInfo? {
        val key = artistName.trim().lowercase()
        if (key.isEmpty()) return null

        dao.get(key)?.let { cached -> return cached.toDomain() }

        val info = client.findArtist(artistName)
        dao.insert(
            ArtistInfoEntity(
                artistNameKey = key,
                biography = info?.biography,
                genre = info?.genre,
                style = info?.style,
                mood = info?.mood,
                bannerUrl = info?.bannerUrl,
                thumbUrl = info?.thumbUrl,
                fetchedAtMs = System.currentTimeMillis(),
            ),
        )
        return info
    }

    private fun ArtistInfoEntity.toDomain(): ArtistInfo? {
        if (biography == null && genre == null && style == null && mood == null && bannerUrl == null && thumbUrl == null) {
            return null
        }
        return ArtistInfo(
            biography = biography,
            genre = genre,
            style = style,
            mood = mood,
            bannerUrl = bannerUrl,
            thumbUrl = thumbUrl,
        )
    }
}
