package com.Gallery.X.ui.editors

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.Gallery.X.R
import com.Gallery.X.data.AudioTargetFormat
import com.Gallery.X.data.ExportState
import com.Gallery.X.data.MediaItem
import com.Gallery.X.data.VideoTargetFormat
import com.Gallery.X.media.MediaTranscodeEngine
import com.Gallery.X.media.VideoAspectPreset
import com.Gallery.X.media.VideoFilterPreset
import com.Gallery.X.ui.components.ExportStatusDialog
import com.Gallery.X.ui.components.FormatCapabilityBadge
import com.Gallery.X.ui.components.formatDuration
import com.Gallery.X.ui.icons.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class VideoExportMode {
    TRANSCODE_VIDEO,
    EXTRACT_AUDIO
}

@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(
    item: MediaItem,
    exportState: ExportState,
    onExportVideo: (
        item: MediaItem,
        trimStartMs: Long,
        trimEndMs: Long,
        aspectPreset: VideoAspectPreset,
        filterPreset: VideoFilterPreset,
        targetFormat: VideoTargetFormat
    ) -> Unit,
    onExtractAudio: (
        item: MediaItem,
        trimStartMs: Long,
        trimEndMs: Long,
        targetFormat: AudioTargetFormat
    ) -> Unit,
    onDismissExport: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var totalDurationMs by remember(item.uri) {
        mutableLongStateOf(item.durationMs.coerceAtLeast(1000L))
    }
    var trimStartMs by remember(item.uri) { mutableLongStateOf(0L) }
    var trimEndMs by remember(item.uri) { mutableLongStateOf(totalDurationMs) }
    var isPlaying by remember { mutableStateOf(false) }

    var exportMode by remember { mutableStateOf(VideoExportMode.TRANSCODE_VIDEO) }
    var aspectPreset by remember { mutableStateOf(VideoAspectPreset.FREE) }
    var filterPreset by remember { mutableStateOf(VideoFilterPreset.ORIGINAL) }
    var targetVideoFormat by remember { mutableStateOf(VideoTargetFormat.MP4) }
    var targetAudioFormat by remember { mutableStateOf(AudioTargetFormat.M4A) }

    val exoPlayer = remember(item.uri) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0L) {
                        totalDurationMs = d
                        if (trimEndMs <= 1000L || trimEndMs > d) {
                            trimEndMs = d
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, trimStartMs, trimEndMs, isPlaying) {
        while (isActive && isPlaying) {
            if (exoPlayer.currentPosition >= trimEndMs) {
                exoPlayer.seekTo(trimStartMs)
            }
            delay(150L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .testTag("video_editor_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_editor_back")
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(R.string.video_editor_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    exoPlayer.pause()
                    if (exportMode == VideoExportMode.TRANSCODE_VIDEO) {
                        onExportVideo(
                            item,
                            trimStartMs,
                            trimEndMs,
                            aspectPreset,
                            filterPreset,
                            targetVideoFormat
                        )
                    } else {
                        onExtractAudio(item, trimStartMs, trimEndMs, targetAudioFormat)
                    }
                },
                enabled = exportState !is ExportState.Processing,
                modifier = Modifier.testTag("video_editor_save")
            ) {
                Icon(
                    imageVector = AppIcons.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_save))
            }
        }

        // Video Preview
        Box(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
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
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.currentPosition < trimStartMs || exoPlayer.currentPosition >= trimEndMs) {
                                exoPlayer.seekTo(trimStartMs)
                            }
                            exoPlayer.play()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                    contentDescription = stringResource(R.string.cd_play),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Controls Panel
        Surface(
            modifier = Modifier.weight(0.55f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Toggle: Video Export vs Extract Audio
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = exportMode == VideoExportMode.TRANSCODE_VIDEO,
                        onClick = { exportMode = VideoExportMode.TRANSCODE_VIDEO },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("mode_transcode_video")
                    ) {
                        Text(stringResource(R.string.video_mode_transcode))
                    }
                    SegmentedButton(
                        selected = exportMode == VideoExportMode.EXTRACT_AUDIO,
                        onClick = { exportMode = VideoExportMode.EXTRACT_AUDIO },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("mode_extract_audio")
                    ) {
                        Text(stringResource(R.string.video_mode_extract_audio))
                    }
                }

                // Dual-Handle Trim Range Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.video_trim_range), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${formatDuration(trimStartMs)} - ${formatDuration(trimEndMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                RangeSlider(
                    value = trimStartMs.toFloat()..trimEndMs.toFloat().coerceAtLeast(trimStartMs.toFloat() + 100f),
                    onValueChange = { range ->
                        trimStartMs = range.start.toLong()
                        trimEndMs = range.endInclusive.toLong().coerceAtLeast(trimStartMs + 250L)
                        exoPlayer.seekTo(trimStartMs)
                    },
                    valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1000f),
                    modifier = Modifier.testTag("video_trim_slider")
                )

                if (exportMode == VideoExportMode.TRANSCODE_VIDEO) {
                    Text(stringResource(R.string.video_aspect_preset), style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        contentPadding = PaddingValues(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(VideoAspectPreset.entries) { preset ->
                            val label = when (preset) {
                                VideoAspectPreset.FREE -> stringResource(R.string.aspect_free)
                                VideoAspectPreset.SQUARE -> stringResource(R.string.aspect_square)
                                VideoAspectPreset.LANDSCAPE_16_9 -> stringResource(R.string.aspect_landscape)
                                VideoAspectPreset.PORTRAIT_9_16 -> stringResource(R.string.aspect_portrait)
                                VideoAspectPreset.CLASSIC_4_3 -> stringResource(R.string.aspect_classic)
                            }
                            FilterChip(
                                selected = aspectPreset == preset,
                                onClick = { aspectPreset = preset },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    Text(stringResource(R.string.editor_tool_filters), style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VideoFilterPreset.entries) { preset ->
                            val label = when (preset) {
                                VideoFilterPreset.ORIGINAL -> stringResource(R.string.filter_original)
                                VideoFilterPreset.MONO -> stringResource(R.string.filter_mono)
                                VideoFilterPreset.WARM -> stringResource(R.string.filter_warm)
                                VideoFilterPreset.COOL -> stringResource(R.string.filter_cool)
                                VideoFilterPreset.VIVID -> stringResource(R.string.filter_vivid)
                            }
                            FilterChip(
                                selected = filterPreset == preset,
                                onClick = { filterPreset = preset },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    val vidCap = remember(targetVideoFormat) {
                        MediaTranscodeEngine.resolveVideoCapability(targetVideoFormat)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.convert_target_format), style = MaterialTheme.typography.titleSmall)
                        FormatCapabilityBadge(capability = vidCap)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VideoTargetFormat.entries) { fmt ->
                            FilterChip(
                                selected = targetVideoFormat == fmt,
                                onClick = { targetVideoFormat = fmt },
                                label = { Text(fmt.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                } else {
                    val audCap = remember(targetAudioFormat) {
                        MediaTranscodeEngine.resolveAudioCapability(targetAudioFormat)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.convert_target_format), style = MaterialTheme.typography.titleSmall)
                        FormatCapabilityBadge(capability = audCap)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AudioTargetFormat.entries) { fmt ->
                            FilterChip(
                                selected = targetAudioFormat == fmt,
                                onClick = { targetAudioFormat = fmt },
                                label = { Text(fmt.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    val successTitle = if (exportMode == VideoExportMode.TRANSCODE_VIDEO) {
        stringResource(R.string.export_saved_video)
    } else {
        stringResource(R.string.export_saved_audio)
    }
    ExportStatusDialog(
        exportState = exportState,
        successHeadline = successTitle,
        onDismiss = {
            val wasSuccess = exportState is ExportState.Success
            onDismissExport()
            if (wasSuccess) onBack()
        }
    )
}
