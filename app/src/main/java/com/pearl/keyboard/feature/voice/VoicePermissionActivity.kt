package com.pearl.keyboard.feature.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Invisible helper activity. An IME can't request a runtime permission itself, so the
 * keyboard launches this to ask for the microphone; the user then taps the mic again.
 */
class VoicePermissionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        Toast.makeText(
            this,
            if (granted) "Microphone enabled — tap the mic again" else "Microphone permission denied",
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    private companion object {
        const val REQUEST_CODE = 4711
    }
}
