package com.vm.soundra

import android.util.Log
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.vm.soundra.ui.components.SplashScreen
import com.vm.soundra.ui.components.SmartDownloadSheet
import com.vm.soundra.ui.components.PremiumBottomNavigation
import com.vm.soundra.ui.components.MiniPlayer
import com.vm.soundra.ui.components.FullPlayer
import com.vm.soundra.ui.Screen
import com.vm.soundra.model.search.SearchResult
import com.vm.soundra.logic.*
import com.vm.soundra.ui.downloads.DownloadsScreen
import com.vm.soundra.ui.home.HomeScreen
import com.vm.soundra.ui.home.HomeViewModel
import com.vm.soundra.ui.search.SearchContent
import com.vm.soundra.ui.search.SearchViewModel
import com.vm.soundra.ui.search.SearchUiState
import com.vm.soundra.ui.playlists.PlaylistsScreen
import com.vm.soundra.ui.playlists.PlaylistDetailScreen
import com.vm.soundra.ui.playlists.AddToPlaylistDialog
import com.vm.soundra.ui.theme.SoundraTheme
import com.vm.soundra.ui.theme.ThemeMode
import com.vm.soundra.ui.theme.BrandCyan
import com.vm.soundra.ui.settings.SettingsScreen
import com.vm.soundra.ui.settings.AboutScreen
import com.vm.soundra.update.DownloadState
import com.vm.soundra.update.UpdateDialog
import com.vm.soundra.update.UpdateViewModel as AppUpdateViewModel
import com.vm.soundra.ui.playlists.PlaylistsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@kotlinx.coroutines.FlowPreview
class MainActivity : ComponentActivity() {

    private val startTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PERF_STARTUP", "MainActivity: onCreate started")
        
        // Non-blocking Ads init (disabled until post-release)
        if (com.vm.soundra.ads.AdConstants.ADS_ENABLED) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    com.google.android.gms.ads.MobileAds.initialize(this@MainActivity) {
                        Log.d("ADMOB", "MobileAds initialized")
                    }
                } catch (e: Exception) {
                    Log.e("ADMOB", "AdMob init failed", e)
                }
            }
        }
        
        // Apply language from DataStore
        val languageManager = LanguageManager(this)
        lifecycleScope.launch {
            val langStart = System.currentTimeMillis()
            try {
                languageManager.applyInitialLanguage()
                Log.d("PERF_STARTUP", "Language init: ${System.currentTimeMillis() - langStart}ms")
            } catch (e: Exception) {
                Log.e("PERF_STARTUP", "Language init failed", e)
            }
        }

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            
            // 1. Language Context Wrapping
            val selectedLanguage by languageManager.selectedLanguage.collectAsState(initial = null)
            val localizedContext = remember(selectedLanguage) {
                if (selectedLanguage != null) {
                    languageManager.getLocalizedContext(context, selectedLanguage!!.tag)
                } else {
                    context
                }
            }

            // 2. Permission Handling
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (!isGranted) {
                    scope.launch {
                        snackbarHostState.showSnackbar(localizedContext.getString(R.string.permission_notif_denied))
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                Log.d("PERF_STARTUP", "MainActivity: UI Content Set in ${System.currentTimeMillis() - startTime}ms")
            }

            val mp3DownloadViewModel: Mp3DownloadViewModel = viewModel(
                factory = Mp3DownloadViewModel.Factory(LocalContext.current)
            )
            
            // Pre-initialize HomeViewModel to avoid delays when screen switches
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(context))

            // NEW: Common PlaylistsViewModel for cover updates
            val playlistsViewModel: PlaylistsViewModel = viewModel(
                factory = PlaylistsViewModel.Factory(context)
            )

            val mp3DownloadState by mp3DownloadViewModel.downloadState.collectAsState()
            val isLoadingMetadata by mp3DownloadViewModel.isLoadingMetadata.collectAsState()

            LaunchedEffect(mp3DownloadState) {
                if (mp3DownloadState is DownloadState.Completed) {
                    delay(3000)
                    mp3DownloadViewModel.resetState()
                }
            }

            // 3. Navigation State
            var currentScreen by remember { mutableStateOf(Screen.Splash.route) }
            var lastCheckedUrl by remember { mutableStateOf("") }
            var initialSearchQuery by remember { mutableStateOf<String?>(null) }
            var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
            
            // NEW: Launcher for picking cover images, stable root scope
            val photoPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia(),
                onResult = { uri ->
                    uri?.let {
                        selectedPlaylistId?.let { id ->
                            playlistsViewModel.updatePlaylistCover(id, it.toString())
                        }
                    }
                }
            )

            // Double-tap logic for Home icon
            var lastHomeClickTime by remember { mutableStateOf(0L) }

            var showDownloadSheet by remember { mutableStateOf(false) }
            var selectedMetadata by remember { mutableStateOf<VideoMetadata?>(null) }
            var songToAddToPlaylist by remember { mutableStateOf<SearchResult?>(null) }
            val selectedBitrate by mp3DownloadViewModel.selectedBitrate.collectAsState()

            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            // Player State for MiniPlayer and FullPlayer
            val currentQueue by mp3DownloadViewModel.currentQueue.collectAsState()
            val currentIndex by mp3DownloadViewModel.currentIndex.collectAsState()
            val previewState by mp3DownloadViewModel.previewState.collectAsState()
            val isPlayerActive = currentIndex != -1 && currentIndex < currentQueue.size && previewState !is PreviewState.Idle

            fun checkClipboard() {
                val text = clipboardManager.getText()?.text ?: ""
                if (text.isNotBlank() && text != lastCheckedUrl) {
                    if (text.contains("youtube.com") || text.contains("youtu.be") || text.contains("music.youtube.com")) {
                        lastCheckedUrl = text
                        scope.launch {
                            val metadata = mp3DownloadViewModel.fetchMetadata(text)
                            if (metadata != null) {
                                selectedMetadata = metadata
                                showDownloadSheet = true
                            }
                        }
                    }
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        checkClipboard()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides (context.findActivity() ?: (context as ActivityResultRegistryOwner))
            ) {
                SoundraTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                            },
                            label = "screen_transition"
                        ) { screen ->
                            Scaffold(
                                snackbarHost = { SnackbarHost(snackbarHostState) },
                                modifier = Modifier.fillMaxSize(),
                                containerColor = MaterialTheme.colorScheme.background,
                                bottomBar = {
                                    // Only show bottom bar for main screens
                                    if (screen != Screen.Splash.route && screen != Screen.About.route) {
                                        Column {
                                            // Mini Player integration
                                            AnimatedVisibility(
                                                visible = isPlayerActive,
                                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                                            ) {
                                                val track = currentQueue[currentIndex]
                                                val pos by mp3DownloadViewModel.playbackPosition.collectAsState()
                                                val dur by mp3DownloadViewModel.playbackDuration.collectAsState()
                                                
                                                MiniPlayer(
                                                    currentTrack = track,
                                                    previewState = previewState,
                                                    playbackPosition = pos,
                                                    playbackDuration = dur,
                                                    onTogglePlayPause = { mp3DownloadViewModel.togglePlayPause() },
                                                    onClick = { mp3DownloadViewModel.showFullScreenPlayer() }
                                                )
                                            }

                                            PremiumBottomNavigation(
                                                currentScreen = screen,
                                                onScreenSelected = { route ->
                                                    if (route == Screen.Home.route) {
                                                        if (screen == Screen.Home.route) {
                                                            val currentTime = System.currentTimeMillis()
                                                            if (currentTime - lastHomeClickTime < 400) {
                                                                homeViewModel.refresh()
                                                                lastHomeClickTime = 0L
                                                            } else {
                                                                lastHomeClickTime = currentTime
                                                            }
                                                        } else {
                                                            lastHomeClickTime = System.currentTimeMillis()
                                                            currentScreen = route
                                                        }
                                                    } else {
                                                        currentScreen = route
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            ) { padding ->
                                // FIX: Use non-negative padding to avoid crash
                                val baseBottomPadding = if (screen != Screen.Splash.route && screen != Screen.About.route) {
                                    (padding.calculateBottomPadding() - 20.dp).coerceAtLeast(0.dp)
                                } else {
                                    0.dp
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = baseBottomPadding)
                                ) {
                                    when (screen) {
                                        Screen.Splash.route -> {
                                            SplashScreen(onTimeout = { currentScreen = Screen.Home.route })
                                        }
                                        Screen.About.route -> {
                                            AboutScreen(onBack = { currentScreen = Screen.Settings.route })
                                        }
                                        Screen.Home.route -> {
                                            HomeScreen(
                                                mp3DownloadViewModel = mp3DownloadViewModel,
                                                homeViewModel = homeViewModel,
                                                initialSearchQuery = initialSearchQuery,
                                                onInitialQueryUsed = { initialSearchQuery = null },
                                                onDownloadClick = { result ->
                                                    val homeState = homeViewModel.uiState.value
                                                    if (homeState is SearchUiState.Success) {
                                                        val results = homeState.results
                                                        val index = results.indexOf(result)
                                                        mp3DownloadViewModel.setPreviewQueue(results, index)
                                                    } else {
                                                        mp3DownloadViewModel.setPreviewQueue(listOf(result), 0)
                                                    }
                                                    
                                                    mp3DownloadViewModel.prepareDownload(
                                                        url = result.url,
                                                        partial = PartialVideoMetadata(
                                                            title = result.title,
                                                            artist = result.uploaderName,
                                                            thumbnailUrl = result.thumbnailUrl,
                                                            videoUrl = result.url
                                                        )
                                                    )
                                                    showDownloadSheet = true
                                                },
                                                onAddToPlaylist = { result ->
                                                    songToAddToPlaylist = result
                                                },
                                                onSearchTriggered = { query ->
                                                    initialSearchQuery = query
                                                    currentScreen = Screen.Search.route
                                                }
                                            )
                                        }
                                        Screen.Search.route -> {
                                            val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(context))
                                            SearchContent(
                                                viewModel = searchViewModel,
                                                mp3DownloadViewModel = mp3DownloadViewModel,
                                                onDownloadClick = { result ->
                                                    val searchState = searchViewModel.uiState.value
                                                    if (searchState is SearchUiState.Success) {
                                                        val results = searchState.results
                                                        val index = results.indexOf(result)
                                                        mp3DownloadViewModel.setPreviewQueue(results, index)
                                                    } else {
                                                        mp3DownloadViewModel.setPreviewQueue(listOf(result), 0)
                                                    }
                                                    
                                                    mp3DownloadViewModel.prepareDownload(
                                                        url = result.url,
                                                        partial = PartialVideoMetadata(
                                                            title = result.title,
                                                            artist = result.uploaderName,
                                                            thumbnailUrl = result.thumbnailUrl,
                                                            videoUrl = result.url
                                                        )
                                                    )
                                                    showDownloadSheet = true
                                                },
                                                onAddToPlaylist = { result ->
                                                    songToAddToPlaylist = result
                                                },
                                                initialQuery = initialSearchQuery,
                                                onInitialQueryUsed = { initialSearchQuery = null }
                                            )
                                        }
                                        Screen.Playlists.route -> {
                                            PlaylistsScreen(
                                                onPlaylistClick = { id ->
                                                    Log.d("PLAYLIST_NAV", "MainActivity: Clicking playlist ID: $id")
                                                    selectedPlaylistId = id
                                                    currentScreen = Screen.PlaylistDetail.route
                                                }
                                            )
                                        }
                                        Screen.PlaylistDetail.route -> {
                                            // FIX: Extract ID safely from state
                                            val currentId = remember(screen) { selectedPlaylistId }
                                            
                                            if (currentId != null) {
                                                PlaylistDetailScreen(
                                                    playlistId = currentId,
                                                    onBack = { 
                                                        currentScreen = Screen.Playlists.route 
                                                    },
                                                    onPlayAll = { queue ->
                                                        if (queue.isNotEmpty()) {
                                                            mp3DownloadViewModel.setLocalQueue(queue, 0)
                                                        }
                                                    },
                                                    onShuffle = { queue ->
                                                        if (queue.isNotEmpty()) {
                                                            if (!mp3DownloadViewModel.shuffleEnabled.value) {
                                                                    mp3DownloadViewModel.toggleShuffle()
                                                            }
                                                            mp3DownloadViewModel.setLocalQueue(queue, 0)
                                                        }
                                                    },
                                                    onPlaySong = { song, queue ->
                                                        val index = queue.indexOf(song)
                                                        mp3DownloadViewModel.setLocalQueue(queue, if (index != -1) index else 0)
                                                    },
                                                    onChangeCover = {
                                                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                                    }
                                                )
                                            } else {
                                                // Fallback if ID is null during transition
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(color = BrandCyan)
                                                }
                                                LaunchedEffect(Unit) {
                                                    Log.e("PLAYLIST_NAV", "MainActivity: playlistId is NULL, returning to list")
                                                    currentScreen = Screen.Playlists.route
                                                }
                                            }
                                        }
                                        Screen.Downloads.route -> DownloadsScreen(
                                            onBack = { currentScreen = Screen.Home.route }
                                        )
                                        Screen.Settings.route -> SettingsScreen(
                                            onBack = { currentScreen = Screen.Home.route },
                                            themeMode = ThemeMode.DARK,
                                            onToggleTheme = {},
                                            onAboutClick = { currentScreen = Screen.About.route }
                                        )
                                    }
                                }
                            }
                        }

                        // Overlays
                        if (showDownloadSheet) {
                            val previewQueue by mp3DownloadViewModel.previewQueue.collectAsState()
                            val previewIndex by mp3DownloadViewModel.previewIndex.collectAsState()
                            val previewOnlyState by mp3DownloadViewModel.previewOnlyState.collectAsState()
                            val previewPos by mp3DownloadViewModel.previewPlaybackPosition.collectAsState()
                            val previewDur by mp3DownloadViewModel.previewPlaybackDuration.collectAsState()
                            val previewBuffer by mp3DownloadViewModel.previewBufferPosition.collectAsState()
                            
                            val shuffleEnabledState by mp3DownloadViewModel.shuffleEnabled.collectAsState()
                            val repeatModeState by mp3DownloadViewModel.repeatMode.collectAsState()
                            
                            SmartDownloadSheet(
                                metadataState = mp3DownloadViewModel.metadataState.collectAsState().value,
                                onRetry = { selectedMetadata?.videoUrl?.let { mp3DownloadViewModel.prepareDownload(it) } },
                                selectedBitrate = selectedBitrate,
                                onBitrateChange = { mp3DownloadViewModel.setBitrate(it) },
                                estimateSize = { dur, br, sz -> mp3DownloadViewModel.estimateSize(dur, br, sz) },
                                previewState = previewOnlyState,
                                playbackPosition = previewPos,
                                playbackDuration = previewDur,
                                bufferPosition = previewBuffer,
                                onTogglePreview = { url -> mp3DownloadViewModel.togglePlayPause(url, previewOnly = true) },
                                onSeek = { pos -> mp3DownloadViewModel.seekPreviewTo(pos) },
                                onPlayNext = { mp3DownloadViewModel.playPreviewNext() },
                                onPlayPrevious = { mp3DownloadViewModel.playPreviewPrevious() },
                                hasNext = previewIndex < previewQueue.size - 1,
                                hasPrevious = previewIndex > 0,
                                shuffleEnabled = shuffleEnabledState,
                                onToggleShuffle = { mp3DownloadViewModel.toggleShuffle() },
                                repeatMode = repeatModeState,
                                onToggleRepeat = { mp3DownloadViewModel.toggleRepeat() },
                                onDownload = { meta ->
                                    showDownloadSheet = false
                                    mp3DownloadViewModel.stopPreview()
                                    mp3DownloadViewModel.downloadMp3(meta.videoUrl, meta.title, meta.artist, meta.thumbnailUrl, selectedBitrate)
                                },

                                onDismiss = { 
                                    showDownloadSheet = false
                                    mp3DownloadViewModel.stopPreview()
                                    mp3DownloadViewModel.cancelMetadataAnalysis()
                                }
                            )
                        }

                        // Full Screen Player Overlay
                        val isFullScreenPlayerVisible by mp3DownloadViewModel.isFullScreenPlayerVisible.collectAsState()
                        if (isFullScreenPlayerVisible && isPlayerActive) {
                            val track = currentQueue[currentIndex]
                            val pos by mp3DownloadViewModel.playbackPosition.collectAsState()
                            val dur by mp3DownloadViewModel.playbackDuration.collectAsState()
                            val shuffleEnabledState by mp3DownloadViewModel.shuffleEnabled.collectAsState()
                            val repeatModeState by mp3DownloadViewModel.repeatMode.collectAsState()

                            FullPlayer(
                                currentTrack = track,
                                previewState = previewState,
                                playbackPosition = pos,
                                playbackDuration = dur,
                                shuffleEnabled = shuffleEnabledState,
                                repeatMode = repeatModeState,
                                currentQueue = currentQueue,
                                onTogglePlayPause = { mp3DownloadViewModel.togglePlayPause() },
                                onSeek = { mp3DownloadViewModel.seekTo(it) },
                                onNext = { mp3DownloadViewModel.playNext() },
                                onPrevious = { mp3DownloadViewModel.playPrevious() },
                                onToggleShuffle = { mp3DownloadViewModel.toggleShuffle() },
                                onToggleRepeat = { mp3DownloadViewModel.toggleRepeat() },
                                onRemoveFromQueue = { mp3DownloadViewModel.removeFromQueue(it) },
                                onDismiss = { mp3DownloadViewModel.hideFullScreenPlayer() }
                            )
                        }

                        if (songToAddToPlaylist != null) {
                            AddToPlaylistDialog(
                                song = songToAddToPlaylist!!,
                                onDismiss = { songToAddToPlaylist = null }
                            )
                        }

                        if (isLoadingMetadata) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
