package com.Gallery.X.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.Gallery.X.data.ImageTargetFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

enum class PhotoFilterPreset(val stringResName: String) {
    ORIGINAL("filter_original"),
    MONO("filter_mono"),
    WARM("filter_warm"),
    COOL("filter_cool"),
    VIVID("filter_vivid"),
    SEPIA("filter_sepia")
}

object PhotoConverterEngine {

    fun buildColorMatrix(
        filter: PhotoFilterPreset,
        brightness: Float, // -1f..1f
        contrast: Float    // 0.5f..1.8f
    ): ColorMatrix {
        val result = ColorMatrix()
        when (filter) {
            PhotoFilterPreset.ORIGINAL -> Unit
            PhotoFilterPreset.MONO -> result.setSaturation(0f)
            PhotoFilterPreset.WARM -> result.set(
                floatArrayOf(
                    1.15f, 0f, 0f, 0f, 12f,
                    0f, 1.03f, 0f, 0f, 4f,
                    0f, 0f, 0.86f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.COOL -> result.set(
                floatArrayOf(
                    0.88f, 0f, 0f, 0f, 0f,
                    0f, 0.98f, 0f, 0f, 4f,
                    0f, 0f, 1.18f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilterPreset.VIVID -> {
                result.setSaturation(1.42f)
            }
            PhotoFilterPreset.SEPIA -> {
                result.set(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
        }

        val scale = contrast
        val translate = ((-0.5f * scale) + 0.5f) * 255f + (brightness * 120f)
        val adjustMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        result.postConcat(adjustMatrix)
        return result
    }

    suspend fun loadSoftwareBitmap(context: Context, uri: Uri, maxEdge: Int = 2400): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val w = info.size.width
                        val h = info.size.height
                        if (w > maxEdge || h > maxEdge) {
                            val ratio = maxEdge.toFloat() / maxOf(w, h).toFloat()
                            decoder.setTargetSize(
                                (w * ratio).roundToInt().coerceAtLeast(1),
                                (h * ratio).roundToInt().coerceAtLeast(1)
                            )
                        }
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            }.getOrElse {
                // Fallback to BitmapFactory for RAW or non-standard streams
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                }.getOrNull()
            }
        }

    suspend fun exportEditedPhoto(
        context: Context,
        sourceBitmap: Bitmap,
        originalName: String,
        cropRectNormalized: RectF,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        filter: PhotoFilterPreset,
        brightness: Float,
        contrast: Float,
        targetFormat: ImageTargetFormat,
        quality: Int
    ): Uri? = withContext(Dispatchers.IO) {
        val transformed = renderTransformedBitmap(
            source = sourceBitmap,
            cropRectNormalized = cropRectNormalized,
            rotationDegrees = rotationDegrees,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical,
            filter = filter,
            brightness = brightness,
            contrast = contrast
        )

        val baseName = originalName.substringBeforeLast('.').ifBlank { "GalleryX_Edit" }
        val fileName = "${baseName}_gx_${System.currentTimeMillis()}.${targetFormat.extension}"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, targetFormat.mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/GalleryX"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null

        val writeSuccess = runCatching {
            resolver.openOutputStream(outUri)?.use { stream ->
                encodeBitmap(transformed, targetFormat, quality.coerceIn(10, 100), stream)
            } ?: false
        }.getOrDefault(false)

        if (transformed !== sourceBitmap) {
            transformed.recycle()
        }

        if (!writeSuccess) {
            resolver.delete(outUri, null, null)
            return@withContext null
        }

        val finalizeValues = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        resolver.update(outUri, finalizeValues, null, null)
        outUri
    }

    private fun renderTransformedBitmap(
        source: Bitmap,
        cropRectNormalized: RectF,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        filter: PhotoFilterPreset,
        brightness: Float,
        contrast: Float
    ): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f)
        }
        val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

        val left = (cropRectNormalized.left.coerceIn(0f, 0.95f) * oriented.width).roundToInt()
        val top = (cropRectNormalized.top.coerceIn(0f, 0.95f) * oriented.height).roundToInt()
        val right = (cropRectNormalized.right.coerceIn(0.05f, 1f) * oriented.width).roundToInt()
        val bottom = (cropRectNormalized.bottom.coerceIn(0.05f, 1f) * oriented.height).roundToInt()
        val cropW = (right - left).coerceAtLeast(1)
        val cropH = (bottom - top).coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(oriented, left, top, cropW, cropH)
        if (oriented !== source) oriented.recycle()

        val output = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildColorMatrix(filter, brightness, contrast))
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        if (cropped !== source) cropped.recycle()
        return output
    }

    private fun encodeBitmap(
        bitmap: Bitmap,
        format: ImageTargetFormat,
        quality: Int,
        output: OutputStream
    ): Boolean {
        return when (format) {
            ImageTargetFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            ImageTargetFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            ImageTargetFormat.WEBP_LOSSY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, output)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, quality, output)
                }
            }
            ImageTargetFormat.WEBP_LOSSLESS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)
                }
            }
            ImageTargetFormat.BMP -> writeBmp24(bitmap, output)
            ImageTargetFormat.ICO -> writeIcoPng(bitmap, output)
            ImageTargetFormat.GIF -> writeGif89a(bitmap, output)
        }
    }

    /**
     * Pure-Kotlin 24-bit uncompressed Windows DIB BMP encoder.
     */
    private fun writeBmp24(bitmap: Bitmap, output: OutputStream): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val rowBytes = w * 3
        val padding = (4 - (rowBytes % 4)) % 4
        val stride = rowBytes + padding
        val pixelDataSize = stride * h
        val fileSize = 54 + pixelDataSize

        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte())
            put('M'.code.toByte())
            putInt(fileSize)
            putShort(0)
            putShort(0)
            putInt(54)
            putInt(40) // BITMAPINFOHEADER
            putInt(w)
            putInt(h)
            putShort(1)
            putShort(24)
            putInt(0)
            putInt(pixelDataSize)
            putInt(2835)
            putInt(2835)
            putInt(0)
            putInt(0)
        }.array()
        output.write(header)

        val pixels = IntArray(w)
        val rowBuffer = ByteArray(stride)
        for (y in h - 1 downTo 0) {
            bitmap.getPixels(pixels, 0, w, 0, y, w, 1)
            var idx = 0
            for (x in 0 until w) {
                val c = pixels[x]
                rowBuffer[idx++] = (c and 0xFF).toByte()         // B
                rowBuffer[idx++] = ((c shr 8) and 0xFF).toByte() // G
                rowBuffer[idx++] = ((c shr 16) and 0xFF).toByte()// R
            }
            for (p in 0 until padding) {
                rowBuffer[idx++] = 0
            }
            output.write(rowBuffer)
        }
        output.flush()
        return true
    }

    /**
     * Pure-Kotlin Windows ICO container wrapping a PNG-compressed icon frame (<= 256px).
     */
    private fun writeIcoPng(bitmap: Bitmap, output: OutputStream): Boolean {
        val maxIconDim = 256
        val iconBmp = if (bitmap.width > maxIconDim || bitmap.height > maxIconDim) {
            val scale = maxIconDim.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceIn(1, 256),
                (bitmap.height * scale).roundToInt().coerceIn(1, 256),
                true
            )
        } else {
            bitmap
        }
        val pngBytes = ByteArrayOutputStream().use { baos ->
            iconBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
        val wByte = if (iconBmp.width >= 256) 0 else iconBmp.width
        val hByte = if (iconBmp.height >= 256) 0 else iconBmp.height
        if (iconBmp !== bitmap) iconBmp.recycle()

        val header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0) // Reserved
            putShort(1) // ICO type
            putShort(1) // 1 image entry
            put(wByte.toByte())
            put(hByte.toByte())
            put(0) // Palette count
            put(0) // Reserved
            putShort(1) // Color planes
            putShort(32) // Bits per pixel
            putInt(pngBytes.size)
            putInt(22) // Offset to PNG payload
        }.array()
        output.write(header)
        output.write(pngBytes)
        output.flush()
        return true
    }

    /**
     * Pure-Kotlin GIF89a encoder using a 216-color web-safe cube + 40 grayscale palette
     * and uncompressed/LZW-clear-code block stream so every image converts cleanly offline.
     */
    private fun writeGif89a(bitmap: Bitmap, output: OutputStream): Boolean {
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val s = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * s).roundToInt().coerceAtLeast(1),
                (bitmap.height * s).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val w = scaled.width
        val h = scaled.height

        // Build 256-entry RGB palette (6x6x6 color cube = 216 + 40 grayscale ramp)
        val palette = ByteArray(256 * 3)
        var pIdx = 0
        for (r in 0..5) {
            for (g in 0..5) {
                for (b in 0..5) {
                    palette[pIdx++] = (r * 51).toByte()
                    palette[pIdx++] = (g * 51).toByte()
                    palette[pIdx++] = (b * 51).toByte()
                }
            }
        }
        for (i in 0 until 40) {
            val gray = (i * 255 / 39).toByte()
            palette[pIdx++] = gray
            palette[pIdx++] = gray
            palette[pIdx++] = gray
        }

        val header = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("GIF89a".toByteArray(Charsets.US_ASCII))
            putShort(w.toShort())
            putShort(h.toShort())
            put(0xF7.toByte()) // Global color table flag + 8-bit color + 256 entries (2^(7+1))
            put(0) // Background index
            put(0) // Pixel aspect ratio
        }.array()
        output.write(header)
        output.write(palette)

        // Image Descriptor
        val imgDesc = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0x2C.toByte())
            putShort(0)
            putShort(0)
            putShort(w.toShort())
            putShort(h.toShort())
            put(0)
        }.array()
        output.write(imgDesc)

        // LZW Minimum Code Size = 8 (9-bit codes: Clear=256, EOI=257)
        output.write(8)

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()

        // Stream 9-bit codes with periodic Clear codes (every 120 pixels) — valid, fast, lossless palette index stream
        val subBlock = ByteArray(256)
        var subLen = 0
        var bitBuf = 0
        var bitCount = 0

        fun flushSubBlock() {
            if (subLen > 0) {
                output.write(subLen)
                output.write(subBlock, 0, subLen)
                subLen = 0
            }
        }

        fun writeBits(code: Int, bits: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += bits
            while (bitCount >= 8) {
                subBlock[subLen++] = (bitBuf and 0xFF).toByte()
                if (subLen == 255) flushSubBlock()
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
        }

        writeBits(256, 9) // Clear code
        var sinceClear = 0
        for (c in pixels) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val ri = (r * 5 + 127) / 255
            val gi = (g * 5 + 127) / 255
            val bi = (b * 5 + 127) / 255
            val colorIndex = ri * 36 + gi * 6 + bi
            writeBits(colorIndex, 9)
            sinceClear++
            if (sinceClear >= 120) {
                writeBits(256, 9)
                sinceClear = 0
            }
        }
        writeBits(257, 9) // End of Information
        if (bitCount > 0) {
            subBlock[subLen++] = (bitBuf and 0xFF).toByte()
        }
        flushSubBlock()
        output.write(0) // Block terminator
        output.write(0x3B) // GIF Trailer
        output.flush()
        return true
    }
}
