package com.Gallery.X.data

import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaRepository(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    fun observeMediaLibrary(): Flow<List<MediaItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        trySend(Unit)

        awaitClose {
            resolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.Main).let { triggers ->
        kotlinx.coroutines.flow.flow {
            triggers.collect {
                emit(queryAllMedia())
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun queryAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        val seenIds = HashSet<Long>(1024)
        val results = ArrayList<MediaItem>(512)

        queryImages(dateFormat, seenIds, results)
        queryVideos(dateFormat, seenIds, results)
        queryAudio(dateFormat, seenIds, results)
        queryUnclassifiedMediaFiles(dateFormat, seenIds, results)

        results.sortByDescending { it.dateModifiedSec }
        results
    }

    private fun queryImages(
        dateFormat: DateFormat,
        seenIds: MutableSet<Long>,
        out: MutableList<MediaItem>
    ) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        runCatching {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (!seenIds.add(id)) continue
                    val name = cursor.getString(nameCol) ?: "image_$id"
                    val bucket = cursor.getString(bucketCol) ?: "Internal Storage"
                    val mime = cursor.getString(mimeCol) ?: "image/*"
                    val size = cursor.getLong(sizeCol)
                    val added = cursor.getLong(addedCol)
                    val modified = cursor.getLong(modCol).takeIf { it > 0L } ?: added
                    val width = cursor.getInt(wCol)
                    val height = cursor.getInt(hCol)
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

                    out.add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                            displayName = name,
                            bucketName = bucket,
                            mimeType = mime,
                            extension = ext,
                            sizeBytes = size,
                            dateAddedSec = added,
                            dateModifiedSec = modified,
                            dateGroupLabel = formatDateGroup(dateFormat, modified),
                            width = width,
                            height = height,
                            mediaType = MediaType.PHOTO
                        )
                    )
                }
            }
        }
    }

    private fun queryVideos(
        dateFormat: DateFormat,
        seenIds: MutableSet<Long>,
        out: MutableList<MediaItem>
    ) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        runCatching {
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (!seenIds.add(id)) continue
                    val name = cursor.getString(nameCol) ?: "video_$id"
                    val bucket = cursor.getString(bucketCol) ?: "Movies"
                    val mime = cursor.getString(mimeCol) ?: "video/*"
                    val size = cursor.getLong(sizeCol)
                    val added = cursor.getLong(addedCol)
                    val modified = cursor.getLong(modCol).takeIf { it > 0L } ?: added
                    val duration = cursor.getLong(durCol).coerceAtLeast(0L)
                    val width = cursor.getInt(wCol)
                    val height = cursor.getInt(hCol)
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

                    out.add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                            displayName = name,
                            bucketName = bucket,
                            mimeType = mime,
                            extension = ext,
                            sizeBytes = size,
                            dateAddedSec = added,
                            dateModifiedSec = modified,
                            dateGroupLabel = formatDateGroup(dateFormat, modified),
                            durationMs = duration,
                            width = width,
                            height = height,
                            mediaType = MediaType.VIDEO
                        )
                    )
                }
            }
        }
    }

    private fun queryAudio(
        dateFormat: DateFormat,
        seenIds: MutableSet<Long>,
        out: MutableList<MediaItem>
    ) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )
        runCatching {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (!seenIds.add(id)) continue
                    val name = cursor.getString(nameCol) ?: "audio_$id"
                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "Audio" else "Audio"
                    val mime = cursor.getString(mimeCol) ?: "audio/*"
                    val size = cursor.getLong(sizeCol)
                    val added = cursor.getLong(addedCol)
                    val modified = cursor.getLong(modCol).takeIf { it > 0L } ?: added
                    val duration = cursor.getLong(durCol).coerceAtLeast(0L)
                    val artistRaw = if (artistCol >= 0) cursor.getString(artistCol).orEmpty() else ""
                    val artist = if (artistRaw == "<unknown>") "" else artistRaw
                    val album = if (albumCol >= 0) cursor.getString(albumCol).orEmpty() else ""
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    out.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            bucketName = bucket,
                            mimeType = mime,
                            extension = ext,
                            sizeBytes = size,
                            dateAddedSec = added,
                            dateModifiedSec = modified,
                            dateGroupLabel = formatDateGroup(dateFormat, modified),
                            durationMs = duration,
                            artist = artist,
                            album = album,
                            mediaType = MediaType.AUDIO
                        )
                    )
                }
            }
        }
    }

    /**
     * Queries MediaStore.Files broadly so uncommon RAW, video, or audio extensions
     * (e.g., .cr3, .raf, .ts, .flv, .mkv, .opus, .ape, .dsf) are still surfaced
     * even if Android's MediaScanner didn't index them into typed tables.
     */
    private fun queryUnclassifiedMediaFiles(
        dateFormat: DateFormat,
        seenIds: MutableSet<Long>,
        out: MutableList<MediaItem>
    ) {
        val filesUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        runCatching {
            resolver.query(
                filesUri,
                projection,
                "${MediaStore.Files.FileColumns.SIZE} > 0",
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (seenIds.contains(id)) continue
                    val name = cursor.getString(nameCol) ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (ext.isEmpty()) continue

                    val detectedType = classifyExtension(ext) ?: continue
                    seenIds.add(id)

                    val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "Storage" else "Storage"
                    val mime = cursor.getString(mimeCol) ?: guessMimeType(ext, detectedType)
                    val size = cursor.getLong(sizeCol)
                    val added = cursor.getLong(addedCol)
                    val modified = cursor.getLong(modCol).takeIf { it > 0L } ?: added
                    val uri = ContentUris.withAppendedId(filesUri, id)

                    out.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            displayName = name,
                            bucketName = bucket,
                            mimeType = mime,
                            extension = ext,
                            sizeBytes = size,
                            dateAddedSec = added,
                            dateModifiedSec = modified,
                            dateGroupLabel = formatDateGroup(dateFormat, modified),
                            durationMs = 0L,
                            mediaType = detectedType
                        )
                    )
                }
            }
        }
    }

    suspend fun loadExifDetails(item: MediaItem): ExifDetails = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(item.uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
                val camera = listOf(make, model).filter { it.isNotEmpty() }.distinct().joinToString(" ")
                val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" }.orEmpty()
                val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { exp ->
                    val d = exp.toDoubleOrNull()
                    if (d != null && d > 0 && d < 1.0) "1/${(1.0 / d).toInt()}s" else "${exp}s"
                }.orEmpty()
                val iso = (exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED)).orEmpty()
                val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { raw ->
                    if (raw.contains('/')) {
                        val parts = raw.split('/')
                        val num = parts.getOrNull(0)?.toDoubleOrNull()
                        val den = parts.getOrNull(1)?.toDoubleOrNull()
                        if (num != null && den != null && den != 0.0) {
                            "${String.format(Locale.US, "%.1f", num / den)} mm"
                        } else "$raw mm"
                    } else "$raw mm"
                }.orEmpty()
                val dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).orEmpty()
                val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, item.width)
                val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, item.height)

                ExifDetails(
                    cameraMakeModel = camera,
                    aperture = fNumber,
                    exposureTime = exposure,
                    iso = if (iso.isNotEmpty()) "ISO $iso" else "",
                    focalLength = focal,
                    dateTaken = dateTaken,
                    width = w,
                    height = h
                )
            }
        }.getOrNull() ?: ExifDetails(width = item.width, height = item.height)
    }

    suspend fun resolveExternalUri(uri: Uri, intentMimeType: String?): MediaItem? = withContext(Dispatchers.IO) {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "external_media"
        var sizeBytes = 0L

        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }

        val resolvedMime = (intentMimeType?.takeIf { it.isNotBlank() && it != "*/*" }
            ?: resolver.getType(uri)
            ?: "").lowercase(Locale.ROOT)
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        val type = when {
            resolvedMime.startsWith("video/") -> MediaType.VIDEO
            resolvedMime.startsWith("audio/") -> MediaType.AUDIO
            resolvedMime.startsWith("image/") -> MediaType.PHOTO
            else -> classifyExtension(ext) ?: when {
                resolvedMime.contains("ogg") || resolvedMime.contains("flac") -> MediaType.AUDIO
                resolvedMime.contains("matroska") || resolvedMime.contains("mxf") || resolvedMime.contains("realmedia") -> MediaType.VIDEO
                else -> MediaType.PHOTO
            }
        }

        val durationMs = if (type != MediaType.PHOTO) safeExtractDurationMs(uri) else 0L
        val nowSec = System.currentTimeMillis() / 1000L

        MediaItem(
            id = uri.hashCode().toLong(),
            uri = uri,
            displayName = displayName,
            bucketName = "External",
            mimeType = resolvedMime.ifEmpty { guessMimeType(ext, type) },
            extension = ext,
            sizeBytes = sizeBytes,
            dateAddedSec = nowSec,
            dateModifiedSec = nowSec,
            dateGroupLabel = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date()),
            durationMs = durationMs,
            mediaType = type
        )
    }

    /**
     * Attempts to delete a MediaStore item directly; returns an IntentSender when
     * Android 10 (RecoverableSecurityException) or Android 11+ (createDeleteRequest)
     * requires explicit system user confirmation.
     */
    suspend fun requestDelete(item: MediaItem): DeleteResult =
        requestDeleteMultiple(listOf(item))

    suspend fun requestDeleteMultiple(items: List<MediaItem>): DeleteResult = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext DeleteResult.Failed
        val uris = items.map { it.uri }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext runCatching {
                val pendingIntent = MediaStore.createDeleteRequest(resolver, uris)
                DeleteResult.RequiresConfirmation(pendingIntent.intentSender)
            }.getOrElse {
                var deletedCount = 0
                for (uri in uris) {
                    val rows = runCatching { resolver.delete(uri, null, null) }.getOrDefault(0)
                    if (rows > 0) deletedCount++
                }
                if (deletedCount > 0) DeleteResult.Deleted else DeleteResult.Failed
            }
        } else {
            var deletedCount = 0
            for (uri in uris) {
                try {
                    val rows = resolver.delete(uri, null, null)
                    if (rows > 0) deletedCount++
                } catch (sec: SecurityException) {
                    val recoverable = sec as? RecoverableSecurityException
                    if (recoverable != null) {
                        return@withContext DeleteResult.RequiresConfirmation(
                            recoverable.userAction.actionIntent.intentSender
                        )
                    }
                } catch (_: Exception) {
                    // Continue attempting remaining items
                }
            }
            return@withContext if (deletedCount > 0) DeleteResult.Deleted else DeleteResult.Failed
        }
    }

    fun shareMediaItem(item: MediaItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, item.displayName).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareMediaItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            shareMediaItem(items.first())
            return
        }
        val uris = ArrayList(items.map { it.uri })
        val types = items.map { it.mediaType }.distinct()
        val mimeType = if (types.size == 1) {
            when (types.first()) {
                MediaType.PHOTO -> "image/*"
                MediaType.VIDEO -> "video/*"
                MediaType.AUDIO -> "audio/*"
            }
        } else {
            "*/*"
        }
        val shareClipData = ClipData.newUri(resolver, "media", uris.first()).apply {
            for (i in 1 until uris.size) {
                addItem(ClipData.Item(uris[i]))
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = shareClipData
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun safeExtractDurationMs(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun classifyExtension(ext: String): MediaType? {
        return when (ext) {
            in MediaItem.ALL_PHOTO_EXTENSIONS -> MediaType.PHOTO
            in MediaItem.ALL_VIDEO_EXTENSIONS -> MediaType.VIDEO
            in MediaItem.ALL_AUDIO_EXTENSIONS -> MediaType.AUDIO
            else -> null
        }
    }

    private fun guessMimeType(ext: String, type: MediaType): String {
        return when (type) {
            MediaType.PHOTO -> if (ext.isNotEmpty()) "image/$ext" else "image/*"
            MediaType.VIDEO -> if (ext.isNotEmpty()) "video/$ext" else "video/*"
            MediaType.AUDIO -> if (ext.isNotEmpty()) "audio/$ext" else "audio/*"
        }
    }

    private fun formatDateGroup(dateFormat: DateFormat, epochSeconds: Long): String {
        if (epochSeconds <= 0L) return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return dateFormat.format(Date(epochSeconds * 1000L))
    }

    sealed interface DeleteResult {
        data object Deleted : DeleteResult
        data class RequiresConfirmation(val intentSender: IntentSender) : DeleteResult
        data object Failed : DeleteResult
    }
}
