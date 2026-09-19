package com.aurora.player.podcast

import com.aurora.player.domain.model.Podcast
import com.aurora.player.domain.model.PodcastEpisode
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser RSS 2.0 (kanał podcastu) — DESIGN.md Etap 32. `javax.xml.parsers` (wbudowane w Android,
 * ten sam wzorzec co PROPFIND w WebDavLibraryRepository), namespace-aware dla `itunes:` —
 * ZERO nowej zależności do parsowania XML.
 */
object PodcastRssParser {

    fun parseChannel(feedUrl: String, xml: String): Podcast? {
        val document = parseDocument(xml) ?: return null
        val channel = document.getElementsByTagName("channel").item(0) as? Element ?: return null
        val title = channel.firstElementByTagName("title")?.textContent?.trim() ?: return null
        return Podcast(
            feedUrl = feedUrl,
            title = title,
            author = channel.firstElementByLocalName("author")?.textContent?.trim().orEmpty(),
            artworkUrl = channel.firstElementByLocalName("image")?.getAttribute("href")
                ?.ifBlank { null }
                ?: channel.firstElementByTagName("image")?.firstElementByTagName("url")?.textContent?.trim(),
            description = channel.firstElementByTagName("description")?.textContent?.trim().orEmpty(),
        )
    }

    fun parseEpisodes(feedUrl: String, xml: String): List<PodcastEpisode> {
        val document = parseDocument(xml) ?: return emptyList()
        val items = document.getElementsByTagName("item")
        val episodes = mutableListOf<PodcastEpisode>()
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val enclosure = item.firstElementByTagName("enclosure") ?: continue
            val audioUrl = enclosure.getAttribute("url").ifBlank { null } ?: continue
            val title = item.firstElementByTagName("title")?.textContent?.trim() ?: continue
            val guid = item.firstElementByTagName("guid")?.textContent?.trim()?.ifBlank { null } ?: audioUrl
            val transcript = item.bestTranscript()
            episodes += PodcastEpisode(
                guid = guid,
                feedUrl = feedUrl,
                title = title,
                audioUrl = audioUrl,
                durationMs = parseDurationMs(item.firstElementByLocalName("duration")?.textContent),
                publishedAtMs = parsePubDateMs(item.firstElementByTagName("pubDate")?.textContent),
                description = item.firstElementByTagName("description")?.textContent?.trim().orEmpty(),
                transcriptUrl = transcript?.first,
                transcriptType = transcript?.second,
            )
        }
        return episodes
    }

    /**
     * `<podcast:transcript>` (namespace Podcasting 2.0, https://podcastindex.org/namespace/1.0) —
     * jeden odcinek może mieć KILKA wariantów tego samego transkryptu w różnych formatach
     * (`text/plain`, `text/vtt`, `application/srt`, `application/json`). Wybieramy najprostszy do
     * sparsowania bez dodatkowej zależności: plain > vtt/srt (napisy z prostym do zdjęcia
     * timecode) > json (osobny, niestandaryzowany schemat, pomijamy).
     */
    private fun Element.bestTranscript(): Pair<String, String>? {
        val nodes = getElementsByTagNameNS("*", "transcript")
        val candidates = (0 until nodes.length).mapNotNull { i ->
            val el = nodes.item(i) as? Element ?: return@mapNotNull null
            val url = el.getAttribute("url").ifBlank { null } ?: return@mapNotNull null
            val type = el.getAttribute("type").ifBlank { "text/plain" }
            url to type
        }
        return candidates.firstOrNull { it.second.contains("text/plain") }
            ?: candidates.firstOrNull { it.second.contains("vtt") || it.second.contains("srt") }
            ?: candidates.firstOrNull()
    }

    private fun parseDocument(xml: String): Document? = try {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        factory.newDocumentBuilder().parse(xml.byteInputStream())
    } catch (e: Exception) {
        null
    }

    /** `itunes:duration` bywa sekundami ("3661") albo "HH:MM:SS"/"MM:SS". */
    private fun parseDurationMs(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return 0L
        return if (':' in value) {
            val parts = value.split(':').mapNotNull { it.toLongOrNull() }
            var seconds = 0L
            for (part in parts) seconds = seconds * 60 + part
            seconds * 1000
        } else {
            (value.toLongOrNull() ?: 0L) * 1000
        }
    }

    /** `pubDate` to RFC 1123 (np. "Wed, 02 Oct 2024 15:00:00 +0000") — nie każdy feed jest w 100% zgodny. */
    private fun parsePubDateMs(raw: String?): Long {
        val value = raw?.trim() ?: return 0L
        return try {
            DateTimeFormatter.RFC_1123_DATE_TIME.parse(value) { temporal ->
                java.time.Instant.from(temporal)
            }.toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    private fun Element.firstElementByTagName(tagName: String): Element? {
        val nodes = getElementsByTagName(tagName)
        return if (nodes.length > 0) nodes.item(0) as? Element else null
    }

    private fun Element.firstElementByLocalName(localName: String): Element? {
        val nodes = getElementsByTagNameNS("*", localName)
        return if (nodes.length > 0) nodes.item(0) as? Element else null
    }
}
