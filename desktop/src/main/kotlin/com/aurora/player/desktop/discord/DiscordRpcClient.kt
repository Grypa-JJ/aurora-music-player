package com.aurora.player.desktop.discord

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Discord Rich Presence przez surowe Discord IPC — bez natywnego SDK/JNA. Cały protokół to
 * ramki JSON (opcode+length, little-endian, potem UTF-8 payload) nad lokalnym named pipe
 * (Windows: `\\.\pipe\discord-ipc-N`) albo unix socketem (macOS/Linux: `$XDG_RUNTIME_DIR/
 * discord-ipc-N`). Discord musi działać NA TYM SAMYM komputerze — to lokalne IPC, nie API
 * przez sieć (patrz DESIGN.md, Etap 48). Oficjalny opis: https://discord.com/developers/docs/topics/rpc
 *
 * Wymaga własnego `clientId` z Discord Developer Portal (discord.com/developers/applications →
 * New Application → "Application ID"). Bez tego cała klasa jest no-opem (patrz [DiscordRpc.create]).
 */
class DiscordRpcClient private constructor(private val clientId: String) : Closeable {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val writeLock = Mutex()
    private var connection: PipeIo? = null
    @Volatile private var connected = false

    fun start() {
        scope.launch {
            while (isActive) {
                if (!connected) {
                    runCatching { connectOnce() }
                }
                delay(if (connected) 15_000 else 10_000)
            }
        }
    }

    /** Ustawia widoczny w Discordzie status. Bezpieczne w wywołaniu, gdy Discord nie jest jeszcze podłączony (no-op). */
    fun setNowPlaying(title: String, artist: String, isPlaying: Boolean, positionMs: Long) {
        scope.launch {
            val activity = JSONObject().apply {
                put("details", title.take(128).ifBlank { "Aurora" })
                put("state", artist.take(128).ifBlank { "Nieznany wykonawca" })
                if (isPlaying) {
                    put("timestamps", JSONObject().put("start", System.currentTimeMillis() - positionMs))
                }
                put(
                    "assets",
                    JSONObject().apply {
                        put("large_image", "aurora_logo")
                        put("large_text", "Aurora")
                        if (!isPlaying) put("small_text", "Wstrzymano")
                    },
                )
            }
            sendSetActivity(activity)
        }
    }

    fun clear() {
        scope.launch { sendSetActivity(null) }
    }

    override fun close() {
        scope.launch {
            runCatching { sendSetActivity(null) }
            connection?.close()
            connection = null
            connected = false
        }
    }

    private fun connectOnce() {
        val io = openPipe() ?: return
        try {
            io.writeFrame(OP_HANDSHAKE, JSONObject().apply { put("v", 1); put("client_id", clientId) }.toString())
            val (opcode, payload) = io.readFrame()
            val evt = runCatching { JSONObject(payload).optString("evt") }.getOrNull()
            if (opcode != OP_FRAME || evt != "READY") throw IOException("Handshake failed: $payload")
            connection = io
            connected = true
        } catch (e: IOException) {
            runCatching { io.close() }
        }
    }

    private suspend fun sendSetActivity(activity: JSONObject?) {
        val io = connection ?: return
        writeLock.withLock {
            try {
                val payload = JSONObject().apply {
                    put("cmd", "SET_ACTIVITY")
                    put(
                        "args",
                        JSONObject().apply {
                            put("pid", ProcessHandle.current().pid())
                            put("activity", activity ?: JSONObject.NULL)
                        },
                    )
                    put("nonce", UUID.randomUUID().toString())
                }.toString()
                io.writeFrame(OP_FRAME, payload)
                io.readFrame()
            } catch (e: IOException) {
                connected = false
                runCatching { connection?.close() }
                connection = null
            }
        }
    }

    private fun openPipe(): PipeIo? {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        for (i in 0..9) {
            if (isWindows) {
                runCatching { WindowsPipeIo("""\\.\pipe\discord-ipc-$i""") }.getOrNull()?.let { return it }
            } else {
                val base = System.getenv("XDG_RUNTIME_DIR") ?: System.getenv("TMPDIR") ?: "/tmp"
                val path = Path.of(base, "discord-ipc-$i")
                if (!Files.exists(path)) continue
                runCatching { UnixPipeIo(path) }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    companion object {
        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1

        /** Zwraca `null` (feature wyłączony), gdy nie skonfigurowano `AURORA_DISCORD_CLIENT_ID`. */
        fun create(): DiscordRpcClient? {
            val clientId = System.getenv("AURORA_DISCORD_CLIENT_ID")?.trim()
            return if (clientId.isNullOrEmpty()) null else DiscordRpcClient(clientId)
        }
    }
}

private interface PipeIo : Closeable {
    fun write(bytes: ByteArray)
    fun readFully(buf: ByteArray)
}

private fun PipeIo.writeFrame(opcode: Int, payload: String) {
    val payloadBytes = payload.toByteArray(Charsets.UTF_8)
    val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(opcode).putInt(payloadBytes.size).array()
    write(header + payloadBytes)
}

private fun PipeIo.readFrame(): Pair<Int, String> {
    val header = ByteArray(8)
    readFully(header)
    val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    val opcode = bb.int
    val length = bb.int
    val payload = ByteArray(length)
    readFully(payload)
    return opcode to String(payload, Charsets.UTF_8)
}

/** Windows: named pipe otwierany jak zwykły plik — standardowa sztuczka dla czystego Javy bez JNA. */
private class WindowsPipeIo(path: String) : PipeIo {
    private val raf = RandomAccessFile(path, "rw")
    override fun write(bytes: ByteArray) = raf.write(bytes)
    override fun readFully(buf: ByteArray) = raf.readFully(buf)
    override fun close() = raf.close()
}

/** macOS/Linux: unix domain socket (java.nio, JDK 16+). */
private class UnixPipeIo(path: Path) : PipeIo {
    private val channel = SocketChannel.open(StandardProtocolFamily.UNIX).apply {
        connect(UnixDomainSocketAddress.of(path))
    }

    override fun write(bytes: ByteArray) {
        channel.write(ByteBuffer.wrap(bytes))
    }

    override fun readFully(buf: ByteArray) {
        val bb = ByteBuffer.wrap(buf)
        while (bb.hasRemaining()) {
            if (channel.read(bb) < 0) throw EOFException("Discord IPC socket closed")
        }
    }

    override fun close() = channel.close()
}
