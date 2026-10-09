package com.rst.player.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class OnlineHit(
    val id: String,
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val url: String
)

/**
 * Native Windows yt-dlp integration. The engine binary is auto-installed into
 * %APPDATA%/RSTPlayer/bin from the official GitHub release on first use, so
 * online search and downloads work with zero manual setup.
 */
class YtdlpPc {

    val status = MutableStateFlow("yt-dlp ready")

    private val binDir: File =
        File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "RSTPlayer/bin")

    val engineFile: File get() = File(binDir, "yt-dlp.exe")

    /** Music/RST in the user profile — mirrors the Android app's public folder. */
    val downloadDir: File = File(System.getProperty("user.home"), "Music/RST")

    suspend fun ensureEngine(): File? = withContext(Dispatchers.IO) {
        if (engineFile.isFile && engineFile.length() > 1_000_000) return@withContext engineFile
        binDir.mkdirs()
        status.value = "Downloading yt-dlp…"
        try {
            val conn = URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 RSTPlayer/2.0")
            if (conn.responseCode >= 400) throw Exception("HTTP ${conn.responseCode}")
            val tmp = File(binDir, "yt-dlp.exe.part")
            conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(engineFile)
            status.value = "yt-dlp installed"
            engineFile
        } catch (e: Exception) {
            status.value = "yt-dlp download failed: ${e.message}"
            null
        }
    }

    /** Runs yt-dlp with args, returning (exitCode, stdout). */
    private suspend fun run(vararg args: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val exe = engineFile
        if (!exe.isFile) return@withContext -2 to ""
        try {
            val proc = ProcessBuilder(exe.absolutePath, *args)
                .redirectErrorStream(true)
                .start()
            val out = StringBuilder()
            val reader = proc.inputStream.bufferedReader()
            val buf = CharArray(8192)
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                out.append(buf, 0, n)
            }
            proc.waitFor()
            proc.exitValue() to out.toString()
        } catch (e: Exception) {
            -2 to e.message.orEmpty()
        }
    }

    suspend fun search(query: String, count: Int = 10): List<OnlineHit> {
        if (ensureEngine() == null) return emptyList()
        status.value = "Searching…"
        val (code, out) = run(
            "ytsearch$count:${query.trim()}",
            "--flat-playlist", "--dump-single-json", "--no-warnings", "--quiet"
        )
        if (code != 0) {
            status.value = "Search failed (exit $code)"
            return emptyList()
        }
        val hits = ArrayList<OnlineHit>()
        try {
            val root = JSONObject(out.trim())
            val entries = root.optJSONArray("entries") ?: return emptyList()
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val id = e.optString("id")
                if (id.isBlank()) continue
                hits.add(
                    OnlineHit(
                        id = id,
                        title = e.optString("title", "Unknown"),
                        uploader = e.optString("uploader", e.optString("channel", "")),
                        durationSec = e.optLong("duration", 0L),
                        url = "https://www.youtube.com/watch?v=$id"
                    )
                )
            }
        } catch (_: Exception) {
        }
        status.value = if (hits.isEmpty()) "No results" else "yt-dlp ready"
        return hits
    }

    /**
     * Downloads best m4a audio straight into Music/RST. Returns the produced
     * file, or null. Progress is reported through [onProgress] (0..100).
     */
    suspend fun download(
        hit: OnlineHit,
        onProgress: (Int, String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val exe = ensureEngine() ?: return@withContext null
        downloadDir.mkdirs()
        onProgress(0, "Starting…")
        val before = downloadDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        try {
            val proc = ProcessBuilder(
                exe.absolutePath,
                hit.url,
                "-f", "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio/best",
                "--no-playlist",
                "--no-warnings",
                "--newline",
                "-o", downloadDir.absolutePath + "/%(title)s.%(ext)s"
            ).redirectErrorStream(true).start()

            val reader = proc.inputStream.bufferedReader()
            var line = reader.readLine()
            while (line != null) {
                Regex("""\[download]\s+(\d+(?:\.\d+)?)%""").find(line)?.let { m ->
                    m.groupValues[1].toIntOrNull()?.let { onProgress(it.coerceIn(0, 100), "Downloading") }
                }
                line = reader.readLine()
            }
            proc.waitFor()

            val file = downloadDir.listFiles()
                ?.filter { it.name !in before && it.extension.lowercase() in LibraryScanner.PLAYABLE_EXTENSIONS }
                ?.maxByOrNull { it.lastModified() }
            if (proc.exitValue() == 0 && file != null) {
                onProgress(100, "Done")
                status.value = "Downloaded \"${file.nameWithoutExtension}\""
                file
            } else {
                onProgress(0, "Failed")
                status.value = "Download failed (exit ${proc.exitValue()})"
                null
            }
        } catch (e: Exception) {
            status.value = "Download failed: ${e.message}"
            null
        }
    }
}
