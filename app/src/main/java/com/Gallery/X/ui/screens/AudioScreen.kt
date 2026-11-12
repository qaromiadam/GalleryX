package com.Gallery.X.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Gallery.X.R
import com.Gallery.X.data.MediaItem
import com.Gallery.X.ui.AudioPlayerUiState
import com.Gallery.X.ui.components.EmptyLibraryState
import com.Gallery.X.ui.components.FolderFilterChipBar
import com.Gallery.X.ui.components.formatDuration
import com.Gallery.X.ui.icons.AppIcons
import java.util.Locale

@Composable
fun AudioScreen(
    audioItems: List<MediaItem>,
    folders: List<String>,
    selectedFolder: String?,
    playerState: AudioPlayerUiState,
    onSelectFolder: (String?) -> Unit,
    onPlayOrToggle: (MediaItem) -> Unit,
    onEditAudio: (MediaItem) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (MediaItem) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
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

        if (audioItems.isEmpty()) {
            EmptyLibraryState(
                icon = AppIcons.AudioOutlined,
                title = stringResource(R.string.empty_audio_title),
                subtitle = stringResource(R.string.empty_audio_subtitle)
            )
        } else {
            val bottomPadding = if (playerState.currentTrack != null) 188.dp else 108.dp
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("audio_lazy_list")
            ) {
                items(
                    items = audioItems,
                    key = { it.id },
                    contentType = { "audio_row" }
                ) { audio ->
                    val isCurrent = playerState.currentTrack?.uri == audio.uri
                    val isSelectionMode = selectedIds.isNotEmpty()
                    val isSelected = audio.id in selectedIds
                    AudioRowCard(
                        item = audio,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && playerState.isPlaying,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onPlayOrToggle = onPlayOrToggle,
                        onToggleSelect = onToggleSelect,
                        onEdit = onEditAudio
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioRowCard(
    item: MediaItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onPlayOrToggle: (MediaItem) -> Unit,
    onToggleSelect: (MediaItem) -> Unit,
    onEdit: (MediaItem) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect(item) else onPlayOrToggle(item)
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelect(item)
                }
            )
            .testTag("audio_item_${item.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected || isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected || isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isSelected -> AppIcons.Check
                        isPlaying -> AppIcons.Pause
                        isCurrent -> AppIcons.Play
                        else -> AppIcons.StudioWaveformEmblem
                    },
                    contentDescription = stringResource(R.string.cd_waveform),
                    tint = if (isSelected || isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = item.extension.uppercase(Locale.ROOT).ifEmpty { "AUD" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    val subtitle = item.artist.takeIf { it.isNotBlank() } ?: item.bucketName
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatDuration(item.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = { onEdit(item) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("audio_edit_button_${item.id}")
            ) {
                Icon(
                    imageVector = AppIcons.EditOutlined,
                    contentDescription = stringResource(R.string.cd_edit),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PersistentAudioMiniPlayer(
    state: AudioPlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onToggleLoop: () -> Unit,
    onEditCurrent: (MediaItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.currentTrack ?: return
    val duration = state.durationMs.coerceAtLeast(1L)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("audio_mini_player"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("mini_player_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (state.isPlaying) stringResource(R.string.cd_pause) else stringResource(R.string.cd_play),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatDuration(state.positionMs)} / ${formatDuration(duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (state.isMuted) AppIcons.VolumeOff else AppIcons.VolumeOn,
                        contentDescription = stringResource(R.string.cd_mute),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onToggleLoop,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (state.isLooping) AppIcons.LoopOn else AppIcons.LoopOff,
                        contentDescription = stringResource(R.string.cd_loop),
                        tint = if (state.isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onEditCurrent(track) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = AppIcons.EditOutlined,
                        contentDescription = stringResource(R.string.cd_edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = AppIcons.Clear,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Slider(
                value = state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )
        }
    }
}
