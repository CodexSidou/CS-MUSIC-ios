package com.rst.player.youtube

import android.content.Context
import android.os.Environment
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YouTubeRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _results = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val results: StateFlow<List<YouTubeVideo>> = _results.asStateFlow()

    private val _albumResults = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val albumResults: StateFlow<List<YouTubeVideo>> = _albumResults.asStateFlow()

    private val _artists = MutableStateFlow<List<YouTubeChannel>>(emptyList())
    val artists: StateFlow<List<YouTubeChannel>> = _artists.asStateFlow()

    private val _engineVersion = MutableStateFlow<String?>(null)
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    private val _engineInitError = MutableStateFlow<String?>(null)
    val engineInitError: StateFlow<String?> = _engineInitError.asStateFlow()

    /** Serializes engine setup so concurrent downloads can't race the first init. */
    private val engineLock = Mutex()

    /** True once the engine has been verified to actually run in this process. */
    @Volatile
    private var engineVerified = false

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    val downloadTracker = DownloadTracker()

    /** Hides the download card a few seconds after a download finishes or errors. */
    private fun scheduleActiveReset(tracker: DownloadTracker) {
        scope.launch {
            kotlinx.coroutines.delay(5000)
            if (batchDownloads > 0) return@launch
            val s = tracker.active.value?.state.orEmpty()
            if (isTerminal(s)) {
                tracker.resetActive()
            }
        }
    }

    /** Number of batch installs currently in flight (keeps the card up until they finish). */
    @Volatile
    private var batchDownloads = 0

    private fun isTerminal(state: String): Boolean =
        state.endsWith("Done") || state.endsWith("Already in library") || state.endsWith("Error")

    @Volatile
    private var initStarted = false

    fun init() {
        if (initStarted) return
        initStarted = true
        // Start the engine as early as possible (app launch). Downloads/search
        // that need it later just wait for or re-run ensureEngine(), which is
        // cheap once the engine is verified.
        scope.launch {
            ensureEngine()
            Log.i(TAG, "yt-dlp init finished: verified=$engineVerified version=${_engineVersion.value}")
        }
    }

    /**
     * Makes sure the on-device engine is extracted, initialized and actually
     * runs. Returns null when it works, or a human-readable failure reason.
     *
     * The engine lives only at the fixed path the library runs; it is always
     * re-installable from the immutable APK resource, so any corruption or stale
     * copy (failed updates, OS cleanup, app upgrades) is repaired automatically.
     * A stale on-disk binary survives APK upgrades (noBackupFilesDir is kept),
     * so a clean re-extraction is forced once per app version, and any engine
     * that fails its runtime smoke test is deleted and re-extracted once.
     * After a failed first attempt, later calls fail fast with the recorded
     * reason instead of repeating the slow self-heal for every download.
     */
    private suspend fun ensureEngine(): String? = engineLock.withLock {
        if (engineVerified) return null
        if (engineTried && _engineInitError.value != null) return _engineInitError.value
        engineTried = true
        repeat(2) { attempt ->
            val reason = extractAndInit()
            if (reason == null) {
                val version = runEngineSmokeTest()
                if (version != null) {
                    _engineVersion.value = version
                    engineVerified = true
                    _ready.value = true
                    _engineInitError.value = null
                    return null
                }
                Log.w(TAG, "engine smoke test failed on attempt ${attempt + 1}")
            } else {
                _lastInitError = reason
                Log.w(TAG, "engine init failed on attempt ${attempt + 1}: $reason")
            }
            if (attempt == 0) {
                // First failure: clear the on-device engine and retry from the
                // bundled resource (repairs corrupt/stale/partial copies).
                Log.w(TAG, "forcing clean engine re-extraction")
                deleteEngine()
            }
        }
        _ready.value = false
        _engineVersion.value = readBundledVersion()
        _engineInitError.value = _lastInitError ?: "Download engine failed to initialize"
        _engineInitError.value!!
    }

    private var _lastInitError: String? = null
    private var engineTried = false

    /** Extracts the bundled engine (if needed) and verifies it runs. */
    private fun extractAndInit(): String? {
        _lastInitError = null
        return try {
            resetStaleEngine()
            // Self-contained extraction (see EngineRuntime): the library's own
            // init()/execute() wrapper is bypassed because its plain unzip
            // corrupts the python runtime's symlink entries.
            val reason = EngineRuntime.ensureExtracted(appContext)
            if (reason != null) {
                _lastInitError = reason
                return reason
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp init failed", e)
            val file = engineFile()
            val state = if (file.exists()) "present (${file.length()} bytes)" else "MISSING"
            "Download engine init failed ($state): ${e.cause?.message ?: e.message ?: "unknown error"}".also {
                _lastInitError = it
            }
        }
    }

    /**
     * Runs `yt-dlp --version` on the device to prove the engine actually
     * executes, not just that the file looks right. This is the last line of
     * defense: anything that can't run is replaced from the bundle.
     *
     * The engine is spawned directly through [EngineRuntime]
     * instead of through the library's `YoutubeDL.execute()`, because the
     * library's exception wrapping only surfaces stderr — a native crash
     * (exit != 0, empty stderr) is reported as an empty message. Running the
     * process ourselves captures stdout, stderr and the exact exit code, and
     * `PYTHONFAULTHANDLER` turns a silent segfault into a Python stack dump.
     */
    private suspend fun runEngineSmokeTest(): String? {
        var version: String? = null
        try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(45_000) {
                    val result = EngineRuntime.execute(
                        appContext,
                        options = listOf("--version"),
                        timeoutSeconds = 45
                    )
                    val out = result.out.trim()
                    val parsed = out.lineSequence().firstOrNull { it.isNotBlank() }
                    when {
                        result.exit == 0 && !parsed.isNullOrBlank() -> version = parsed
                        else -> {
                            // Isolate "python runtime broken" vs "yt-dlp zipapp
                            // broken": `-V` runs before any script is loaded.
                            val probe = EngineRuntime.execute(
                                appContext,
                                options = listOf("-V"),
                                includeEngine = false,
                                timeoutSeconds = 30
                            )
                            val detail = StringBuilder("exit=${result.exit}")
                            if (result.err.isNotBlank()) detail.append("\n--- stderr ---\n").append(result.err.trim())
                            if (out.isNotBlank()) detail.append("\n--- stdout ---\n").append(out)
                            detail.append("\n--- python -V: exit=${probe.exit} ---")
                            if (probe.out.isNotBlank()) detail.append("\n").append(probe.out.trim())
                            if (probe.err.isNotBlank()) detail.append("\n").append(probe.err.trim())
                            failSmoke(detail.toString())
                        }
                    }
                    Unit
                } ?: run {
                    if (_lastInitError == null) failSmoke("yt-dlp smoke test timed out after 45s")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "engine smoke test failed", e)
            failSmoke("yt-dlp smoke test failed: ${e.cause?.message ?: e.message}")
        }
        return version
    }

    /**
     * Records a smoke-test failure. The full text is persisted to a log file in
     * app-specific external storage (visible to file managers, no root needed)
     * and the reported error keeps the traceback tail — the final line of a
     * Python traceback is the actual exception, which is what diagnoses the
     * failure — instead of the first 200 characters.
     */
    private fun failSmoke(detail: String) {
        val full = detail.trim()
        persistSmokeLog(full)
        val tail = full.lineSequence().toList().takeLast(30).joinToString("\n")
        _lastInitError = "$tail [${runtimeState()}]"
    }

    /** <externalFilesDir>/engine_smoke.log — full stderr of the last failed smoke test. */
    private fun smokeLogFile(): File =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "engine_smoke.log")

    private fun persistSmokeLog(text: String) {
        try {
            smokeLogFile().writeText(text)
            Log.w(TAG, "engine smoke failure logged to ${smokeLogFile().absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "engine smoke log write failed", e)
        }
    }

    /** <noBackupFilesDir>/youtubedl-android/yt-dlp/yt-dlp — the file the engine runs. */
    private fun engineFile(): File = EngineRuntime.engineFile(appContext)

    /** True when the engine file is present and looks like a runnable yt-dlp zipapp. */
    private fun engineHealthy(file: File): Boolean = EngineRuntime.engineHealthy(file)

    /** Removes the whole on-device engine tree so it is re-extracted from the bundle. */
    private fun deleteEngine() = EngineRuntime.deleteEngine(appContext)

    /**
     * Deletes the on-device yt-dlp once per installed app version, so the
     * bundled (fresh) binary is re-extracted on upgrade instead of the stale
     * one surviving forever.
     */
    private fun resetStaleEngine() {
        try {
            val prefs = appContext.getSharedPreferences("rst_player", Context.MODE_PRIVATE)
            val version = if (android.os.Build.VERSION.SDK_INT >= 28) {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionCode.toLong()
            }
            if (prefs.getLong("engine_reset_version", -1L) != version) {
                deleteEngine()
                prefs.edit().putLong("engine_reset_version", version).apply()
                Log.i(TAG, "re-extracting bundled yt-dlp for app version $version")
            }
        } catch (e: Exception) {
            Log.w(TAG, "engine reset skipped", e)
        }
    }

    /** On-device state of the python runtime, appended to engine failures for diagnosis. */
    private fun runtimeState(): String = EngineRuntime.state(appContext)

    /** Waits until the yt-dlp engine is initialized. Returns an error message, or null when ready. */
    private suspend fun awaitReady(): String? {
        if (!initStarted) init()
        // ensureEngine self-heals (re-extracts from the bundle) and serializes
        // concurrent callers, so this returns fast once the engine is verified.
        return ensureEngine()
    }

    fun ensureEngineInit() {
        scope.launch {
            if (!initStarted) {
                init()
            } else if (_engineInitError.value != null) {
                // Opening Settings while the engine is broken = explicit retry.
                engineTried = false
                engineVerified = false
                _engineInitError.value = null
                ensureEngine()
            }
        }
    }

    /**
     * Manual engine update. Never leaves a broken engine behind: the updated
     * binary is verified with a real run, and on any failure the bundled
     * engine is restored and verified.
     */
    fun updateEngine() {
        scope.launch {
            _updating.value = true
            try {
                EngineRuntime.updateYtdlp(appContext)
                Log.i(TAG, "yt-dlp update completed")
                engineVerified = false
                engineTried = false
                _engineInitError.value = ensureEngine()?.let {
                    "Engine update failed — bundled engine restored ($it)"
                } ?: "Engine updated successfully (yt-dlp ${_engineVersion.value ?: ""})".trimEnd()
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp update failed", e)
                engineVerified = false
                engineTried = false
                _engineInitError.value = ensureEngine()?.let {
                    "Engine update failed (${e.message}) — $it"
                } ?: "Engine update failed (${e.message}) — bundled engine still working"
            } finally {
                _updating.value = false
            }
        }
    }

    /**
     * Runs a yt-dlp request through the self-contained engine. The engine is
     * guaranteed extracted and verified before the process starts.
     */
    private suspend fun engineExecute(
        req: YoutubeDLRequest,
        redirectErrorStream: Boolean = false,
        progress: ((Float, String) -> Unit)? = null
    ): EngineRuntime.RunResult {
        val engineError = awaitReady()
        if (engineError != null) throw EngineRuntime.EngineException(engineError)
        return withContext(Dispatchers.IO) {
            EngineRuntime.execute(
                appContext,
                options = req.buildCommand(),
                redirectErrorStream = redirectErrorStream,
                progress = progress
            )
        }
    }

    private fun readBundledVersion(): String? {
        val file = engineFile()
        if (!file.exists()) return null
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                val entry = zip.getEntry("yt_dlp/version.py") ?: return@use null
                val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                Regex("__version__\\s*=\\s*['\"]([^'\"]+)['\"]").find(text)?.groupValues?.get(1)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _searching.value = true
        _error.value = null
        try {
            val q = trimmed
            // iTunes/Apple's API first: it always works, has no bot-check and
            // returns real songs, artists and albums instantly. Piped is only a
            // fallback (its YouTube IDs let downloads run directly).
            val itunes = withTimeoutOrNull(ITUNES_TIMEOUT) { searchItunes(q) }
            if (itunes != null &&
                (itunes.songs.isNotEmpty() || itunes.channels.isNotEmpty() || itunes.albums.isNotEmpty())
            ) {
                _results.value = itunes.songs
                _albumResults.value = itunes.albums
                _artists.value = officialFirst(itunes.channels)
                return
            }

            val piped = withTimeoutOrNull(PIPED_TIMEOUT) { searchPiped(q) }
            if (piped != null &&
                (piped.songs.isNotEmpty() || piped.channels.isNotEmpty() || piped.albums.isNotEmpty())
            ) {
                _results.value = piped.songs
                _albumResults.value = piped.albums
                _artists.value = officialFirst(piped.channels)
                return
            }

            // The bundled engine's own search is the last fallback: it works
            // whenever the engine is healthy, even with iTunes and Piped both
            // unreachable, and its hits carry real YouTube IDs for downloads.
            val engineSongs = withTimeoutOrNull(SEARCH_TIMEOUT) {
                withContext(Dispatchers.IO) {
                    try {
                        val req = YoutubeDLRequest("ytsearch8:$q")
                        req.addOption("--dump-single-json")
                        req.addOption("--no-playlist")
                        req.addOption("--no-check-certificates")
                        req.addOption("--socket-timeout", "20")
                        parseSearch(engineExecute(req).out)
                    } catch (e: Exception) {
                        Log.w(TAG, "yt-dlp search fallback failed for \"$q\"", e)
                        emptyList()
                    }
                }
            }
            if (!engineSongs.isNullOrEmpty()) {
                _results.value = engineSongs
                _albumResults.value = emptyList()
                _artists.value = emptyList()
                return
            }

            _error.value = "Search found nothing for “$q”. Try a different query or check your connection."
        } catch (e: Exception) {
            Log.e(TAG, "search failed", e)
            _error.value = e.cause?.message ?: e.message ?: "Search failed"
        } finally {
            _searching.value = false
        }
    }

    /**
     * Puts verified (official) channels first so the app surfaces the real
     * artist page instead of random covers/fan channels, and drops duplicates.
     */
    private fun officialFirst(channels: List<YouTubeChannel>): List<YouTubeChannel> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<YouTubeChannel>()
        for (c in channels) {
            if (!seen.add(c.name.lowercase())) continue
            if (c.isVerified) result.add(0, c) else result.add(c)
        }
        return result
    }

    private data class SearchResults(
        val songs: List<YouTubeVideo>,
        val channels: List<YouTubeChannel>,
        val albums: List<YouTubeVideo>
    )

    private suspend fun searchItunes(query: String): SearchResults? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://itunes.apple.com/search?" +
                        "term=${URLEncoder.encode(query, "UTF-8")}" +
                        "&media=music&entity=song,album,musicArtist&limit=100"
                )
                val root = JSONObject(readJson(url))
                val arr = root.optJSONArray("results") ?: return@withContext null
                val songs = mutableListOf<YouTubeVideo>()
                val channels = mutableListOf<YouTubeChannel>()
                val albums = mutableListOf<YouTubeVideo>()
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    when (e.optString("wrapperType")) {
                        "track" -> {
                            val title = e.optString("trackName").takeIf { it.isNotBlank() } ?: continue
                            val artist = e.optString("artistName", "").takeIf { it.isNotBlank() } ?: "Unknown"
                            songs.add(
                                YouTubeVideo(
                                    id = "itunes-track-${e.optLong("trackId")}",
                                    title = title,
                                    uploader = artist,
                                    duration = e.optLong("trackTimeMillis", 0L) / 1000,
                                    thumbnail = bigArtwork(e.optString("artworkUrl100", "")),
                                    webpageUrl = "",
                                    viewCount = 0L
                                )
                            )
                            if (channels.none { it.name == artist }) {
                                channels.add(
                                    YouTubeChannel(
                                        name = artist,
                                        thumbnail = bigArtwork(e.optString("artworkUrl100", "")),
                                        channelUrl = "",
                                        subscriberCount = "",
                                        artistId = e.optLong("artistId", 0L).takeIf { it > 0 }
                                    )
                                )
                            }
                        }
                        "collection" -> {
                            if (e.optString("collectionType", "") == "Album") {
                                val name = e.optString("collectionName").takeIf { it.isNotBlank() } ?: continue
                                val artist = e.optString("artistName", "").takeIf { it.isNotBlank() } ?: "Unknown"
                                albums.add(
                                    YouTubeVideo(
                                        id = "itunes-album-${e.optLong("collectionId")}",
                                        title = name,
                                        uploader = artist,
                                        duration = 0L,
                                        thumbnail = bigArtwork(e.optString("artworkUrl100", "")),
                                        webpageUrl = "",
                                        viewCount = 0L
                                    )
                                )
                            }
                        }
                        "artist" -> {
                            val name = e.optString("artistName").takeIf { it.isNotBlank() } ?: continue
                            if (channels.none { it.name == name }) {
                                channels.add(
                                    YouTubeChannel(
                                        name = name,
                                        thumbnail = bigArtwork(e.optString("artworkUrl100", "")),
                                        channelUrl = "",
                                        subscriberCount = "",
                                        artistId = e.optLong("artistId", 0L).takeIf { it > 0 }
                                    )
                                )
                            }
                        }
                    }
                }
                if (songs.isEmpty() && albums.isEmpty() && channels.isEmpty()) null
                else SearchResults(songs, channels, albums)
            } catch (e: Exception) {
                Log.w(TAG, "itunes search failed", e)
                null
            }
        }
    }

    private fun bigArtwork(url: String): String = url.replace("100x100bb", "600x600bb")

    private suspend fun searchPiped(query: String): SearchResults? {
        return withContext(Dispatchers.IO) {
            for (base in PIPED_INSTANCES) {
                try {
                    val songs = fetchPipedVideos(base, query)
                    val channels = fetchPipedChannels(base, query)
                    val albums = fetchPipedVideos(base, "$query album")
                    if (songs.isNotEmpty() || channels.isNotEmpty() || albums.isNotEmpty()) {
                        return@withContext SearchResults(songs, channels, albums)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "piped instance $base failed", e)
                }
            }
            null
        }
    }

    private fun fetchPipedVideos(base: String, query: String): List<YouTubeVideo> {
        val url = URL("$base/search?q=${URLEncoder.encode(query, "UTF-8")}&filter=videos&region=US")
        val root = JSONObject(readJson(url))
        val arr = root.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("url", "")
                .substringAfter("v=")
                .substringBefore("&")
                .takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            YouTubeVideo(
                id = id,
                title = e.optString("title", "Unknown"),
                uploader = e.optString("uploaderName", ""),
                duration = e.optLong("duration", 0L),
                thumbnail = e.optString("thumbnail", ""),
                webpageUrl = "https://www.youtube.com/watch?v=$id",
                viewCount = e.optLong("views", 0L)
            )
        }
    }

    private fun fetchPipedChannels(base: String, query: String): List<YouTubeChannel> {
        val url = URL("$base/search?q=${URLEncoder.encode(query, "UTF-8")}&filter=channels&region=US")
        val root = JSONObject(readJson(url))
        val arr = root.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = e.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            YouTubeChannel(
                name = name,
                thumbnail = e.optString("thumbnail", ""),
                channelUrl = e.optString("url", ""),
                subscriberCount = e.optString("subscribers", ""),
                isVerified = e.optBoolean("verified", false)
            )
        }
    }

    /**
     * Fetches the trending music for a region. The Apple/iTunes RSS top-songs
     * chart is used first: it is the official, per-country music chart, has no
     * bot-check and returns real songs instantly. Piped's /trending is only a
     * fallback when the Apple feed is unreachable.
     */
    suspend fun trending(region: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        val r = region.takeIf { it.isNotBlank() } ?: "US"
        try {
            val chart = fetchItunesChart(r)
            if (chart.isNotEmpty()) return@withContext chart
        } catch (e: Exception) {
            Log.w(TAG, "itunes chart failed for $r", e)
        }
        for (base in PIPED_INSTANCES.take(3)) {
            try {
                val videos = fetchPipedTrending(base, r)
                if (videos.isNotEmpty()) return@withContext videos
            } catch (e: Exception) {
                Log.w(TAG, "piped trending $base failed", e)
            }
        }
        emptyList()
    }

    /**
     * Next page of chart songs for the Explore infinite scroll. The top chart is
     * capped at ~85 songs by Apple, so once it is exhausted the feed continues
     * through per-genre top charts. Returns an empty list when everything is
     * loaded (the UI then shows the "all caught up" footer).
     */
    suspend fun moreTrending(region: String, alreadyShown: Int): List<YouTubeVideo> =
        withContext(Dispatchers.IO) {
            val code = region.takeIf { it.length == 2 }?.lowercase() ?: "us"
            try {
                val top = fetchItunesChart(code, 100)
                if (alreadyShown < top.size) {
                    val start = alreadyShown.coerceAtLeast(0)
                    val end = minOf(start + 25, top.size)
                    return@withContext top.subList(start, end)
                }
                val genreIds = listOf(20, 17, 14, 11, 7, 21, 24, 25)
                val genreIndex = (alreadyShown - top.size) / 25
                if (genreIndex < genreIds.size) {
                    return@withContext fetchGenreChart(code, genreIds[genreIndex])
                }
                emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "more trending failed for $code", e)
                emptyList()
            }
        }

    /**
     * Fetches the top albums for a region from Apple's official per-country
     * RSS chart. The ids are the real iTunes collection ids ("itunes-album-…"),
     * so tapping a card opens the actual album page instead of a mislabeled video.
     */
    suspend fun topAlbums(region: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        val code = region.takeIf { it.length == 2 }?.lowercase() ?: "us"
        try {
            val url = URL("https://itunes.apple.com/$code/rss/topalbums/limit=25/json")
            val feed = JSONObject(readJson(url)).optJSONObject("feed") ?: return@withContext emptyList()
            val arr = feed.optJSONArray("entry") ?: return@withContext emptyList()
            return@withContext (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = e.optJSONObject("im:name")?.optString("label").orEmpty().takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val artist = e.optJSONObject("im:artist")?.optString("label").orEmpty()
                    .takeIf { it.isNotBlank() } ?: "Unknown"
                val collectionId = e.optJSONObject("id")?.optJSONObject("attributes")?.optLong("im:id", 0L) ?: 0L
                if (collectionId <= 0) return@mapNotNull null
                var thumb = ""
                val images = e.optJSONArray("im:image")
                if (images != null && images.length() > 0) {
                    thumb = images.optJSONObject(images.length() - 1)?.optString("label").orEmpty()
                }
                YouTubeVideo(
                    id = "itunes-album-$collectionId",
                    title = title,
                    uploader = artist,
                    duration = 0L,
                    thumbnail = bigArtwork(thumb),
                    webpageUrl = "",
                    viewCount = 0L
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "itunes topalbums failed for $code", e)
            emptyList()
        }
    }

    /** Apple's official top-songs chart for a country (lowercase 2-letter code). */
    private fun fetchItunesChart(country: String, limit: Int = 25): List<YouTubeVideo> {
        val code = country.lowercase().takeIf { it.length == 2 } ?: "us"
        return fetchItunesSongs(code, "topsongs/limit=$limit/json")
    }

    /** A single genre's top songs chart (Apple's RSS feeds expose per-genre charts). */
    private fun fetchGenreChart(country: String, genreId: Int): List<YouTubeVideo> {
        val code = country.lowercase().takeIf { it.length == 2 } ?: "us"
        return fetchItunesSongs(code, "topsongs/limit=25/genre=$genreId/json")
    }

    private fun fetchItunesSongs(code: String, urlPath: String): List<YouTubeVideo> {
        val url = URL("https://itunes.apple.com/$code/rss/$urlPath")
        val root = JSONObject(readJson(url))
        val feed = root.optJSONObject("feed") ?: return emptyList()
        val arr = feed.optJSONArray("entry") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val title = e.optJSONObject("im:name")?.optString("label").orEmpty().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val artist = e.optJSONObject("im:artist")?.optString("label").orEmpty()
                .takeIf { it.isNotBlank() } ?: "Unknown"
            val trackId = e.optJSONObject("id")?.optJSONObject("attributes")?.optLong("im:id", 0L) ?: 0L
            if (trackId <= 0) return@mapNotNull null
            var thumb = ""
            val images = e.optJSONArray("im:image")
            if (images != null && images.length() > 0) {
                thumb = images.optJSONObject(images.length() - 1)?.optString("label").orEmpty()
            }
            YouTubeVideo(
                id = "itunes-track-$trackId",
                title = title,
                uploader = artist,
                duration = 0L,
                thumbnail = bigArtwork(thumb),
                webpageUrl = "",
                viewCount = 0L
            )
        }
    }

    private fun fetchPipedTrending(base: String, region: String): List<YouTubeVideo> {
        // Some instances return {"items":[...]}, others a bare [...] array.
        val url = URL("$base/trending?region=$region")
        val raw = readJson(url).trim()
        val root = if (raw.startsWith("[")) JSONObject("{\"items\":$raw}") else JSONObject(raw)
        val arr = root.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("url", "")
                .substringAfter("v=")
                .substringBefore("&")
                .takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val duration = e.optLong("duration", 0L)
            if (duration <= 0) return@mapNotNull null
            YouTubeVideo(
                id = id,
                title = e.optString("title", "Unknown"),
                uploader = e.optString("uploaderName", ""),
                duration = duration,
                thumbnail = e.optString("thumbnail", ""),
                webpageUrl = "https://www.youtube.com/watch?v=$id",
                viewCount = e.optLong("views", 0L)
            )
        }
    }

    /**
     * Fetches the direct audio stream for a YouTube video from a Piped instance
     * and downloads it with plain HTTP. This bypasses YouTube's extraction
     * entirely (no yt-dlp, no player/client bot-checks), so it keeps working
     * whenever at least one Piped instance can serve /streams.
     */
    private suspend fun fetchPipedStream(
        videoId: String,
        info: VideoInfo,
        tracker: DownloadTracker,
        prefix: (String) -> String,
        onProgress: (Float, String) -> Unit
    ): File? {
        val file = pipedStreamFile(videoId, rawDownloadDir(), sanitizeFileName(info.title)) { p ->
            tracker.active.value = ActiveDownload(info.id, info.title, p, prefix("Downloading"))
            onProgress(p, prefix("Downloading"))
        }
        if (file != null) {
            tracker.active.value = ActiveDownload(info.id, info.title, 1f, prefix("Downloaded"))
            onProgress(1f, prefix("Downloaded"))
        }
        return file
    }

    /** Core direct-stream download shared by installs and previews. */
    private suspend fun pipedStreamFile(
        videoId: String,
        outDir: File,
        fileName: String,
        onProgress: (Float) -> Unit
    ): File? {
        for (base in PIPED_INSTANCES.take(2)) {
            try {
                val root = JSONObject(readJson(URL("$base/streams/$videoId")))
                // Prefer a dedicated audio stream; many videos only expose a
                // combined video stream, so fall back to the smallest one of
                // those before giving up on this instance.
                val picked = root.optJSONArray("audioStreams")?.let { pickAudioStream(it) }
                    ?: pickVideoStreamFallback(root.optJSONArray("videoStreams"))
                    ?: continue
                val streamUrl = picked.optString("url").takeIf { it.isNotBlank() } ?: continue
                val mime = picked.optString("mimeType", "")
                val ext = when {
                    mime.contains("m4a") -> "m4a"
                    mime.contains("mp4") || mime.contains("video") -> "mp4"
                    else -> "webm"
                }
                val target = File(outDir, "$fileName.$ext")
                target.delete()
                val ok = downloadToFile(streamUrl, target) { p ->
                    onProgress(p.coerceIn(0f, 1f))
                }
                if (ok && target.exists() && target.length() > 0) return target
                target.delete()
            } catch (e: Exception) {
                Log.w(TAG, "piped stream $base failed for $videoId", e)
            }
        }
        return null
    }

    /**
     * Chooses the smallest combined (non-videoOnly, non-HLS, non-mirror) video
     * stream as a last resort for downloads when an instance exposes no audio
     * streams. Lower resolutions keep the file small and the download fast.
     */
    private fun pickVideoStreamFallback(streams: org.json.JSONArray?): org.json.JSONObject? {
        if (streams == null) return null
        var best: org.json.JSONObject? = null
        var bestHeight = Int.MAX_VALUE
        for (i in 0 until streams.length()) {
            val s = streams.optJSONObject(i) ?: continue
            if (s.optBoolean("videoOnly", false)) continue
            val mime = s.optString("mimeType", "")
            if (mime.contains("m3u8") || mime.contains("application/x-mpegurl")) continue
            val url = s.optString("url", "")
            if (url.isBlank() || url.contains("odycdn")) continue
            val quality = s.optString("quality", "")
            val height = when {
                quality.contains("144") -> 144
                quality.contains("240") -> 240
                quality.contains("360") -> 360
                quality.contains("480") -> 480
                quality.contains("720") -> 720
                else -> s.optInt("height", Int.MAX_VALUE).takeIf { it > 0 } ?: Int.MAX_VALUE
            }
            if (height < bestHeight) {
                bestHeight = height
                best = s
            }
        }
        return best
    }

    /** Picks the best audio stream: m4a/mp4 preferred over webm/opus, highest bitrate within that tier. */
    private fun pickAudioStream(audios: org.json.JSONArray): org.json.JSONObject? {
        var best: org.json.JSONObject? = null
        var bestKey = -1.0
        for (i in 0 until audios.length()) {
            val s = audios.optJSONObject(i) ?: continue
            val mime = s.optString("mimeType", "")
            val bitrate = s.optDouble("bitrate", 0.0)
            val key = when {
                mime.contains("m4a") -> 1_000_000.0 + bitrate
                mime.contains("mp4") -> 800_000.0 + bitrate
                else -> bitrate
            }
            if (key > bestKey) {
                bestKey = key
                best = s
            }
        }
        return best
    }

    /** Streams a binary URL into a file, reporting fraction-complete when the length is known. */
    private suspend fun downloadToFile(url: String, target: File, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) RSTPlayer/1.0")
                conn.connect()
                if (conn.responseCode != 200) {
                    conn.disconnect()
                    return@withContext false
                }
                val total = conn.contentLengthLong
                val input = conn.inputStream
                val output = target.outputStream()
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastReport = 0L
                try {
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0 && done - lastReport > 256 * 1024) {
                            lastReport = done
                            onProgress(done.toFloat() / total.toFloat())
                        }
                    }
                    output.flush()
                } finally {
                    try { input.close() } catch (_: Exception) {}
                    try { output.close() } catch (_: Exception) {}
                    conn.disconnect()
                }
                (total > 0 && done >= total) || (total <= 0 && done > 0)
            } catch (e: Exception) {
                Log.w(TAG, "direct stream download failed for $url", e)
                target.delete()
                false
            }
        }

    /** Keeps a song title safe to use as a file name. */
    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "audio" }

    /** Detects YouTube bot-check / sign-in wall failures inside engine errors. */
    private fun isBotBlock(msg: String): Boolean {
        val m = msg.lowercase()
        return m.contains("sign in to confirm") || m.contains("not a bot") ||
            m.contains("login_required") || m.contains("unable to extract player") ||
            m.contains("unable to download video data") ||
            m.contains("bot") && m.contains("youtube")
    }

    private fun readJson(url: URL): String {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 6000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) RSTPlayer/1.0")
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun parseSearch(out: String): List<YouTubeVideo> {
        val root = JSONObject(out)
        val list = mutableListOf<YouTubeVideo>()
        val entries = if (root.has("entries")) {
            val arr = root.getJSONArray("entries")
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i) }
        } else {
            listOf(root)
        }
        for (e in entries) {
            val id = e.optString("id").takeIf { it.isNotEmpty() } ?: continue
            list.add(
                YouTubeVideo(
                    id = id,
                    title = e.optString("title", "Unknown"),
                    uploader = e.optString("uploader", e.optString("channel", "")),
                    duration = e.optLong("duration", 0L),
                    thumbnail = e.optString("thumbnail", ""),
                    webpageUrl = e.optString("webpage_url", "https://www.youtube.com/watch?v=$id"),
                    viewCount = e.optLong("view_count", 0L)
                )
            )
        }
        return list
    }

    /** Folder where yt-dlp initially writes raw downloads. */
    fun rawDownloadDir(): File {
        val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "RST")
        dir.mkdirs()
        return dir
    }

    /** Public-facing folder shown to the user. */
    fun publicDownloadPath(): String = "Music/RST"

    private data class VideoInfo(
        val id: String,
        val title: String,
        val artist: String,
        val album: String = "",
        val thumbnail: String
    )

    /** Metadata resolved from a shared Spotify track link. */
    data class SpotifyTrack(
        val id: String,
        val title: String,
        val artists: List<String>,
        val album: String,
        val coverUrl: String?
    )

    /** Reads the 22-char track id out of an `open.spotify.com/track/…` link or `spotify:track:…` uri. */
    fun extractSpotifyTrackId(url: String): String? {
        val trimmed = url.trim()
        Regex("""(?i)open\.spotify\.com/track/([a-zA-Z0-9]{22})""")
            .find(trimmed)?.let { return it.groupValues[1] }
        Regex("""(?i)spotify:track:([a-zA-Z0-9]{22})""")
            .find(trimmed)?.let { return it.groupValues[1] }
        return null
    }

    /**
     * Resolves a shared Spotify track link into real title/artist/cover using
     * Spotify's public oEmbed endpoint, falling back to the embed page meta tags.
     */
    suspend fun resolveSpotifyTrack(url: String): SpotifyTrack? = withContext(Dispatchers.IO) {
        val id = extractSpotifyTrackId(url) ?: return@withContext null
        try {
            val encoded = java.net.URLEncoder.encode("https://open.spotify.com/track/$id", "UTF-8")
            val conn = java.net.URL("https://open.spotify.com/oembed?url=$encoded").openConnection()
            conn.setConnectTimeout(10_000)
            conn.setReadTimeout(15_000)
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) RSTPlayer/1.0")
            val body = conn.getInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
            val json = JSONObject(body)
            val title = json.optString("title").takeIf { it.isNotBlank() }
            if (title != null) {
                val author = json.optString("author_name").takeIf { it.isNotBlank() }
                return@withContext SpotifyTrack(
                    id = id,
                    title = title,
                    artists = (author ?: "Spotify").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    album = "",
                    coverUrl = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "spotify oEmbed failed", e)
        }
        try {
            val conn = java.net.URL("https://open.spotify.com/embed/track/$id").openConnection()
            conn.setConnectTimeout(10_000)
            conn.setReadTimeout(15_000)
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
            val html = conn.getInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
            val ogTitle = metaContent(html, "og:title") ?: return@withContext null
            val decodedTitle = android.net.Uri.decode(ogTitle)
            // embed titles look like "Shape of You - song and lyrics by Ed Sheeran | Spotify"
            val trackMatch = Regex("""(.+?)\s*-\s*song and lyrics by\s*(.+?)\s*\|""").find(decodedTitle)
            val title = trackMatch?.groupValues?.get(1)?.trim() ?: decodedTitle
            val artist = trackMatch?.groupValues?.get(2)?.trim()
            SpotifyTrack(
                id = id,
                title = title,
                artists = (artist ?: "Spotify").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                album = "",
                coverUrl = metaContent(html, "og:image")
            )
        } catch (e: Exception) {
            Log.w(TAG, "spotify embed fallback failed", e)
            null
        }
    }

    private fun metaContent(html: String, property: String): String? {
        val tag = Regex("""<meta[^>]*property="$property"[^>]*>""").find(html)?.value ?: return null
        return Regex("""content="([^"]*)"""").find(tag)?.groupValues?.get(1)
    }

    /** Runs a one-result yt-dlp search (used to locate a Spotify song on YouTube). */
    suspend fun searchFirst(query: String): YouTubeVideo? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        return try {
            withContext(Dispatchers.IO) {
                val req = YoutubeDLRequest("ytsearch1:$trimmed")
                req.addOption("--dump-single-json")
                req.addOption("--no-playlist")
                req.addOption("--no-warnings")
                req.addOption("--quiet")
                req.addOption("--skip-download")
                req.addOption("--no-check-certificates")
                req.addOption("--socket-timeout", "30")
                val resp = engineExecute(req)
                parseSearch(resp.out).firstOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp spotify search failed", e)
            null
        }
    }

    /**
     * Downloads audio for a shared YouTube URL into the library.
     * Downloads clean m4a/mp3 (the engine has no bundled ffmpeg, so MP3
     * conversion/embedding is not possible) and then saves the file with the
     * real title, artist and video cover so it shows up properly in the library.
     * Call from the download service's own scope.
     */
    suspend fun downloadFromUrl(
        url: String,
        titleHint: String,
        qualityIndex: Int = 0,
        onProgress: (Float, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (title: String, artist: String) -> Unit = { _, _ -> }
    ): Boolean {
        val engineError = awaitReady()
        if (engineError != null) {
            onError(engineError)
            downloadTracker.active.value = ActiveDownload("", titleHint, 0f, errorState(null, shortError(engineError)))
            return false
        }

        val tracker = downloadTracker
        tracker.active.value = ActiveDownload("", titleHint, 0f, "Reading info…")
        onProgress(0f, "Reading info…")

        val info = fetchVideoInfo(url) ?: VideoInfo(
            id = extractVideoId(url).orEmpty(),
            title = titleHint.ifBlank { "YouTube audio" },
            artist = "YouTube",
            thumbnail = extractVideoId(url)
                ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()
        )

        return downloadCore(url, info, qualityIndex, onProgress, onError, onComplete)
    }

    /**
     * Downloads any Spotify track from a shared link: resolves the real song
     * metadata, finds the matching video on YouTube and saves it into the
     * library tagged with the Spotify title and artist.
     * Call from the download service's own scope.
     */
    suspend fun downloadSpotifyUrl(
        url: String,
        qualityIndex: Int = 0,
        onProgress: (Float, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (title: String, artist: String) -> Unit = { _, _ -> }
    ): Boolean {
        val engineError = awaitReady()
        if (engineError != null) {
            onError(engineError)
            downloadTracker.active.value = ActiveDownload("", "Spotify track", 0f, errorState(null, shortError(engineError)))
            return false
        }

        val tracker = downloadTracker
        tracker.active.value = ActiveDownload("", "Spotify track", 0f, "Reading Spotify track…")
        onProgress(0f, "Reading Spotify track…")

        val track = resolveSpotifyTrack(url)
        if (track == null) {
            val msg = "Couldn't read this Spotify track. Share a song link (not a playlist or album)."
            _error.value = msg
            tracker.active.value = ActiveDownload("", "Spotify track", 0f, errorState(null, "bad Spotify link"))
            onError(msg)
            return false
        }

        val query = (listOf(track.title) + track.artists).joinToString(" ")
        onProgress(0.05f, "Searching YouTube…")
        tracker.active.value = ActiveDownload("", track.title, 0.05f, "Searching YouTube…")
        val video = searchFirst(query)
        if (video == null) {
            val msg = "No matching YouTube video found for \"${track.title}\""
            _error.value = msg
            tracker.active.value = ActiveDownload("", track.title, 0f, errorState(null, "no YouTube match"))
            onError(msg)
            return false
        }

        val info = VideoInfo(
            id = video.id,
            title = track.title,
            artist = track.artists.joinToString(", ").ifBlank { "Spotify" },
            album = track.album,
            thumbnail = video.thumbnail.ifBlank { "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg" }
        )
        return downloadCore(video.webpageUrl, info, qualityIndex, onProgress, onError, onComplete)
    }

    /** Lists the tracks of a shared YouTube playlist/album link without downloading them. */
    suspend fun listPlaylistEntries(url: String): List<YouTubeVideo> {
        return try {
            withContext(Dispatchers.IO) {
                val req = YoutubeDLRequest(url)
                req.addOption("--dump-single-json")
                req.addOption("--yes-playlist")
                req.addOption("--no-warnings")
                req.addOption("--quiet")
                req.addOption("--skip-download")
                req.addOption("--no-check-certificates")
                req.addOption("--socket-timeout", "30")
                val resp = engineExecute(req)
                parseSearch(resp.out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp playlist listing failed", e)
            emptyList()
        }
    }

    /**
     * Downloads every track in a shared YouTube playlist/album link, one by one,
     * each tagged with its own title/artist and saved into the library.
     * Fails only if the playlist itself can't be read; a bad individual track is
     * skipped and the counts are reported through onComplete.
     */
    suspend fun downloadPlaylist(
        url: String,
        qualityIndex: Int = 0,
        onProgress: (Float, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (downloaded: Int, total: Int) -> Unit = { _, _ -> }
    ): Boolean {
        val engineError = awaitReady()
        if (engineError != null) {
            onError(engineError)
            downloadTracker.active.value = ActiveDownload("", "Playlist", 0f, errorState(null, shortError(engineError)))
            return false
        }

        val tracker = downloadTracker
        tracker.active.value = ActiveDownload("", "Playlist", 0f, "Reading playlist…")
        onProgress(0f, "Reading playlist…")

        val entries = listPlaylistEntries(url)
        if (entries.isEmpty()) {
            val msg = "Couldn't read this playlist/album. Check that the link is a YouTube playlist."
            _error.value = msg
            tracker.active.value = ActiveDownload("", "Playlist", 0f, errorState(null, "bad playlist link"))
            onError(msg)
            return false
        }

        val total = entries.size
        var downloaded = 0
        for ((index, video) in entries.withIndex()) {
            val info = VideoInfo(
                id = video.id,
                title = video.title,
                artist = video.uploader.ifBlank { "YouTube" },
                thumbnail = video.thumbnail
            )
            val base = index.toFloat() / total
            val span = 1f / total
            val ok = downloadCore(
                url = video.webpageUrl,
                info = info,
                qualityIndex = qualityIndex,
                onProgress = { p, state ->
                    onProgress(base + p * span, "$state · ${index + 1}/$total")
                },
                onError = { /* keep going on a single bad track */ },
                onComplete = { _, _ -> }
            )
            if (ok) downloaded++
        }

        tracker.active.value = ActiveDownload("", "Playlist", 1f, "Done")
        onProgress(1f, "Done")
        onComplete(downloaded, total)
        scheduleActiveReset(tracker)
        return true
    }

    /** Shared low-level download routine: yt-dlp audio, tag, move into the library. */
    private suspend fun downloadCore(
        url: String,
        info: VideoInfo,
        qualityIndex: Int,
        onProgress: (Float, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: (title: String, artist: String) -> Unit,
        statePrefix: String = ""
    ): Boolean {
        val prefix = if (statePrefix.isBlank()) { s: String -> s } else { s: String -> "$statePrefix · $s" }
        val tracker = downloadTracker
        val repo = (appContext as com.rst.player.RstApplication).graph.musicRepository
        if (repo.alreadyInLibrary(info.title, info.artist)) {
            tracker.done.value += DownloadedItem(
                title = info.title,
                path = "Already in library",
                addedAt = System.currentTimeMillis()
            )
            tracker.active.value = ActiveDownload(info.id, info.title, 1f, prefix("Already in library"))
            onProgress(1f, prefix("Already in library"))
            onComplete(info.title, info.artist)
            return true
        }
        val outDir = rawDownloadDir()
        val before = outDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val errors = mutableListOf<String>()

        var completedFile: File? = null
        val videoId = extractVideoId(url)
        // Real YouTube URLs use yt-dlp with the android_vr player client first:
        // since 2025 YouTube's media server rejects the default and android
        // clients with HTTP 403, while android_vr still serves m4a audio. Each
        // attempt walks a chain of player clients so a future client block just
        // falls through to the next one instead of breaking downloads.
        val attempts = if (videoId != null) {
            listOf(
                audioFormat(qualityIndex) to "android_vr",
                "bestaudio/best" to "android_vr",
                audioFormat(qualityIndex) to "android",
                audioFormat(qualityIndex) to null
            )
        } else {
            listOf(
                audioFormat(qualityIndex) to null,
                "bestaudio/best" to null
            )
        }
        for ((i, entry) in attempts.withIndex()) {
            val (fmt, client) = entry
            if (completedFile != null) break
            tracker.active.value = ActiveDownload(info.id, info.title, 0f, prefix(if (i == 0) "Downloading" else "Retrying…"))
            onProgress(0f, prefix(if (i == 0) "Downloading" else "Retrying…"))
            try {
                val req = YoutubeDLRequest(url)
                req.addOption("-f", fmt)
                if (client != null) req.addOption("--extractor-args", "youtube:player_client=$client")
                req.addOption("--no-playlist")
                req.addOption("--no-check-certificates")
                req.addOption("--no-warnings")
                req.addOption("-o", outDir.absolutePath + "/%(title)s.%(ext)s")

                withContext(Dispatchers.IO) {
                    engineExecute(req, redirectErrorStream = true, progress = { progress, line ->
                        val state = when {
                            line?.contains("Extracting") == true -> "Reading info…"
                            line?.contains("Destination") == true -> "Processing"
                            line?.contains("Deleting") == true -> "Finishing"
                            else -> "Downloading"
                        }
                        val p = progress.coerceIn(0f, 100f) / 100f
                        tracker.active.value = ActiveDownload(info.id, info.title, p, prefix(state))
                        onProgress(p, prefix(state))
                    })
                }

                val file = outDir.listFiles()
                    ?.filter { it.name !in before && isAudioFile(it.name) }
                    ?.maxByOrNull { it.lastModified() }
                if (file != null) {
                    completedFile = file
                    break
                }
                errors.add("yt-dlp finished but no audio file was produced")
            } catch (e: Exception) {
                errors.add(buildErrorDetail(e))
                Log.e(TAG, "yt-dlp download attempt failed (fmt=$fmt)", e)
            }
        }

        // Piped's direct audio stream is the fallback when the engine itself
        // cannot serve a file (old engine or temporarily throttled video). It
        // is a plain HTTP download that never touches YouTube extraction.
        if (completedFile == null && videoId != null) {
            tracker.active.value = ActiveDownload(info.id, info.title, 0f, prefix("Resolving stream…"))
            onProgress(0f, prefix("Resolving stream…"))
            completedFile = fetchPipedStream(videoId, info, tracker, prefix, onProgress)
        }

        val produced = completedFile
        if (produced == null) {
            val detail = errors.joinToString("\n").ifBlank { "No audio file was produced" }
            val engineLine = when {
                _engineVersion.value != null -> "Engine v${_engineVersion.value}"
                engineHealthy(engineFile()) -> "Engine ready"
                else -> "Download engine unavailable — reinstall the app"
            }
            val combined = "$engineLine\n$detail"
            _error.value = if (errors.any { isBotBlock(it) }) {
                "YouTube is blocking the download right now (bot check). Try again in a few minutes or pick another song.\n\n$combined"
            } else {
                combined
            }
            val reason = when {
                errors.any { isBotBlock(it) } -> "YouTube blocked (bot check)"
                _engineVersion.value == null && !engineHealthy(engineFile()) -> "engine unavailable"
                else -> shortError(detail)
            }
            tracker.active.value = ActiveDownload(info.id, info.title, 0f, prefix("$reason · Error"))
            onError(combined)
            return false
        }

        val coverPath = downloadCover(info.thumbnail, info.id)
        // Unique per-track album ("Artist - Title") so MediaStore gives every
        // download its own ALBUM_ID. Shared album names would collapse tracks
        // from one artist into one album whose art gets overwritten by the last
        // downloaded cover — causing songs to show the wrong picture.
        val albumName = info.album.ifBlank { "${info.artist} - ${info.title}" }

        // If the file is webm/opus (MediaStore rejects these MIME types), try
        // to transcode to m4a using yt-dlp's built-in ffmpeg post-processing.
        var finalFile = produced
        if (produced.name.endsWith(".webm", true) || produced.name.endsWith(".opus", true)) {
            tracker.active.value = ActiveDownload(info.id, info.title, 0.95f, prefix("Converting…"))
            onProgress(0.95f, prefix("Converting…"))
            val converted = tryConvertToM4a(produced)
            if (converted != null) {
                produced.delete()
                finalFile = converted
            }
        }

        tagAudioFile(finalFile, info.title, info.artist, albumName, coverPath)
        val ok = saveToLibrary(appContext, finalFile, info.title, info.artist, albumName, coverPath)
        if (!ok) {
            val msg = "Downloaded but could not save the file to your library"
            _error.value = msg
            tracker.active.value = ActiveDownload(info.id, info.title, 0f, prefix(errorState(null, "could not save file")))
            onError(msg)
            return false
        }

        tracker.done.value += DownloadedItem(
            title = info.title,
            path = publicDownloadPath(),
            addedAt = System.currentTimeMillis()
        )
        tracker.active.value = ActiveDownload(info.id, info.title, 1f, prefix("Done"))
        onProgress(1f, prefix("Done"))
        onComplete(info.title, info.artist)
        scheduleActiveReset(tracker)
        CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(1200)
            (appContext as com.rst.player.RstApplication).graph.musicRepository.scan()
        }
        return true
    }

    private fun isAudioFile(name: String): Boolean =
        name.endsWith(".mp3", true) || name.endsWith(".m4a", true) ||
            name.endsWith(".mp4", true) || name.endsWith(".webm", true) ||
            name.endsWith(".opus", true) || name.endsWith(".ogg", true) ||
            name.endsWith(".flac", true) || name.endsWith(".wav", true) ||
            name.endsWith(".aac", true)

    /**
     * Attempts to convert a webm/opus file to m4a using yt-dlp's
     * --extract-audio post-processing (which relies on ffmpeg being
     * available on the system PATH). Returns the converted file on
     * success, or null if conversion fails (e.g. no ffmpeg).
     */
    private suspend fun tryConvertToM4a(source: File): File? = withContext(Dispatchers.IO) {
        try {
            val outDir = source.parentFile ?: return@withContext null
            val req = YoutubeDLRequest(source.absolutePath)
            req.addOption("--extract-audio")
            req.addOption("--audio-format", "m4a")
            req.addOption("--audio-quality", "0")
            req.addOption("-o", outDir.absolutePath + "/%(title)s.%(ext)s")
            engineExecute(req)
            val expected = source.nameWithoutExtension + ".m4a"
            outDir.listFiles()?.find { it.name.equals(expected, ignoreCase = true) }
        } catch (e: Exception) {
            Log.w(TAG, "webm→m4a conversion failed, will use original file", e)
            null
        }
    }

    /** Collapses the full exception cause chain into readable text for the user. */
    private fun buildErrorDetail(e: Exception): String {
        val sb = StringBuilder()
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 5) {
            val msg = t.message?.trim().orEmpty()
            if (msg.isNotEmpty()) sb.append(msg).append("\n")
            t = t.cause
            depth++
        }
        if (sb.isBlank()) sb.append(e.javaClass.simpleName)
        return sb.toString().trim().take(1500)
    }

    /**
     * Audio format selectors for the quality picker. Prefers m4a/mp3 so the
     * file saves cleanly to the library without needing ffmpeg to convert webm.
     */
    fun audioFormat(qualityIndex: Int): String = when (qualityIndex) {
        1 -> "bestaudio[abr<=256][ext=m4a]/bestaudio[abr<=256][ext=mp3]/bestaudio[abr<=256]/bestaudio/best"
        2 -> "bestaudio[abr<=192][ext=m4a]/bestaudio[abr<=192][ext=mp3]/bestaudio[abr<=192]/bestaudio/best"
        3 -> "bestaudio[abr<=128][ext=m4a]/bestaudio[abr<=128][ext=mp3]/bestaudio[abr<=128]/bestaudio/best"
        else -> "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio/best"
    }

    /** Fetches title/artist/cover for a URL using the engine (no media download). */
    private suspend fun fetchVideoInfo(url: String): VideoInfo? {
        return try {
            val req = YoutubeDLRequest(url)
            req.addOption("--dump-single-json")
            req.addOption("--no-playlist")
            req.addOption("--no-warnings")
            req.addOption("--quiet")
            req.addOption("--skip-download")
            req.addOption("--no-check-certificates")
            req.addOption("--socket-timeout", "30")
            val resp = engineExecute(req)
            val v = parseSearch(resp.out).firstOrNull() ?: return null
            VideoInfo(
                id = v.id,
                title = v.title,
                artist = v.uploader.ifBlank { "YouTube" },
                thumbnail = v.thumbnail
            )
        } catch (e: Exception) {
            Log.w(TAG, "metadata fetch failed", e)
            null
        }
    }

    private fun extractVideoId(url: String): String? =
        Regex("(?:v=|youtu\\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})")
            .find(url)?.groupValues?.get(1)

    /** Downloads the video cover into external storage so MediaStore can serve it as album art. */
    private fun downloadCover(thumbnailUrl: String, videoId: String): String? {
        // yt-dlp/Piped often hand back webp cover URLs; MediaStore and some
        // scanners can't decode webp art from an m4a/mp3, so force a real jpg.
        val coverUrl = jpegCover(thumbnailUrl).ifBlank {
            videoId.takeIf { it.isNotEmpty() }
                ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty()
        }
        if (coverUrl.isBlank()) return null
        return try {
            val dir = File(appContext.getExternalFilesDir(null), "covers").apply { mkdirs() }
            val file = File(dir, "${videoId.ifBlank { "cover" }}.jpg")
            if (file.exists() && file.length() > 0) return file.absolutePath
            val conn = java.net.URL(coverUrl).openConnection()
            conn.setConnectTimeout(10_000)
            conn.setReadTimeout(15_000)
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.getInputStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            if (file.length() == 0L) {
                file.delete()
                null
            } else {
                file.absolutePath
            }
        } catch (e: Exception) {
            Log.w(TAG, "cover download failed", e)
            null
        }
    }

    /** Rewrites YouTube's webp cover URLs to equivalent jpg ones. */
    private fun jpegCover(url: String): String {
        if (url.isBlank()) return url
        if (!url.contains("i.ytimg.com")) return url
        return url.replace("/vi_webp/", "/vi/").replace(".webp", ".jpg")
    }

    /**
     * Writes real title/artist/album + the cover INTO the audio file, so the
     * device's own media scanner reads them from the file (this is what makes
     * the name, artist and picture show up in the library on every device).
     */
    private fun tagAudioFile(file: File, title: String, artist: String, album: String, coverPath: String?) {
        val name = file.name.lowercase()
        val taggable = name.endsWith(".mp3") || name.endsWith(".m4a") ||
            name.endsWith(".mp4") || name.endsWith(".flac")
        if (!taggable) return
        try {
            val af = org.jaudiotagger.audio.AudioFileIO.read(file)
            val tag = af.tagOrCreateAndSetDefault
            tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
            tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
            tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, album)
            tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, artist)
            if (coverPath != null) {
                tag.deleteArtworkField()
                val artwork = org.jaudiotagger.tag.images.ArtworkFactory.getNew()
                artwork.setFromFile(java.io.File(coverPath))
                tag.setField(artwork)
            }
            org.jaudiotagger.audio.AudioFileIO.write(af)
        } catch (e: Exception) {
            Log.w(TAG, "tagging failed for ${file.name}", e)
        }
    }

    /**
     * Starts a single download. Ignores the tap while a download is already
     * running so cards can't stack, but lets a finished/errored card be
     * replaced right away (it also auto-hides after a few seconds).
     */
    fun runDownload(video: YouTubeVideo) {
        val tracker = downloadTracker
        val current = tracker.active.value
        if (current != null && !isTerminal(current.state)) return
        scope.launch {
            downloadOne(video, null)
            scheduleActiveReset(tracker)
        }
    }

    /**
     * Installs several songs at once (used by the artist/album batch install).
     * Runs them one after another on the same download card, showing a "2/5"
     * progress prefix so the user sees the whole queue advance. When some items
     * fail, the card stays up with a "3/5 failed" summary so failures are never
     * silent.
     */
    fun runDownloads(videos: List<YouTubeVideo>) {
        if (videos.isEmpty()) return
        val tracker = downloadTracker
        val current = tracker.active.value
        if (current != null && !isTerminal(current.state)) return
        scope.launch {
            batchDownloads++
            try {
                val total = videos.size
                var failed = 0
                for ((index, video) in videos.withIndex()) {
                    val label = "${index + 1}/$total"
                    if (!downloadOne(video, label)) failed++
                }
                val s = tracker.active.value?.state.orEmpty()
                if (isTerminal(s)) {
                    if (failed > 0 && failed < total) {
                        _error.value = "$failed of $total songs could not be downloaded."
                        tracker.active.value = ActiveDownload(
                            tracker.active.value?.videoId ?: "",
                            tracker.active.value?.title ?: "",
                            1f,
                            "some songs could not be downloaded · Error"
                        )
                    }
                    scheduleActiveReset(tracker)
                }
            } finally {
                batchDownloads--
            }
        }
    }

    private fun errorState(label: String?, reason: String): String =
        if (label == null) "$reason · Error" else "$label · $reason · Error"

    /** Short, readable first reason for the one-line download card state. */
    private fun shortError(detail: String): String {
        val lines = detail.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val first = lines.firstOrNull { it.startsWith("ERROR:", ignoreCase = true) }
            ?: lines.firstOrNull()
            ?: return "Download failed"
        return first.removePrefix("ERROR:").removePrefix("error:").trim().take(56)
    }

    /**
     * Shared single-item download: resolves the YouTube match when the result
     * has no URL yet (iTunes/API results), then runs the low-level routine.
     * Returns true when the item reached the library, false when it failed.
     */
    private suspend fun downloadOne(video: YouTubeVideo, label: String?): Boolean {
        val tracker = downloadTracker
        tracker.active.value = ActiveDownload(
            video.id,
            video.title,
            0f,
            if (label == null) "Starting" else "$label · Starting"
        )
        val resolved = if (video.webpageUrl.isBlank()) {
            try {
                resolveYouTubeVideo(video)
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp search download failed", e)
                val msg = e.message ?: "Download failed"
                tracker.active.value = ActiveDownload(video.id, video.title, 0f, errorState(label, shortError(msg)))
                _error.value = msg
                return false
            }
        } else video
        if (resolved == null) {
            tracker.active.value = ActiveDownload(video.id, video.title, 0f, errorState(label, "not found on YouTube"))
            _error.value = "Could not find “${video.title}” on YouTube (it may be blocked). Try another song."
            return false
        }
        // A search hit whose title came back as a bare "Unknown" must not
        // clobber the known-good iTunes/API title.
        val info = VideoInfo(
            id = resolved.id,
            title = resolved.title.takeUnless { it.isBlank() || it.equals("Unknown", true) } ?: video.title,
            artist = resolved.uploader.ifBlank { video.uploader.ifBlank { "YouTube" } },
            thumbnail = resolved.thumbnail
        )
        downloadCore(
            url = resolved.webpageUrl,
            info = info,
            qualityIndex = 0,
            onProgress = { _, _ -> },
            onError = { /* handled through tracker/error flow below */ },
            onComplete = { _, _ -> },
            statePrefix = label ?: ""
        )
        val endState = tracker.active.value?.state.orEmpty()
        return isTerminal(endState) && (endState.endsWith("Done") || endState.endsWith("Already in library"))
    }

    /**
     * Returns a playable/downloadable video. iTunes/API results carry no YouTube
     * URL yet, so the best match is looked up on YouTube. Tries progressively
     * simpler queries ("Artist Title", Artist "Title", "Title") through two
     * sources: yt-dlp's own search first (it uses the bundled engine and works
     * even when every Piped instance is down), then the Piped API as a fallback.
     */
    private suspend fun resolveYouTubeVideo(video: YouTubeVideo): YouTubeVideo? {
        if (video.webpageUrl.isNotBlank()) return video
        val title = cleanLookupTitle(video.title)
        val artist = cleanLookupArtist(video.uploader)
        val queries = buildList {
            if (artist.isNotBlank() && title.isNotBlank()) add("$artist $title")
            if (artist.isNotBlank() && title.isNotBlank()) add("$artist \"$title\"")
            if (title.isNotBlank()) add(title)
        }.distinct()

        for (q in queries) {
            val result = withTimeoutOrNull(SEARCH_TIMEOUT) {
                withContext(Dispatchers.IO) {
                    try {
                        val req = YoutubeDLRequest("ytsearch1:$q")
                        req.addOption("--dump-single-json")
                        req.addOption("--no-playlist")
                        req.addOption("--no-check-certificates")
                        req.addOption("--socket-timeout", "20")
                        parseSearch(engineExecute(req).out).firstOrNull()
                    } catch (e: Exception) {
                        Log.w(TAG, "yt-dlp single search failed for \"$q\"", e)
                        null
                    }
                }
            }
            if (result != null) return result
        }

        for (q in queries) {
            val result = withTimeoutOrNull(PIPED_TIMEOUT) {
                withContext(Dispatchers.IO) {
                    var match: YouTubeVideo? = null
                    for (base in PIPED_INSTANCES) {
                        try {
                            match = fetchPipedVideos(base, q).firstOrNull()
                            if (match != null) break
                        } catch (e: Exception) {
                            Log.w(TAG, "piped resolve $base failed for \"$q\"", e)
                        }
                    }
                    match
                }
            }
            if (result != null) return result
        }
        return null
    }

    /** Strips " (Official Audio)", " - Official Music Video", etc. so lookups match. */
    private fun cleanLookupTitle(value: String): String {
        var t = value.trim()
        val suffixes = listOf(
            " (official audio)", " (official video)", " (official music video)",
            " (official lyric video)", " (official hd video)", " (audio)", " (video)",
            " (lyrics)", " (lyric video)", " (music video)", " (official)",
            " - official audio", " - official video", " - official music video",
            " [official audio]", " [official video]", " [official music video]"
        )
        for (suffix in suffixes) {
            if (t.endsWith(suffix, ignoreCase = true)) {
                t = t.dropLast(suffix.length).trim()
                break
            }
        }
        return t
    }

    /** Strips " - Topic", " - Official", etc. from uploader/channel names. */
    private fun cleanLookupArtist(value: String): String {
        var a = value.trim()
        val suffixes = listOf(" - topic", " - official", " - official audio", " - audio", " official")
        for (suffix in suffixes) {
            if (a.endsWith(suffix, ignoreCase = true)) {
                a = a.dropLast(suffix.length).trim()
                break
            }
        }
        return a
    }

    /** A song staged in the app cache, ready to be played without saving to the library. */
    data class PreviewResult(
        val mediaId: String,
        val filePath: String,
        val title: String,
        val artist: String,
        val artworkPath: String?
    )

    /** Row-level progress for a preview being staged (kept off the global download card). */
    data class PreviewProgress(
        val videoId: String,
        val title: String,
        val progress: Float
    )

    private val _previewProgress = MutableStateFlow<PreviewProgress?>(null)
    val previewProgress: StateFlow<PreviewProgress?> = _previewProgress.asStateFlow()

    /**
     * Stages a song in the app cache so it can be played as a preview without
     * touching the library. Already-staged songs are reused instantly (no
     * re-download), so tapping a song a second time starts playback right away.
     *
     * Previews never touch the global download tracker — no "Downloading…" or
     * "Done" cards pop up while you are just listening. A compact audio format
     * is used so the first stream is as fast as possible.
     */
    suspend fun preview(video: YouTubeVideo): PreviewResult? {
        try {
            val resolved = resolveYouTubeVideo(video)
            if (resolved == null) {
                _error.value = "Could not find “${video.title}” on YouTube"
                return null
            }
            val outDir = File(appContext.cacheDir, "preview/${resolved.id}").apply { mkdirs() }
            val coverPath = downloadCover(resolved.thumbnail, resolved.id)
            // Cached cover file wins; if the cover download failed (YouTube
            // sometimes blocks it), fall back to the original thumbnail URL —
            // Coil loads http(s) directly, so the picture still shows.
            val artwork = coverPath ?: resolved.thumbnail.takeIf { it.isNotBlank() }
            val title = resolved.title.takeUnless { it.isBlank() || it.equals("Unknown", true) } ?: video.title
            val artist = resolved.uploader.ifBlank { video.uploader.ifBlank { "Unknown" } }

            val cached = outDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 && isAudioFile(it.name) }
                ?.maxByOrNull { it.lastModified() }
            if (cached != null) {
                return PreviewResult(
                    mediaId = "preview-${resolved.id}",
                    filePath = cached.absolutePath,
                    title = title,
                    artist = artist,
                    artworkPath = artwork
                )
            }

            val before = outDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
            // Try Piped's direct audio stream first: it avoids yt-dlp's YouTube
            // extraction entirely, so previews keep working under bot-checks.
            val direct = pipedStreamFile(resolved.id, outDir, "stream") { p ->
                _previewProgress.value = PreviewProgress(resolved.id, title, p.coerceIn(0f, 1f))
            }
            if (direct != null) {
                return PreviewResult(
                    mediaId = "preview-${resolved.id}",
                    filePath = direct.absolutePath,
                    title = title,
                    artist = artist,
                    artworkPath = artwork
                )
            }
            val req = YoutubeDLRequest(resolved.webpageUrl)
            req.addOption("-f", audioFormat(3))
            req.addOption("-S", "+size")
            req.addOption("--extractor-args", "youtube:player_client=android_vr")
            req.addOption("--no-playlist")
            req.addOption("--no-check-certificates")
            req.addOption("--no-warnings")
            req.addOption("-o", outDir.absolutePath + "/%(title)s.%(ext)s")
            _previewProgress.value = PreviewProgress(resolved.id, title, 0f)
            withContext(Dispatchers.IO) {
                engineExecute(req, redirectErrorStream = true, progress = { progress, _ ->
                    _previewProgress.value = PreviewProgress(
                        resolved.id,
                        title,
                        progress.coerceIn(0f, 100f) / 100f
                    )
                })
            }
            _previewProgress.value = null
            val file = outDir.listFiles()
                ?.filter { it.name !in before && isAudioFile(it.name) }
                ?.maxByOrNull { it.lastModified() }
            if (file == null) {
                _error.value = "Could not prepare “${video.title}” for playback"
                return null
            }
            return PreviewResult(
                mediaId = "preview-${resolved.id}",
                filePath = file.absolutePath,
                title = title,
                artist = artist,
                artworkPath = artwork
            )
        } catch (e: Exception) {
            Log.e(TAG, "yt-dlp preview failed", e)
            _previewProgress.value = null
            _error.value = e.message ?: "Preview failed"
            return null
        }
    }

    /** Full online artist profile: header info + top songs + albums (via iTunes). */
    data class OnlineArtistInfo(
        val name: String,
        val thumbnail: String,
        val genre: String,
        val trackCount: Int,
        val albumCount: Int,
        val topSongs: List<YouTubeVideo>,
        val albums: List<YouTubeVideo>
    )

    suspend fun artistProfile(artistId: Long): OnlineArtistInfo? {
        if (artistId <= 0) return null
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://itunes.apple.com/lookup?id=$artistId&entity=album,song&limit=100")
                val root = JSONObject(readJson(url))
                val arr = root.optJSONArray("results") ?: return@withContext null
                var name = ""
                var thumbnail = ""
                var genre = ""
                val topSongs = mutableListOf<YouTubeVideo>()
                val albums = mutableListOf<YouTubeVideo>()
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    when (e.optString("wrapperType")) {
                        "artist" -> {
                            name = e.optString("artistName").takeIf { it.isNotBlank() } ?: ""
                            genre = e.optString("primaryGenreName", "")
                            thumbnail = bigArtwork(e.optString("artworkUrl100", ""))
                        }
                        "track" -> {
                            val title = e.optString("trackName").takeIf { it.isNotBlank() } ?: continue
                            topSongs.add(
                                YouTubeVideo(
                                    id = "itunes-track-${e.optLong("trackId")}",
                                    title = title,
                                    uploader = e.optString("artistName", "").ifBlank { name },
                                    duration = e.optLong("trackTimeMillis", 0L) / 1000,
                                    thumbnail = bigArtwork(e.optString("artworkUrl100", "")),
                                    webpageUrl = "",
                                    viewCount = 0L
                                )
                            )
                        }
                        "collection" -> {
                            if (e.optString("collectionType", "") == "Album") {
                                val aName = e.optString("collectionName").takeIf { it.isNotBlank() } ?: continue
                                albums.add(
                                    YouTubeVideo(
                                        id = "itunes-album-${e.optLong("collectionId")}",
                                        title = aName,
                                        uploader = e.optString("artistName", "").ifBlank { name },
                                        duration = e.optLong("trackCount", 0L),
                                        thumbnail = bigArtwork(e.optString("artworkUrl100", "")),
                                        webpageUrl = "",
                                        viewCount = 0L
                                    )
                                )
                            }
                        }
                    }
                }
                if (topSongs.isEmpty() && albums.isEmpty()) null
                else OnlineArtistInfo(
                    name = name.ifBlank { "Artist" },
                    thumbnail = thumbnail,
                    genre = genre,
                    trackCount = topSongs.size,
                    albumCount = albums.size,
                    topSongs = topSongs,
                    albums = albums
                )
            } catch (e: Exception) {
                Log.w(TAG, "artist profile lookup failed", e)
                null
            }
        }
    }

    /**
     * Resolves an artist by name (iTunes search) and loads their profile.
     * Used when a result carries no iTunes artist ID (e.g. Piped channels).
     */
    suspend fun artistProfileByName(name: String): OnlineArtistInfo? {
        val id = resolveArtistIdByName(name) ?: return null
        return artistProfile(id)
    }

    private suspend fun resolveArtistIdByName(name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://itunes.apple.com/search?" +
                        "term=${URLEncoder.encode(trimmed, "UTF-8")}" +
                        "&media=music&entity=musicArtist&limit=5"
                )
                val root = JSONObject(readJson(url))
                val arr = root.optJSONArray("results") ?: return@withContext null
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    if (e.optString("wrapperType") == "artist") {
                        val id = e.optLong("artistId", 0L)
                        if (id > 0) return@withContext id
                    }
                }
                null
            } catch (e: Exception) {
                Log.w(TAG, "itunes artist id lookup failed", e)
                null
            }
        }
    }

    /**
     * Full online album: the album itself + its track list (via iTunes lookup).
     * The album id is the iTunes collection id (the "itunes-album-…" suffix).
     */
    data class OnlineAlbumInfo(
        val name: String,
        val artist: String,
        val thumbnail: String,
        val trackCount: Int,
        val tracks: List<YouTubeVideo>
    )

    suspend fun albumInfo(collectionId: Long): OnlineAlbumInfo? {
        if (collectionId <= 0) return null
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://itunes.apple.com/lookup?id=$collectionId&entity=song&limit=200"
                )
                val root = JSONObject(readJson(url))
                val arr = root.optJSONArray("results") ?: return@withContext null
                var name = ""
                var artist = ""
                var thumbnail = ""
                val tracks = mutableListOf<YouTubeVideo>()
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    when (e.optString("wrapperType")) {
                        "collection" -> {
                            name = e.optString("collectionName").takeIf { it.isNotBlank() } ?: ""
                            artist = e.optString("artistName", "").takeIf { it.isNotBlank() } ?: ""
                            thumbnail = bigArtwork(e.optString("artworkUrl100", ""))
                        }
                        "track" -> {
                            val title = e.optString("trackName").takeIf { it.isNotBlank() } ?: continue
                            tracks.add(
                                YouTubeVideo(
                                    id = "itunes-track-${e.optLong("trackId")}",
                                    title = title,
                                    uploader = e.optString("artistName", "").ifBlank { artist },
                                    duration = e.optLong("trackTimeMillis", 0L) / 1000,
                                    thumbnail = bigArtwork(e.optString("artworkUrl100", "")).ifBlank { thumbnail },
                                    webpageUrl = "",
                                    viewCount = 0L
                                )
                            )
                        }
                    }
                }
                if (tracks.isEmpty()) null
                else OnlineAlbumInfo(
                    name = name.ifBlank { "Album" },
                    artist = artist,
                    thumbnail = thumbnail,
                    trackCount = tracks.size,
                    tracks = tracks
                )
            } catch (e: Exception) {
                Log.w(TAG, "album lookup failed", e)
                null
            }
        }
    }

    private fun saveToLibrary(
        context: Context,
        file: File,
        title: String,
        artist: String,
        album: String,
        coverPath: String?
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "RST"
            )
            dir.mkdirs()
            val dest = File(dir, file.name)
            return if (file.renameTo(dest)) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(dest.absolutePath),
                    arrayOf("audio/*"),
                    null
                )
                true
            } else {
                false
            }
        }

        val resolver = context.contentResolver
        val cleanTitle = title.ifBlank { file.nameWithoutExtension }
        val rawMime = mimeFor(file.name)
        // MediaStore rejects audio/webm and audio/opus — fall back to audio/mp4
        // so the insert always succeeds.  The media scanner will correct the
        // MIME type on the next pass.
        val safeMime = when (rawMime) {
            "audio/webm", "audio/opus" -> "audio/mp4"
            else -> rawMime
        }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(android.provider.MediaStore.Audio.Media.MIME_TYPE, safeMime)
            put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, publicDownloadPath())
            put(android.provider.MediaStore.Audio.Media.IS_MUSIC, 1)
            put(android.provider.MediaStore.Audio.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(android.provider.MediaStore.Audio.Media.TITLE, cleanTitle)
            put(android.provider.MediaStore.Audio.Media.ARTIST, artist)
            put(android.provider.MediaStore.Audio.Media.ALBUM, album)
        }
        val uri = resolver.insert(
            android.provider.MediaStore.Audio.Media.getContentUri(
                android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
            ),
            values
        ) ?: return false

        val copied = try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } != null
        } catch (e: Exception) {
            Log.w(TAG, "write to MediaStore failed", e)
            false
        }
        file.delete()
        if (!copied) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            return false
        }

        if (coverPath != null) {
            val albumId = queryAlbumId(resolver, uri)
            if (albumId > 0) setAlbumArt(resolver, albumId, coverPath)
        }

        // Ask the media scanner to fully parse the file so the embedded
        // title/artist/album and cover art are guaranteed to appear.
        val dataPath = queryColumn(resolver, uri, android.provider.MediaStore.Audio.Media.DATA)
        if (dataPath != null) {
            try {
                android.media.MediaScannerConnection.scanFile(context, arrayOf(dataPath), arrayOf("audio/*"), null)
            } catch (_: Exception) {
            }
        }

        // Formats we can't embed tags into (e.g. webm without ffmpeg) get their
        // metadata wiped by the scan and show as "Unknown artist". Re-apply it
        // right after so the library always shows the real title and artist.
        try {
            val reapply = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.TITLE, cleanTitle)
                put(android.provider.MediaStore.Audio.Media.ARTIST, artist)
                put(android.provider.MediaStore.Audio.Media.ALBUM_ARTIST, artist)
                put(android.provider.MediaStore.Audio.Media.ALBUM, album)
            }
            resolver.update(uri, reapply, null, null)
        } catch (_: Exception) {
        }
        return true
    }

    private fun queryColumn(
        resolver: android.content.ContentResolver,
        uri: android.net.Uri,
        column: String
    ): String? {
        return try {
            resolver.query(uri, arrayOf(column), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            Log.w(TAG, "query $column failed", e)
            null
        }
    }

    private fun queryAlbumId(resolver: android.content.ContentResolver, uri: android.net.Uri): Long {
        return try {
            resolver.query(
                uri,
                arrayOf(android.provider.MediaStore.Audio.Media.ALBUM_ID),
                null,
                null,
                null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "query album id failed", e)
            0L
        }
    }

    /** Points the downloaded file's album at the cover we saved on disk. */
    private fun setAlbumArt(resolver: android.content.ContentResolver, albumId: Long, coverPath: String) {
        try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Albums.ALBUM_ART, coverPath)
            }
            resolver.update(
                android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                values,
                "${android.provider.MediaStore.Audio.Albums._ID} = ?",
                arrayOf(albumId.toString())
            )
        } catch (e: Exception) {
            Log.w(TAG, "album art update failed", e)
        }
    }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".mp3", true) -> "audio/mpeg"
        name.endsWith(".m4a", true) || name.endsWith(".mp4", true) -> "audio/mp4"
        name.endsWith(".opus", true) -> "audio/opus"
        name.endsWith(".webm", true) -> "audio/webm"
        name.endsWith(".ogg", true) -> "audio/ogg"
        name.endsWith(".flac", true) -> "audio/flac"
        name.endsWith(".wav", true) -> "audio/wav"
        else -> "audio/mp4"
    }

    companion object {
        private const val TAG = "YouTubeRepository"
        private const val SEARCH_TIMEOUT = 15_000L
        private const val ITUNES_TIMEOUT = 8_000L
        private const val PIPED_TIMEOUT = 15_000L
        private val PIPED_INSTANCES = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.leptons.xyz",
            "https://pipedapi.reallyaweso.me",
            "https://pipedapi.syncpundit.io",
            "https://pipedapi.moomoo.me"
        )
    }
}
