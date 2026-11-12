package com.Gallery.X.data

import android.net.Uri
import androidx.compose.runtime.Immutable

enum class MediaType {
    PHOTO,
    VIDEO,
    AUDIO
}

@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val bucketName: String,
    val mimeType: String,
    val extension: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val dateGroupLabel: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val artist: String = "",
    val album: String = "",
    val mediaType: MediaType
) {
    val isRawImage: Boolean
        get() = extension.lowercase() in RAW_IMAGE_EXTENSIONS

    val isAnimatedImage: Boolean
        get() = extension.lowercase() in ANIMATED_IMAGE_EXTENSIONS || mimeType.equals("image/gif", ignoreCase = true)

    companion object {
        val RAW_IMAGE_EXTENSIONS = setOf(
            "dng", "cr2", "cr3", "nef", "nrw", "arw", "sr2", "srf",
            "raf", "orf", "rw2", "pef", "srw", "rwl", "3fr", "x3f"
        )

        val ANIMATED_IMAGE_EXTENSIONS = setOf("gif", "apng", "mng")

        val ALL_PHOTO_EXTENSIONS = setOf(
            "jpg", "jpeg", "jpe", "png", "webp", "bmp", "dib", "wbmp", "ico", "cur",
            "heic", "heif", "avif", "jxr", "hdp", "wdp", "gif", "apng", "mng"
        ) + RAW_IMAGE_EXTENSIONS

        val ALL_VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mp4v", "mkv", "webm", "3gp", "3g2", "3gpp", "avi", "mov", "qt",
            "ts", "mts", "m2ts", "tp", "trp", "flv", "f4v", "wmv", "asf", "ogv", "ogg",
            "mpg", "mpeg", "mpe", "mpv", "dv", "mxf", "roq", "nsv", "rm", "rmvb"
        )

        val ALL_AUDIO_EXTENSIONS = setOf(
            "mp3", "mp2", "aac", "m4a", "m4b", "m4p", "m4r", "mp4a", "ogg", "opus", "oga",
            "flac", "wav", "amr", "awb", "mid", "midi", "xmf", "rmi", "kar", "wma",
            "aiff", "aif", "aifc", "alac", "tta", "ape", "wv", "mpc", "spx", "dsf", "dff",
            "ac3", "eac3", "dts", "qcp", "shn"
        )
    }
}

@Immutable
data class PhotoDateSection(
    val dateLabel: String,
    val items: List<MediaItem>
)

@Immutable
data class SelectionBreakdown(
    val total: Int = 0,
    val photos: Int = 0,
    val videos: Int = 0,
    val audio: Int = 0
)

@Immutable
data class ExifDetails(
    val cameraMakeModel: String = "",
    val aperture: String = "",
    val exposureTime: String = "",
    val iso: String = "",
    val focalLength: String = "",
    val dateTaken: String = "",
    val width: Int = 0,
    val height: Int = 0
)

enum class PermissionAccessLevel {
    DENIED,
    PARTIAL_VISUAL,
    GRANTED
}

enum class CapabilityMode {
    NATIVE_HARDWARE,
    PURE_KOTLIN,
    SMART_FALLBACK
}

enum class ImageTargetFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val supportsQuality: Boolean,
    val capability: CapabilityMode
) {
    JPEG("JPEG", "jpg", "image/jpeg", true, CapabilityMode.NATIVE_HARDWARE),
    PNG("PNG", "png", "image/png", false, CapabilityMode.NATIVE_HARDWARE),
    WEBP_LOSSY("WebP (Lossy)", "webp", "image/webp", true, CapabilityMode.NATIVE_HARDWARE),
    WEBP_LOSSLESS("WebP (Lossless)", "webp", "image/webp", false, CapabilityMode.NATIVE_HARDWARE),
    BMP("BMP (24-bit DIB)", "bmp", "image/bmp", false, CapabilityMode.PURE_KOTLIN),
    ICO("ICO (Icon)", "ico", "image/x-icon", false, CapabilityMode.PURE_KOTLIN),
    GIF("GIF (89a)", "gif", "image/gif", false, CapabilityMode.PURE_KOTLIN)
}

enum class VideoTargetFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val preferredVideoMime: String,
    val preferredAudioMime: String
) {
    MP4("MP4 (H.264)", "mp4", "video/mp4", "video/avc", "audio/mp4a-latm"),
    MP4_HEVC("MP4 (HEVC)", "mp4", "video/mp4", "video/hevc", "audio/mp4a-latm"),
    WEBM("WebM (VP9)", "webm", "video/webm", "video/x-vnd.on2.vp9", "audio/opus"),
    MKV("MKV (Matroska)", "mkv", "video/x-matroska", "video/avc", "audio/mp4a-latm"),
    THREE_GP("3GP (Mobile)", "3gp", "video/3gpp", "video/3gpp", "audio/mp4a-latm"),
    MOV("MOV (QuickTime)", "mov", "video/quicktime", "video/avc", "audio/mp4a-latm"),
    AVI("AVI (RIFF)", "avi", "video/x-msvideo", "video/avc", "audio/mp4a-latm"),
    TS("TS (MPEG-TS)", "ts", "video/mp2t", "video/avc", "audio/mp4a-latm"),
    FLV("FLV (Flash)", "flv", "video/x-flv", "video/avc", "audio/mp4a-latm"),
    WMV("WMV (Windows Media)", "wmv", "video/x-ms-wmv", "video/avc", "audio/mp4a-latm"),
    OGV("OGV (Ogg Video)", "ogv", "video/ogg", "video/x-vnd.on2.vp8", "audio/vorbis")
}

enum class AudioTargetFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val preferredAudioMime: String
) {
    M4A("M4A (AAC)", "m4a", "audio/mp4", "audio/mp4a-latm"),
    AAC("AAC (ADTS)", "aac", "audio/aac", "audio/mp4a-latm"),
    WAV("WAV (PCM Lossless)", "wav", "audio/wav", "audio/raw"),
    FLAC("FLAC (Lossless)", "flac", "audio/flac", "audio/flac"),
    OPUS("Opus (Ogg)", "opus", "audio/opus", "audio/opus"),
    OGG("OGG (Vorbis)", "ogg", "audio/ogg", "audio/vorbis"),
    MP3("MP3 (MPEG Audio)", "mp3", "audio/mpeg", "audio/mpeg"),
    AMR("AMR-WB (Voice)", "amr", "audio/amr-wb", "audio/amr-wb"),
    AIFF("AIFF (Apple PCM)", "aiff", "audio/aiff", "audio/raw"),
    ALAC("ALAC (Apple Lossless)", "m4a", "audio/mp4", "audio/alac"),
    WMA("WMA (Windows Audio)", "wma", "audio/x-ms-wma", "audio/mp4a-latm"),
    APE("APE (Monkey Audio)", "ape", "audio/x-ape", "audio/flac")
}

@Immutable
data class FormatCapabilityResolution(
    val mode: CapabilityMode,
    val actualExtension: String,
    val actualMimeType: String,
    val fallbackLabel: String
)

sealed interface ExportState {
    data object Idle : ExportState
    data class Processing(val progressPercent: Int) : ExportState
    data class Success(val outputUri: Uri, val summaryMessage: String) : ExportState
    data class Error(val message: String) : ExportState
}
