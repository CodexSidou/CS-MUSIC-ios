package com.rst.player

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RstApplication : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashLogger()
        com.rst.player.diagnostics.PerformanceMonitor.start(this)
        initCoil()
        try {
            graph = AppGraph(this)
        } catch (e: Exception) {
            Log.e(TAG, "AppGraph init failed", e)
            throw e
        }
        runCatching { graph.playerController.connect() }
            .onFailure { Log.e(TAG, "player connect failed", it) }
        runCatching { graph.musicRepository.scan() }
            .onFailure { Log.e(TAG, "scan failed", it) }
        runCatching { graph.youTubeRepository.init() }
            .onFailure { Log.e(TAG, "yt init failed", it) }
    }

    private fun initCoil() {
        val memCacheBytes = 32L * 1024 * 1024
        val diskCacheBytes = 50L * 1024 * 1024
        val loader = coil.ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizeBytes(memCacheBytes.toInt())
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_album_art"))
                    .maxSizeBytes(diskCacheBytes)
                    .build()
            }
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(true)
            .build()
        coil.Coil.setImageLoader(loader)
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        // --- 1. Catch every unhandled exception on ANY thread ---
        val defaultHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(thread, throwable)
            } catch (_: Exception) {}
            previous?.uncaughtException(thread, throwable)
                ?: runCatching { Process.killProcess(Process.myPid()) }
        }
        Thread.setDefaultUncaughtExceptionHandler(defaultHandler)

        // --- 2. Watchdog: detect main-thread stalls (ANR-like) ---
        val mainHandler = Handler(Looper.getMainLooper())
        var watchdogRunning = true
        val watchdog = Runnable {
            // If we can schedule this, the main thread is NOT blocked.
            // The watchdog reschedules itself; if it stops being scheduled,
            // the next crash log will show a main-thread stall.
        }
        val watchdogCheck = object : Runnable {
            override fun run() {
                if (!watchdogRunning) return
                // Poke the main thread — if it's stuck, this never runs
                mainHandler.post(watchdog)
                // Check again in 8 seconds (less than the 10 s ANR threshold)
                mainHandler.postDelayed(this, 8_000)
            }
        }
        mainHandler.postDelayed(watchdogCheck, 8_000)

        // --- 3. Log low-memory events ---
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
            override fun onLowMemory() {
                appendLog("perf.log", "=== LOW MEMORY EVENT ===\n${deviceSnapshot()}\n")
            }
            override fun onTrimMemory(level: Int) {
                if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
                    appendLog("perf.log", "=== MEMORY PRESSURE (level=$level) ===\n${deviceSnapshot()}\n")
                }
            }
        })

        // --- 4. Hook all uncaught coroutine exceptions via default handler ---
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(thread, throwable)
            } catch (_: Exception) {}
            previous?.uncaughtException(thread, throwable)
                ?: runCatching { Process.killProcess(Process.myPid()) }
        }
    }

    /** Builds a full diagnostic snapshot for a crash. */
    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        // Classify the crash
        val kind = classifyCrash(throwable)

        val report = buildString {
            append("=== CRASH REPORT ===\n")
            append("Timestamp : $ts\n")
            append("Thread    : ${thread.name} (id=${thread.id})\n")
            append("Kind      : $kind\n")
            append("Exception : ${throwable.javaClass.name}\n")
            append("Message   : ${throwable.message ?: "(none)"}\n")
            append("\n--- Device ---\n")
            append(deviceSnapshot())
            append("\n--- Stack ---\n")
            append(sw.toString())
            append("\n")
            // If caused by another exception, include that too
            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 4) {
                val csw = StringWriter()
                cause.printStackTrace(PrintWriter(csw))
                append("--- Caused by ---\n")
                append(csw.toString())
                append("\n")
                cause = cause.cause
                depth++
            }
        }
        appendLog("crash.log", report)
    }

    private fun classifyCrash(t: Throwable): String {
        val name = t.javaClass.name
        return when {
            name.contains("OutOfMemoryError", true)         -> "OOM"
            name.contains("StackOverflow", true)             -> "STACK_OVERFLOW"
            name.contains("SecurityException", true)         -> "SECURITY"
            name.contains("SecurityException", true)         -> "PERMISSION"
            name.contains("NullPointerException", true)      -> "NPE"
            name.contains("IllegalStateException", true)     -> "ILLEGAL_STATE"
            name.contains("ClassCastException", true)        -> "CLASS_CAST"
            name.contains("IndexOutOfBoundsException", true) -> "INDEX_OOB"
            name.contains("CancellationException", true)     -> "COROUTINE_CANCEL"
            name.contains("ConcurrentModification", true)    -> "CONCURRENT_MOD"
            name.contains("SQLite", true)                    -> "DATABASE"
            name.contains("TransactionTooLarge", true)       -> "IPC_TOO_LARGE"
            name.contains("DeadObjectException", true)       -> "DEAD_OBJECT"
            name.contains("RemoteException", true)           -> "REMOTE"
            name.contains("NetworkOnMainThread", true)       -> "NETWORK_ON_MAIN"
            name.contains("FileNotFound", true)              -> "FILE_NOT_FOUND"
            name.contains("SecurityException", true)         -> "SECURITY"
            t is Error                                      -> "SYSTEM_ERROR"
            else                                            -> "UNCAUGHT"
        }
    }

    private fun deviceSnapshot(): String = buildString {
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        appendLine("Device    : ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android   : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("CPU ABI   : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}")
        appendLine("Cores     : ${Runtime.getRuntime().availableProcessors()}")
        appendLine("Total RAM : ${memInfo.totalMem / (1024 * 1024)} MB")
        appendLine("Free RAM  : ${memInfo.availMem / (1024 * 1024)} MB")
        appendLine("Low RAM   : ${memInfo.lowMemory}")
        val maxHeap = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val usedHeap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        appendLine("Heap      : $usedHeap / $maxHeap MB")
    }

    private fun appendLog(filename: String, content: String) {
        try {
            val logFile = File(filesDir, filename)
            // Keep last 50 KB of log to prevent unbounded growth
            if (logFile.exists() && logFile.length() > 50_000) {
                val tail = logFile.readText().takeLast(30_000)
                logFile.writeText(tail)
            }
            logFile.appendText(content + "\n")
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "RstApplication"
        lateinit var instance: RstApplication
            private set

        fun graphOf(context: Context): AppGraph =
            (context.applicationContext as RstApplication).graph

        fun deviceSnapshot(): String = instance.deviceSnapshotInternal()

        private fun RstApplication.deviceSnapshotInternal(): String {
            val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            return buildString {
                val maxHeap = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                val usedHeap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
                append("${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} | ")
                append("${Runtime.getRuntime().availableProcessors()} cores | ")
                append("${memInfo.totalMem / (1024 * 1024)} MB RAM | ")
                append("Heap $usedHeap/$maxHeap MB")
            }
        }
    }
}
