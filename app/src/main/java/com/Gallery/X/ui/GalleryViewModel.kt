package com.Gallery.X.ui

import android.app.Application
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.Gallery.X.data.AudioTargetFormat
import com.Gallery.X.data.ExifDetails
import com.Gallery.X.data.ExportState
import com.Gallery.X.data.GallerySettings
import com.Gallery.X.data.ImageTargetFormat
import com.Gallery.X.data.MediaItem
import com.Gallery.X.data.MediaRepository
import com.Gallery.X.data.MediaType
import com.Gallery.X.data.PermissionAccessLevel
import com.Gallery.X.data.PhotoDateSection
import com.Gallery.X.data.SettingsDataStore
import com.Gallery.X.data.VideoTargetFormat
import com.Gallery.X.media.MediaTranscodeEngine
import com.Gallery.X.media.PhotoConverterEngine
import com.Gallery.X.media.PhotoFilterPreset
import com.Gallery.X.media.VideoAspectPreset
import com.Gallery.X.media.VideoFilterPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppTab {
    PHOTOS,
    VIDEOS,
    AUDIO,
    SETTINGS
}

data class AudioPlayerUiState(
    val currentTrack: MediaItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isMuted: Boolean = false,
    val isLooping: Boolean = false
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val settingsStore = SettingsDataStore(application)

    val settings: StateFlow<GallerySettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, GallerySettings())

    private val _permissionLevel = MutableStateFlow(PermissionAccessLevel.DENIED)
    val permissionLevel: StateFlow<PermissionAccessLevel> = _permissionLevel.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    private var observerJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPhotoFolder = MutableStateFlow<String?>(null)
    val selectedPhotoFolder: StateFlow<String?> = _selectedPhotoFolder.asStateFlow()

    private val _selectedVideoFolder = MutableStateFlow<String?>(null)
    val selectedVideoFolder: StateFlow<String?> = _selectedVideoFolder.asStateFlow()

    private val _selectedAudioFolder = MutableStateFlow<String?>(null)
    val selectedAudioFolder: StateFlow<String?> = _selectedAudioFolder.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _externalOpenItem = MutableStateFlow<MediaItem?>(null)
    val externalOpenItem: StateFlow<MediaItem?> = _externalOpenItem.asStateFlow()

    private val _audioPlayerState = MutableStateFlow(AudioPlayerUiState())
    val audioPlayerState: StateFlow<AudioPlayerUiState> = _audioPlayerState.asStateFlow()

    private var audioExoPlayer: ExoPlayer? = null
    private var audioTickerJob: Job? = null

    val photoFolders: StateFlow<List<String>> = _allMedia
        .combine(_permissionLevel) { items, _ ->
            items.asSequence()
                .filter { it.mediaType == MediaType.PHOTO }
                .map { it.bucketName }
                .distinct()
                .sorted()
                .toList()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoFolders: StateFlow<List<String>> = _allMedia
        .combine(_permissionLevel) { items, _ ->
            items.asSequence()
                .filter { it.mediaType == MediaType.VIDEO }
                .map { it.bucketName }
                .distinct()
                .sorted()
                .toList()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioFolders: StateFlow<List<String>> = _allMedia
        .combine(_permissionLevel) { items, _ ->
            items.asSequence()
                .filter { it.mediaType == MediaType.AUDIO }
                .map { it.bucketName }
                .distinct()
                .sorted()
                .toList()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredPhotos: StateFlow<List<MediaItem>> = combine(
        _allMedia,
        _searchQuery,
        _selectedPhotoFolder
    ) { items, query, folder ->
        val q = query.trim().lowercase()
        items.filter { item ->
            item.mediaType == MediaType.PHOTO &&
                (folder == null || item.bucketName == folder) &&
                (q.isEmpty() || matchesQuery(item, q))
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val photoDateSections: StateFlow<List<PhotoDateSection>> = filteredPhotos
        .combine(_selectedPhotoFolder) { photos, _ ->
            photos.groupBy { it.dateGroupLabel }
                .map { (date, list) -> PhotoDateSection(date, list) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVideos: StateFlow<List<MediaItem>> = combine(
        _allMedia,
        _searchQuery,
        _selectedVideoFolder
    ) { items, query, folder ->
        val q = query.trim().lowercase()
        items.filter { item ->
            item.mediaType == MediaType.VIDEO &&
                (folder == null || item.bucketName == folder) &&
                (q.isEmpty() || matchesQuery(item, q))
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAudio: StateFlow<List<MediaItem>> = combine(
        _allMedia,
        _searchQuery,
        _selectedAudioFolder
    ) { items, query, folder ->
        val q = query.trim().lowercase()
        items.filter { item ->
            item.mediaType == MediaType.AUDIO &&
                (folder == null || item.bucketName == folder) &&
                (q.isEmpty() || matchesQuery(item, q))
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun matchesQuery(item: MediaItem, q: String): Boolean {
        return item.displayName.lowercase().contains(q) ||
            item.bucketName.lowercase().contains(q) ||
            item.dateGroupLabel.lowercase().contains(q) ||
            item.extension.lowercase().contains(q) ||
            item.artist.lowercase().contains(q)
    }

    fun updatePermissionLevel(level: PermissionAccessLevel) {
        val prev = _permissionLevel.value
        _permissionLevel.value = level
        if (level != PermissionAccessLevel.DENIED) {
            if (observerJob == null || prev == PermissionAccessLevel.DENIED) {
                startObservingLibrary()
            } else {
                refreshLibrary()
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _isScanning.value = true
            _allMedia.value = repository.queryAllMedia()
            _isScanning.value = false
        }
    }

    private fun startObservingLibrary() {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            _isScanning.value = true
            repository.observeMediaLibrary().collect { list ->
                _allMedia.value = list
                _isScanning.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectPhotoFolder(folder: String?) {
        _selectedPhotoFolder.value = folder
    }

    fun selectVideoFolder(folder: String?) {
        _selectedVideoFolder.value = folder
    }

    fun selectAudioFolder(folder: String?) {
        _selectedAudioFolder.value = folder
    }

    fun resolveExternalOpenIntent(uri: Uri, mimeType: String?) {
        viewModelScope.launch {
            val item = repository.resolveExternalUri(uri, mimeType)
            _externalOpenItem.value = item
        }
    }

    fun consumeExternalOpenItem() {
        _externalOpenItem.value = null
    }

    suspend fun loadExif(item: MediaItem): ExifDetails {
        return repository.loadExifDetails(item)
    }

    fun shareItem(item: MediaItem) {
        repository.shareMediaItem(item)
    }

    fun deleteItem(
        item: MediaItem,
        onNeedConfirmation: (IntentSender) -> Unit,
        onCompleted: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = repository.requestDelete(item)) {
                MediaRepository.DeleteResult.Deleted -> {
                    refreshLibrary()
                    onCompleted(true)
                }
                is MediaRepository.DeleteResult.RequiresConfirmation -> {
                    onNeedConfirmation(res.intentSender)
                }
                MediaRepository.DeleteResult.Failed -> {
                    onCompleted(false)
                }
            }
        }
    }

    // --- Audio Playback Engine (Persistent Mini-Player + Audio Tab) ---

    @OptIn(UnstableApi::class)
    private fun ensureAudioPlayer(): ExoPlayer {
        return audioExoPlayer ?: run {
            val renderersFactory = DefaultRenderersFactory(getApplication())
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)

            ExoPlayer.Builder(getApplication(), renderersFactory)
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _audioPlayerState.update { it.copy(isPlaying = isPlaying) }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                _audioPlayerState.update {
                                    it.copy(durationMs = duration.coerceAtLeast(0L))
                                }
                            } else if (playbackState == Player.STATE_ENDED && !isCurrentMediaItemDynamic) {
                                if (repeatMode == Player.REPEAT_MODE_OFF) {
                                    _audioPlayerState.update { it.copy(isPlaying = false, positionMs = 0L) }
                                }
                            }
                        }
                    })
                }
                .also { audioExoPlayer = it }
        }
    }

    fun playOrToggleAudio(item: MediaItem) {
        val current = _audioPlayerState.value.currentTrack
        val player = ensureAudioPlayer()
        if (current?.uri == item.uri) {
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0L)
                }
                player.play()
            }
            return
        }

        player.setMediaItem(Media3Item.fromUri(item.uri))
        player.prepare()
        player.play()
        _audioPlayerState.update {
            it.copy(
                currentTrack = item,
                isPlaying = true,
                positionMs = 0L,
                durationMs = item.durationMs.coerceAtLeast(1L)
            )
        }
        startAudioTicker()
    }

    fun toggleAudioPlayPause() {
        val player = audioExoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    fun seekAudioTo(ms: Long) {
        audioExoPlayer?.seekTo(ms.coerceAtLeast(0L))
        _audioPlayerState.update { it.copy(positionMs = ms.coerceAtLeast(0L)) }
    }

    fun toggleAudioMute() {
        val player = audioExoPlayer ?: return
        val nextMute = !_audioPlayerState.value.isMuted
        player.volume = if (nextMute) 0f else 1f
        _audioPlayerState.update { it.copy(isMuted = nextMute) }
    }

    fun toggleAudioLoop() {
        val player = audioExoPlayer ?: return
        val nextLoop = !_audioPlayerState.value.isLooping
        player.repeatMode = if (nextLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _audioPlayerState.update { it.copy(isLooping = nextLoop) }
    }

    fun stopAudioMiniPlayer() {
        audioTickerJob?.cancel()
        audioExoPlayer?.stop()
        _audioPlayerState.value = AudioPlayerUiState()
    }

    private fun startAudioTicker() {
        audioTickerJob?.cancel()
        audioTickerJob = viewModelScope.launch {
            while (isActive) {
                val player = audioExoPlayer
                if (player != null && _audioPlayerState.value.currentTrack != null) {
                    _audioPlayerState.update {
                        it.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.takeIf { d -> d > 0L } ?: it.durationMs,
                            isPlaying = player.isPlaying
                        )
                    }
                }
                delay(250L)
            }
        }
    }

    // --- Studio Exports & Universal Format Converters ---

    fun exportPhoto(
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
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Processing(35)
            val savedUri = PhotoConverterEngine.exportEditedPhoto(
                context = getApplication(),
                sourceBitmap = sourceBitmap,
                originalName = originalName,
                cropRectNormalized = cropRect,
                rotationDegrees = rotationDegrees,
                flipHorizontal = flipH,
                flipVertical = flipV,
                filter = filter,
                brightness = brightness,
                contrast = contrast,
                targetFormat = targetFormat,
                quality = quality
            )
            if (savedUri != null) {
                refreshLibrary()
                _exportState.value = ExportState.Success(
                    outputUri = savedUri,
                    summaryMessage = "${targetFormat.label} (${targetFormat.extension.uppercase()})"
                )
            } else {
                _exportState.value = ExportState.Error("Unable to encode ${targetFormat.label}")
            }
        }
    }

    fun exportVideo(
        item: MediaItem,
        trimStartMs: Long,
        trimEndMs: Long,
        aspectPreset: VideoAspectPreset,
        filterPreset: VideoFilterPreset,
        targetFormat: VideoTargetFormat
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Processing(0)
            val res = MediaTranscodeEngine.exportEditedVideo(
                context = getApplication(),
                inputUri = item.uri,
                originalName = item.displayName,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                aspectPreset = aspectPreset,
                filterPreset = filterPreset,
                targetFormat = targetFormat,
                onProgress = { pct -> _exportState.value = ExportState.Processing(pct) }
            )
            res.onSuccess { (uri, summary) ->
                refreshLibrary()
                _exportState.value = ExportState.Success(uri, summary)
            }.onFailure { err ->
                _exportState.value = ExportState.Error(err.localizedMessage ?: "Video export failed")
            }
        }
    }

    fun extractAudioFromVideoOrConvertAudio(
        item: MediaItem,
        trimStartMs: Long,
        trimEndMs: Long,
        fadeIn: Boolean,
        fadeOut: Boolean,
        targetFormat: AudioTargetFormat
    ) {
        viewModelScope.launch {
            _exportState.value = ExportState.Processing(0)
            val res = MediaTranscodeEngine.exportAudioOrExtractFromVideo(
                context = getApplication(),
                inputUri = item.uri,
                originalName = item.displayName,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                totalDurationMs = item.durationMs.coerceAtLeast(1000L),
                fadeIn = fadeIn,
                fadeOut = fadeOut,
                targetFormat = targetFormat,
                onProgress = { pct -> _exportState.value = ExportState.Processing(pct) }
            )
            res.onSuccess { (uri, summary) ->
                refreshLibrary()
                _exportState.value = ExportState.Success(uri, summary)
            }.onFailure { err ->
                _exportState.value = ExportState.Error(err.localizedMessage ?: "Audio conversion failed")
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    // --- Settings Persistence ---

    fun setUseCustomTheme(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseCustomTheme(enabled) }
    }

    fun setBackgroundColor(argb: Long) {
        viewModelScope.launch { settingsStore.setBackgroundColor(argb) }
    }

    fun setPrimaryTextColor(argb: Long) {
        viewModelScope.launch { settingsStore.setPrimaryTextColor(argb) }
    }

    fun setAccentColor(argb: Long) {
        viewModelScope.launch { settingsStore.setAccentColor(argb) }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { settingsStore.setGridColumns(columns) }
    }

    fun setLanguageTag(tag: String) {
        viewModelScope.launch { settingsStore.setLanguageTag(tag) }
    }

    fun resetThemeDefaults(isSystemDark: Boolean) {
        viewModelScope.launch { settingsStore.resetThemeDefaults(isSystemDark) }
    }

    override fun onCleared() {
        super.onCleared()
        audioTickerJob?.cancel()
        audioExoPlayer?.release()
        audioExoPlayer = null
    }
}
