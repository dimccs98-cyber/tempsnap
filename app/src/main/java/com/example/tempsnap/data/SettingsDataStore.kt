package com.example.tempsnap.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    
    companion object {
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val VIDEO_QUALITY = intPreferencesKey("video_quality") // 720 or 1080
        val CLEANUP_NOTIFICATION = booleanPreferencesKey("cleanup_notification")
        val LAST_LENS_FACING = intPreferencesKey("last_lens_facing")
        val FLASH_MODE = intPreferencesKey("flash_mode")
        
        const val QUALITY_720P = 720
        const val QUALITY_1080P = 1080
    }
    
    val retentionDays: Flow<Int> = context.dataStore.data.map { it[RETENTION_DAYS] ?: 7 }
    val videoQuality: Flow<Int> = context.dataStore.data.map { it[VIDEO_QUALITY] ?: QUALITY_1080P }
    val cleanupNotification: Flow<Boolean> = context.dataStore.data.map { it[CLEANUP_NOTIFICATION] ?: false }
    val lastLensFacing: Flow<Int> = context.dataStore.data.map { it[LAST_LENS_FACING] ?: 0 }
    val flashMode: Flow<Int> = context.dataStore.data.map { it[FLASH_MODE] ?: 0 }
    
    suspend fun setRetentionDays(days: Int) {
        context.dataStore.edit { it[RETENTION_DAYS] = days }
    }
    
    suspend fun setVideoQuality(quality: Int) {
        context.dataStore.edit { it[VIDEO_QUALITY] = quality }
    }
    
    suspend fun setCleanupNotification(enabled: Boolean) {
        context.dataStore.edit { it[CLEANUP_NOTIFICATION] = enabled }
    }
    
    suspend fun setLastLensFacing(facing: Int) {
        context.dataStore.edit { it[LAST_LENS_FACING] = facing }
    }
    
    suspend fun setFlashMode(mode: Int) {
        context.dataStore.edit { it[FLASH_MODE] = mode }
    }
}
