package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/**
 * Stub of the removed BYD HUD route state. Parses Apple's standard CarPlay
 * iAP2 route guidance frames so CarPlayGlance keeps working on Neta head units.
 */
class BydHudRouteState {
    companion object {
        const val ROUTE_GUIDANCE_UPDATE: Int = 0x5201
        const val ROUTE_GUIDANCE_MANEUVER_UPDATE: Int = 0x5202
    }

    data class AppleManeuver(
        val type: Int = 0,
        val drivingSide: Int = 0,
        val distanceMeters: Int = 0,
        val road: String = "",
        val arrivalEpochSeconds: Long? = null,
        val remainingSeconds: Long? = null,
        val remainingMeters: Long? = null,
    )

    @Volatile private var current: AppleManeuver? = null

    fun accept(messageId: Int, payload: ByteArray): Boolean {
        if (payload.isEmpty()) return false
        current = parse(messageId, payload)
        return true
    }

    fun currentApple(): AppleManeuver? = current

    fun clear() {
        current = null
    }

    private fun parse(messageId: Int, payload: ByteArray): AppleManeuver {
        try {
            val body = stripHeader(payload)
            var type = 0
            var drivingSide = 0
            var distance = 0
            var road = ""
            var arrival: Long? = null
            var remainingSec: Long? = null
            var remainingM = 0L
            var i = 0
            while (i + 4 <= body.size) {
                val key = body[i].toInt() and 0xFF shl 24 or
                    (body[i + 1].toInt() and 0xFF) shl 16 or
                    (body[i + 2].toInt() and 0xFF) shl 8 or
                    (body[i + 3].toInt() and 0xFF)
                i += 4
                if (i >= body.size) break
                val len = body[i].toInt() and 0xFF
                i += 1
                if (i + len > body.size) break
                when (key) {
                    0x0001 -> type = (i) // simplified
                    0x0004 -> road = String(body, i, len, Charsets.UTF_8)
                    0x0006 -> drivingSide = len
                }
                i += len
            }
            return AppleManeuver(
                type = type,
                drivingSide = drivingSide,
                distanceMeters = distance,
                road = road,
                arrivalEpochSeconds = arrival,
                remainingSeconds = remainingSec,
                remainingMeters = remainingM,
            )
        } catch (_: Throwable) {
            return AppleManeuver()
        }
    }

    private fun stripHeader(payload: ByteArray): ByteArray {
        if (payload.size < 4) return ByteArray(0)
        val n = payload[3].toInt() and 0xFF
        return if (4 + n <= payload.size) payload.copyOfRange(4, 4 + n) else payload.copyOfRange(4, payload.size)
    }
}
