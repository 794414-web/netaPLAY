package com.shilapi.xcertplay.hud

/**
 * Neta stub of the removed BYD cluster navigation mode. Neta's instrument
 * cluster doesn't render a navigation overlay, so all modes are reported as
 * OFF. The enum values still exist because LocalizedOptions.localizedLabel
 * in the common module switches over them.
 */
enum class BydClusterNaviMode {
    OFF,
    TURN_ON_BY_NAVI,
    SMALL,
    FULL,
}
