package com.rst.player.desktop

import androidx.compose.desktop.Window
import androidx.compose.desktop.exitApplication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.prefs.Preferences

private val DEFAULT_DIRS = listOf(File(System.getProperty("user.home"), "Music"))

fun main() = Window(
    onCloseRequest = { exitApplication(0) },
    title = "RST Player",
    state = WindowState(width = 1100.dp, height = 740.dp)
) {
    RstDesktopTheme { RstAppRoot() }
}

enum class Tab(val label: String) { LIBRARY("Library"), ONLINE("Online") }

@Composable
fun RstAppRoot() {
    val scope = rememberCoroutineScope()
    val engine = remember { PlayerEngine() }
    val ytdlp = remember { YtdlpPc() }

    LaunchedEffect(Unit) {
        engine.startToolkit()
        engine.onTrackEnd = { playRelative(1) }
        scanning = true
        val dirs = loadLibraryDirs()
        tracks.clear()
        tracks.addAll(LibraryScanner.scan(dirs))
        scanning = false
    }

    // ---- state ----
    var tab by remember { mutableStateOf(Tab.LIBRARY) }
    val tracks = remember { mutableStateListOf<Track>() }
    var scanning by remember { mutableStateOf(false) }
    var libraryQuery by remember { mutableStateOf("") }
    var onlineQuery by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<OnlineHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val downloadProgress = remember { mutableStateMapOf<String, Pair<Int, String>>() }
    var currentIndex by remember { mutableStateOf(-1) }

    val positionMs by engine.positionMs.collectAsState()
    val durationMs by engine.durationMs.collectAsState()
    val playingNow by engine.playing.collectAsState()
    val ytdlpStatus by ytdlp.status.collectAsState()

    fun visibleTracks(): List<Track> =
        if (libraryQuery.isBlank()) tracks.toList()
        else tracks.filter {
            it.title.contains(libraryQuery, true) ||
                it.artist.contains(libraryQuery, true) ||
                it.album.contains(libraryQuery, true)
        }

    fun playIndex(list: List<Track>, index: Int) {
        if (index !in list.indices) return
        currentIndex = index
        scope.launch { engine.play(list[index].file) }
    }

    fun playRelative(step: Int) {
        val list = visibleTracks()
        if (list.isEmpty()) return
        val next = if (currentIndex in list.indices) currentIndex + step else 0
        playIndex(list, next.coerceIn(0, list.size - 1))
    }

    LaunchedEffect(Unit) {
        engine.onTrackEnd = { playRelative(1) }
        scanning = true
        val dirs = loadLibraryDirs()
        tracks.clear()
        tracks.addAll(LibraryScanner.scan(dirs))
        scanning = false
    }

    Column(Modifier.fillMaxSize().background(RstBg)) {
        HeaderBar(tab, onTab = { tab = it }, statusLine = if (tab == Tab.ONLINE) ytdlpStatus else null)

        when (tab) {
            Tab.LIBRARY -> LibraryTab(
                query = libraryQuery,
                onQuery = { libraryQuery = it },
                scanning = scanning,
                tracks = visibleTracks(),
                currentPath = engine.nowPlayingPath.value,
                onPlay = { list, i -> playIndex(list, i) },
                onPickFolder = {
                    pickDirectory()?.let { dir ->
                        saveLibraryDir(dir)
                        scope.launch {
                            scanning = true
                            val all = (loadLibraryDirs() + dir).distinct()
                            tracks.clear()
                            tracks.addAll(LibraryScanner.scan(all))
                            scanning = false
                        }
                    }
                }
            )

            Tab.ONLINE -> OnlineTab(
                query = onlineQuery,
                onQuery = { onlineQuery = it },
                searching = searching,
                hits = hits,
                downloads = downloadProgress.toMap(),
                onSearch = {
                    scope.launch {
                        searching = true
                        hits = ytdlp.search(onlineQuery)
                        searching = false
                    }
                },
                onDownload = { hit ->
                    scope.launch {
                        downloadProgress[hit.id] = 0 to "Queued"
                        val file = ytdlp.download(hit) { p, s ->
                            downloadProgress[hit.id] = p to s
                        }
                        if (file != null) {
                            val added = LibraryScanner.scan(loadLibraryDirs())
                            tracks.clear()
                            tracks.addAll(added)
                        }
                        launch {
                            kotlinx.coroutines.delay(2500)
                            downloadProgress.remove(hit.id)
                        }
                    }
                }
            )
        }

        NowPlayingBar(
            track = tracks.getOrNull(currentIndex),
            playing = playingNow,
            positionMs = positionMs,
            durationMs = durationMs,
            onToggle = { engine.toggle() },
            onNext = { playRelative(1) },
            onPrev = { playRelative(-1) },
            onSeek = { engine.seek(it) },
            onVolume = { engine.setVolume(it) },
            onStop = { engine.stopCurrent(); currentIndex = -1 }
        )
    }
}

@Composable
private fun HeaderBar(tab: Tab, onTab: (Tab) -> Unit, statusLine: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(RstSurfaceHigh, RstSurface)))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandTitle()
        Spacer(Modifier.width(36.dp))
        PillTab(Tab.LIBRARY.label, tab == Tab.LIBRARY) { onTab(Tab.LIBRARY) }
        Spacer(Modifier.width(8.dp))
        PillTab(Tab.ONLINE.label, tab == Tab.ONLINE) { onTab(Tab.ONLINE) }
        Spacer(Modifier.weight(1f))
        if (statusLine != null) Text(statusLine, color = RstOnBgDim, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun LibraryTab(
    query: String,
    onQuery: (String) -> Unit,
    scanning: Boolean,
    tracks: List<Track>,
    currentPath: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onPickFolder: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SearchField(query, onQuery, "Search your music…", Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(RstAccentGlow, RstAccent)))
                    .clickable { onPickFolder() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text("Add folder", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${tracks.size} songs",
                color = RstOnBgDim,
                fontSize = 12.sp
            )
            if (scanning) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("Scanning…", color = RstOnBgDim, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            items(tracks, key = { it.file.absolutePath }) { t ->
                TrackRow(
                    title = t.title,
                    subtitle = "${t.artist} — ${t.album}",
                    duration = formatTime(t.durationMs),
                    isCurrent = currentPath == t.file.absolutePath,
                    playable = t.file.extension.lowercase() in LibraryScanner.PLAYABLE_EXTENSIONS,
                    onDoubleClick = {},
                    onClick = { onPlay(tracks, tracks.indexOf(t)) }
                )
            }
        }
    }
}

@Composable
private fun OnlineTab(
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    hits: List<OnlineHit>,
    downloads: Map<String, Pair<Int, String>>,
    onSearch: () -> Unit,
    onDownload: (OnlineHit) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        SearchField(query, onQuery, "Search YouTube (yt-dlp)…")
        Spacer(Modifier.height(8.dp))
        Row {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(RstAccent, RstCyan)))
                    .clickable(enabled = !searching && query.isNotBlank(), onClick = onSearch)
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text(
                    if (searching) "Searching…" else "Search",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(hits, key = { it.id }) { hit ->
                val prog = downloads[hit.id]
                Column {
                    TrackRow(
                        title = hit.title,
                        subtitle = hit.uploader.ifBlank { "YouTube" },
                        duration = if (hit.durationSec > 0) formatTime(hit.durationSec * 1000) else "",
                        isCurrent = false,
                        onDoubleClick = {},
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(RstAccentGlow.copy(alpha = 0.85f))
                                    .clickable(enabled = prog == null) { onDownload(hit) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    when {
                                        prog?.second == "Done" -> "Added ✓"
                                        prog != null -> "${prog.first}%"
                                        else -> "Download"
                                    },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    )
                    if (prog != null) ProgressBar(prog.first / 100f)
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    track: Track?,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Double) -> Unit,
    onStop: () -> Unit
) {
    var volume by remember { mutableStateOf(0.85f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    SurfaceBar {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(null, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.widthIn(max = 260.dp)) {
                Text(
                    track?.title ?: "Nothing playing",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track?.artist ?: "—",
                    color = RstOnBgDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(18.dp))
            PlayGlyphButton("⏮", 34) { onPrev() }
            Spacer(Modifier.width(8.dp))
            PlayGlyphButton(if (playing) "❚❚" else "▶", 44, filled = true) { onToggle() }
            Spacer(Modifier.width(8.dp))
            PlayGlyphButton("⏭", 34) { onNext() }
            Spacer(Modifier.width(16.dp))
            Text(formatTime(positionMs), color = RstOnBgDim, fontSize = 11.sp)
            Slider(
                value = if (dragging) dragValue
                else if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                else 0f,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    dragging = false
                    if (durationMs > 0) onSeek((dragValue * durationMs).toLong())
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(formatTime(durationMs), color = RstOnBgDim, fontSize = 11.sp)
            Text("VOL", color = RstOnBgDim.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.padding(start = 12.dp))
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                    onVolume(it.toDouble())
                },
                modifier = Modifier.width(110.dp).padding(end = 8.dp)
            )
        }
    }
}

@Composable
private fun SurfaceBar(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(RstSurfaceHigh, RstSurface)))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ---- library folders persistence ----

private val PREFS = Preferences.userNodeForPackage(RstAppRootMarker::class.java)

class RstAppRootMarker

private const val KEY_DIRS = "library_dirs"

private fun loadLibraryDirs(): List<File> {
    val stored = PREFS.get(KEY_DIRS, null)
    return if (stored.isNullOrBlank()) DEFAULT_DIRS
    else stored.split("\n").map(::File).filter { it.isDirectory }.ifEmpty { DEFAULT_DIRS }
}

private fun saveLibraryDir(dir: File) {
    val current = loadLibraryDirs().map { it.absolutePath }.toMutableSet()
    current.add(dir.absolutePath)
    PREFS.put(KEY_DIRS, current.joinToString("\n"))
}

private fun pickDirectory(): File? {
    return try {
        val chooser = javax.swing.JFileChooser()
        chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "Choose a music folder"
        val result = chooser.showOpenDialog(null)
        if (result == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    } catch (_: Exception) {
        null
    }
}
