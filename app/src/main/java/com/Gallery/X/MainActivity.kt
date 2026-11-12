package com.Gallery.X

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Gallery.X.data.MediaItem
import com.Gallery.X.data.MediaType
import com.Gallery.X.data.PermissionAccessLevel
import com.Gallery.X.ui.AppTab
import com.Gallery.X.ui.GalleryViewModel
import com.Gallery.X.ui.components.DeleteMultipleConfirmDialog
import com.Gallery.X.ui.components.FloatingSearchBar
import com.Gallery.X.ui.components.PartialAccessBanner
import com.Gallery.X.ui.components.PermissionGateCard
import com.Gallery.X.ui.components.SelectionTopBar
import com.Gallery.X.ui.editors.AudioEditorScreen
import com.Gallery.X.ui.editors.PhotoEditorScreen
import com.Gallery.X.ui.editors.VideoEditorScreen
import com.Gallery.X.ui.icons.AppIcons
import com.Gallery.X.ui.screens.AudioScreen
import com.Gallery.X.ui.screens.PersistentAudioMiniPlayer
import com.Gallery.X.ui.screens.PhotoViewerScreen
import com.Gallery.X.ui.screens.PhotosScreen
import com.Gallery.X.ui.screens.SettingsScreen
import com.Gallery.X.ui.screens.VideoPlayerScreen
import com.Gallery.X.ui.screens.VideosScreen
import com.Gallery.X.ui.theme.GalleryXTheme

private sealed interface ActiveOverlay {
    data object None : ActiveOverlay
    data class PhotoViewer(val item: MediaItem) : ActiveOverlay
    data class VideoPlayer(val item: MediaItem) : ActiveOverlay
    data class PhotoEditor(val item: MediaItem) : ActiveOverlay
    data class VideoEditor(val item: MediaItem) : ActiveOverlay
    data class AudioEditor(val item: MediaItem) : ActiveOverlay
}

class MainActivity : AppCompatActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            GalleryXRoot(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            viewModel.resolveExternalOpenIntent(uri, intent.type)
        }
    }
}

@Composable
private fun GalleryXRoot(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissionLevel by viewModel.permissionLevel.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val photoSections by viewModel.photoDateSections.collectAsStateWithLifecycle()
    val filteredPhotos by viewModel.filteredPhotos.collectAsStateWithLifecycle()
    val photoFolders by viewModel.photoFolders.collectAsStateWithLifecycle()
    val selectedPhotoFolder by viewModel.selectedPhotoFolder.collectAsStateWithLifecycle()

    val filteredVideos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val videoFolders by viewModel.videoFolders.collectAsStateWithLifecycle()
    val selectedVideoFolder by viewModel.selectedVideoFolder.collectAsStateWithLifecycle()

    val filteredAudio by viewModel.filteredAudio.collectAsStateWithLifecycle()
    val audioFolders by viewModel.audioFolders.collectAsStateWithLifecycle()
    val selectedAudioFolder by viewModel.selectedAudioFolder.collectAsStateWithLifecycle()

    val audioPlayerState by viewModel.audioPlayerState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val externalOpenItem by viewModel.externalOpenItem.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectionBreakdown by viewModel.selectionBreakdown.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(AppTab.PHOTOS) }
    var overlay by remember { mutableStateOf<ActiveOverlay>(ActiveOverlay.None) }
    var isGridScrolled by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val requiredPermissions = remember { buildRequiredPermissions() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.updatePermissionLevel(evaluatePermissionLevel(context))
    }

    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, context.getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
            viewModel.clearSelection()
            viewModel.refreshLibrary()
            overlay = ActiveOverlay.None
        } else {
            Toast.makeText(context, context.getString(R.string.delete_failed), Toast.LENGTH_SHORT).show()
        }
        pendingDeleteItem = null
    }

    LaunchedEffect(Unit) {
        val currentLevel = evaluatePermissionLevel(context)
        viewModel.updatePermissionLevel(currentLevel)
        if (currentLevel == PermissionAccessLevel.DENIED) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // Open external ACTION_VIEW URI directly into matching viewer/player
    LaunchedEffect(externalOpenItem) {
        val ext = externalOpenItem ?: return@LaunchedEffect
        when (ext.mediaType) {
            MediaType.PHOTO -> overlay = ActiveOverlay.PhotoViewer(ext)
            MediaType.VIDEO -> overlay = ActiveOverlay.VideoPlayer(ext)
            MediaType.AUDIO -> {
                currentTab = AppTab.AUDIO
                viewModel.playOrToggleAudio(ext)
            }
        }
        viewModel.consumeExternalOpenItem()
    }

    GalleryXTheme(settings = settings) {
        when (val active = overlay) {
            is ActiveOverlay.PhotoViewer -> {
                PhotoViewerScreen(
                    photos = filteredPhotos,
                    initialPhoto = active.item,
                    loadExif = viewModel::loadExif,
                    onBack = { overlay = ActiveOverlay.None },
                    onEdit = { overlay = ActiveOverlay.PhotoEditor(it) },
                    onShare = viewModel::shareItem,
                    onDelete = { target ->
                        pendingDeleteItem = target
                        viewModel.deleteItem(
                            item = target,
                            onNeedConfirmation = { sender ->
                                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            },
                            onCompleted = { ok ->
                                Toast.makeText(
                                    context,
                                    context.getString(if (ok) R.string.delete_success else R.string.delete_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (ok) overlay = ActiveOverlay.None
                            }
                        )
                    }
                )
            }
            is ActiveOverlay.VideoPlayer -> {
                VideoPlayerScreen(
                    item = active.item,
                    onBack = { overlay = ActiveOverlay.None },
                    onEdit = { overlay = ActiveOverlay.VideoEditor(it) },
                    onShare = viewModel::shareItem,
                    onDelete = { target ->
                        pendingDeleteItem = target
                        viewModel.deleteItem(
                            item = target,
                            onNeedConfirmation = { sender ->
                                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            },
                            onCompleted = { ok ->
                                Toast.makeText(
                                    context,
                                    context.getString(if (ok) R.string.delete_success else R.string.delete_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (ok) overlay = ActiveOverlay.None
                            }
                        )
                    }
                )
            }
            is ActiveOverlay.PhotoEditor -> {
                PhotoEditorScreen(
                    item = active.item,
                    exportState = exportState,
                    onExportPhoto = viewModel::exportPhoto,
                    onDismissExport = viewModel::resetExportState,
                    onBack = { overlay = ActiveOverlay.None }
                )
            }
            is ActiveOverlay.VideoEditor -> {
                VideoEditorScreen(
                    item = active.item,
                    exportState = exportState,
                    onExportVideo = viewModel::exportVideo,
                    onExtractAudio = { item, start, end, fmt ->
                        viewModel.extractAudioFromVideoOrConvertAudio(
                            item = item,
                            trimStartMs = start,
                            trimEndMs = end,
                            fadeIn = false,
                            fadeOut = false,
                            targetFormat = fmt
                        )
                    },
                    onDismissExport = viewModel::resetExportState,
                    onBack = { overlay = ActiveOverlay.None }
                )
            }
            is ActiveOverlay.AudioEditor -> {
                AudioEditorScreen(
                    item = active.item,
                    exportState = exportState,
                    onConvertAudio = { item, start, end, fIn, fOut, fmt ->
                        viewModel.extractAudioFromVideoOrConvertAudio(
                            item = item,
                            trimStartMs = start,
                            trimEndMs = end,
                            fadeIn = fIn,
                            fadeOut = fOut,
                            targetFormat = fmt
                        )
                    },
                    onDismissExport = viewModel::resetExportState,
                    onBack = { overlay = ActiveOverlay.None }
                )
            }
            ActiveOverlay.None -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 700.dp

                    val navItems = listOf(
                        Triple(AppTab.PHOTOS, R.string.nav_photos, Pair(AppIcons.PhotosFilled, AppIcons.PhotosOutlined)),
                        Triple(AppTab.VIDEOS, R.string.nav_videos, Pair(AppIcons.VideosFilled, AppIcons.VideosOutlined)),
                        Triple(AppTab.AUDIO, R.string.nav_audio, Pair(AppIcons.AudioFilled, AppIcons.AudioOutlined)),
                        Triple(AppTab.SETTINGS, R.string.nav_settings, Pair(AppIcons.SettingsFilled, AppIcons.SettingsOutlined))
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useRail) {
                            NavigationRail(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .windowInsetsPadding(WindowInsets.safeDrawing),
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                navItems.forEach { (tab, labelRes, icons) ->
                                    val selected = currentTab == tab
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            currentTab = tab
                                            isGridScrolled = false
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) icons.first else icons.second,
                                                contentDescription = stringResource(labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(labelRes)) }
                                    )
                                }
                            }
                        }

                        Scaffold(
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.background,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                Column {
                                    if (audioPlayerState.currentTrack != null && currentTab != AppTab.SETTINGS) {
                                        PersistentAudioMiniPlayer(
                                            state = audioPlayerState,
                                            onTogglePlayPause = viewModel::toggleAudioPlayPause,
                                            onSeek = viewModel::seekAudioTo,
                                            onToggleMute = viewModel::toggleAudioMute,
                                            onToggleLoop = viewModel::toggleAudioLoop,
                                            onEditCurrent = { overlay = ActiveOverlay.AudioEditor(it) },
                                            onClose = viewModel::stopAudioMiniPlayer,
                                            modifier = if (useRail) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier
                                        )
                                    }
                                    if (!useRail) {
                                        NavigationBar(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            windowInsets = WindowInsets.navigationBars,
                                            modifier = Modifier.testTag("bottom_nav_bar")
                                        ) {
                                            navItems.forEach { (tab, labelRes, icons) ->
                                                val selected = currentTab == tab
                                                NavigationBarItem(
                                                    selected = selected,
                                                    onClick = {
                                                        currentTab = tab
                                                        isGridScrolled = false
                                                    },
                                                    icon = {
                                                        Icon(
                                                            imageVector = if (selected) icons.first else icons.second,
                                                            contentDescription = stringResource(labelRes)
                                                        )
                                                    },
                                                    label = { Text(stringResource(labelRes)) },
                                                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .statusBarsPadding()
                            ) {
                                val activeTabItems = when (currentTab) {
                                    AppTab.PHOTOS -> filteredPhotos
                                    AppTab.VIDEOS -> filteredVideos
                                    AppTab.AUDIO -> filteredAudio
                                    AppTab.SETTINGS -> emptyList()
                                }
                                val isSelectionMode = selectedIds.isNotEmpty() && currentTab != AppTab.SETTINGS
                                BackHandler(enabled = isSelectionMode) {
                                    viewModel.clearSelection()
                                }

                                // Floating Google Photos-style Search Bar or Multi-Select Top Bar (hidden on Settings tab)
                                AnimatedVisibility(visible = currentTab != AppTab.SETTINGS) {
                                    val hintRes = when (currentTab) {
                                        AppTab.PHOTOS -> R.string.search_photos_hint
                                        AppTab.VIDEOS -> R.string.search_videos_hint
                                        AppTab.AUDIO -> R.string.search_audio_hint
                                        AppTab.SETTINGS -> R.string.search_photos_hint
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = 16.dp,
                                                vertical = if (isGridScrolled) 6.dp else 10.dp
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelectionMode) {
                                            val allSelected = activeTabItems.isNotEmpty() &&
                                                activeTabItems.all { it.id in selectedIds }
                                            SelectionTopBar(
                                                breakdown = selectionBreakdown,
                                                allSelected = allSelected,
                                                onClearSelection = viewModel::clearSelection,
                                                onToggleSelectAll = { viewModel.toggleSelectAll(activeTabItems) },
                                                onShareSelected = viewModel::shareSelectedItems,
                                                onDeleteSelected = { showBatchDeleteConfirm = true }
                                            )
                                        } else {
                                            FloatingSearchBar(
                                                query = searchQuery,
                                                onQueryChange = viewModel::setSearchQuery,
                                                placeholder = stringResource(hintRes)
                                            )
                                        }
                                    }
                                }

                                if (isScanning && currentTab != AppTab.SETTINGS) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 2.dp)
                                    )
                                }

                                if (permissionLevel == PermissionAccessLevel.PARTIAL_VISUAL && currentTab != AppTab.SETTINGS) {
                                    PartialAccessBanner(
                                        onManageClick = { permissionLauncher.launch(requiredPermissions) }
                                    )
                                }

                                if (permissionLevel == PermissionAccessLevel.DENIED && currentTab != AppTab.SETTINGS) {
                                    PermissionGateCard(
                                        onGrantClick = { permissionLauncher.launch(requiredPermissions) },
                                        onOpenAppSettings = {
                                            val intent = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", context.packageName, null)
                                            )
                                            context.startActivity(intent)
                                        }
                                    )
                                } else {
                                    when (currentTab) {
                                        AppTab.PHOTOS -> PhotosScreen(
                                            sections = photoSections,
                                            folders = photoFolders,
                                            selectedFolder = selectedPhotoFolder,
                                            gridColumns = settings.gridColumns,
                                            onSelectFolder = viewModel::selectPhotoFolder,
                                            onPhotoClick = { overlay = ActiveOverlay.PhotoViewer(it) },
                                            onScrollStateChanged = { isGridScrolled = it },
                                            selectedIds = selectedIds,
                                            onToggleSelect = { viewModel.toggleSelection(it.id) },
                                            onToggleDateSection = viewModel::toggleSelectAll
                                        )
                                        AppTab.VIDEOS -> VideosScreen(
                                            videos = filteredVideos,
                                            folders = videoFolders,
                                            selectedFolder = selectedVideoFolder,
                                            gridColumns = settings.gridColumns,
                                            onSelectFolder = viewModel::selectVideoFolder,
                                            onVideoClick = { overlay = ActiveOverlay.VideoPlayer(it) },
                                            onScrollStateChanged = { isGridScrolled = it },
                                            selectedIds = selectedIds,
                                            onToggleSelect = { viewModel.toggleSelection(it.id) }
                                        )
                                        AppTab.AUDIO -> AudioScreen(
                                            audioItems = filteredAudio,
                                            folders = audioFolders,
                                            selectedFolder = selectedAudioFolder,
                                            playerState = audioPlayerState,
                                            onSelectFolder = viewModel::selectAudioFolder,
                                            onPlayOrToggle = viewModel::playOrToggleAudio,
                                            onEditAudio = { overlay = ActiveOverlay.AudioEditor(it) },
                                            onScrollStateChanged = { isGridScrolled = it },
                                            selectedIds = selectedIds,
                                            onToggleSelect = { viewModel.toggleSelection(it.id) }
                                        )
                                        AppTab.SETTINGS -> SettingsScreen(
                                            settings = settings,
                                            onToggleCustomTheme = viewModel::setUseCustomTheme,
                                            onPickBackgroundColor = viewModel::setBackgroundColor,
                                            onPickTextColor = viewModel::setPrimaryTextColor,
                                            onPickAccentColor = viewModel::setAccentColor,
                                            onResetColors = viewModel::resetThemeDefaults,
                                            onSetGridColumns = viewModel::setGridColumns,
                                            onSetLanguageTag = viewModel::setLanguageTag
                                        )
                                    }
                                }

                                if (showBatchDeleteConfirm) {
                                    DeleteMultipleConfirmDialog(
                                        count = selectedIds.size,
                                        onConfirm = {
                                            showBatchDeleteConfirm = false
                                            viewModel.deleteAllSelectedItems(
                                                onNeedConfirmation = { sender ->
                                                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                                },
                                                onCompleted = { ok ->
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(if (ok) R.string.delete_success else R.string.delete_failed),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            )
                                        },
                                        onDismiss = { showBatchDeleteConfirm = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildRequiredPermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}

private fun evaluatePermissionLevel(context: android.content.Context): PermissionAccessLevel {
    fun has(perm: String) = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            val fullVisual = has(Manifest.permission.READ_MEDIA_IMAGES) && has(Manifest.permission.READ_MEDIA_VIDEO)
            val audio = has(Manifest.permission.READ_MEDIA_AUDIO)
            val partial = has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            when {
                fullVisual || (has(Manifest.permission.READ_MEDIA_IMAGES) || has(Manifest.permission.READ_MEDIA_VIDEO)) && !partial -> PermissionAccessLevel.GRANTED
                partial -> PermissionAccessLevel.PARTIAL_VISUAL
                audio -> PermissionAccessLevel.PARTIAL_VISUAL
                else -> PermissionAccessLevel.DENIED
            }
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            if (has(Manifest.permission.READ_MEDIA_IMAGES) ||
                has(Manifest.permission.READ_MEDIA_VIDEO) ||
                has(Manifest.permission.READ_MEDIA_AUDIO)
            ) {
                PermissionAccessLevel.GRANTED
            } else {
                PermissionAccessLevel.DENIED
            }
        }
        else -> {
            if (has(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                PermissionAccessLevel.GRANTED
            } else {
                PermissionAccessLevel.DENIED
            }
        }
    }
}
