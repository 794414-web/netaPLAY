package com.shilapi.xcertplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.shilapi.xcertplay.airplay.AirPlayDisplaySettings
import java.lang.Math.sqrt

/**
 * Runtime detector for the physical width (mm) of the display CarPlay is actually drawn onto.
 *
 * Why this exists: `AirPlayDisplaySettings.DEFAULT_WIDTH_PHYSICAL_MM` is a fixed value that
 * works on some head units but under-reports width on larger ones (e.g. Neta 哪吒 HZEP32
 * 15.6" screen is ~430mm wide; reporting 200mm makes iOS hide the CarPlay dock because the
 * layout overflows). Reading [Display.getRealMetrics] lets us derive the actual physical width
 * from Android-reported dpi and pixel counts, so the value sent to iPhone via AirPlayInfoPlist
 * matches the real canvas.
 *
 * On multi-display vehicles (main + passenger + HUD) we enumerate all Displays, skip virtual
 * cluster screens, and return the widest real panel. That's the one DiPlay's Activity is
 * rendered into on Neta hardware.
 */
object NetaDisplayDetector {

    data class DisplayGeometry(
        val widthPixels: Int,
        val heightPixels: Int,
        val xdpi: Float,
        val ydpi: Float,
    )

    /**
     * Derive physical width in millimeters from a raw display geometry. Returns null when the
     * inputs are unusable (bad dpi or zero pixels) so callers can fall back to a sane default.
     *
     * The formula is:
     *   diagonalInches = sqrt(w^2 + h^2) / dpiforDiagonal
     *   widthInches    = diagonalInches * w / sqrt(w^2 + h^2)
     * which simplifies to widthInches = w / xdpi (using xdpi as the dominant density). We use
     * the diagonal form so xdpi and ydpi can differ without breaking the result.
     */
    fun widthMillimeters(geometry: DisplayGeometry): Int? {
        if (geometry.widthPixels <= 0 || geometry.heightPixels <= 0) return null
        if (geometry.xdpi <= 1f || geometry.ydpi <= 1f) return null
        // Diagonal in pixels / average dpi = diagonal in inches, then take the width component.
        val diagonalPixels = sqrt(
            geometry.widthPixels.toDouble() * geometry.widthPixels +
                geometry.heightPixels.toDouble() * geometry.heightPixels,
        )
        // Some OEMs report xdpi == ydpi == 0 for virtual or synthetic displays. Bail.
        if (diagonalPixels <= 0.0) return null
        // Use xdpi for the width calculation so a wrong ydpi doesn't skew the horizontal result.
        val diagonalInches = diagonalPixels / geometry.xdpi.toDouble()
        val widthInches = diagonalInches * geometry.widthPixels / diagonalPixels
        val widthMm = widthInches * 25.4
        return Math.round(widthMm).toInt()
    }

    /**
     * Enumerate displays and return the widest physical panel width in mm. Preference order:
     *   1. Real external/wide panel (15.6" main screen) — largest pixel-count × dpi product.
     *   2. Any other physical panel — as a fallback.
     *   3. null — when nothing usable is available.
     *
     * Virtual displays (cluster / instrument clusters rendered by the OS) are typically small
     * (720p, very high dpi) and would under-report width, so we prefer the largest pixel area.
     */
    fun detectWidthMillimeters(context: Context): Int? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        val displays = manager.displays ?: return null
        var best: Int? = null
        var bestArea = -1L
        for (display in displays) {
            val dm = android.util.DisplayMetrics()
            display.getRealMetrics(dm)
            val geometry = DisplayGeometry(
                widthPixels = dm.widthPixels,
                heightPixels = dm.heightPixels,
                xdpi = dm.xdpi,
                ydpi = dm.ydpi,
            )
            val area = geometry.widthPixels.toLong() * geometry.heightPixels.toLong()
            if (area <= 0) continue
            val mm = widthMillimeters(geometry) ?: continue
            // Sanity gate: ignore obvious outliers (smaller than a wristwatch or larger than a wall).
            if (mm < AirPlayDisplaySettings.MIN_WIDTH_PHYSICAL_MM) continue
            if (area > bestArea) {
                bestArea = area
                best = mm
            }
        }
        return best
    }

    /**
     * Return the display id and metadata of the widest real panel. Useful for diagnostics.
     * Returns null when no usable display is found.
     */
    fun inspectWidestDisplay(context: Context): Pair<Int, DisplayGeometry>? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        val displays = manager.displays ?: return null
        var best: Pair<Int, DisplayGeometry>? = null
        var bestArea = -1L
        for (display in displays) {
            val dm = android.util.DisplayMetrics()
            display.getRealMetrics(dm)
            val geometry = DisplayGeometry(
                widthPixels = dm.widthPixels,
                heightPixels = dm.heightPixels,
                xdpi = dm.xdpi,
                ydpi = dm.ydpi,
            )
            val area = geometry.widthPixels.toLong() * geometry.heightPixels.toLong()
            if (area > bestArea) {
                bestArea = area
                best = display.displayId to geometry
            }
        }
        return best
    }

    /** Display.DEFAULT_DISPLAY constant is `0`; re-exposed here so tests don't import android.view. */
    const val DEFAULT_DISPLAY_ID = Display.DEFAULT_DISPLAY
}
