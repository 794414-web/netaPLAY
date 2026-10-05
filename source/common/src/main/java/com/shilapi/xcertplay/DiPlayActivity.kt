package com.shilapi.xcertplay

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * Neta HZEP32 launcher stub. The BYD/DiAuto-branded 2313-line activity was
 * removed during de-BYD work. Tapping the launcher icon forwards to
 * CarPlayHostActivity, which is the real CarPlay receiver. USB_DEVICE_ATTACHED
 * broadcasts also launch CarPlayHostActivity directly (see AndroidManifest).
 */
class DiPlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("DiPlayActivity", "Neta CarPlay launcher. Forwarding to CarPlayHostActivity.")
        val next = Intent(this, CarPlayHostActivity::class.java)
        // Carry the requested page (e.g. "settings") so external entries land in it.
        intent.getStringExtra("page")?.let { next.putExtra("page", it) }
        startActivity(next)
        finish()
    }
}
