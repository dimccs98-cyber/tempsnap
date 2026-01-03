package com.example.tempsnap.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.*
import com.example.tempsnap.R
import com.example.tempsnap.data.AppDatabase
import com.example.tempsnap.data.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class CleanupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        const val WORK_NAME = "cleanup_expired_media"
        private const val CHANNEL_ID = "cleanup_notification"
        
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
    
    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(context)
        val dao = database.mediaItemDao()
        val settings = SettingsDataStore(context)
        
        val currentTime = System.currentTimeMillis()
        val expiredItems = dao.getExpiredItems(currentTime)
        
        if (expiredItems.isEmpty()) return Result.success()
        
        val showNotification = settings.cleanupNotification.first()
        var deletedCount = 0
        
        for (item in expiredItems) {
            try {
                val uri = android.net.Uri.parse(item.uriString)
                context.contentResolver.delete(uri, null, null)
                deletedCount++
            } catch (e: SecurityException) {
                // 用户移动了文件，失去 Owner 身份
            } catch (e: Exception) {
                // 文件可能已被用户删除
            } finally {
                // 无论删除结果如何，都从数据库移除记录（防死循环机制）
                dao.delete(item)
            }
        }
        
        if (showNotification && deletedCount > 0) {
            showCleanupNotification(deletedCount)
        }
        
        return Result.success()
    }
    
    private fun showCleanupNotification(count: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "清理通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "临时照片清理通知"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("TempSnap")
            .setContentText("已清理 $count 个过期文件")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(1, notification)
    }
}
