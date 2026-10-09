package com.rst.player.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rst.player.RstApplication
import com.rst.player.data.preferences.AppSettings
import com.rst.player.data.repository.ModeRepository
import com.rst.player.player.NowPlayingItem
import com.rst.player.ui.components.AppIntroOverlay
import com.rst.player.ui.components.HeadphoneOverlay
import com.rst.player.ui.components.backgrounds.AnimatedAppBackground
import com.rst.player.ui.components.MiniPlayer
import com.rst.player.ui.components.rememberHeadphoneOverlayState
import com.rst.player.ui.core.LocalGraph
import com.rst.player.ui.core.PlayerHeroState
import com.rst.player.ui.screens.album.AlbumScreen
import com.rst.player.ui.screens.artist.ArtistScreen
import com.rst.player.ui.screens.downloads.DownloadsScreen
import com.rst.player.ui.screens.explore.ExploreScreen
import com.rst.player.ui.screens.home.HomeScreen
import com.rst.player.ui.screens.library.LibraryScreen
import com.rst.player.ui.screens.mode.ModeScreen
import com.rst.player.ui.screens.mode.ModesScreen
import com.rst.player.ui.screens.nowplaying.NowPlayingScreen
import com.rst.player.ui.screens.playlist.PlaylistDetailScreen
import com.rst.player.ui.screens.search.OnlineAlbumScreen
import com.rst.player.ui.screens.search.OnlineArtistScreen
import com.rst.player.ui.screens.search.SearchScreen
import com.rst.player.ui.screens.settings.SettingsScreen
import com.rst.player.ui.theme.BrandAccentGlow
import com.rst.player.ui.theme.RstTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RstApp() {
    val context = LocalContext.current
    val graph = RstApplication.graphOf(context)
    val settings by graph.userPreferences.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    androidx.compose.runtime.CompositionLocalProvider(LocalGraph provides graph) {
        RstTheme(settings) {
            val navController = rememberNavController()

            // Kept out of RstApp's direct composition: player events (play/pause,
            // track changes) must NOT recompose the whole app + every screen. This
            // only emits a new value when the now-playing ITEM actually changes,
            // so mini player / now playing stay in sync without app-wide jank.
            val nowPlaying by produceState<NowPlayingItem?>(initialValue = null) {
                combine(
                    graph.playerController.ui,
                    graph.musicRepository.songs
                ) { playerUi, allSongs ->
                    val currentSong = allSongs.find { it.id.toString() == playerUi.currentMediaId }
                    playerUi.currentMediaId?.let { id ->
                        currentSong?.let { NowPlayingItem.fromSong(it) }
                            // Online previews aren't in the library, so the mini
                            // player / now playing fall back to the track metadata
                            // delivered by the playback service.
                            ?: if (id.startsWith("preview-")) {
                                NowPlayingItem(
                                    mediaId = id,
                                    title = playerUi.currentTitle.ifBlank { "Unknown" },
                                    artist = playerUi.currentArtist.ifBlank { "Unknown" },
                                    artworkUrl = playerUi.currentArtworkUrl
                                )
                            } else null
                    }
                }
                    .distinctUntilChanged()
                    .collect { value = it }
            }

            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val immersive = currentRoute == Routes.MODES || currentRoute?.startsWith("mode/") == true

            // Home / Explore / Library / Settings live in one swipeable pager so
            // users can flick between them horizontally (and the pill stays in sync).
            val pagerState = rememberPagerState(
                initialPage = if (settings.startScreen == AppSettings.START_LIBRARY) 2 else 0,
                pageCount = { 4 }
            )
            var lastTab by rememberSaveable { mutableIntStateOf(0) }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { lastTab = it }
            }

            // A tab tap either lands on the already-open pager or navigates back to
            // it from a detail screen (album/artist/playlist/search). When already on
            // the pager, the scroll is driven directly here so the tap can never be
            // lost to a race with navigation state.
            var pendingTab by remember { mutableIntStateOf(-1) }
            val scope = rememberCoroutineScope()
            val keyboard = LocalSoftwareKeyboardController.current
            val activity = LocalContext.current as? Activity

            val onTabSelected: (Int) -> Unit = { index ->
                // Dismiss the search keyboard first — with edge-to-edge the IME
                // used to overlay the bottom bar, swallowing the taps (this made
                // Home/Library/Settings feel dead while on the search screen).
                // The Compose controller is only a request; forcing it at the
                // window level closes the IME even mid-animation.
                keyboard?.hide()
                (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(activity.window?.decorView?.windowToken, 0)
                if (navController.currentDestination?.route != Routes.TABS) {
                    pendingTab = index
                    navController.navigate(Routes.TABS) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
                    pendingTab = -1
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            }

            var showIntro by remember { mutableStateOf(true) }
            val headphoneState = rememberHeadphoneOverlayState()

            // Shared-element bridge between the mini player artwork and the full
            // Now Playing artwork; Now Playing renders as a full-screen overlay so
            // the mini player stays composed underneath and the artwork can morph
            // exactly onto it when collapsing.
            val hero = remember { PlayerHeroState() }
            var showNowPlaying by rememberSaveable { mutableStateOf(false) }

            Box(modifier = Modifier.fillMaxSize()) {
                PermissionHandler()

                Scaffold(
                bottomBar = {
                    if (!immersive) {
                        Column(modifier = Modifier.imePadding()) {
                            AnimatedVisibility(
                                visible = nowPlaying != null,
                                enter = slideInVertically(
                                    animationSpec = tween(280),
                                    initialOffsetY = { it }
                                ) + fadeIn(tween(280)),
                                exit = slideOutVertically(
                                    animationSpec = tween(220),
                                    targetOffsetY = { it }
                                ) + fadeOut(tween(220))
                            ) {
                                MiniPlayer(
                                    item = nowPlaying,
                                    hero = hero,
                                    onToggle = { graph.playerController.togglePlay() },
                                    onNext = { graph.playerController.next() },
                                    onOpen = { showNowPlaying = true }
                                )
                            }
                            FloatingNav(selectedIndex = lastTab, onTabSelected = onTabSelected)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    AnimatedAppBackground(
                        style = settings.backgroundStyle,
                        modifier = Modifier.fillMaxSize()
                    )
                    NavHost(
                        navController = navController,
                        startDestination = Routes.TABS,
                        modifier = Modifier.fillMaxSize(),
                        // Fade-only transitions: full-screen slide+fade forces layout +
                        // draw of entire screens every frame, which reads heavy on
                        // 120 Hz panels. A short crossfade is imperceptibly fast and
                        // leaves the GPU work to a plain alpha blend.
                        enterTransition = { fadeIn(tween(180)) },
                        exitTransition = { fadeOut(tween(150)) },
                        popEnterTransition = { fadeIn(tween(180)) },
                        popExitTransition = { fadeOut(tween(150)) }
                    ) {
                    composable(Routes.TABS) {
                        // Watch pendingTab from a stable effect: if this effect were
                        // keyed on pendingTab, resetting it to -1 would cancel the
                        // pager animation the instant it starts.
                        LaunchedEffect(pagerState) {
                            snapshotFlow { pendingTab }.collect { page ->
                                if (page >= 0) {
                                    pendingTab = -1
                                    pagerState.animateScrollToPage(page)
                                }
                            }
                        }
                        MainTabPager(
                            pagerState = pagerState,
                            onOpenNowPlaying = { showNowPlaying = true },
                            onOpenAlbum = { id -> navController.navigate("album/$id") },
                            onOpenArtist = { name -> navController.navigate("artist/$name") },
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenOnlineArtist = { artistId, name ->
                                navController.navigate(
                                    "online_artist/$artistId?name=${Uri.encode(name)}"
                                )
                            },
                            onOpenOnlineAlbum = { albumId, name, artistName ->
                                navController.navigate(
                                    "online_album/$albumId?name=${Uri.encode(name)}&artist=${Uri.encode(artistName)}"
                                )
                            },
                            onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                            onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                            onOpenModes = { navController.navigate(Routes.MODES) }
                        )
                    }
                    composable(Routes.MODES) {
                        ModesScreen(
                            onBack = { navController.popBackStack() },
                            onOpenMode = { modeId -> navController.navigate("mode/$modeId") }
                        )
                    }
                    composable(Routes.MODE) { entry ->
                        ModeScreen(
                            modeId = entry.arguments?.getString("modeId").orEmpty(),
                            onExit = { navController.popBackStack() },
                            onCustomizePlaylist = { m ->
                                scope.launch {
                                    val pid = graph.playlistRepository.getOrCreateNamed(ModeRepository.playlistNameFor(m))
                                    graph.modeRepository.setPlaylist(m.id, pid)
                                    navController.navigate("playlist/$pid")
                                }
                            }
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(
                            onOpenAlbum = { id -> navController.navigate("album/$id") },
                            onOpenArtist = { name -> navController.navigate("artist/$name") },
                            onOpenOnlineArtist = { artistId, name ->
                                navController.navigate(
                                    "online_artist/$artistId?name=${Uri.encode(name)}"
                                )
                            },
                            onOpenOnlineAlbum = { albumId, name, artistName ->
                                navController.navigate(
                                    "online_album/$albumId?name=${Uri.encode(name)}&artist=${Uri.encode(artistName)}"
                                )
                            },
                            onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) }
                        )
                    }
                    composable(Routes.ONLINE_ARTIST) { entry ->
                        OnlineArtistScreen(
                            artistId = entry.arguments?.getString("artistId")?.toLongOrNull() ?: 0L,
                            artistName = entry.arguments?.getString("name")?.let { Uri.decode(it) }.orEmpty(),
                            onBack = { navController.popBackStack() },
                            onOpenOnlineAlbum = { albumId, name, artistName ->
                                navController.navigate(
                                    "online_album/$albumId?name=${Uri.encode(name)}&artist=${Uri.encode(artistName)}"
                                )
                            }
                        )
                    }
                    composable(Routes.ONLINE_ALBUM) { entry ->
                        OnlineAlbumScreen(
                            collectionId = entry.arguments?.getString("albumId")?.toLongOrNull() ?: 0L,
                            albumName = entry.arguments?.getString("name")?.let { Uri.decode(it) }.orEmpty(),
                            artistName = entry.arguments?.getString("artist")?.let { Uri.decode(it) }.orEmpty(),
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.ALBUM) { entry ->
                        AlbumScreen(
                            albumId = entry.arguments?.getString("albumId")?.toLongOrNull() ?: 0L,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.ARTIST) { entry ->
                        ArtistScreen(
                            artistName = entry.arguments?.getString("artistName") ?: "",
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.PLAYLIST) { entry ->
                        PlaylistDetailScreen(
                            playlistId = entry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L,
                            isFavorites = false,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.FAVORITES) {
                        PlaylistDetailScreen(
                            playlistId = 0L,
                            isFavorites = true,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Routes.DOWNLOADS) {
                        DownloadsScreen(onBack = { navController.popBackStack() })
                    }
                }
                }
            }

                if (showNowPlaying) {
                    NowPlayingScreen(
                        item = nowPlaying,
                        hero = hero,
                        onClose = { showNowPlaying = false }
                    )
                }

                if (showIntro) {
                    AppIntroOverlay(onFinished = { showIntro = false })
                }
                HeadphoneOverlay(state = headphoneState)
            }
        }
    }
}

private data class NavItemData(
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabPager(
    pagerState: PagerState,
    onOpenNowPlaying: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenOnlineArtist: (Long, String) -> Unit,
    onOpenOnlineAlbum: (Long, String, String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenModes: () -> Unit
) {
    val scope = rememberCoroutineScope()
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> HomeScreen(
                onOpenNowPlaying = onOpenNowPlaying,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenSettings = { scope.launch { pagerState.animateScrollToPage(3) } },
                onOpenSearch = onOpenSearch,
                onOpenModes = onOpenModes
            )
            1 -> ExploreScreen(
                onOpenOnlineArtist = onOpenOnlineArtist,
                onOpenOnlineAlbum = onOpenOnlineAlbum
            )
            2 -> LibraryScreen(
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onOpenPlaylist = onOpenPlaylist,
                onOpenFavorites = onOpenFavorites,
                onOpenSearch = onOpenSearch
            )
            else -> SettingsScreen()
        }
    }
}

@Composable
private fun FloatingNav(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val navItems = listOf(
        NavItemData("Home", Icons.Rounded.Home),
        NavItemData("Explore", Icons.Rounded.Explore),
        NavItemData("Library", Icons.Rounded.LibraryMusic),
        NavItemData("Settings", Icons.Rounded.Settings)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(modifier = Modifier.height(56.dp)) {
                val itemWidth = maxWidth / navItems.size
                val pillOffset by animateDpAsState(
                    targetValue = itemWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "navPill"
                )
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.CenterStart)
                        .offset(x = pillOffset)
                        .padding(start = (itemWidth - 44.dp) / 2, top = 8.dp, bottom = 8.dp)
                        .width(44.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
                Row(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    navItems.forEachIndexed { index, item ->
                        NavItem(
                            label = item.label,
                            icon = item.icon,
                            selected = index == selectedIndex,
                            onClick = { onTabSelected(index) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "navIconScale"
    )
    Column(
        modifier = modifier
            .height(56.dp)
            .clickable { onClick() },
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionHandler() {
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val notificationPermission = Manifest.permission.POST_NOTIFICATIONS

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        RstApplication.graphOf(context).musicRepository.scan()
    }

    LaunchedEffect(Unit) {
        val missing = listOfNotNull(
            audioPermission,
            // Real-time FFT visualizer on Android 10+; denied = animated fallback.
            Manifest.permission.RECORD_AUDIO.takeIf { Build.VERSION.SDK_INT >= 29 },
            notificationPermission.takeIf { Build.VERSION.SDK_INT >= 33 }
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            launcher.launch(missing.toTypedArray())
        }
    }
}
