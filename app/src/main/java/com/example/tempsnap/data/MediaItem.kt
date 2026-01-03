package com.example.tempsnap.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uriString: String,
    val mediaType: Int, // 0=Photo, 1=Video
    val createTime: Long,
    val expireTime: Long,
    val duration: Long = 0 // 视频时长（毫秒）
) {
    companion object {
        const val TYPE_PHOTO = 0
        const val TYPE_VIDEO = 1
    }
    
    val isVideo: Boolean get() = mediaType == TYPE_VIDEO
}
