package com.Gallery.X.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.memory.MemoryCache
import coil.request.Options
import coil.size.Dimension
import com.Gallery.X.data.MediaItem
import java.util.Locale

object UniversalImageLoader {

    @Volatile
    private var sharedInstance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return sharedInstance ?: synchronized(this) {
            sharedInstance ?: buildImageLoader(context.applicationContext).also {
                sharedInstance = it
            }
        }
    }

    private fun buildImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_media_cache"))
                    .maxSizeBytes(128L * 1024L * 1024L)
                    .build()
            }
            .allowHardware(true)
            .crossfade(false)
            .components {
                // Fast OS-cached micro-thumbnails on Android 10+ for large photo & video grids
                add(MediaStoreThumbnailFetcher.Factory(context))
                // API 28+ ImageDecoderDecoder handles animated GIF, animated WebP, HEIC/HEIF/AVIF
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                // RAW Exif embedded thumbnail fast extractor for 60 FPS grid loading
                add(RawThumbnailFetcher.Factory(context))
                // Video frame decoder + fallback MediaMetadataRetriever for uncommon containers
                add(VideoFrameDecoder.Factory())
                add(VideoFallbackFrameFetcher.Factory(context))
            }
            .build()
    }
}

/**
 * Uses Android 10+ ContentResolver.loadThumbnail() for fast pre-cached grid thumbnails
 * on MediaStore content URIs when a small bounded size is requested, avoiding full-res
 * photo/4K-video frame decoding during fast scrolling through large libraries.
 */
class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val targetWidth: Int,
    private val targetHeight: Int
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bitmap = runCatching {
            context.contentResolver.loadThumbnail(uri, Size(targetWidth, targetHeight), null)
        }.getOrNull() ?: return null

        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content" || data.authority != "media") return null
            val w = (options.size.width as? Dimension.Pixels)?.px ?: return null
            val h = (options.size.height as? Dimension.Pixels)?.px ?: return null
            if (w <= 0 || h <= 0 || w > 400 || h > 400) return null

            val ext = data.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext == "gif") return null
            val mime = context.contentResolver.getType(data).orEmpty().lowercase(Locale.ROOT)
            if (mime == "image/gif") return null

            return MediaStoreThumbnailFetcher(context, data, w, h)
        }
    }
}

/**
 * Extracts embedded JPEG preview thumbnails from Camera RAW files (.dng, .cr2, .cr3, .nef,
 * .arw, .raf, .orf, .rw2, .pef, .srw, etc.) using ExifInterface for instant 60 FPS scrolling,
 * falling back to downsampled BitmapFactory/ImageDecoder when no embedded preview exists.
 */
class RawThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val resolver = context.contentResolver
        val embeddedBitmap = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).thumbnailBitmap
            }
        }.getOrNull()

        val bitmap = embeddedBitmap ?: runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val targetPx = 512
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetPx, targetPx)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
        }.getOrNull() ?: return null

        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val name = data.lastPathSegment.orEmpty()
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val mime = context.contentResolver.getType(data).orEmpty().lowercase(Locale.ROOT)
            val isRaw = ext in MediaItem.RAW_IMAGE_EXTENSIONS ||
                mime.contains("dng") || mime.contains("raw") || mime.contains("cr2") ||
                mime.contains("nef") || mime.contains("arw") || mime.contains("orf")
            return if (isRaw) RawThumbnailFetcher(context, data, options) else null
        }
    }
}

/**
 * Fallback video frame fetcher for uncommon video containers (.mkv, .avi, .mov, .ts, .flv,
 * .wmv, .rmvb, .mxf) when standard MIME detection skips VideoFrameDecoder.
 */
class VideoFallbackFrameFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        val frame = try {
            retriever.setDataSource(context, uri)
            val rawFrame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            rawFrame?.let { bmp ->
                val maxDim = 480
                if (bmp.width > maxDim || bmp.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height).toFloat()
                    Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * scale).toInt().coerceAtLeast(1),
                        (bmp.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    bmp
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }

        return frame?.let {
            DrawableResult(
                drawable = BitmapDrawable(context.resources, it),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val mime = context.contentResolver.getType(data).orEmpty().lowercase(Locale.ROOT)
            val ext = data.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
            val isUncommonVideo = ext in MediaItem.ALL_VIDEO_EXTENSIONS &&
                !mime.startsWith("video/mp4") && !mime.startsWith("video/webm")
            return if (isUncommonVideo) VideoFallbackFrameFetcher(context, data) else null
        }
    }
}
