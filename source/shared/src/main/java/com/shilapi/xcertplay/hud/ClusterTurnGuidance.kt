package com.shilapi.xcertplay.hud

/**
 * Turn-card snapshot shown on the (optional) instrument cluster.
 *
 * On Neta HZEP32 there is no instrument-nav cluster, so [ClusterTurnCardView] is
 * never actually rendered — but CarPlayHostActivity still routes iAP2 turn
 * guidance into this value so the code path stays exercised and the class
 * keeps a shape close to the BYD original for future clusters.
 */
data class ClusterTurnGuidance(
    val icon: Int = 0,
    val distanceMeters: Int = 0,
    val road: String = "",
    val roundaboutExit: Int? = null,
) {
    companion object {
        /**
         * Direct map from Apple's CarPlay maneuver types to the drawable tag the
         * turn-card renderer switches on. See ClusterTurnCardView.glyphRes().
         */
        fun from(maneuver: AppleManeuverLike): ClusterTurnGuidance {
            val icon = maneuver.type.let { t ->
                when (t) {
                    1 -> 2 // LEFT_TURN
                    2 -> 3 // RIGHT_TURN
                    3 -> 9 // STRAIGHT
                    4 -> 11 // UT_LEFT
                    5 -> 15 // UT_RIGHT
                    6 -> 4  // SLIGHT_LEFT
                    7 -> 5  // SLIGHT_RIGHT
                    8 -> 17 // HARD_LEFT
                    9 -> 18 // HARD_RIGHT
                    10 -> 6 // SHARP_LEFT
                    11 -> 7 // SHARP_RIGHT
                    12 -> 8 // ROUNDABOUT_EXIT
                    13 -> 19 // FEED_IN_LEFT
                    14 -> 20 // FEED_IN_RIGHT
                    15 -> 1  // MERGE
                    16 -> 2  // ENTER_HIGHER_WAY
                    else -> 0 // unknown -> no glyph
                }
            }
            val exit: Int? = maneuver.roundaboutExit.takeIf { it in 1..9 }
            return ClusterTurnGuidance(
                icon = icon,
                distanceMeters = maneuver.distanceMeters.coerceAtLeast(0),
                road = maneuver.road,
                roundaboutExit = exit,
            )
        }

        fun from(type: Int, distanceMeters: Int, road: String = "", roundaboutExit: Int? = null): ClusterTurnGuidance =
            from(object : AppleManeuverLike {
                override val type = type
                override val distanceMeters = distanceMeters
                override val road = road
                override val roundaboutExit = roundaboutExit
            })
    }
}

interface AppleManeuverLike {
    val type: Int
    val distanceMeters: Int
    val road: String
    val roundaboutExit: Int?
}
