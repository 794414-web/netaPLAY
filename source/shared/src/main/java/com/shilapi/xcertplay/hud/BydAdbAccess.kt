package com.shilapi.xcertplay.hud

/**
 * Neta stub: BYD's ADB access state is not applicable. CarPlay on Neta uses
 * the standard host flow without ADB-privileged vehicle probes.
 */
object BydAdbAccess {
    enum class Status { UNAVAILABLE, AVAILABLE, AUTHENTICATED }
    enum class State { UNAVAILABLE, PAIRED, AUTHENTICATED, REVOKED, EXPIRED }

    fun check(context: android.content.Context, mayAsk: Boolean = false): Status = Status.UNAVAILABLE
    fun checkState(context: android.content.Context, mayAsk: Boolean = false): State = State.UNAVAILABLE
}
