package com.example.tempsnap.ui.list

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tempsnap.data.AppDatabase
import com.example.tempsnap.data.MediaItem
import com.example.tempsnap.data.SettingsDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MediaListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.mediaItemDao()
    val settings = SettingsDataStore(application)
    
    val allItems: StateFlow<List<MediaItem>> = dao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val retentionDays: StateFlow<Int> = settings.retentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)
    
    val videoQuality: StateFlow<Int> = settings.videoQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.QUALITY_1080P)
    
    val cleanupNotification: StateFlow<Boolean> = settings.cleanupNotification
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    private val _selectedItems = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItems: StateFlow<Set<Long>> = _selectedItems.asStateFlow()
    
    fun toggleSelection(itemId: Long) {
        _selectedItems.value = if (_selectedItems.value.contains(itemId)) {
            _selectedItems.value - itemId
        } else {
            _selectedItems.value + itemId
        }
    }
    
    fun clearSelection() {
        _selectedItems.value = emptySet()
    }
    
    // 永久保留：从删除队列移除
    fun keepSelectedItems() {
        viewModelScope.launch {
            val items = allItems.value.filter { _selectedItems.value.contains(it.id) }
            items.forEach { dao.delete(it) }
            clearSelection()
        }
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
    
    fun getRemainingTime(item: MediaItem): String {
        val remaining = item.expireTime - System.currentTimeMillis()
        if (remaining <= 0) return "即将删除"
        
        val hours = remaining / (1000 * 60 * 60)
        val days = hours / 24
        val minutes = remaining / (1000 * 60)
        
        return when {
            days > 0 -> "${days}天后删除"
            hours > 0 -> "${hours}小时后删除"
            else -> "${minutes}分钟后删除"
        }
    }
    
    fun getRemainingTimeColor(item: MediaItem): androidx.compose.ui.graphics.Color {
        val remaining = item.expireTime - System.currentTimeMillis()
        val hours = remaining / (1000 * 60 * 60)
        
        return when {
            hours < 1 -> androidx.compose.ui.graphics.Color.Red
            hours < 24 -> androidx.compose.ui.graphics.Color.Yellow
            else -> androidx.compose.ui.graphics.Color.White
        }
    }
}
