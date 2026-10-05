package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.Window
import android.view.WindowManager

/**
 * Nezha (哪吒) head-unit window mode support.
 *
 * The Nezha system extends [WindowManager.LayoutParams] with a `splitState` field:
 * `1` leaves the split-screen area and takes the whole panel (fullscreen), `0` returns the
 * window to the split area. The field only exists on Nezha builds, so every access is
 * reflective and silently skipped elsewhere. The new LayoutParams must be assigned back to
 * `window.attributes` — mutating the field in place does nothing.
 */
object NetaWindowMode {
    private const val TAG = "DiPlay-NetaWindow"

    const val SPLIT_STATE_FULLSCREEN = 1
    const val SPLIT_STATE_SPLIT = 0

    /** True when the current framework exposes the Nezha `splitState` extension field. */
    fun supported(): Boolean = runCatching {
        WindowManager.LayoutParams().javaClass.getDeclaredField("splitState")
    }.isSuccess

    /**
     * Submit [targetValue] as the window's split state. The copy-then-reassign pattern is
     * required: the system only sees the value from the re-committed window attributes.
     */
    fun updateSplitState(window: Window, targetValue: Int) {
        runCatching {
            val updatedAttributes = WindowManager.LayoutParams().also {
                it.copyFrom(window.attributes)
            }
            val field = updatedAttributes.javaClass.getDeclaredField("splitState")
            field.isAccessible = true
            field.setInt(updatedAttributes, targetValue)
            window.attributes = updatedAttributes
        }.onFailure {
            android.util.Log.w(TAG, "splitState update failed: ${it.javaClass.simpleName}")
        }
    }
}

/**
 * Nezha day/night theme state. The head unit publishes its theme through system properties
 * and a sticky broadcast; the broadcast only announces a change, so the properties are always
 * re-read rather than trusting the extras.
 *
 * Returns `null` whenever the Nezha properties are absent (other head units), so callers keep
 * their existing Android uiMode signal in that case.
 */
object NetaThemeState {
    private const val NIGHT_MODE_PROPERTY = "persist.sys.theme_night_mode"
    private const val MAP_THEME_PROPERTY = "persist.sys.theme.from.map"

    /** `true` = night, `false` = day, `null` = no Nezha theme signal on this unit. */
    fun isNight(context: Context): Boolean? {
        val configured = readProperty(NIGHT_MODE_PROPERTY) ?: return null
        return when (configured) {
            "night_mode_on" -> true
            "night_mode_off" -> false
            // Auto mode is decided by the map property; fall back to Android's own signal
            // when that property is missing too.
            "night_mode_auto" -> readProperty(MAP_THEME_PROPERTY)?.let { it == "night_mode" }
                ?: isAndroidNight(context)
            else -> isAndroidNight(context)
        }
    }

    private fun isAndroidNight(context: Context): Boolean {
        val mask = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun readProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        (method.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

/**
 * Follows the Nezha theme manager: re-reads the theme at start and on every theme-change
 * broadcast, then hands the result to [applyTheme]. Only fires on units that expose the
 * Nezha theme properties. Pair [start] and [stop] with the owner's lifecycle.
 */
class NetaThemeMonitor(
    context: Context,
    private val applyTheme: (night: Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_THEME_CHANGED) syncFromSystem()
        }
    }

    fun start() {
        if (registered) return
        if (!syncFromSystem()) return // not a Nezha unit; no point listening
        val flags = if (Build.VERSION.SDK_INT >= 33) Context.RECEIVER_EXPORTED else 0
        appContext.registerReceiver(receiver, IntentFilter(ACTION_THEME_CHANGED), flags)
        registered = true
    }

    fun stop() {
        if (!registered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        registered = false
    }

    /** Returns true when the unit exposes a Nezha theme signal. */
    private fun syncFromSystem(): Boolean {
        val night = NetaThemeState.isNight(appContext) ?: return false
        applyTheme(night)
        return true
    }

    private companion object {
        const val ACTION_THEME_CHANGED =
            "com.hozonauto.thememanager.ACTION_HOZON_THEME_CHANGED"
    }
}
