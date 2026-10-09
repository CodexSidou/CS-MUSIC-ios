package com.rst.player

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rst.player.ui.RstApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestMaxRefreshRate()
        setContent {
            RstApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply after the window is fully drawn — some OEMs ignore
        // the request if it comes before the first layout pass.
        requestMaxRefreshRate()
    }

    private fun requestMaxRefreshRate() {
        try {
            val display = if (Build.VERSION.SDK_INT >= 30) display else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return

            if (Build.VERSION.SDK_INT >= 23) {
                val modes = display.supportedModes
                val maxMode = modes.maxByOrNull { it.refreshRate } ?: return
                val maxHz = maxMode.refreshRate

                // Method 1: preferredDisplayModeId (API 30+)
                if (Build.VERSION.SDK_INT >= 30) {
                    window.attributes.preferredDisplayModeId = maxMode.modeId
                } else {
                    @Suppress("DEPRECATION")
                    window.attributes.preferredRefreshRate = maxHz
                }

                window.setAttributes(window.attributes)
                Log.d("RST", "Requested ${maxHz}Hz — modes=${modes.map { "${it.refreshRate.toInt()}Hz(id=${it.modeId})" }}")
            }
        } catch (e: Exception) {
            Log.w("RST", "Failed to request high refresh rate", e)
        }
    }
}
