package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.transport.EvChargingConnectors

/**
 * Neta stub of the removed BYD output settings controller. All BYD-specific
 * toggles (battery to iPhone, wheel-speed to iPhone, video-while-parked,
 * cluster navigation mode, etc.) default to off since Neta HZEP32 doesn't
 * render BYD's cluster overlays. Battery-to-iPhone is enabled by default so
 * CarPlay can show vehicle status via Android BatteryManager.
 */
object BydOutputSettings {

    fun batteryToIphoneActive(context: Context): Boolean = true

    fun wheelSpeedToIphoneActive(context: Context): Boolean = true

    fun videoWhileParkedActive(context: Context): Boolean = false

    fun navigationAvailable(context: Context): Boolean = false

    fun chargingConnectors(context: Context): EvChargingConnectors = EvChargingConnectors.GB_T

    fun batterySupported(context: Context): Boolean = true

    fun motionSupported(context: Context): Boolean = false

    fun gearSupported(context: Context): Boolean = false

    fun batteryPercent(context: Context): Int {
        return try {
            val sticky = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
            ) ?: return 0
            val level = sticky.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0)
            val scale = sticky.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            if (scale <= 0) 0 else ((level * 100) / scale).coerceIn(0, 100)
        } catch (_: Throwable) {
            0
        }
    }

    fun rangeKm(context: Context): Int = 0

    fun speedKmh(context: Context): Int = 0

    fun gear(context: Context): String = ""
}
