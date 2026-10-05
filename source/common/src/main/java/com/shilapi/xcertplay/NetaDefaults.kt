package com.shilapi.xcertplay

import android.content.Context

/**
 * One-time first-run defaults tuned for the Nezha (哪吒 NETA L) head unit, mirroring the
 * community NETA L build's proven profile:
 *
 *  - System bars stay visible (hide_top_bar/hide_bottom_bar = false). Hiding them makes the
 *    window aspect change after the CarPlay canvas was negotiated, and the letterboxed stream
 *    then shows black borders inside the app zone.
 *  - Media pipeline tuned for the NETA L decoder: H.264 only, 30 fps, 0.8x canvas, 500 ms buffer.
 *  - Wireless (manual hotspot) is the default transport; audio focus takeover enabled.
 *
 * Applied once (guarded by the `neta_defaults_v1` preference flag); users can change every
 * value in settings afterwards.
 */
object NetaDefaults {
    fun apply(context: Context) {
        val preferences = context.getSharedPreferences(AirPlayPersistence.PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(FLAG, false)) {
            // Units that already ran the earlier profile still get the top touch buffer and
            // the two-finger settings gesture (Nezha's launcher claims three-finger swipes).
            if (!preferences.contains("top_touch_buffer_percent")) {
                preferences.edit().putInt("top_touch_buffer_percent", 3).apply()
            }
            if (!preferences.contains("settings_gesture_fingers")) {
                preferences.edit().putInt("settings_gesture_fingers", 2).apply()
            }
            return
        }
        // Legacy installs may carry an under-reported physical width (e.g. 187 mm on the
        // 15.6" NETA L panel). iOS renders CarPlay at a fixed physical size, so a too-small
        // report makes every icon giant and pushes UI off-screen. Correct it once; users can
        // still tune it in settings (Display → physical width).
        if (preferences.getInt("display_width_physical_mm", 0) < 250) {
            preferences.edit().putInt("display_width_physical_mm", 450).apply()
        }
        val applied = preferences.edit()
            .putBoolean("wireless_enabled", true)
            .putString("wireless_hotspot_mode", "MANUAL")
            .putBoolean("hevc_enabled", false)
            .putInt("display_fps", 30)
            .putInt("display_scale_tenths", 8)
            .putInt("media_buffer_ms", 500)
            .putBoolean("audio_focus_enabled", true)
            .putInt("media_audio_channel", 0)
            .putInt("navigation_audio_channel", 0)
            .putBoolean("hide_top_bar", false)
            .putBoolean("hide_bottom_bar", false)
            .putBoolean("cluster_map_enabled", false)
            .putInt("top_touch_buffer_percent", 3)
            .putInt("settings_gesture_fingers", 2)
            .putBoolean(FLAG, true)
            .commit()
        check(applied) { "Could not save Nezha first-run defaults" }
    }

    private const val FLAG = "neta_defaults_v1"
}
