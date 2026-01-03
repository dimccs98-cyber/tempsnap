package com.example.tempsnap

import android.app.Application
import com.example.tempsnap.worker.CleanupWorker

class TempSnapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CleanupWorker.schedule(this)
    }
}
