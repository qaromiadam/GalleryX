package com.Gallery.X.ui.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.Gallery.X.R
import com.Gallery.X.data.MediaItem
import com.Gallery.X.ui.components.DeleteConfirmDialog
import com.Gallery.X.ui.components.EmptyLibraryState
import com.Gallery.X.ui.components.FolderFilterChipBar
import com.Gallery.X.ui.components.formatDuration
import com.Gallery.X.ui.icons.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@Composable
fun VideosScreen(
    videos: List<MediaItem>,
    folders: List<String>,
    selectedFolder: String?,
    gridColumns: Int,
    onSelectFolder: (String?) -> Unit,
    onVideoClick: (MediaItem) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 24
        }
    }
    LaunchedEffect(isScrolled) {
        onScrollStateChanged(isScrolled)
    }

    Column(modifier = modifier.fillMaxSize()) {
        FolderFilterChipBar(
            folders = folders,
            selectedFolder = selectedFolder,
            onSelectFolder = onSelectFolder,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (videos.isEmpty()) {
            EmptyLibraryState(
                icon = AppIcons.VideosOutlined,
                title = stringResource(R.string.empty_videos_title),
                subtitle = stringResource(R.string.empty_videos_subtitle)
            )
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumns.coerceIn(3, 5)),
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("videos_lazy_grid")
            ) {
                items(
                    items = videos,
                    key = { it.id },
                    contentType = { "video_tile" }
                ) { video ->
                    VideoGridTile(
                        item = video,
                        onClick = { onVideoClick(video) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoGridTile(
    item: MediaItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(400)
            .allowHardware(true)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag("video_tile_${item.id}")
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle center play icon
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.56f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Play,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Format badge for uncommon containers
        val ext = item.extension.uppercase(Locale.ROOT)
        if (ext.isNotEmpty() && ext != "MP4") {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.68f)
            ) {
                Text(
                    text = ext,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        // Duration badge
        if (item.durationMs > 0L) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.72f)
            ) {
                Text(
                    text = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    item: MediaItem,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val exoPlayer = remember(item.uri) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory)
            .build()
            .apply {
                setMediaItem(Media3Item.fromUri(item.uri))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.takeIf { it > 0L } ?: durationMs
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Decoder error"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0L) durationMs = d
            delay(250L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap video canvas: toggle playback or controls cleanly
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                    controlsVisible = true
                } else {
                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(0L)
                    }
                    exoPlayer.play()
                }
            }
            .testTag("video_player_screen")
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (playbackError != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.85f)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.playback_error),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playbackError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Top Bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent))
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("video_back_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.extension.uppercase(Locale.ROOT)} - ${item.bucketName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
                IconButton(
                    onClick = { onShare(item) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = AppIcons.Share,
                        contentDescription = stringResource(R.string.cd_share),
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = {
                        exoPlayer.pause()
                        onEdit(item)
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("video_edit_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Edit,
                        contentDescription = stringResource(R.string.cd_edit),
                        tint = Color.White
                    )
                }
            }
        }

        // Center Play/Pause indicator
        AnimatedVisibility(
            visible = !isPlaying || controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(
                onClick = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0L)
                        exoPlayer.play()
                    }
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.56f))
                    .testTag("video_play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                    contentDescription = if (isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        // Bottom Controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)))
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Slider(
                    value = currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { pos ->
                        currentPosMs = pos.toLong()
                        exoPlayer.seekTo(currentPosMs)
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("video_seek_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatDuration(currentPosMs)} / ${formatDuration(durationMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) AppIcons.VolumeOff else AppIcons.VolumeOn,
                                contentDescription = if (isMuted) stringResource(R.string.cd_unmute) else stringResource(R.string.cd_mute),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                isLooping = !isLooping
                                exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("video_loop_button")
                        ) {
                            Icon(
                                imageVector = if (isLooping) AppIcons.LoopOn else AppIcons.LoopOff,
                                contentDescription = stringResource(R.string.cd_loop),
                                tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            fileName = item.displayName,
            onConfirm = {
                showDeleteConfirm = false
                exoPlayer.stop()
                onDelete(item)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
