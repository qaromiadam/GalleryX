package com.Gallery.X.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    // Navigation Icons
    val PhotosFilled: ImageVector = Icons.Filled.PhotoLibrary
    val PhotosOutlined: ImageVector = Icons.Outlined.PhotoLibrary
    val VideosFilled: ImageVector = Icons.Filled.VideoLibrary
    val VideosOutlined: ImageVector = Icons.Outlined.VideoLibrary
    val AudioFilled: ImageVector = Icons.Filled.AudioFile
    val AudioOutlined: ImageVector = Icons.Outlined.AudioFile
    val SettingsFilled: ImageVector = Icons.Filled.Settings
    val SettingsOutlined: ImageVector = Icons.Outlined.Settings

    // Search & Navigation Controls
    val Search: ImageVector = Icons.Filled.Search
    val Clear: ImageVector = Icons.Filled.Close
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Folder: ImageVector = Icons.Filled.Folder
    val Collections: ImageVector = Icons.Filled.Collections

    // Viewer & Media Actions
    val Share: ImageVector = Icons.Outlined.Share
    val Delete: ImageVector = Icons.Outlined.Delete
    val DeleteFilled: ImageVector = Icons.Filled.Delete
    val Edit: ImageVector = Icons.Filled.Edit
    val EditOutlined: ImageVector = Icons.Outlined.Edit
    val Info: ImageVector = Icons.Outlined.Info
    val InfoFilled: ImageVector = Icons.Filled.Info
    val Save: ImageVector = Icons.Filled.Save
    val Check: ImageVector = Icons.Filled.Check
    val Reset: ImageVector = Icons.Filled.Refresh

    // Playback Icons
    val Play: ImageVector = Icons.Filled.PlayArrow
    val Pause: ImageVector = Icons.Filled.Pause
    val VideoBadge: ImageVector = Icons.Filled.Videocam
    val VolumeOn: ImageVector = Icons.AutoMirrored.Outlined.VolumeUp
    val VolumeOff: ImageVector = Icons.AutoMirrored.Outlined.VolumeOff
    val LoopOn: ImageVector = Icons.Filled.RepeatOne
    val LoopOff: ImageVector = Icons.Filled.Repeat
    val MusicNote: ImageVector = Icons.Filled.MusicNote
    val Waveform: ImageVector = Icons.Filled.GraphicEq

    // Editor Tools
    val Crop: ImageVector = Icons.Filled.Crop
    val CropOutlined: ImageVector = Icons.Outlined.Crop
    val RotateRight: ImageVector = Icons.Filled.RotateRight
    val FlipHorizontal: ImageVector = Icons.Outlined.Flip
    val Palette: ImageVector = Icons.Filled.Palette
    val Tune: ImageVector = Icons.Filled.Tune
    val Convert: ImageVector = Icons.Filled.SwapHoriz

    // Settings & Privacy
    val ColorLens: ImageVector = Icons.Filled.ColorLens
    val GridView: ImageVector = Icons.Filled.GridView
    val Language: ImageVector = Icons.Filled.Language
    val Shield: ImageVector = Icons.Filled.Shield
    val Lock: ImageVector = Icons.Filled.Lock

    // Custom Geometric Vector: Flip Vertical
    val FlipVertical: ImageVector by lazy {
        ImageVector.Builder(
            name = "FlipVertical",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
            ) {
                // Top triangle
                moveTo(12f, 3f)
                lineTo(7f, 9f)
                lineTo(17f, 9f)
                close()
                // Center dashed divider
                moveTo(4f, 11.2f)
                lineTo(20f, 11.2f)
                lineTo(20f, 12.8f)
                lineTo(4f, 12.8f)
                close()
                // Bottom triangle
                moveTo(12f, 21f)
                lineTo(17f, 15f)
                lineTo(7f, 15f)
                close()
            }
        }.build()
    }

    // Custom Geometric Vector: Studio Waveform Emblem
    val StudioWaveformEmblem: ImageVector by lazy {
        ImageVector.Builder(
            name = "StudioWaveformEmblem",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 10f); lineTo(3f, 14f)
                moveTo(7.5f, 6f); lineTo(7.5f, 18f)
                moveTo(12f, 3.5f); lineTo(12f, 20.5f)
                moveTo(16.5f, 7f); lineTo(16.5f, 17f)
                moveTo(21f, 9.5f); lineTo(21f, 14.5f)
            }
        }.build()
    }

    // Custom Geometric Vector: Brand GX Mark
    val BrandMarkGX: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrandMarkGX",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 108f,
            viewportHeight = 108f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(50.5f, 38.5f)
                lineTo(45.5f, 43.5f)
                curveTo(43.5f, 40.8f, 41.0f, 39.5f, 38.0f, 39.5f)
                curveTo(33.0f, 39.5f, 30.0f, 44.5f, 30.0f, 54.0f)
                curveTo(30.0f, 63.5f, 33.0f, 68.5f, 38.5f, 68.5f)
                curveTo(42.5f, 68.5f, 45.0f, 66.0f, 45.5f, 61.5f)
                lineTo(38.5f, 61.5f)
                lineTo(38.5f, 54.5f)
                lineTo(52.5f, 54.5f)
                lineTo(52.5f, 62.0f)
                curveTo(52.5f, 71.0f, 46.5f, 76.0f, 38.5f, 76.0f)
                curveTo(28.5f, 76.0f, 23.0f, 67.5f, 23.0f, 54.0f)
                curveTo(23.0f, 40.5f, 29.0f, 32.0f, 38.5f, 32.0f)
                curveTo(44.0f, 32.0f, 48.0f, 34.5f, 50.5f, 38.5f)
                close()

                moveTo(57.0f, 32.0f)
                lineTo(65.5f, 32.0f)
                lineTo(85.0f, 76.0f)
                lineTo(76.5f, 76.0f)
                close()

                moveTo(76.5f, 32.0f)
                lineTo(85.0f, 32.0f)
                lineTo(65.5f, 76.0f)
                lineTo(57.0f, 76.0f)
                close()
            }
        }.build()
    }
}
