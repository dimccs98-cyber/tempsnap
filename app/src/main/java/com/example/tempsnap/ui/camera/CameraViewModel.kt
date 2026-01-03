package com.example.tempsnap.ui.camera

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tempsnap.data.AppDatabase
import com.example.tempsnap.data.MediaItem
import com.example.tempsnap.data.SettingsDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.mediaItemDao()
    private val settings = SettingsDataStore(application)
    
    // UI State
    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()
    
    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_AUTO)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()
    
    private val _lensFacing = MutableStateFlow(0) // 0=back, 1=front
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()
    
    // Data
    val latestItem: StateFlow<MediaItem?> = dao.getLatestItem()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val itemCount: StateFlow<Int> = dao.getItemCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    val retentionDays: StateFlow<Int> = settings.retentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)
    
    val videoQuality: StateFlow<Int> = settings.videoQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.QUALITY_1080P)
    
    val cleanupNotification: StateFlow<Boolean> = settings.cleanupNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    init {
        viewModelScope.launch {
            settings.lastLensFacing.collect { _lensFacing.value = it }
        }
        viewModelScope.launch {
            settings.flashMode.collect { _flashMode.value = it }
        }
    }
    
    fun toggleMode() {
        _isVideoMode.value = !_isVideoMode.value
    }
    
    fun setVideoMode(isVideo: Boolean) {
        _isVideoMode.value = isVideo
    }
    
    fun toggleFlash() {
        val newMode = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }
        _flashMode.value = newMode
        viewModelScope.launch { settings.setFlashMode(newMode) }
    }
    
    fun toggleLens() {
        val newFacing = if (_lensFacing.value == 0) 1 else 0
        _lensFacing.value = newFacing
        viewModelScope.launch { settings.setLastLensFacing(newFacing) }
    }
    
    fun setRecording(recording: Boolean) {
        _isRecording.value = recording
        if (!recording) _recordingDuration.value = 0
    }
    
    fun updateRecordingDuration(duration: Long) {
        _recordingDuration.value = duration
    }
    
    fun createImageContentValues(): ContentValues {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "TEMP_$timestamp.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/TempSnap")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
    }
    
    fun createVideoContentValues(): ContentValues {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "TEMP_$timestamp.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/TempSnap")
        }
    }
    
    fun onMediaCaptured(uri: Uri, isVideo: Boolean, duration: Long = 0) {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val expireTime = currentTime + TimeUnit.DAYS.toMillis(retentionDays.value.toLong())
            
            val item = MediaItem(
                uriString = uri.toString(),
                mediaType = if (isVideo) MediaItem.TYPE_VIDEO else MediaItem.TYPE_PHOTO,
                createTime = currentTime,
                expireTime = expireTime,
                duration = duration
            )
            dao.insert(item)
        }
    }
    
    fun getQualitySelector(): QualitySelector {
        val quality = if (videoQuality.value == SettingsDataStore.QUALITY_720P) {
            Quality.HD
        } else {
            Quality.FHD
        }
        return QualitySelector.from(quality)
    }
    
    fun setRetentionDays(days: Int) {
        viewModelScope.launch { settings.setRetentionDays(days) }
    }
    
    fun setVideoQuality(quality: Int) {
        viewModelScope.launch { settings.setVideoQuality(quality) }
    }
    
    fun setCleanupNotification(enabled: Boolean) {
        viewModelScope.launch { settings.setCleanupNotification(enabled) }
    }
}
