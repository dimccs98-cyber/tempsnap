package com.example.tempsnap.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY createTime DESC")
    fun getAllItems(): Flow<List<MediaItem>>
    
    @Query("SELECT * FROM media_items ORDER BY createTime DESC LIMIT 1")
    fun getLatestItem(): Flow<MediaItem?>
    
    @Query("SELECT COUNT(*) FROM media_items")
    fun getItemCount(): Flow<Int>
    
    @Query("SELECT * FROM media_items WHERE expireTime < :currentTime")
    suspend fun getExpiredItems(currentTime: Long): List<MediaItem>
    
    @Insert
    suspend fun insert(item: MediaItem): Long
    
    @Delete
    suspend fun delete(item: MediaItem)
    
    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM media_items WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)
}
