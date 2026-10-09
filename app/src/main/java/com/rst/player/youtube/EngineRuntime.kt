package com.rst.player.youtube

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Self-contained yt-dlp runtime for Android, modeled on YTDLnis's
 * RuntimeManager. The youtubedl-android AAR is used only as a source of native
 * binaries (`libpython.so`, `libpython.zip.so`) and the bundled yt-dlp zipapp;
 * this class extracts and runs them itself with the exact environment YTDLnis
 * ships with (LD_LIBRARY_PATH + nativeLibraryDir, SSL_CERT_FILE, PYTHONHOME,
 * HOME, TMPDIR, PATH). The library's `init()`/`execute()` wrapper is bypassed
 * entirely: its plain unzip writes the python zip's symlink entries as tiny
 * text files, corrupting the runtime (libz.so.1 and friends become non-ELF
 * stubs and Python dies with "zlib not available").
 */
object EngineRuntime {
    private const val TAG = "EngineRuntime"

    const val BASE_DIR = "youtubedl-android"
    const val YTDLP_DIR_NAME = "yt-dlp"
    const val YTDLP_BIN = "yt-dlp"
    private const val PYTHON_PKG = "packages/python"
    private const val PYTHON_ZIP = "libpython.zip.so"
    private const val PYTHON_LIB = "libpython.so"

    /**
     * SHA-256 of the bundled `res/raw/ytdlp` that is verified to run on this
     * app's embedded Python (yt-dlp 2025.11.12). yt-dlp 2026.07.04 crashed the
     * embedded zlib with a SIGSEGV inside zipimport, so the bundle must never
     * be swapped for an unverified build. Any fresh extraction that doesn't
     * match this checksum fails fast with a clear message instead of
     * segfaulting later. Deliberate engine upgrades require changing this
     * constant together with an on-device runtime validation.
     */
    private const val KNOWN_GOOD_BUNDLED_SHA256 =
        "89A0D9058EA9018E380B7771898FF46E393A1986DCD13FEF331693C87CE1FCA4"

    class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class RunResult(val exit: Int, val out: String, val err: String, val timedOut: Boolean = false)

    // ---------- paths ----------

    private fun baseDir(context: Context): File = File(context.noBackupFilesDir, BASE_DIR)

    private fun pythonDir(context: Context): File = File(baseDir(context), PYTHON_PKG)

    fun engineFile(context: Context): File =
        File(File(baseDir(context), YTDLP_DIR_NAME), YTDLP_BIN)

    private fun pythonZip(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, PYTHON_ZIP)

    private fun pythonExe(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, PYTHON_LIB)

    /** QuickJS runtime bundled by the library AAR — yt-dlp's JS engine for YouTube. */
    private fun quickJsExe(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libqjs.so")

    /** On-device state summary, appended to engine errors for diagnosis. */
    fun state(context: Context): String {
        return try {
            val usrLib = File(pythonDir(context), "usr/lib")
            val support = File(usrLib, "libandroid-support.so")
            val core = File(usrLib, "libpython3.12.so.1.0")
            val zlib = File(usrLib, "libz.so.1")
            val engine = engineFile(context)
            "py=${if (pythonDir(context).exists()) "dir" else "MISSING"}, " +
                "support=${if (support.exists()) support.length().toString() else "MISSING"}, " +
                "py312=${if (core.exists()) core.length().toString() else "MISSING"}, " +
                "zlib=${if (zlib.isFile && zlib.length() > 1024 && isElfFile(zlib)) zlib.length().toString() else "STUB"}, " +
                "ytdlp=${if (engine.exists()) engine.length().toString() else "MISSING"}, " +
                "zip=${if (pythonZip(context).exists()) pythonZip(context).length().toString() else "MISSING"}, " +
                "qjs=${if (quickJsExe(context).exists()) quickJsExe(context).length().toString() else "MISSING"}"
        } catch (e: Exception) {
            "state: ${e.message}"
        }
    }

    // ---------- extraction ----------

    /** Extracts/repairs the python runtime and the yt-dlp zipapp. Null on success. */
    fun ensureExtracted(context: Context): String? {
        return try {
            val reason = extractPython(context)
            if (reason != null) return reason
            val engineReason = extractYtdlp(context)
            if (engineReason != null) return engineReason
            null
        } catch (e: Exception) {
            Log.e(TAG, "engine extraction failed", e)
            "engine extraction failed: ${e.message}"
        }
    }

    private fun extractPython(context: Context): String? {
        val zip = pythonZip(context)
        if (!zip.exists() || !zip.isFile) {
            return "python runtime not bundled (${PYTHON_ZIP} missing from the APK)"
        }
        val dir = pythonDir(context)
        val usrLib = File(dir, "usr/lib")
        val core = File(usrLib, "libpython3.12.so.1.0")
        val support = File(usrLib, "libandroid-support.so")
        if (usrLib.isDirectory && core.exists() && support.exists()) {
            repairSymlinks(dir)
            return null
        }
        Log.w(TAG, "python runtime incomplete (core=${core.exists()} support=${support.exists()}), re-extracting")
        dir.deleteRecursively()
        unzip(zip, dir)
        repairSymlinks(dir)
        if (!core.exists() || !support.exists()) {
            return "python runtime missing core libraries after extraction [${state(context)}]"
        }
        Log.i(TAG, "python runtime extracted to ${dir.absolutePath}")
        return null
    }

    private fun extractYtdlp(context: Context): String? {
        val target = engineFile(context)
        if (engineHealthy(target)) return null
        return try {
            target.parentFile?.mkdirs()
            val input = try {
                context.resources.openRawResource(com.rst.player.R.raw.ytdlp)
            } catch (e: android.content.res.Resources.NotFoundException) {
                // The shrinker may rename resource paths; look the name up.
                val id = context.resources.getIdentifier("ytdlp", "raw", context.packageName)
                if (id == 0) throw e
                context.resources.openRawResource(id)
            }
            try {
                input.use { it.copyTo(target.outputStream()) }
            } finally {
                try { input.close() } catch (_: Exception) {}
            }
            if (!engineHealthy(target)) {
                target.delete()
                "extracted engine is not a valid yt-dlp binary"
            } else {
                val sha = sha256(target)
                if (sha != KNOWN_GOOD_BUNDLED_SHA256) {
                    target.delete()
                    "bundled engine is not the known-good build (yt-dlp 2025.11.12): " +
                        "sha256=$sha expected=$KNOWN_GOOD_BUNDLED_SHA256"
                } else {
                    Log.i(TAG, "extracted bundled yt-dlp (${target.length()} bytes, sha verified)")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp extraction failed", e)
            "engine extraction failed: ${e.message}"
        }
    }

    /** Removes the on-device yt-dlp tree so it is re-extracted from the bundle. */
    fun deleteEngine(context: Context) {
        try {
            val dir = File(baseDir(context), YTDLP_DIR_NAME)
            if (dir.exists()) dir.deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "engine delete failed", e)
        }
    }

    fun engineHealthy(file: File): Boolean {
        if (!file.exists() || file.length() < 1_000_000) return false
        return try {
            java.io.DataInputStream(file.inputStream().buffered()).use {
                it.readByte() == '#'.code.toByte() && it.readByte() == '!'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Plain unzip; symlink entries are fixed up afterwards. */
    private fun unzip(zip: File, target: File) {
        java.util.zip.ZipFile(zip).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val out = File(target, e.name)
                out.parentFile?.mkdirs()
                zf.getInputStream(e).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    /**
     * The python runtime zip stores symlinks as regular entries whose content
     * is the target file name (e.g. `libz.so.1` contains `libz.so.1.3.1`). A
     * plain unzip writes those as tiny text files, so the dynamic loader
     * rejects them ("too small to be an ELF executable"). This replaces each
     * such stub with a real symlink (falling back to a byte copy so the ELF
     * actually loads). Walks the WHOLE extracted tree — stubs can appear in any
     * package subdirectory, not only usr/lib. Idempotent.
     */
    private fun repairSymlinks(root: File) {
        if (!root.isDirectory) return
        var repaired = 0
        root.walkBottomUp().filter { it.isFile }.forEach { f ->
            if (f.length() > 128L) return@forEach
            if (!Regex("""\.so(\.[0-9]+)*$""").containsMatchIn(f.name)) return@forEach
            val targetName = try { f.readText().trim() } catch (_: Exception) { return@forEach }
            if (targetName.isEmpty() || targetName == f.name) return@forEach
            if (!Regex("""[A-Za-z0-9._\-]+""").matches(targetName)) return@forEach
            val target = File(f.parentFile, targetName)
            if (!target.isFile || isElfFile(f)) return@forEach
            f.delete()
            try {
                android.system.Os.symlink(targetName, f.absolutePath)
                repaired++
            } catch (_: Exception) {
                try {
                    target.copyTo(f, overwrite = true)
                    repaired++
                } catch (e2: Exception) {
                    Log.w(TAG, "symlink repair failed for ${f.name}", e2)
                }
            }
        }
        if (repaired > 0) Log.i(TAG, "repaired $repaired python symlinks under ${root.absolutePath}")
    }

    private fun isElfFile(f: File): Boolean {
        if (!f.isFile || f.length() < 4) return false
        return try {
            f.inputStream().buffered().use { input ->
                val head = ByteArray(4)
                if (input.read(head) != 4) return false
                head[0] == 0x7f.toByte() && head[1] == 'E'.code.toByte() &&
                    head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    // ---------- execution ----------

    /**
     * Spawns `libpython.so [<engine>] <options>` with the same environment
     * YTDLnis uses. `includeEngine=false` runs the interpreter directly (used
     * for the `-V` probe that isolates a broken runtime from a broken zipapp).
     * Exit codes: -1 = killed after the watchdog, -2 = failed to spawn,
     * otherwise the real process exit code (139 = SIGSEGV, 134 = SIGABRT, …).
     */
    fun execute(
        context: Context,
        options: List<String>,
        includeEngine: Boolean = true,
        redirectErrorStream: Boolean = false,
        timeoutSeconds: Long? = null,
        progress: ((Float, String) -> Unit)? = null
    ): RunResult {
        val extractError = ensureExtracted(context)
        if (extractError != null) throw EngineException(extractError)
        return try {
            val cmd = ArrayList<String>()
            cmd.add(pythonExe(context).absolutePath)
            if (includeEngine) {
                cmd.add(engineFile(context).absolutePath)
                cmd.add("--no-cache-dir")
            }
            cmd.addAll(options)
            // Modern yt-dlp needs a JavaScript runtime to solve YouTube's nsig
            // challenges. The library AAR bundles QuickJS next to the python
            // launcher; hand it to yt-dlp exactly like YoutubeDLRequest does.
            // Pure probes (--version / -V) skip it so an updated zipapp that
            // dropped the flag can still be validated.
            val isProbe = options.any { it == "--version" || it == "-V" }
            val qjs = quickJsExe(context)
            if (includeEngine && !isProbe && qjs.exists() &&
                !options.contains("--js-runtimes")
            ) {
                cmd.add("--js-runtimes")
                cmd.add("quickjs:" + qjs.absolutePath)
            }
            if (includeEngine && progress != null) cmd.add("--newline")

            val pb = ProcessBuilder(cmd)
            val env = pb.environment()
            buildEnvironment(context).forEach { (k, v) -> env[k] = v }
            env["PYTHONFAULTHANDLER"] = "1"
            if (redirectErrorStream) pb.redirectErrorStream(true)
            val proc = pb.start()

            val outBuf = StringBuilder()
            val errBuf = StringBuilder()
            val outThread = Thread {
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            outBuf.append(line).append('\n')
                            if (progress != null) {
                                parseProgress(line)?.let { progress(it, line) }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            val errThread = Thread {
                if (!redirectErrorStream) {
                    try {
                        proc.errorStream.bufferedReader().use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                errBuf.append(line).append('\n')
                                if (progress != null) {
                                    parseProgress(line)?.let { progress(it, line) }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            outThread.start()
            errThread.start()

            val finished = if (timeoutSeconds != null) {
                proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            } else {
                proc.waitFor()
                true
            }
            if (!finished) {
                proc.destroyForcibly()
                outThread.join(2000)
                errThread.join(2000)
                return RunResult(-1, outBuf.toString(), errBuf.toString(), timedOut = true)
            }
            outThread.join(2000)
            errThread.join(2000)
            RunResult(proc.exitValue(), outBuf.toString(), errBuf.toString())
        } catch (e: Exception) {
            Log.w(TAG, "engine process failed", e)
            RunResult(-2, "", "engine process error: ${e.message}")
        }
    }

    fun version(context: Context): String? {
        val result = execute(context, listOf("--version"), timeoutSeconds = 45)
        if (result.exit != 0) return null
        return result.out.trim().lineSequence().firstOrNull { it.isNotBlank() }
    }

    /** `[download]  42.3% of …` -> 42.3 (0-100 scale, matching the old callback). */
    private fun parseProgress(line: String): Float? {
        val match = Regex("""\[download\]\s+(\d+(?:\.\d+)?)%""").find(line) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    private fun buildEnvironment(context: Context): Map<String, String> {
        val pydir = pythonDir(context)
        val binDir = context.applicationInfo.nativeLibraryDir
        val ldPaths = listOf(
            File(pydir, "usr/lib"),
            File(baseDir(context), "packages/ffmpeg/usr/lib"),
            File(baseDir(context), "packages/aria2c/usr/lib"),
            File(binDir)
        ).distinct().joinToString(":") { it.absolutePath }
        return mapOf(
            "LD_LIBRARY_PATH" to ldPaths,
            "SSL_CERT_FILE" to File(pydir, "usr/etc/tls/cert.pem").absolutePath,
            "PATH" to ((System.getenv("PATH") ?: "") + ":" + binDir),
            "PYTHONHOME" to File(pydir, "usr").absolutePath,
            "HOME" to File(pydir, "usr").absolutePath,
            "TMPDIR" to context.cacheDir.absolutePath
        )
    }

    // ---------- manual update ----------

    /** SHA-256 of a file, hex lowercase. */
    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Runs a yt-dlp zipapp with the embedded python and returns its reported version, or null. */
    private fun probeVersion(context: Context, zipapp: File, timeoutSeconds: Long): String? {
        val probe = execute(
            context,
            options = listOf(zipapp.absolutePath, "--version"),
            includeEngine = false,
            timeoutSeconds = timeoutSeconds
        )
        if (probe.exit != 0) return null
        val line = probe.out.trim().lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        return line.takeIf { Regex("""\d{4}\.\d{2}\.\d{2}""").containsMatchIn(it) }
    }

    /** Short reason why a zipapp failed to run (exit code + last stderr/stdout lines). */
    private fun probeFailureDetail(context: Context, zipapp: File, timeoutSeconds: Long): String {
        val probe = execute(
            context,
            options = listOf(zipapp.absolutePath, "--version"),
            includeEngine = false,
            timeoutSeconds = timeoutSeconds
        )
        val tail = (probe.err.trim().lineSequence().toList().takeLast(6) +
            probe.out.trim().lineSequence().toList().takeLast(6))
            .filter { it.isNotBlank() }
            .joinToString(" | ")
        return if (tail.isBlank()) "exit=${probe.exit}, no output" else "exit=${probe.exit}: $tail"
    }

    /**
     * Downloads the latest yt-dlp zipapp from GitHub and installs it only if it
     * actually runs on this device's embedded Python (validated BEFORE the
     * swap, so a broken zipapp like yt-dlp 2026.07.04 — which segfaults inside
     * zipimport — is rejected and never becomes active). The previous engine is
     * kept as a backup and restored if the swap fails. Throws on failure.
     */
    fun updateYtdlp(context: Context): String? {
        val temp = File(context.cacheDir, "ytdlp-update-${System.currentTimeMillis()}")
        val target = engineFile(context)
        val backup = File(target.parentFile, "yt-dlp.bak")
        try {
            val conn = URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp")
                .openConnection() as HttpURLConnection
            conn.setConnectTimeout(15_000)
            conn.setReadTimeout(30_000)
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode >= 400) {
                throw EngineException("update server returned HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            if (!engineHealthy(temp)) {
                temp.delete()
                throw EngineException("downloaded file is not a valid yt-dlp zipapp")
            }

            // Real runtime validation: run the downloaded zipapp with the exact
            // python that will execute it. This catches crashes (segfault in
            // zipimport/zlib, missing modules, incompatible python) that a
            // structural check can't.
            val version = probeVersion(context, temp, 60)
            if (version == null) {
                val detail = probeFailureDetail(context, temp, 60)
                temp.delete()
                throw EngineException("downloaded yt-dlp does not run on this device — $detail")
            }

            // Swap, keeping a backup of the current engine for rollback.
            target.parentFile?.mkdirs()
            if (target.exists()) {
                backup.delete()
                if (!target.renameTo(backup)) throw EngineException("could not back up current engine")
            }
            if (!temp.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw EngineException("could not replace engine file")
            }

            // Re-verify the active file after the swap; roll back if anything broke.
            if (probeVersion(context, target, 60) == null) {
                target.delete()
                if (backup.exists()) backup.renameTo(target)
                throw EngineException("updated engine failed after swap — previous engine restored")
            }
            backup.delete()
            Log.i(TAG, "yt-dlp updated to $version (${target.length()} bytes)")
            return null
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }
}
