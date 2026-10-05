package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.EvChargingConnectors
import com.shilapi.xcertplay.transport.VehicleGear
import com.shilapi.xcertplay.transport.VehicleSpeedReading
import com.shilapi.xcertplay.transport.VehicleSpeedSample
import com.shilapi.xcertplay.transport.VehicleSpeedSource
import com.shilapi.xcertplay.transport.VehicleStatusProvider
import com.shilapi.xcertplay.transport.VehicleStatusSnapshot

private object BatteryStubState {
    fun read(context: Context): Pair<Int, Boolean> {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = context.registerReceiver(null, filter)
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val percent = if (level >= 0 && scale > 0) {
                ((level * 100) / scale).coerceIn(0, 100)
            } else 0
            percent to charging
        } catch (_: Throwable) {
            0 to false
        }
    }
}

/**
 * Neta stub of the removed BYD navigation output controller. On Neta HZEP32
 * there is no HUD and the instrument cluster does not render navigation
 * overlays, so all BYD cluster-side methods are no-ops. Battery / parked /
 * wheel-speed accessors fall back to the standard Android APIs so the rest
 * of CarPlay can still query them.
 */
object BydNavigationOutputs {

    @Volatile private var running: Boolean = false
    private var turnOverlayListener: ((ClusterTurnGuidance?) -> Unit)? = null
    private var clusterStreamControl: ((Boolean) -> Unit)? = null

    fun start(context: Context) {
        running = true
    }

    fun endNow() {
        running = false
    }

    fun isRunning(): Boolean = running

    fun setTurnOverlayListener(listener: ((ClusterTurnGuidance?) -> Unit)?) {
        turnOverlayListener = listener
    }

    fun setClusterMapShown(shown: Boolean) {
        clusterStreamControl?.invoke(shown)
    }

    fun setClusterStreamControl(handler: (Boolean) -> Unit) {
        clusterStreamControl = handler
    }

    fun clearClusterStreamControl(handler: (Boolean) -> Unit) {
        if (clusterStreamControl == handler) clusterStreamControl = null
    }

    fun onFrame(frame: Iap2Frame) {
        // No HUD/cluster on Neta head unit — frame is discarded.
    }

    fun refreshTurnOverlay() {
        // no-op: Neta has no turn overlay rendering.
    }

    fun parked(context: Context): Boolean? {
        val (_, charging) = BatteryStubState.read(context)
        return charging
    }

    /**
     * Wheel-speed source for iAP2 `$PASCD`. Neta HZEP32 exposes no bus-level
     * wheel-speed channel, so we always report 0 m/s on gear D. The rest of
     * CarPlay keeps working; the iPhone simply sees the vehicle as standing
     * still when there is no other odometry source.
     */
    fun wheelSpeed(context: Context): VehicleSpeedSource = ZeroSpeedSource

    private object ZeroSpeedSource : VehicleSpeedSource {
        override fun start() {}
        override fun stop() {}
        override fun drain(): VehicleSpeedReading =
            VehicleSpeedReading(
                gear = VehicleGear.DRIVE,
                samples = listOf(VehicleSpeedSample(System.nanoTime(), 0.0)),
            )
    }

    fun batteryStatus(context: Context): VehicleStatusProvider {
        val app = context.applicationContext
        return VehicleStatusProvider {
            val (percent, charging) = BatteryStubState.read(app)
            VehicleStatusSnapshot(
                rangeKm = (percent * 5).toInt(),
                rangeWarning = percent < 15,
                batteryPercent = percent.toDouble(),
                currentChargeWh = null,
                maxChargeWh = null,
                maxRangeKm = 500,
                charging = charging,
            )
        }
    }
}
