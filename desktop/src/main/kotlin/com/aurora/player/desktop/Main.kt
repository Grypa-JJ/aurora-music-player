package com.aurora.player.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.util.TrackIdHasher
import javafx.embed.swing.JFXPanel
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration as FxDuration
import javax.swing.JFileChooser

/**
 * Minimalny desktopowy port Aurory (DESIGN.md Etap 35) — reużywa :domain 1:1 (Track,
 * TrackIdHasher), własny skan lokalnych plików + odtwarzanie przez JavaFX Media
 * (javax.sound.sampled nie obsługuje MP3, więc czyste Kotlin/JVM audio nie wystarczy).
 *
 * Świadomie NIE wchodzi jeszcze: EQ/DSP, wizualizator projectM, panel ocen Geniusa, chmura
 * (Drive/WebDAV), Radio, Podcasty, trwałe playlisty/ulubione — to jest fundament (skan +
 * playback), nie parytet funkcji z appką na Androida.
 *
 * JavaFX Media obsługuje MP3/WAV/AIFF/M4A(AAC) — NIE obsługuje natywnie FLAC/OGG/Opus, więc
 * skaner celowo filtruje tylko do formatów, które faktycznie da się odtworzyć.
 */
private val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "aiff", "aif", "m4a", "mp4")

private const val ID_DISCRIMINATOR = "desktop_local"

fun main() {
    // Inicjalizuje JavaFX runtime bez okna JavaFX — Compose Desktop rysuje przez Skiko/AWT,
    // więc MediaPlayer (JavaFX) potrzebuje własnego toolkitu wystartowanego raz na starcie.
    JFXPanel()

    application {
        Window(onCloseRequest = ::exitApplication, title = "Aurora") {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AuroraDesktopApp()
            }
        }
    }
}

@Composable
private fun AuroraDesktopApp() {
    val tracks = remember { mutableStateListOf<Track>() }
    var currentIndex by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun disposeCurrentPlayer() {
        player?.stop()
        player?.dispose()
        player = null
    }

    fun playIndex(index: Int) {
        if (index !in tracks.indices) return
        disposeCurrentPlayer()
        val track = tracks[index]
        val media = Media(java.io.File(track.uri).toURI().toString())
        val newPlayer = MediaPlayer(media)
        newPlayer.setOnReady { durationMs = media.duration.toMillis().toLong() }
        newPlayer.currentTimeProperty().addListener { _, _, newValue -> positionMs = newValue.toMillis().toLong() }
        newPlayer.setOnEndOfMedia {
            if (index + 1 in tracks.indices) playIndex(index + 1) else isPlaying = false
        }
        newPlayer.play()
        player = newPlayer
        currentIndex = index
        isPlaying = true
    }

    DisposableEffect(Unit) {
        onDispose { disposeCurrentPlayer() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aurora", style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountButton()
                    IconButton(onClick = {
                        val chooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            dialogTitle = "Wybierz folder z muzyką"
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val root = chooser.selectedFile
                            val found = root.walkTopDown()
                                .filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
                                .map { file -> file.toTrack() }
                                .toList()
                            tracks.clear()
                            tracks.addAll(found)
                            currentIndex = -1
                            disposeCurrentPlayer()
                            isPlaying = false
                        }
                    }) {
                        Icon(Icons.Filled.Folder, contentDescription = "Wybierz folder")
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(tracks.size) { index ->
                    val track = tracks[index]
                    val isCurrent = index == currentIndex
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
                            .clickable { playIndex(index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyLarge)
                            Text(track.artist, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (currentIndex in tracks.indices) {
                val track = tracks[currentIndex]
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall)

                    Slider(
                        value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                        onValueChange = { fraction ->
                            player?.seek(FxDuration.millis((fraction * durationMs).toDouble()))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { if (currentIndex - 1 in tracks.indices) playIndex(currentIndex - 1) }) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Poprzedni")
                        }
                        IconButton(onClick = {
                            val current = player ?: return@IconButton
                            if (isPlaying) current.pause() else current.play()
                            isPlaying = !isPlaying
                        }) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pauza" else "Odtwórz",
                            )
                        }
                        IconButton(onClick = { if (currentIndex + 1 in tracks.indices) playIndex(currentIndex + 1) }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Następny")
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    Text("Wybierz folder, żeby zacząć", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun java.io.File.toTrack(): Track {
    val displayName = nameWithoutExtension
    return Track(
        id = TrackIdHasher.deriveId(ID_DISCRIMINATOR, absolutePath),
        uri = absolutePath,
        title = displayName,
        artist = parentFile?.name ?: "Nieznany wykonawca",
        album = parentFile?.name ?: "",
        genre = null,
        year = null,
        durationMs = 0L,
        dateAddedMs = lastModified(),
        albumArtUri = null,
        source = TrackSource.LOCAL,
    )
}
