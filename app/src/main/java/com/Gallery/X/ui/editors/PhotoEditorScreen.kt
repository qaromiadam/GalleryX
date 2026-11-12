package com.Gallery.X.ui.editors

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.Gallery.X.R
import com.Gallery.X.data.ExportState
import com.Gallery.X.data.FormatCapabilityResolution
import com.Gallery.X.data.ImageTargetFormat
import com.Gallery.X.data.MediaItem
import com.Gallery.X.media.PhotoConverterEngine
import com.Gallery.X.media.PhotoFilterPreset
import com.Gallery.X.ui.components.ExportStatusDialog
import com.Gallery.X.ui.components.FormatCapabilityBadge
import com.Gallery.X.ui.icons.AppIcons
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class PhotoToolTab {
    TRANSFORM,
    FILTERS,
    ADJUST,
    CONVERT
}

private enum class CropHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

@Composable
fun PhotoEditorScreen(
    item: MediaItem,
    exportState: ExportState,
    onExportPhoto: (
        sourceBitmap: Bitmap,
        originalName: String,
        cropRect: RectF,
        rotationDegrees: Int,
        flipH: Boolean,
        flipV: Boolean,
        filter: PhotoFilterPreset,
        brightness: Float,
        contrast: Float,
        targetFormat: ImageTargetFormat,
        quality: Int
    ) -> Unit,
    onDismissExport: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var softwareBitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(item.uri) { mutableStateOf(true) }

    var activeTool by remember { mutableStateOf(PhotoToolTab.TRANSFORM) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    var cropEnabled by remember { mutableStateOf(false) }
    var cropLeft by remember { mutableFloatStateOf(0.08f) }
    var cropTop by remember { mutableFloatStateOf(0.08f) }
    var cropRight by remember { mutableFloatStateOf(0.92f) }
    var cropBottom by remember { mutableFloatStateOf(0.92f) }

    var selectedFilter by remember { mutableStateOf(PhotoFilterPreset.ORIGINAL) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var targetFormat by remember { mutableStateOf(ImageTargetFormat.JPEG) }
    var quality by remember { mutableIntStateOf(92) }

    LaunchedEffect(item.uri) {
        isLoading = true
        softwareBitmap = PhotoConverterEngine.loadSoftwareBitmap(context, item.uri)
        isLoading = false
    }

    val composeColorFilter = remember(selectedFilter, brightness, contrast) {
        val nativeMatrix = PhotoConverterEngine.buildColorMatrix(selectedFilter, brightness, contrast)
        ColorFilter.colorMatrix(ColorMatrix(nativeMatrix.array))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .testTag("photo_editor_screen")
    ) {
        // Top App Bar
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
                    .testTag("photo_editor_back")
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(R.string.photo_editor_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val bmp = softwareBitmap ?: return@Button
                    val rect = if (cropEnabled) {
                        RectF(cropLeft, cropTop, cropRight, cropBottom)
                    } else {
                        RectF(0f, 0f, 1f, 1f)
                    }
                    onExportPhoto(
                        bmp,
                        item.displayName,
                        rect,
                        rotationDegrees,
                        flipH,
                        flipV,
                        selectedFilter,
                        brightness,
                        contrast,
                        targetFormat,
                        quality
                    )
                },
                enabled = softwareBitmap != null && exportState !is ExportState.Processing,
                modifier = Modifier.testTag("photo_editor_save")
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

        // Interactive Canvas Preview + Drag-Handle Crop Overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = softwareBitmap
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else if (bmp != null) {
                var boxSize by remember { mutableStateOf(IntSize.Zero) }
                var activeHandle by remember { mutableStateOf(CropHandle.NONE) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { boxSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        colorFilter = composeColorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = rotationDegrees.toFloat()
                                scaleX = if (flipH) -1f else 1f
                                scaleY = if (flipV) -1f else 1f
                            }
                    )

                    if (cropEnabled && boxSize.width > 0 && boxSize.height > 0) {
                        val accentColor = MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val w = boxSize.width.toFloat()
                                            val h = boxSize.height.toFloat()
                                            val pts = listOf(
                                                CropHandle.TOP_LEFT to Offset(cropLeft * w, cropTop * h),
                                                CropHandle.TOP_RIGHT to Offset(cropRight * w, cropTop * h),
                                                CropHandle.BOTTOM_LEFT to Offset(cropLeft * w, cropBottom * h),
                                                CropHandle.BOTTOM_RIGHT to Offset(cropRight * w, cropBottom * h)
                                            )
                                            activeHandle = pts.minByOrNull { (_, pt) ->
                                                hypot(pt.x - start.x, pt.y - start.y)
                                            }?.takeIf { (_, pt) ->
                                                hypot(pt.x - start.x, pt.y - start.y) < 120f
                                            }?.first ?: CropHandle.NONE
                                        },
                                        onDragEnd = { activeHandle = CropHandle.NONE },
                                        onDragCancel = { activeHandle = CropHandle.NONE }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / boxSize.width.toFloat()
                                        val dy = dragAmount.y / boxSize.height.toFloat()
                                        val minGap = 0.15f
                                        when (activeHandle) {
                                            CropHandle.TOP_LEFT -> {
                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minGap)
                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minGap)
                                            }
                                            CropHandle.TOP_RIGHT -> {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + minGap, 1f)
                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minGap)
                                            }
                                            CropHandle.BOTTOM_LEFT -> {
                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minGap)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + minGap, 1f)
                                            }
                                            CropHandle.BOTTOM_RIGHT -> {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + minGap, 1f)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + minGap, 1f)
                                            }
                                            CropHandle.NONE -> Unit
                                        }
                                    }
                                }
                        ) {
                            val l = cropLeft * size.width
                            val t = cropTop * size.height
                            val r = cropRight * size.width
                            val b = cropBottom * size.height
                            val w = r - l
                            val h = b - t

                            // Dim outer crop region
                            drawRect(Color.Black.copy(alpha = 0.55f), Offset.Zero, Size(size.width, t))
                            drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, b), Size(size.width, size.height - b))
                            drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, t), Size(l, h))
                            drawRect(Color.Black.copy(alpha = 0.55f), Offset(r, t), Size(size.width - r, h))

                            // Bounding border + thirds grid
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(l, t),
                                size = Size(w, h),
                                style = Stroke(width = 3f)
                            )
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(l + w / 3f, t), Offset(l + w / 3f, b), 1.5f)
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(l + 2f * w / 3f, t), Offset(l + 2f * w / 3f, b), 1.5f)
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(l, t + h / 3f), Offset(r, t + h / 3f), 1.5f)
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(l, t + 2f * h / 3f), Offset(r, t + 2f * h / 3f), 1.5f)

                            // Corner drag handles
                            val corners = listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))
                            corners.forEach { corner ->
                                drawCircle(color = accentColor, radius = 16f, center = corner)
                                drawCircle(color = Color.White, radius = 8f, center = corner)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.playback_error),
                    color = Color.White
                )
            }
        }

        // Studio Tool Dock
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp)
            ) {
                // Active Tool Controls
                when (activeTool) {
                    PhotoToolTab.TRANSFORM -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            EditorIconAction(
                                icon = AppIcons.Crop,
                                label = stringResource(R.string.editor_tool_crop),
                                active = cropEnabled,
                                testTag = "photo_crop_toggle",
                                onClick = { cropEnabled = !cropEnabled }
                            )
                            EditorIconAction(
                                icon = AppIcons.RotateRight,
                                label = stringResource(R.string.editor_tool_rotate),
                                active = rotationDegrees != 0,
                                testTag = "photo_rotate_button",
                                onClick = { rotationDegrees = (rotationDegrees + 90) % 360 }
                            )
                            EditorIconAction(
                                icon = AppIcons.FlipHorizontal,
                                label = stringResource(R.string.editor_tool_flip_h),
                                active = flipH,
                                testTag = "photo_flip_h_button",
                                onClick = { flipH = !flipH }
                            )
                            EditorIconAction(
                                icon = AppIcons.FlipVertical,
                                label = stringResource(R.string.editor_tool_flip_v),
                                active = flipV,
                                testTag = "photo_flip_v_button",
                                onClick = { flipV = !flipV }
                            )
                        }
                    }
                    PhotoToolTab.FILTERS -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(PhotoFilterPreset.entries) { preset ->
                                val label = when (preset) {
                                    PhotoFilterPreset.ORIGINAL -> stringResource(R.string.filter_original)
                                    PhotoFilterPreset.MONO -> stringResource(R.string.filter_mono)
                                    PhotoFilterPreset.WARM -> stringResource(R.string.filter_warm)
                                    PhotoFilterPreset.COOL -> stringResource(R.string.filter_cool)
                                    PhotoFilterPreset.VIVID -> stringResource(R.string.filter_vivid)
                                    PhotoFilterPreset.SEPIA -> stringResource(R.string.filter_sepia)
                                }
                                FilterChip(
                                    selected = selectedFilter == preset,
                                    onClick = { selectedFilter = preset },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                    PhotoToolTab.ADJUST -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.adjust_brightness), style = MaterialTheme.typography.labelLarge)
                                Text("${(brightness * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = brightness,
                                onValueChange = { brightness = it },
                                valueRange = -0.5f..0.5f
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.adjust_contrast), style = MaterialTheme.typography.labelLarge)
                                Text(String.format("%.2fx", contrast), style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = contrast,
                                onValueChange = { contrast = it },
                                valueRange = 0.5f..1.6f
                            )
                        }
                    }
                    PhotoToolTab.CONVERT -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.convert_target_format),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                FormatCapabilityBadge(
                                    capability = FormatCapabilityResolution(
                                        mode = targetFormat.capability,
                                        actualExtension = targetFormat.extension,
                                        actualMimeType = targetFormat.mimeType,
                                        fallbackLabel = targetFormat.label
                                    )
                                )
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ImageTargetFormat.entries) { fmt ->
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
                            if (targetFormat.supportsQuality) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.convert_quality), style = MaterialTheme.typography.labelLarge)
                                    Text("$quality%", style = MaterialTheme.typography.labelMedium)
                                }
                                Slider(
                                    value = quality.toFloat(),
                                    onValueChange = { quality = it.roundToInt() },
                                    valueRange = 20f..100f
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Tool Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ToolTabPill(
                        label = stringResource(R.string.editor_tool_crop),
                        selected = activeTool == PhotoToolTab.TRANSFORM,
                        onClick = { activeTool = PhotoToolTab.TRANSFORM }
                    )
                    ToolTabPill(
                        label = stringResource(R.string.editor_tool_filters),
                        selected = activeTool == PhotoToolTab.FILTERS,
                        onClick = { activeTool = PhotoToolTab.FILTERS }
                    )
                    ToolTabPill(
                        label = stringResource(R.string.editor_tool_adjust),
                        selected = activeTool == PhotoToolTab.ADJUST,
                        onClick = { activeTool = PhotoToolTab.ADJUST }
                    )
                    ToolTabPill(
                        label = stringResource(R.string.editor_tool_convert),
                        selected = activeTool == PhotoToolTab.CONVERT,
                        onClick = { activeTool = PhotoToolTab.CONVERT }
                    )
                }
            }
        }
    }

    ExportStatusDialog(
        exportState = exportState,
        successHeadline = stringResource(R.string.export_saved_photo),
        onDismiss = {
            val wasSuccess = exportState is ExportState.Success
            onDismissExport()
            if (wasSuccess) onBack()
        }
    )
}

@Composable
private fun EditorIconAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ToolTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
