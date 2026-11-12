package com.Gallery.X.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.Gallery.X.data.AudioTargetFormat
import com.Gallery.X.data.CapabilityMode
import com.Gallery.X.data.FormatCapabilityResolution
import com.Gallery.X.data.VideoTargetFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class VideoAspectPreset(val labelKey: String, val ratio: Float?) {
    FREE("aspect_free", null),
    SQUARE("aspect_square", 1f),
    LANDSCAPE_16_9("aspect_landscape", 16f / 9f),
    PORTRAIT_9_16("aspect_portrait", 9f / 16f),
    CLASSIC_4_3("aspect_classic", 4f / 3f)
}

enum class VideoFilterPreset(val labelKey: String) {
    ORIGINAL("filter_original"),
    MONO("filter_mono"),
    WARM("filter_warm"),
    COOL("filter_cool"),
    VIVID("filter_vivid")
}

object MediaTranscodeEngine {

    private val encoderMimeCache: Set<String> by lazy {
        runCatching {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            list.codecInfos
                .filter { it.isEncoder }
                .flatMap { it.supportedTypes.toList() }
                .map { it.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun hasDeviceEncoder(mimeType: String): Boolean {
        return mimeType.lowercase() in encoderMimeCache
    }

    fun resolveVideoCapability(format: VideoTargetFormat): FormatCapabilityResolution {
        val hasVideoEnc = hasDeviceEncoder(format.preferredVideoMime)
        return when (format) {
            VideoTargetFormat.MP4 -> FormatCapabilityResolution(
                mode = CapabilityMode.NATIVE_HARDWARE,
                actualExtension = "mp4",
                actualMimeType = "video/mp4",
                fallbackLabel = "MP4"
            )
            VideoTargetFormat.MP4_HEVC -> if (hasVideoEnc) {
                FormatCapabilityResolution(CapabilityMode.NATIVE_HARDWARE, "mp4", "video/mp4", "HEVC MP4")
            } else {
                FormatCapabilityResolution(CapabilityMode.SMART_FALLBACK, "mp4", "video/mp4", "H.264 MP4")
            }
            VideoTargetFormat.THREE_GP -> if (hasDeviceEncoder("video/3gpp")) {
                FormatCapabilityResolution(CapabilityMode.NATIVE_HARDWARE, "3gp", "video/3gpp", "3GP")
            } else {
                FormatCapabilityResolution(CapabilityMode.SMART_FALLBACK, "mp4", "video/mp4", "MP4")
            }
            VideoTargetFormat.MOV -> FormatCapabilityResolution(
                mode = CapabilityMode.SMART_FALLBACK,
                actualExtension = "mp4",
                actualMimeType = "video/mp4",
                fallbackLabel = "ISO-BMFF MP4"
            )
            VideoTargetFormat.WEBM -> if (hasVideoEnc) {
                FormatCapabilityResolution(CapabilityMode.SMART_FALLBACK, "mp4", "video/mp4", "VP9/H.264 MP4")
            } else {
                FormatCapabilityResolution(CapabilityMode.SMART_FALLBACK, "mp4", "video/mp4", "H.264 MP4")
            }
            VideoTargetFormat.MKV,
            VideoTargetFormat.AVI,
            VideoTargetFormat.TS,
            VideoTargetFormat.FLV,
            VideoTargetFormat.WMV,
            VideoTargetFormat.OGV -> FormatCapabilityResolution(
                mode = CapabilityMode.SMART_FALLBACK,
                actualExtension = "mp4",
                actualMimeType = "video/mp4",
                fallbackLabel = "H.264 MP4"
            )
        }
    }

    fun resolveAudioCapability(format: AudioTargetFormat): FormatCapabilityResolution {
        return when (format) {
            AudioTargetFormat.WAV -> FormatCapabilityResolution(
                mode = CapabilityMode.PURE_KOTLIN,
                actualExtension = "wav",
                actualMimeType = "audio/wav",
                fallbackLabel = "WAV PCM"
            )
            AudioTargetFormat.AIFF -> FormatCapabilityResolution(
                mode = CapabilityMode.PURE_KOTLIN,
                actualExtension = "aiff",
                actualMimeType = "audio/aiff",
                fallbackLabel = "AIFF PCM"
            )
            AudioTargetFormat.M4A, AudioTargetFormat.AAC -> FormatCapabilityResolution(
                mode = CapabilityMode.NATIVE_HARDWARE,
                actualExtension = "m4a",
                actualMimeType = "audio/mp4",
                fallbackLabel = "AAC M4A"
            )
            AudioTargetFormat.FLAC -> if (hasDeviceEncoder("audio/flac")) {
                FormatCapabilityResolution(CapabilityMode.NATIVE_HARDWARE, "m4a", "audio/mp4", "FLAC/M4A")
            } else {
                FormatCapabilityResolution(CapabilityMode.SMART_FALLBACK, "wav", "audio/wav", "Lossless WAV")
            }
            AudioTargetFormat.OPUS -> if (hasDeviceEncoder("audio/opus")) {
                FormatCapabilityResolution(CapabilityMode.SMART_FALLBACK, "m4a", "audio/mp4", "AAC/Opus M4A")
            } else {
                FormatCapabilityResolution(CapabilityMode.SMART_FALLBACK, "m4a", "audio/mp4", "AAC M4A")
            }
            AudioTargetFormat.OGG,
            AudioTargetFormat.MP3,
            AudioTargetFormat.AMR,
            AudioTargetFormat.ALAC,
            AudioTargetFormat.WMA,
            AudioTargetFormat.APE -> if (hasDeviceEncoder(format.preferredAudioMime) && format == AudioTargetFormat.AMR) {
                FormatCapabilityResolution(CapabilityMode.SMART_FALLBACK, "m4a", "audio/mp4", "AAC M4A")
            } else {
                val fallbackExt = if (format == AudioTargetFormat.ALAC || format == AudioTargetFormat.APE) "wav" else "m4a"
                val fallbackMime = if (fallbackExt == "wav") "audio/wav" else "audio/mp4"
                val label = if (fallbackExt == "wav") "Lossless WAV" else "AAC M4A"
                FormatCapabilityResolution(CapabilityMode.SMART_FALLBACK, fallbackExt, fallbackMime, label)
            }
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun exportEditedVideo(
        context: Context,
        inputUri: Uri,
        originalName: String,
        trimStartMs: Long,
        trimEndMs: Long,
        aspectPreset: VideoAspectPreset,
        filterPreset: VideoFilterPreset,
        targetFormat: VideoTargetFormat,
        onProgress: (Int) -> Unit
    ): Result<Pair<Uri, String>> {
        val resolution = resolveVideoCapability(targetFormat)
        val tempFile = File(context.cacheDir, "gx_vid_${System.currentTimeMillis()}.${resolution.actualExtension}")

        val videoEffects = buildList<Effect> {
            aspectPreset.ratio?.let { ratio ->
                add(Presentation.createForAspectRatio(ratio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            }
            when (filterPreset) {
                VideoFilterPreset.ORIGINAL -> Unit
                VideoFilterPreset.MONO -> add(RgbFilter.createGrayscaleFilter())
                VideoFilterPreset.WARM -> add(
                    RgbAdjustment.Builder()
                        .setRedScale(1.16f)
                        .setGreenScale(1.02f)
                        .setBlueScale(0.84f)
                        .build()
                )
                VideoFilterPreset.COOL -> add(
                    RgbAdjustment.Builder()
                        .setRedScale(0.85f)
                        .setGreenScale(0.98f)
                        .setBlueScale(1.18f)
                        .build()
                )
                VideoFilterPreset.VIVID -> {
                    add(HslAdjustment.Builder().adjustSaturation(38f).build())
                    add(Contrast(0.18f))
                }
            }
        }

        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
        if (trimStartMs > 0L) clippingBuilder.setStartPositionMs(trimStartMs)
        if (trimEndMs > trimStartMs) clippingBuilder.setEndPositionMs(trimEndMs)

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clippingBuilder.build())
            .build()

        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val targetVideoMime = when {
            targetFormat == VideoTargetFormat.MP4_HEVC && hasDeviceEncoder(MimeTypes.VIDEO_H265) -> MimeTypes.VIDEO_H265
            else -> MimeTypes.VIDEO_H264
        }

        val transformResult = runTransformerOnMain(
            context = context,
            editedMediaItem = editedItem,
            outputPath = tempFile.absolutePath,
            videoMimeType = targetVideoMime,
            audioMimeType = MimeTypes.AUDIO_AAC,
            onProgress = onProgress
        )

        if (transformResult.isFailure) {
            tempFile.delete()
            return Result.failure(transformResult.exceptionOrNull() ?: IllegalStateException("Video export failed"))
        }

        val baseName = originalName.substringBeforeLast('.').ifBlank { "GalleryX_Video" }
        val displayName = "${baseName}_gx_${System.currentTimeMillis()}.${resolution.actualExtension}"
        val savedUri = saveFileToMediaStore(
            context = context,
            sourceFile = tempFile,
            displayName = displayName,
            mimeType = resolution.actualMimeType,
            isVideo = true
        )
        tempFile.delete()

        return if (savedUri != null) {
            Result.success(savedUri to "${resolution.fallbackLabel} ($displayName)")
        } else {
            Result.failure(IllegalStateException("Failed to insert exported video into MediaStore"))
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun exportAudioOrExtractFromVideo(
        context: Context,
        inputUri: Uri,
        originalName: String,
        trimStartMs: Long,
        trimEndMs: Long,
        totalDurationMs: Long,
        fadeIn: Boolean,
        fadeOut: Boolean,
        targetFormat: AudioTargetFormat,
        onProgress: (Int) -> Unit
    ): Result<Pair<Uri, String>> {
        val resolution = resolveAudioCapability(targetFormat)
        val effectiveEndMs = if (trimEndMs > trimStartMs) trimEndMs else totalDurationMs
        val clipDurationMs = (effectiveEndMs - trimStartMs).coerceAtLeast(500L)

        // Pure-Kotlin PCM path for WAV and AIFF (and smart-fallback lossless WAV formats)
        if (resolution.actualExtension == "wav" || resolution.actualExtension == "aiff") {
            val tempPcmOut = File(context.cacheDir, "gx_aud_${System.currentTimeMillis()}.${resolution.actualExtension}")
            val pcmResult = withContext(Dispatchers.IO) {
                runCatching {
                    transcodeToPcmContainer(
                        context = context,
                        inputUri = inputUri,
                        outFile = tempPcmOut,
                        isAiff = resolution.actualExtension == "aiff",
                        trimStartMs = trimStartMs,
                        trimEndMs = effectiveEndMs,
                        fadeIn = fadeIn,
                        fadeOut = fadeOut,
                        onProgress = onProgress
                    )
                }
            }
            if (pcmResult.isFailure) {
                tempPcmOut.delete()
                return Result.failure(pcmResult.exceptionOrNull() ?: IllegalStateException("PCM export failed"))
            }

            val baseName = originalName.substringBeforeLast('.').ifBlank { "GalleryX_Audio" }
            val displayName = "${baseName}_gx_${System.currentTimeMillis()}.${resolution.actualExtension}"
            val savedUri = saveFileToMediaStore(
                context = context,
                sourceFile = tempPcmOut,
                displayName = displayName,
                mimeType = resolution.actualMimeType,
                isVideo = false
            )
            tempPcmOut.delete()
            return if (savedUri != null) {
                Result.success(savedUri to "${resolution.fallbackLabel} ($displayName)")
            } else {
                Result.failure(IllegalStateException("Failed to save audio to MediaStore"))
            }
        }

        // Media3 Transformer path (strips video track if input is a video, applies trim + fade AudioProcessor)
        val tempFile = File(context.cacheDir, "gx_aud_${System.currentTimeMillis()}.${resolution.actualExtension}")
        val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
        if (trimStartMs > 0L) clippingBuilder.setStartPositionMs(trimStartMs)
        if (trimEndMs > trimStartMs) clippingBuilder.setEndPositionMs(trimEndMs)

        val mediaItem = MediaItem.Builder()
            .setUri(inputUri)
            .setClippingConfiguration(clippingBuilder.build())
            .build()

        val audioProcessors: List<AudioProcessor> = if (fadeIn || fadeOut) {
            listOf(FadeAudioProcessor(fadeIn, fadeOut, clipDurationMs))
        } else {
            emptyList()
        }

        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(audioProcessors, emptyList()))
            .build()

        val transformResult = runTransformerOnMain(
            context = context,
            editedMediaItem = editedItem,
            outputPath = tempFile.absolutePath,
            videoMimeType = null,
            audioMimeType = MimeTypes.AUDIO_AAC,
            onProgress = onProgress
        )

        if (transformResult.isFailure) {
            tempFile.delete()
            return Result.failure(transformResult.exceptionOrNull() ?: IllegalStateException("Audio export failed"))
        }

        val baseName = originalName.substringBeforeLast('.').ifBlank { "GalleryX_Audio" }
        val displayName = "${baseName}_gx_${System.currentTimeMillis()}.${resolution.actualExtension}"
        val savedUri = saveFileToMediaStore(
            context = context,
            sourceFile = tempFile,
            displayName = displayName,
            mimeType = resolution.actualMimeType,
            isVideo = false
        )
        tempFile.delete()

        return if (savedUri != null) {
            Result.success(savedUri to "${resolution.fallbackLabel} ($displayName)")
        } else {
            Result.failure(IllegalStateException("Failed to save audio to MediaStore"))
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerOnMain(
        context: Context,
        editedMediaItem: EditedMediaItem,
        outputPath: String,
        videoMimeType: String?,
        audioMimeType: String?,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            var finished = false
            var transformerRef: Transformer? = null

            val progressRunnable = object : Runnable {
                override fun run() {
                    if (finished) return
                    val t = transformerRef ?: return
                    val state = t.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress.coerceIn(0, 99))
                    }
                    if (!finished && state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        handler.postDelayed(this, 200L)
                    }
                }
            }

            val builder = Transformer.Builder(context)
            if (videoMimeType != null) {
                builder.setVideoMimeType(videoMimeType)
            }
            if (audioMimeType != null) {
                builder.setAudioMimeType(audioMimeType)
            }

            val transformer = builder
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished = true
                        handler.removeCallbacks(progressRunnable)
                        onProgress(100)
                        if (cont.isActive) cont.resume(Result.success(Unit))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exception: ExportException
                    ) {
                        finished = true
                        handler.removeCallbacks(progressRunnable)
                        if (cont.isActive) cont.resume(Result.failure(exception))
                    }
                })
                .build()

            transformerRef = transformer
            cont.invokeOnCancellation {
                finished = true
                handler.removeCallbacks(progressRunnable)
                transformer.cancel()
            }

            transformer.start(editedMediaItem, outputPath)
            handler.postDelayed(progressRunnable, 200L)
        }
    }

    private suspend fun saveFileToMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext null
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val relativeDir = if (isVideo) {
            "${Environment.DIRECTORY_MOVIES}/GalleryX"
        } else {
            "${Environment.DIRECTORY_MUSIC}/GalleryX"
        }

        val values = ContentValues().apply {
            if (isVideo) {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, relativeDir)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, relativeDir)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val outUri = resolver.insert(collection, values) ?: return@withContext null
        val copied = runCatching {
            resolver.openOutputStream(outUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            }
            true
        }.getOrDefault(false)

        if (!copied) {
            resolver.delete(outUri, null, null)
            return@withContext null
        }

        val finalizeValues = ContentValues().apply {
            if (isVideo) {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            } else {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
        }
        resolver.update(outUri, finalizeValues, null, null)
        outUri
    }

    /**
     * Decodes any audio track (from audio or video URI) to 16-bit PCM using MediaExtractor + MediaCodec,
     * applies trim timestamps and fade-in/out envelopes, and writes valid RIFF WAV or big-endian AIFF.
     */
    private fun transcodeToPcmContainer(
        context: Context,
        inputUri: Uri,
        outFile: File,
        isAiff: Boolean,
        trimStartMs: Long,
        trimEndMs: Long,
        fadeIn: Boolean,
        fadeOut: Boolean,
        onProgress: (Int) -> Unit
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, inputUri, null)

        var audioTrackIndex = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                inputFormat = fmt
                break
            }
        }
        require(audioTrackIndex >= 0 && inputFormat != null) { "No audio track found in source media" }

        extractor.selectTrack(audioTrackIndex)
        if (trimStartMs > 0L) {
            extractor.seekTo(trimStartMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val totalDurationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
            inputFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            trimEndMs * 1000L
        }
        val startUs = trimStartMs * 1000L
        val endUs = if (trimEndMs > trimStartMs) trimEndMs * 1000L else totalDurationUs

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val pcmChunks = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleSize < 0 || (endUs > startUs && sampleTimeUs > endUs)) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= startUs) {
                            val bytes = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.get(bytes)
                            pcmChunks.write(bytes)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (endUs > startUs) {
                            val pct = (((bufferInfo.presentationTimeUs - startUs).toDouble() / (endUs - startUs)) * 85.0)
                                .roundToInt().coerceIn(5, 85)
                            onProgress(pct)
                        }
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
        }

        val rawPcm = pcmChunks.toByteArray()
        applyPcmFadeInOut(rawPcm, sampleRate, channelCount, fadeIn, fadeOut)
        onProgress(95)

        outFile.outputStream().use { out ->
            if (isAiff) {
                writeAiffFile(out, rawPcm, sampleRate, channelCount)
            } else {
                writeWavFile(out, rawPcm, sampleRate, channelCount)
            }
        }
        onProgress(100)
    }

    private fun applyPcmFadeInOut(
        pcmBytes: ByteArray,
        sampleRate: Int,
        channels: Int,
        fadeIn: Boolean,
        fadeOut: Boolean
    ) {
        if (!fadeIn && !fadeOut) return
        val bytesPerFrame = (channels * 2).coerceAtLeast(2)
        val totalFrames = pcmBytes.size / bytesPerFrame
        if (totalFrames <= 0) return

        val fadeFrames = (sampleRate * 1.5).roundToInt().coerceAtMost(totalFrames / 2)
        if (fadeFrames <= 0) return

        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until totalFrames) {
            var gain = 1.0f
            if (fadeIn && frame < fadeFrames) {
                gain = minOf(gain, frame.toFloat() / fadeFrames.toFloat())
            }
            if (fadeOut && frame >= totalFrames - fadeFrames) {
                val rem = totalFrames - 1 - frame
                gain = minOf(gain, rem.toFloat() / fadeFrames.toFloat())
            }
            if (gain < 0.999f) {
                for (ch in 0 until channels) {
                    val bytePos = (frame * channels + ch) * 2
                    if (bytePos + 1 < pcmBytes.size) {
                        val sample = bb.getShort(bytePos)
                        val scaled = (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        bb.putShort(bytePos, scaled)
                    }
                }
            }
        }
    }

    private fun writeWavFile(output: OutputStream, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcmData.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(16) // 16-bit
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }.array()
        output.write(header)
        output.write(pcmData)
        output.flush()
    }

    private fun writeAiffFile(output: OutputStream, pcmLittleEndian: ByteArray, sampleRate: Int, channels: Int) {
        val numFrames = pcmLittleEndian.size / (channels * 2).coerceAtLeast(2)
        val ssndSize = 8 + pcmLittleEndian.size
        val formSize = 4 + 26 + 8 + ssndSize

        val header = ByteBuffer.allocate(54).order(ByteOrder.BIG_ENDIAN).apply {
            put("FORM".toByteArray(Charsets.US_ASCII))
            putInt(formSize)
            put("AIFF".toByteArray(Charsets.US_ASCII))
            put("COMM".toByteArray(Charsets.US_ASCII))
            putInt(18)
            putShort(channels.toShort())
            putInt(numFrames)
            putShort(16)
            put(encodeIeee80(sampleRate.toDouble()))
            put("SSND".toByteArray(Charsets.US_ASCII))
            putInt(ssndSize)
            putInt(0) // offset
            putInt(0) // blockSize
        }.array()
        output.write(header)

        // Swap little-endian 16-bit PCM samples to big-endian for AIFF
        val swapped = ByteArray(pcmLittleEndian.size)
        var i = 0
        while (i + 1 < pcmLittleEndian.size) {
            swapped[i] = pcmLittleEndian[i + 1]
            swapped[i + 1] = pcmLittleEndian[i]
            i += 2
        }
        output.write(swapped)
        output.flush()
    }

    private fun encodeIeee80(value: Double): ByteArray {
        val out = ByteArray(10)
        if (value <= 0.0) return out
        var v = value
        var exp = 16383 + 63
        while (v < (1L shl 62).toDouble()) {
            v *= 2.0
            exp--
        }
        while (v >= (1L shl 63).toDouble()) {
            v /= 2.0
            exp++
        }
        val mantissa = v.toLong()
        out[0] = ((exp shr 8) and 0x7F).toByte()
        out[1] = (exp and 0xFF).toByte()
        for (i in 0 until 8) {
            out[2 + i] = ((mantissa ushr ((7 - i) * 8)) and 0xFF).toByte()
        }
        return out
    }
}

@UnstableApi
class FadeAudioProcessor(
    private val fadeIn: Boolean,
    private val fadeOut: Boolean,
    private val totalDurationMs: Long
) : BaseAudioProcessor() {

    private var framesProcessed: Long = 0L
    private var totalFramesEstimate: Long = 0L
    private var fadeFrames: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val sampleRate = inputAudioFormat.sampleRate.toLong().coerceAtLeast(8000L)
        totalFramesEstimate = (sampleRate * totalDurationMs) / 1000L
        fadeFrames = ((sampleRate * 1500L) / 1000L).coerceAtMost((totalFramesEstimate / 2L).coerceAtLeast(1L))
        framesProcessed = 0L
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outBuf = replaceOutputBuffer(remaining)
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)

        while (inputBuffer.remaining() >= 2) {
            val sample = inputBuffer.getShort()
            var gain = 1.0f
            if (fadeIn && framesProcessed < fadeFrames) {
                gain = minOf(gain, framesProcessed.toFloat() / fadeFrames.toFloat())
            }
            if (fadeOut && totalFramesEstimate > fadeFrames && framesProcessed >= totalFramesEstimate - fadeFrames) {
                val rem = (totalFramesEstimate - 1L - framesProcessed).coerceAtLeast(0L)
                gain = minOf(gain, rem.toFloat() / fadeFrames.toFloat())
            }
            val scaled = (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            outBuf.putShort(scaled)

            if (channels == 1 || (outBuf.position() % (channels * 2) == 0)) {
                framesProcessed++
            }
        }
        outBuf.flip()
    }

    override fun onReset() {
        framesProcessed = 0L
    }
}
