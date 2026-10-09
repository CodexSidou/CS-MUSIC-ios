package com.rst.player.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Display
import android.view.WindowManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

/**
 * Monitors frame timing, display Hz, memory pressure, and detects
 * sustained low-FPS or Hz-mismatch conditions.  Logs diagnostics
 * to perf.log so the crash report card can surface them.
 */
object PerformanceMonitor {

    data class FrameStats(
        val avgFps: Float,
        val displayHz: Int,
        val jankThresholdMs: Float,
        val jankPercent: Float,
        val droppedFrames: Int,
        val totalFrames: Int,
        val isHealthy: Boolean,
        val isHzMismatch: Boolean
    )

    private var running = false
    private var choreographer: Choreographer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val frameTimes = CopyOnWriteArrayList<Long>()
    private var lastFrameTimeNanos = 0L
    private var windowStartNanos = 0L
    private var windowFrameCount = 0
    private var droppedInWindow = 0
    private var windowIndex = 0

    private const val WINDOW_MS = 3_000L

    private var displayHz = 60
    private var lastStats: FrameStats? = null

    private val callbacks = CopyOnWriteArrayList<(FrameStats) -> Unit>()

    fun start(context: Context) {
        if (running) return
        running = true
        displayHz = detectRefreshRate(context)
        lastFrameTimeNanos = System.nanoTime()
        windowStartNanos = lastFrameTimeNanos
        windowIndex = 0

        choreographer = Choreographer.getInstance()
        choreographer?.postFrameCallback(frameCallback)
        handler.post(windowCheck)
        logEvent("Monitor started — display=${displayHz}Hz jankThreshold=${(1000f / displayHz * 1.5f).roundToInt()}ms")
    }

    fun stop() {
        running = false
        choreographer?.removeFrameCallback(frameCallback)
        choreographer = null
        handler.removeCallbacks(windowCheck)
        logEvent("Monitor stopped")
    }

    fun onFrame(callback: (FrameStats) -> Unit): () -> Unit {
        callbacks.add(callback)
        return { callbacks.remove(callback) }
    }

    fun getLastStats(): FrameStats = lastStats ?: snapshot()

    // ---------- internals ----------

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastFrameTimeNanos > 0) {
                val delta = frameTimeNanos - lastFrameTimeNanos
                frameTimes.add(delta)
                windowFrameCount++
                val jankThresholdNs = (1_000_000_000L / displayHz) * 3 / 2 // 1.5x frame budget
                if (delta > jankThresholdNs) droppedInWindow++
            }
            lastFrameTimeNanos = frameTimeNanos
            choreographer?.postFrameCallback(this)
        }
    }

    private val windowCheck = object : Runnable {
        override fun run() {
            if (!running) return
            val stats = snapshot()
            lastStats = stats
            for (cb in callbacks) cb(stats)

            // Skip logging the first 2 windows (app startup is always janky)
            if (windowIndex >= 2 && !stats.isHealthy) {
                val reason = buildString {
                    if (stats.isHzMismatch) append("Hz_MISMATCH ")
                    if (stats.avgFps < stats.displayHz * 0.75f) append("LOW_FPS ")
                    if (stats.jankPercent > 5f) append("JANKY ")
                }.trim()
                val msg = "PERF WARNING [$reason]: avgFps=${stats.avgFps.roundToInt()} " +
                    "display=${stats.displayHz}Hz jank=${stats.jankPercent.roundToInt()}% " +
                    "dropped=${stats.droppedFrames}/${stats.totalFrames}"
                logEvent(msg)
            }
            windowIndex++
            windowFrameCount = 0
            droppedInWindow = 0
            windowStartNanos = System.nanoTime()
            handler.postDelayed(this, WINDOW_MS)
        }
    }

    private fun snapshot(): FrameStats {
        // Collect recent frame deltas (last 180 entries = ~9s at 60fps, ~18s at 120fps)
        val recent = if (frameTimes.size > 180) {
            frameTimes.toList().takeLast(180)
        } else {
            frameTimes.toList()
        }
        if (recent.isEmpty()) {
            return FrameStats(0f, displayHz, 1000f / displayHz * 1.5f, 0f, 0, 0, true, false)
        }

        val avgDeltaNs = recent.average()
        val avgFps = if (avgDeltaNs > 0) (1_000_000_000.0 / avgDeltaNs).roundToInt().toFloat() else 0f

        // Auto-correct Hz: if sustained FPS > 80 and we thought it was 60Hz,
        // the panel is actually 120Hz (very common on modern Android).
        if (avgFps > 80f && displayHz <= 60 && recent.size >= 30) {
            displayHz = 120
            logEvent("Hz auto-corrected: measured ${avgFps.roundToInt()}fps → display is 120Hz")
        }

        // Jank threshold: 1.5x the ideal frame duration for this display
        val jankThresholdNs = (1_000_000_000L / displayHz) * 3 / 2
        val jankThresholdMs = jankThresholdNs / 1_000_000f

        val jankCount = recent.count { it > jankThresholdNs }
        val jankPct = if (recent.isNotEmpty()) (jankCount.toFloat() / recent.size * 100f) else 0f

        // Hz mismatch detection
        val isHzMismatch = displayHz <= 60 && avgFps > 70f

        // Healthy: fps within 80% of effective Hz AND jank under 5%
        val fpsOk = avgFps >= displayHz * 0.80f
        val jankOk = jankPct < 5f
        val healthy = fpsOk && jankOk

        return FrameStats(
            avgFps = avgFps,
            displayHz = displayHz,
            jankThresholdMs = jankThresholdMs,
            jankPercent = jankPct,
            droppedFrames = droppedInWindow,
            totalFrames = recent.size,
            isHealthy = healthy,
            isHzMismatch = isHzMismatch
        )
    }

    /**
     * Detects the real display refresh rate.  Uses getSupportedModes()
     * on API 23+ because display.mode.refreshRate often lies — it
     * returns the current mode (60 Hz) while the panel supports 120 Hz.
     * The snapshot() method will auto-correct if sustained FPS proves us wrong.
     */
    private fun detectRefreshRate(context: Context): Int {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = if (Build.VERSION.SDK_INT >= 30) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay
            } ?: return 60

            // Method 1: get supported modes, pick the highest refresh rate
            if (Build.VERSION.SDK_INT >= 23) {
                val modes = display.supportedModes
                val maxHz = modes.maxOfOrNull { it.refreshRate.roundToInt() } ?: 0
                if (maxHz > 0) return maxHz
            }

            // Method 2: current mode
            display.mode.refreshRate.roundToInt()
        } catch (_: Exception) { 60 }
    }

    private fun logEvent(message: String) {
        try {
            val ctx = com.rst.player.RstApplication.instance
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val logFile = File(ctx.filesDir, "perf.log")
            if (logFile.exists() && logFile.length() > 40_000) {
                logFile.writeText(logFile.readText().takeLast(25_000))
            }
            logFile.appendText("[$ts] $message\n")
        } catch (_: Exception) {}
    }

    fun readLog(context: Context): String? = try {
        val f = File(context.filesDir, "perf.log")
        if (f.exists()) f.readText().trim().ifBlank { null } else null
    } catch (_: Exception) { null }

    fun clearLog(context: Context) = try {
        File(context.filesDir, "perf.log").delete()
    } catch (_: Exception) {}
}
