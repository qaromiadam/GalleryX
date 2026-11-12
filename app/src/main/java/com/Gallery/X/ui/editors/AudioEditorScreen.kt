package com.Gallery.X.ui.editors

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.Gallery.X.R
import com.Gallery.X.data.AudioTargetFormat
import com.Gallery.X.data.ExportState
import com.Gallery.X.data.MediaItem
import com.Gallery.X.media.MediaTranscodeEngine
import com.Gallery.X.ui.components.ExportStatusDialog
import com.Gallery.X.ui.components.FormatCapabilityBadge
import com.Gallery.X.ui.components.formatDuration
import com.Gallery.X.ui.icons.AppIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sin

@OptIn(UnstableApi::class)
@Composable
fun AudioEditorScreen(
    item: MediaItem,
    exportState: ExportState,
    onConvertAudio: (
        item: MediaItem,
        trimStartMs: Long,
        trimEndMs: Long,
        fadeIn: Boolean,
        fadeOut: Boolean,
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
    var currentPositionMs by remember(item.uri) { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    var fadeIn by remember { mutableStateOf(false) }
    var fadeOut by remember { mutableStateOf(false) }
    var targetFormat by remember { mutableStateOf(AudioTargetFormat.M4A) }

    // Deterministic visual amplitude profile derived from track ID & duration
    val barAmplitudes = remember(item.id, item.sizeBytes) {
        val seed = (item.id xor item.sizeBytes).toInt()
        List(56) { idx ->
            val wave = abs(sin((idx * 0.42) + (seed % 17)))
            (0.20f + (wave.toFloat() * 0.78f)).coerceIn(0.18f, 1.0f)
        }
    }

    val exoPlayer = remember(item.uri) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            prepare()
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

    LaunchedEffect(exoPlayer, isPlaying, trimStartMs, trimEndMs) {
        while (isActive && isPlaying) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            currentPositionMs = pos
            if (pos >= trimEndMs) {
                exoPlayer.seekTo(trimStartMs)
            }
            delay(100L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("audio_editor_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("audio_editor_back")
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.audio_editor_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = {
                    exoPlayer.pause()
                    onConvertAudio(item, trimStartMs, trimEndMs, fadeIn, fadeOut, targetFormat)
                },
                enabled = exportState !is ExportState.Processing,
                modifier = Modifier.testTag("audio_editor_save")
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

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Visual Amplitude Waveform & Play Preview Card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val accent = MaterialTheme.colorScheme.primary
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    val cursorColor = MaterialTheme.colorScheme.onSurface

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        val count = barAmplitudes.size
                        val slotW = size.width / count
                        val barW = slotW * 0.58f
                        val startFrac = trimStartMs.toFloat() / totalDurationMs.toFloat().coerceAtLeast(1f)
                        val endFrac = trimEndMs.toFloat() / totalDurationMs.toFloat().coerceAtLeast(1f)
                        val playFrac = currentPositionMs.toFloat() / totalDurationMs.toFloat().coerceAtLeast(1f)

                        barAmplitudes.forEachIndexed { idx, amp ->
                            val frac = idx.toFloat() / count.toFloat()
                            val inSelection = frac in startFrac..endFrac
                            val barH = (size.height * amp).coerceAtLeast(10f)
                            val top = (size.height - barH) / 2f
                            drawRoundRect(
                                color = if (inSelection) accent else muted,
                                topLeft = Offset(idx * slotW + (slotW - barW) / 2f, top),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }

                        // Playback cursor
                        val cx = (playFrac * size.width).coerceIn(0f, size.width)
                        drawLine(
                            color = cursorColor,
                            start = Offset(cx, 4f),
                            end = Offset(cx, size.height - 4f),
                            strokeWidth = 3f
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${stringResource(R.string.audio_trim_start)}: ${formatDuration(trimStartMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        if (exoPlayer.currentPosition < trimStartMs || exoPlayer.currentPosition >= trimEndMs) {
                                            exoPlayer.seekTo(trimStartMs)
                                        }
                                        exoPlayer.play()
                                    }
                                }
                                .testTag("audio_editor_play_toggle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                                contentDescription = stringResource(R.string.cd_play),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(
                            text = "${stringResource(R.string.audio_trim_end)}: ${formatDuration(trimEndMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    RangeSlider(
                        value = trimStartMs.toFloat()..trimEndMs.toFloat().coerceAtLeast(trimStartMs.toFloat() + 100f),
                        onValueChange = { range ->
                            trimStartMs = range.start.toLong()
                            trimEndMs = range.endInclusive.toLong().coerceAtLeast(trimStartMs + 250L)
                            currentPositionMs = trimStartMs
                            exoPlayer.seekTo(trimStartMs)
                        },
                        valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1000f),
                        modifier = Modifier.testTag("audio_trim_slider")
                    )
                }
            }

            // Volume Envelope Toggles
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.audio_fade_in), style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = fadeIn,
                            onCheckedChange = { fadeIn = it },
                            modifier = Modifier.testTag("audio_fade_in_switch")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.audio_fade_out), style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = fadeOut,
                            onCheckedChange = { fadeOut = it },
                            modifier = Modifier.testTag("audio_fade_out_switch")
                        )
                    }
                }
            }

            // Universal Audio-to-Audio Format Converter
            val audCap = remember(targetFormat) {
                MediaTranscodeEngine.resolveAudioCapability(targetFormat)
            }
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                selected = targetFormat == fmt,
                                onClick = { targetFormat = fmt },
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

    ExportStatusDialog(
        exportState = exportState,
        successHeadline = stringResource(R.string.export_saved_audio),
        onDismiss = {
            val wasSuccess = exportState is ExportState.Success
            onDismissExport()
            if (wasSuccess) onBack()
        }
    )
}
