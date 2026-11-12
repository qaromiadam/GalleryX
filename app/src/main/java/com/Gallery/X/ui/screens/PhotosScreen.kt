package com.Gallery.X.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.Gallery.X.R
import com.Gallery.X.data.ExifDetails
import com.Gallery.X.data.MediaItem
import com.Gallery.X.data.PhotoDateSection
import com.Gallery.X.ui.components.DeleteConfirmDialog
import com.Gallery.X.ui.components.EmptyLibraryState
import com.Gallery.X.ui.components.FolderFilterChipBar
import com.Gallery.X.ui.components.formatFileSize
import com.Gallery.X.ui.icons.AppIcons
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotosScreen(
    sections: List<PhotoDateSection>,
    folders: List<String>,
    selectedFolder: String?,
    gridColumns: Int,
    onSelectFolder: (String?) -> Unit,
    onPhotoClick: (MediaItem) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (MediaItem) -> Unit = {},
    onToggleDateSection: (List<MediaItem>) -> Unit = {}
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

    val isSelectionMode = selectedIds.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        FolderFilterChipBar(
            folders = folders,
            selectedFolder = selectedFolder,
            onSelectFolder = onSelectFolder,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (sections.isEmpty()) {
            EmptyLibraryState(
                icon = AppIcons.PhotosOutlined,
                title = stringResource(R.string.empty_photos_title),
                subtitle = stringResource(R.string.empty_photos_subtitle)
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
                    .testTag("photos_lazy_grid")
            ) {
                sections.forEach { section ->
                    item(
                        key = "header_${section.dateLabel}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "date_header"
                    ) {
                        val allSectionSelected = remember(isSelectionMode, section.items, selectedIds) {
                            isSelectionMode && section.items.isNotEmpty() && section.items.all { it.id in selectedIds }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = if (isSelectionMode) 2.dp else 10.dp,
                                    end = 10.dp,
                                    top = if (isSelectionMode) 2.dp else 10.dp,
                                    bottom = if (isSelectionMode) 2.dp else 10.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clip(CircleShape)
                                            .clickable(
                                                role = Role.Checkbox,
                                                onClickLabel = stringResource(R.string.cd_select_date_section, section.dateLabel),
                                                onClick = { onToggleDateSection(section.items) }
                                            )
                                            .testTag("date_select_circle_${section.dateLabel}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (allSectionSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                                .border(
                                                    width = 1.6.dp,
                                                    color = if (allSectionSelected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (allSectionSelected) {
                                                Icon(
                                                    imageVector = AppIcons.Check,
                                                    contentDescription = stringResource(R.string.cd_select_date_section, section.dateLabel),
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = section.dateLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${section.items.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(
                        items = section.items,
                        key = { it.id },
                        contentType = { "photo_tile" }
                    ) { photo ->
                        val isSelected = photo.id in selectedIds
                        PhotoGridTile(
                            item = photo,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onPhotoClick = onPhotoClick,
                            onToggleSelect = onToggleSelect
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridTile(
    item: MediaItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onPhotoClick: (MediaItem) -> Unit,
    onToggleSelect: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val imageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(256)
            .allowHardware(true)
            .build()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect(item) else onPhotoClick(item)
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelect(item)
                }
            )
            .testTag("photo_tile_${item.id}")
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = if (isSelected) 0.90f else 1f
                    scaleX = s
                    scaleY = s
                }
        )

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.45f)
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        val badge = when {
            item.isRawImage -> item.extension.uppercase(Locale.ROOT)
            item.isAnimatedImage -> "GIF"
            item.extension.equals("heic", true) || item.extension.equals("heif", true) -> "HEIC"
            item.extension.equals("avif", true) -> "AVIF"
            else -> null
        }

        if (badge != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.68f)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photos: List<MediaItem>,
    initialPhoto: MediaItem,
    loadExif: suspend (MediaItem) -> ExifDetails,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit
) {
    BackHandler(onBack = onBack)

    val list = remember(photos, initialPhoto) {
        photos.ifEmpty { listOf(initialPhoto) }
    }
    val startIndex = remember(list, initialPhoto) {
        list.indexOfFirst { it.uri == initialPhoto.uri }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { list.size })
    val currentPhoto = list.getOrElse(pagerState.currentPage) { initialPhoto }

    var controlsVisible by remember { mutableStateOf(true) }
    var showExifSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var exifDetails by remember(currentPhoto.uri) { mutableStateOf<ExifDetails?>(null) }

    LaunchedEffect(showExifSheet, currentPhoto.uri) {
        if (showExifSheet) {
            exifDetails = loadExif(currentPhoto)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("photo_viewer_screen")
    ) {
        HorizontalPager(
            state = pagerState,
            key = { index -> list[index].uri.toString() },
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val photo = list[pageIndex]
            ZoomablePhotoPage(
                item = photo,
                onTap = { controlsVisible = !controlsVisible }
            )
        }

        // Top Bar Overlay
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
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("viewer_back_button")
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
                        text = currentPhoto.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentPhoto.bucketName} - ${currentPhoto.dateGroupLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = { showExifSheet = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("viewer_info_button")
                ) {
                    Icon(
                        imageVector = AppIcons.Info,
                        contentDescription = stringResource(R.string.cd_info),
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom Action Bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewerActionButton(
                    icon = AppIcons.Share,
                    label = stringResource(R.string.action_share),
                    testTag = "viewer_share_button",
                    onClick = { onShare(currentPhoto) }
                )
                ViewerActionButton(
                    icon = AppIcons.Edit,
                    label = stringResource(R.string.action_edit),
                    testTag = "viewer_edit_button",
                    onClick = { onEdit(currentPhoto) }
                )
                ViewerActionButton(
                    icon = AppIcons.Delete,
                    label = stringResource(R.string.action_delete),
                    testTag = "viewer_delete_button",
                    onClick = { showDeleteConfirm = true }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            fileName = currentPhoto.displayName,
            onConfirm = {
                showDeleteConfirm = false
                onDelete(currentPhoto)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showExifSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExifSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val details = exifDetails
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.info_sheet_title),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                MetadataRow(stringResource(R.string.info_filename), currentPhoto.displayName)
                MetadataRow(stringResource(R.string.info_folder), currentPhoto.bucketName)
                MetadataRow(
                    stringResource(R.string.info_format),
                    "${currentPhoto.extension.uppercase(Locale.ROOT)} (${currentPhoto.mimeType})"
                )
                val w = details?.width?.takeIf { it > 0 } ?: currentPhoto.width
                val h = details?.height?.takeIf { it > 0 } ?: currentPhoto.height
                MetadataRow(
                    stringResource(R.string.info_dimensions),
                    if (w > 0 && h > 0) "$w x $h px" else stringResource(R.string.info_unknown)
                )
                MetadataRow(stringResource(R.string.info_size), formatFileSize(currentPhoto.sizeBytes))
                MetadataRow(
                    stringResource(R.string.info_date),
                    details?.dateTaken?.takeIf { it.isNotBlank() } ?: DateFormat.getDateTimeInstance()
                        .format(Date(currentPhoto.dateModifiedSec * 1000L))
                )
                if (!details?.cameraMakeModel.isNullOrBlank()) {
                    MetadataRow(stringResource(R.string.info_camera), details!!.cameraMakeModel)
                }
                val exposureIso = listOfNotNull(
                    details?.exposureTime?.takeIf { it.isNotBlank() },
                    details?.iso?.takeIf { it.isNotBlank() }
                ).joinToString("  ")
                if (exposureIso.isNotBlank()) {
                    MetadataRow(stringResource(R.string.info_exposure), exposureIso)
                }
                val focalAp = listOfNotNull(
                    details?.focalLength?.takeIf { it.isNotBlank() },
                    details?.aperture?.takeIf { it.isNotBlank() }
                ).joinToString("  ")
                if (focalAp.isNotBlank()) {
                    MetadataRow(stringResource(R.string.info_focal), focalAp)
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhotoPage(
    item: MediaItem,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(item.uri) { mutableFloatStateOf(1f) }
    var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        offset = if (nextScale > 1.02f) {
            Offset(offset.x + panChange.x, offset.y + panChange.y)
        } else {
            Offset.Zero
        }
    }

    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(2048)
            .allowHardware(true)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.uri) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = { onTap() }
                )
            }
            .transformable(state = transformState),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
private fun ViewerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}
