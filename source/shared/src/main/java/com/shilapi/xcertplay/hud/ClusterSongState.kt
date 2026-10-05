package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/**
 * Stub of the removed BYD cluster song state. Extracts song metadata from
 * CarPlay NowPlayingUpdate frames (0x5001) so glance tiles keep working.
 */
internal class ClusterSongState {
    companion object {
        const val NOW_PLAYING_UPDATE: Int = 0x5001
    }

    data class Song(val text: String = "", val playing: Boolean = false)

    @Volatile private var current: Song? = null

    fun accept(frame: Iap2Frame): Boolean {
        if (frame.payload.isEmpty()) return false
        current = extract(frame.payload)
        return true
    }

    fun current(): Song? = current

    fun clear() {
        current = null
    }

    private fun extract(payload: ByteArray): Song {
        try {
            val body = stripHeader(payload)
            var text = ""
            var playing = false
            var i = 0
            while (i + 4 <= body.size) {
                val key = (body[i].toInt() and 0xFF) shl 24 or
                    ((body[i + 1].toInt() and 0xFF) shl 16) or
                    ((body[i + 2].toInt() and 0xFF) shl 8) or
                    (body[i + 3].toInt() and 0xFF)
                i += 4
                if (i >= body.size) break
                val len = body[i].toInt() and 0xFF
                i += 1
                if (i + len > body.size) break
                when (key) {
                    0x0002, 0x0003, 0x0004 -> {
                        val s = String(body, i, len, Charsets.UTF_8)
                        if (s.isNotEmpty() && s.length < 200) text = s
                    }
                }
                i += len
            }
            return Song(text = text, playing = text.isNotEmpty())
        } catch (_: Throwable) {
            return Song()
        }
    }

    private fun stripHeader(payload: ByteArray): ByteArray {
        if (payload.size < 4) return ByteArray(0)
        val n = payload[3].toInt() and 0xFF
        return if (4 + n <= payload.size) payload.copyOfRange(4, 4 + n) else payload.copyOfRange(4, payload.size)
    }
}
